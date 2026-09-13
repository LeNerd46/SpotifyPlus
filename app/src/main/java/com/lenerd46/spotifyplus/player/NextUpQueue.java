package com.lenerd46.spotifyplus.player;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public final class NextUpQueue {
    private final ClassLoader classLoader;

    public NextUpQueue(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public NextUpAction resolve(Object[] roots, Object track) {
        return resolve(roots, track, 2);
    }

    public NextUpAction resolve(Object[] roots, Object track, int depth) {
        try {
            if (track == null || !String.valueOf(XposedHelpers.callMethod(track, "uri")).startsWith("spotify:track:")) return null;

            Class<?> setQueueCommandClass = XposedHelpers.findClass("com.spotify.player.model.command.SetQueueCommand", classLoader);
            Object queueRepository = null;
            for (Object root : roots) {
                queueRepository = findQueueRepository(root, setQueueCommandClass, depth, new IdentityHashMap<>());
                if (queueRepository != null) break;
            }
            if (queueRepository == null) throw new IllegalStateException("Could not find a queue repository beneath any context-menu item dependency");
            Method queueDispatchMethod = findSingleArgumentMethod(queueRepository.getClass(), setQueueCommandClass);
            Class<?> flowableClass = XposedHelpers.findClass("io.reactivex.rxjava3.core.Flowable", classLoader);
            Object queueStream = getField(queueRepository, flowableClass);
            if (queueStream == null) throw new IllegalStateException("Could not find the PlayerQueue Flowable in " + queueRepository.getClass().getName());
            Class<?> singleClass = XposedHelpers.findClass("io.reactivex.rxjava3.core.Single", classLoader);
            List<Method> queueReadMethods = Arrays.stream(queueStream.getClass().getMethods()).filter(method -> !Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && singleClass.isAssignableFrom(method.getReturnType())).collect(Collectors.toList());
            if (queueReadMethods.size() != 1) throw new IllegalStateException("Expected one zero-parameter Single method on " + queueStream.getClass().getName() + " but found " + queueReadMethods.size() + ": " + queueReadMethods.stream().map(Method::toString).collect(Collectors.joining(", ")));
            Method setQueueFactory = Arrays.stream(setQueueCommandClass.getDeclaredMethods()).filter(method -> Modifier.isStatic(method.getModifiers()) && method.getReturnType() == setQueueCommandClass && Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class, List.class, List.class})).findFirst().orElseThrow(() -> new NoSuchMethodException("No (String, List, List) SetQueueCommand factory"));
            return new NextUpAction(queueRepository, queueStream, queueDispatchMethod, queueReadMethods.get(0), setQueueFactory, track);
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Failed resolving the Next Up queue action: " + t);
            return null;
        }
    }

    public void insertAtTopOfNextUp(NextUpAction action) {
        try {
            XposedHelpers.callMethod(createUpdateSingle(action), "subscribe",
                    newRxConsumer(ignored -> { }), newRxConsumer(this::logNextUpError));
        } catch (Throwable t) {
            logNextUpError(t);
        }
    }

    public Object createUpdateSingle(NextUpAction action) throws Exception {
        Object queueSingle = action.queueReadMethod.invoke(action.queueStream);
        return flatMap(queueSingle, queue -> {
            List<?> currentNextTracks = (List<?>) XposedHelpers.callMethod(queue, "nextTracks");
            List<?> currentPrevTracks = (List<?>) XposedHelpers.callMethod(queue, "prevTracks");
            String revision = (String) XposedHelpers.callMethod(queue, "revision");
            ArrayList<Object> nextTracks = new ArrayList<>(currentNextTracks);
            int nextUpStart = 0;
            while (nextUpStart < nextTracks.size() && isQueuedTrack(nextTracks.get(nextUpStart))) {
                nextUpStart++;
            }
            Object track = withoutQueuedFlag(action.track);
            if (track == null) throw new IllegalStateException("Could not create an unqueued ContextTrack");
            nextTracks.add(nextUpStart, track);
            Object command = action.setQueueFactory.invoke(null, revision, nextTracks, new ArrayList<>(currentPrevTracks));
            return action.queueDispatchMethod.invoke(action.queueRepository, command);
        });
    }

    public interface RxFunction {
        Object apply(Object value) throws Exception;
    }

    public Object flatMap(Object single, RxFunction callback) {
        Class<?> function = XposedHelpers.findClass("io.reactivex.rxjava3.functions.Function", classLoader);
        Object mapper = Proxy.newProxyInstance(classLoader, new Class<?>[]{function}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "apply": return callback.apply(args[0]);
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "toString": return "SpotifyPlusNextUpFunction";
                default: return null;
            }
        });
        return XposedHelpers.callMethod(single, "flatMap", mapper);
    }

    private Object withoutQueuedFlag(Object track) {
        try {
            Object metadataValue = XposedHelpers.callMethod(track, "metadata");
            if (!(metadataValue instanceof Map)) return null;

            HashMap<Object, Object> metadata = new HashMap<>((Map<?, ?>) metadataValue);
            metadata.remove("is_queued");

            Object builder = XposedHelpers.callMethod(track, "toBuilder");
            XposedHelpers.callMethod(builder, "metadata", metadata);
            return XposedHelpers.callMethod(builder, "build");
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Failed clearing is_queued from Next Up track: " + t);
            return null;
        }
    }

    private boolean isQueuedTrack(Object track) {
        try {
            Object metadataValue = XposedHelpers.callMethod(track, "metadata");
            if (!(metadataValue instanceof Map)) return false;
            return Boolean.parseBoolean(String.valueOf(((Map<?, ?>) metadataValue).get("is_queued")));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object findQueueRepository(Object value, Class<?> commandType, int remainingDepth, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || remainingDepth < 0 || visited.put(value, Boolean.TRUE) != null) return null;
        if (findSingleArgumentMethod(value.getClass(), commandType) != null) return value;
        if (remainingDepth == 0) return null;
        String name = value.getClass().getName();
        if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("io.reactivex.")) return null;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object repository = findQueueRepository(field.get(value), commandType, remainingDepth - 1, visited);
                    if (repository != null) return repository;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private Method findSingleArgumentMethod(Class<?> receiverClass, Class<?> argumentClass) {
        Class<?> type = receiverClass;
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) && parameterTypes.length == 1 && parameterTypes[0] == argumentClass) {
                    method.setAccessible(true);
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private Object newRxConsumer(java.util.function.Consumer<Object> callback) {
        Class<?> consumerClass = XposedHelpers.findClass(
                "io.reactivex.rxjava3.functions.Consumer",
                classLoader
        );
        return java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{consumerClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "accept":
                            callback.accept(args[0]);
                            return null;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "SpotifyPlusRxConsumer";
                        default:
                            return null;
                    }
                }
        );
    }

    private void logNextUpError(Object error) {
        XposedBridge.log("[SpotifyPlus] Failed adding track to Next Up: " + error);
        if (error instanceof Throwable) {
            XposedBridge.log((Throwable) error);
        }
    }

    public static final class NextUpAction {
        final Object queueRepository;
        final Object queueStream;
        final Method queueDispatchMethod;
        final Method queueReadMethod;
        final Method setQueueFactory;
        final Object track;

        NextUpAction(Object queueRepository, Object queueStream, Method queueDispatchMethod, Method queueReadMethod, Method setQueueFactory, Object track) {
            this.queueRepository = queueRepository;
            this.queueStream = queueStream;
            this.queueDispatchMethod = queueDispatchMethod;
            this.queueReadMethod = queueReadMethod;
            this.setQueueFactory = setQueueFactory;
            this.track = track;
        }
    }

    private Object getField(Object obj, Class<?> wantType) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;
                    if (wantType.isInstance(v)) return v;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

}

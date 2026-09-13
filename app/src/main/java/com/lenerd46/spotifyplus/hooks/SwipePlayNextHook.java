package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.player.NextUpQueue;

import android.content.SharedPreferences;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

final class SwipePlayNextHook extends SpotifyHook {
    static final String PREFERENCE = "swipe_play_next";
    private final SharedPreferences prefs;
    private final Map<String, String> swipeUris = new LinkedHashMap<>();
    private final ThreadLocal<String> legacySwipeUri = new ThreadLocal<>();
    private final ThreadLocal<Integer> legacySwipeDepth = ThreadLocal.withInitial(() -> 0);

    SwipePlayNextHook(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    protected void hook() {
        List<XC_MethodHook.Unhook> installed = new ArrayList<>();
        try {
            Class<?> eventClass = uniqueClass(ClassMatcher.create().usingStrings("interaction = "));
            Class<?> interactionClass = uniqueClass(ClassMatcher.create().usingStrings("Empty action id"));
            Class<?> gestureClass = uniqueClass(ClassMatcher.create().usingStrings("Empty interaction type"));
            Field eventInteraction = Arrays.stream(eventClass.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers())).reduce((a, b) -> {
                        throw new IllegalStateException("Ambiguous interaction event payload");
                    }).orElseThrow();
            eventInteraction.setAccessible(true);
            Field actionField = gestureClass == interactionClass ? null : uniqueField(eventInteraction.getType(), interactionClass);
            Field gestureField = gestureClass == interactionClass ? null : uniqueField(eventInteraction.getType(), gestureClass);
            Field interactionMetadata = uniqueField(interactionClass, Map.class);
            Class<?> repository = uniqueClass(ClassMatcher.create().usingStrings("GetQueue", "AddToQueue", "SetQueue"));
            if (bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .usingStrings("com.spotify.ubi.model.InteractionId"))).isEmpty()) {
                installLegacy(repository, eventClass, eventInteraction, interactionMetadata, installed);
                return;
            }
            Class<?> idClass = serializedClass("com.spotify.ubi.model.InteractionId", List.of("int", "java.lang.String"));
            Class<?> resultClass = serializedResultClass(idClass);
            Field resultId = uniqueField(resultClass, idClass);
            Field idValue = uniqueField(idClass, String.class);
            Class<?> single = XposedHelpers.findClass("io.reactivex.rxjava3.core.Single", lpparm.classLoader);
            var enqueueCandidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramTypes(int.class, String.class, String.class, List.class).returnType(single)));
            List<Method> enqueueMethods = new ArrayList<>();
            for (var candidate : enqueueCandidates) {
                Method method = candidate.getMethodInstance(lpparm.classLoader);
                if (!Modifier.isStatic(method.getModifiers()) && hasField(method.getDeclaringClass(), repository)) {
                    enqueueMethods.add(method);
                }
            }
            if (enqueueMethods.size() != 1) throw new IllegalStateException("Expected one shared enqueue service, found " + enqueueMethods.size());
            Method enqueue = enqueueMethods.get(0);
            NextUpQueue queue = new NextUpQueue(lpparm.classLoader);

            var logMethods = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().returnType(resultClass)));
            for (var candidate : logMethods) {
                Method method = candidate.getMethodInstance(lpparm.classLoader);
                if (Modifier.isAbstract(method.getModifiers()) || method.getParameterCount() == 0
                        || method.getParameterTypes()[0] != eventClass) continue;
                installed.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!prefs.getBoolean(PREFERENCE, false) || param.hasThrowable() || param.getResult() == null) return;
                        try {
                            Object payload = eventInteraction.get(param.args[0]);
                            Object interaction = actionField == null ? payload : actionField.get(payload);
                            Object gesture = gestureField == null ? payload : gestureField.get(payload);
                            if (!hasString(interaction, "add_item_to_queue") || !hasString(gesture, "swipe", "swipe_right")) return;
                            Map<?, ?> metadata = (Map<?, ?>) interactionMetadata.get(interaction);
                            Object uri = metadata.get("item_to_add_to_queue");
                            Object id = idValue.get(resultId.get(param.getResult()));
                            if (!(uri instanceof String trackUri) || !trackUri.startsWith("spotify:track:")
                                    || !(id instanceof String interactionId) || interactionId.isEmpty()) return;
                            synchronized (swipeUris) {
                                swipeUris.put(interactionId, trackUri);
                                while (swipeUris.size() > 64) swipeUris.remove(swipeUris.keySet().iterator().next());
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[SpotifyPlus][SwipePlayNext] Could not read swipe interaction: " + t);
                        }
                    }
                }));
            }
            if (installed.isEmpty()) throw new IllegalStateException("No interaction logger implementations found");

            installed.add(XposedBridge.hookMethod(enqueue, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean(PREFERENCE, false)) return;
                    List<?> tracks = (List<?>) param.args[3];
                    if (tracks == null || tracks.size() != 1 || tracks.get(0) == null) return;
                    String expectedUri;
                    synchronized (swipeUris) {
                        expectedUri = swipeUris.remove(param.args[2]);
                    }
                    if (expectedUri == null) return;
                    try {
                        Object track = tracks.get(0);
                        if (!expectedUri.equals(XposedHelpers.callMethod(track, "uri"))) return;
                        NextUpQueue.NextUpAction action = queue.resolve(new Object[]{param.thisObject}, track);
                        if (action == null) return;
                        Object update = queue.createUpdateSingle(action);
                        // The empty-list branch supplies Spotify's own enqueue-success result without
                        // adding anything. The outer service retains scheduling and error feedback.
                        Object[] emptyArgs = param.args.clone();
                        emptyArgs[3] = Collections.emptyList();
                        Object success = XposedBridge.invokeOriginalMethod(enqueue, param.thisObject, emptyArgs);
                        param.setResult(queue.flatMap(update, result -> {
                            if (String.valueOf(result).startsWith("Failure{reasons=")) {
                                throw new IllegalStateException("Next Up update rejected: " + result);
                            }
                            return success;
                        }));
                    } catch (Throwable t) {
                        // No subscription has happened yet: Spotify can still perform its original action.
                        XposedBridge.log("[SpotifyPlus][SwipePlayNext] Keeping native queue action: " + t);
                    }
                }
            }));
            XposedBridge.log("[SpotifyPlus][SwipePlayNext] Installed shared enqueue hook: " + enqueue);
        } catch (Throwable t) {
            for (var hook : installed) hook.unhook();
            XposedBridge.log("[SpotifyPlus][SwipePlayNext] Unsupported swipe/queue implementation: " + t);
        }
    }

    private Class<?> serializedResultClass(Class<?> idClass) throws Exception {
        Class<?> serializer = uniqueClass(ClassMatcher.create().usingStrings("com.spotify.ubi.logger.InteractionLoggingResult"));
        Set<String> models = new HashSet<>();
        for (var reader : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().declaredClass(serializer).name("deserialize")))) {
            for (MethodData call : reader.getInvokes()) {
                if (call.isConstructor() && call.getParamTypeNames().contains(idClass.getName())) models.add(call.getClassName());
            }
        }
        if (models.size() != 1) throw new IllegalStateException("Ambiguous interaction result model: " + models);
        return XposedHelpers.findClass(models.iterator().next(), lpparm.classLoader);
    }

    private static boolean hasString(Object value, String... matches) throws IllegalAccessException {
        if (value == null) return false;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            field.setAccessible(true);
            for (String match : matches) if (match.equals(field.get(value))) return true;
        }
        return false;
    }

    // The oldest implementation discards interaction IDs in several swipe callers.
    // Scope correlation to the actual swipe callback; never use a global URI/time guess.
    private void installLegacy(Class<?> repository, Class<?> eventClass, Field eventInteraction,
                               Field metadataField, List<XC_MethodHook.Unhook> installed) throws Exception {
        // Before serializers existed, logging returned a two-field Serializable
        // (interaction ID, optional page ID). Derive it from the logger's API.
        var loggerMethods = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().paramTypes(eventClass)));
        Set<Class<?>> resultTypes = new HashSet<>();
        for (var logger : loggerMethods) {
            if (!logger.isMethod() || Modifier.isAbstract(logger.getModifiers())) continue;
            Class<?> result = logger.getMethodInstance(lpparm.classLoader).getReturnType();
            // Framework types (notably Android's String) can share the field/constructor
            // counts. The model must belong to Spotify's DEX, as in the static audit.
            if (result.getClassLoader() != eventClass.getClassLoader()) continue;
            if (java.io.Serializable.class.isAssignableFrom(result)
                    && Arrays.stream(result.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).count() == 2
                    && Arrays.stream(result.getDeclaredConstructors()).anyMatch(c -> c.getParameterCount() == 2)) resultTypes.add(result);
        }
        if (resultTypes.size() != 1) throw new IllegalStateException("Ambiguous legacy interaction logging result: " + resultTypes);
        Class<?> resultType = resultTypes.iterator().next();
        Class<?> idType = Arrays.stream(resultType.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 2)
                .findFirst().orElseThrow().getParameterTypes()[0];
        Field resultId = uniqueField(resultType, idType);
        Field idString = uniqueField(idType, String.class);
        var scopes = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingStrings("add_item_to_queue", "swipe")));
        scopes.addAll(bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingStrings("add_item_to_queue", "swipe_right"))));
        Set<String> scoped = new HashSet<>();
        for (var scope : scopes) {
            if (!scope.isMethod() || !scoped.add(scope.getDescriptor())) continue;
            installed.add(XposedBridge.hookMethod(scope.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    param.setObjectExtra("previousSwipeUri", legacySwipeUri.get());
                    legacySwipeDepth.set(legacySwipeDepth.get() + 1);
                    legacySwipeUri.remove();
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    int depth = legacySwipeDepth.get() - 1;
                    if (depth == 0) { legacySwipeDepth.remove(); legacySwipeUri.remove(); }
                    else { legacySwipeDepth.set(depth); legacySwipeUri.set((String) param.getObjectExtra("previousSwipeUri")); }
                }
            }));
        }
        if (scoped.isEmpty()) throw new IllegalStateException("No legacy swipe callbacks");
        for (var logger : loggerMethods) {
            if (!logger.isMethod() || Modifier.isAbstract(logger.getModifiers()) || logger.getReturnTypeName().equals("void")) continue;
            installed.add(XposedBridge.hookMethod(logger.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.hasThrowable() || !prefs.getBoolean(PREFERENCE, false)) return;
                    Object interaction = eventInteraction.get(param.args[0]);
                    if (!hasString(interaction, "add_item_to_queue") || !hasString(interaction, "swipe", "swipe_right")) return;
                    Object uri = ((Map<?, ?>) metadataField.get(interaction)).get("item_to_add_to_queue");
                    if (!(uri instanceof String trackUri) || !trackUri.startsWith("spotify:track:")) return;
                    if (legacySwipeDepth.get() > 0) legacySwipeUri.set(trackUri);
                    if (resultType.isInstance(param.getResult())) {
                        Object id = idString.get(resultId.get(param.getResult()));
                        if (id instanceof String interactionId && !interactionId.isEmpty()) {
                            synchronized (swipeUris) {
                                swipeUris.put(interactionId, trackUri);
                                while (swipeUris.size() > 64) swipeUris.remove(swipeUris.keySet().iterator().next());
                            }
                        }
                    }
                }
            }));
        }
        Method enqueue = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().declaredClass(repository)
                .paramTypes("com.spotify.player.model.ContextTrack").returnType("io.reactivex.rxjava3.core.Single")))
                .single().getMethodInstance(lpparm.classLoader);
        NextUpQueue queue = new NextUpQueue(lpparm.classLoader);
        installed.add(XposedBridge.hookMethod(enqueue, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String uri = legacySwipeUri.get();
                if (!prefs.getBoolean(PREFERENCE, false) || uri == null || !uri.equals(XposedHelpers.callMethod(param.args[0], "uri"))) return;
                legacySwipeUri.remove();
                NextUpQueue.NextUpAction action = queue.resolve(new Object[]{param.thisObject}, param.args[0]);
                if (action != null) param.setResult(queue.createUpdateSingle(action));
            }
        }));
        Method enqueueCommand = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().declaredClass(repository)
                .paramTypes("com.spotify.player.model.command.AddToQueueCommand").returnType("io.reactivex.rxjava3.core.Single")))
                .single().getMethodInstance(lpparm.classLoader);
        installed.add(XposedBridge.hookMethod(enqueueCommand, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean(PREFERENCE, false)) return;
                Object command = param.args[0];
                Object optional = XposedHelpers.callMethod(command, "loggingParams");
                List<Method> getters = Arrays.stream(optional.getClass().getMethods())
                        .filter(m -> !Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == Object.class).toList();
                if (getters.size() != 1) return;
                Object logging;
                try { logging = getters.get(0).invoke(optional); } catch (ReflectiveOperationException absent) { return; }
                if (logging == null) return;
                Object track = XposedHelpers.callMethod(command, "track");
                String uri = (String) XposedHelpers.callMethod(track, "uri");
                for (Object id : (Iterable<?>) XposedHelpers.callMethod(logging, "interactionIds")) {
                    String expected;
                    synchronized (swipeUris) { expected = swipeUris.remove(id); }
                    if (!uri.equals(expected)) continue;
                    NextUpQueue.NextUpAction action = queue.resolve(new Object[]{param.thisObject}, track);
                    if (action != null) param.setResult(queue.createUpdateSingle(action));
                    return;
                }
            }
        }));
        XposedBridge.log("[SpotifyPlus][SwipePlayNext] Installed legacy scoped swipe adapter");
    }

    private Class<?> uniqueClass(ClassMatcher matcher) throws ClassNotFoundException {
        var classes = bridge.findClass(FindClass.create().matcher(matcher));
        if (classes.size() != 1) throw new IllegalStateException("Expected one fingerprint match, found " + classes.size());
        return classes.get(0).getInstance(lpparm.classLoader);
    }

    private Class<?> serializedClass(String serialName, List<String> constructorParams) throws Exception {
        Class<?> serializer = uniqueClass(ClassMatcher.create().usingStrings(serialName));
        var readers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(serializer).name("deserialize")));
        Set<String> constructed = new HashSet<>();
        for (var reader : readers) {
            for (MethodData invoked : reader.getInvokes()) {
                if (invoked.isConstructor() && (constructorParams == null
                        ? invoked.getParamCount() == 8 : invoked.getParamTypeNames().equals(constructorParams))) {
                    constructed.add(invoked.getClassName());
                }
            }
        }
        if (constructed.size() != 1) throw new IllegalStateException("Could not resolve model for " + serialName);
        return XposedHelpers.findClass(constructed.iterator().next(), lpparm.classLoader);
    }

    private static boolean hasField(Class<?> owner, Class<?> type) {
        return Arrays.stream(owner.getDeclaredFields()).anyMatch(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == type);
    }

    private static Field uniqueField(Class<?> owner, Class<?> type) {
        List<Field> fields = Arrays.stream(owner.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())).toList();
        if (fields.size() != 1) throw new IllegalStateException("Ambiguous " + type.getName() + " field in " + owner.getName());
        Field field = fields.get(0);
        field.setAccessible(true);
        return field;
    }
}

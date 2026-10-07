package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.ScriptSideDrawerItem;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.module.scripting.ExtensionAssetRegistry;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SideDrawerHook extends SpotifyHook {
    private static int idToUse = 8001;
    private static int resourceIdToUse = 2131957898;
    private static final AtomicReference<Object> sideDrawerBackDispatcher = new AtomicReference<>();
    private static final AtomicReference<Object> sideDrawerBackCallback = new AtomicReference<>();

    private static Class<?> bti0Class;

    private static Method routeIntentMethod;
    private static Method routeRewriteMethod;
    private static Method mainOnCreateMethod;
    private static Method mainOnNewIntentMethod;
    private static Method mainOnResumeMethod;
    private static final Map<Member, Field> drawerArrayMethods = new HashMap<>();
    private static final Set<Method> clickMethods = new HashSet<>();
    private static final Set<Member> resourceMethods = new HashSet<>();

    private static final List<ScriptSideDrawerItem> scriptItems = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<Object, Runnable> clickHandlers = Collections.synchronizedMap(new IdentityHashMap<>());

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        routeIntentMethod = findClass("p.jz60").getDeclaredMethod("l", findClass("p.goi0"));
        hook(routeIntentMethod);
        bti0Class = findClass("p.wm31");
        routeRewriteMethod = findClass("p.df5").getDeclaredMethod("U", String.class);
        hook(routeRewriteMethod);

        Class<?> main = findClass("com.spotify.music.SpotifyMainActivity");
        mainOnCreateMethod = main.getDeclaredMethod("onCreate", Bundle.class);
        hook(mainOnCreateMethod);
        mainOnNewIntentMethod = main.getDeclaredMethod("onNewIntent", Intent.class);
        hook(mainOnNewIntentMethod);
        mainOnResumeMethod = main.getDeclaredMethod("onResume");
        hook(mainOnResumeMethod);

        for (String name : List.of("p.b6y", "p.yoj0", "p.z03")) {
            Class<?> coroutine = findClass(name);
            Method method = coroutine.getDeclaredMethod("invokeSuspend", Object.class);
            Field field = coroutine.getDeclaredField("d");
            field.setAccessible(true);
            drawerArrayMethods.put(method, field);
            hook(method);
        }

        for (Method method : android.content.res.Resources.class.getDeclaredMethods()) {
            if ((method.getName().equals("getString") || method.getName().equals("getText"))
                    && method.getParameterCount() > 0 && method.getParameterTypes()[0] == int.class) {
                resourceMethods.add(method);
                hook(method);
            }
        }

        SpotifyNativeBridge.registerHandler("side", this);
    }


    @Override
    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        try {
            if (resourceMethods.contains(member)) {
                if (currentActivity.get() == null || callback.getThisObject() != currentActivity.get().getResources()) return;
                int id = (int) callback.getArgs()[0];

                var thing = scriptItems.stream().filter(x -> x.resourceId == id).findFirst();
                if (thing.isEmpty()) return;

                ScriptSideDrawerItem item = thing.get();
                callback.returnAndSkip(item.title);
            }

            if (member == mainOnCreateMethod || member == mainOnNewIntentMethod || member == mainOnResumeMethod) {
                if (callback.getThisObject() instanceof Activity activity) {
                    currentActivity = new WeakReference<>(activity);
                }
                return;
            }

            if (member == routeRewriteMethod) {
                String s = (String) callback.getArgs()[0];
                if (s == null) return;

                Uri uri = Uri.parse(s);
                if (s.contains("spotifyplus")) {
                    log(s);
                }
                if (!uri.isHierarchical()) return;
                String spotifyPlus = uri.getQueryParameter("spotifyplus");

                if (spotifyPlus != null && spotifyPlus.equals("side")) {
                    try {
                        log(s);

                        if (uri.getHost().equals("side")) {
                            openScriptSideDrawer(uri.getQueryParameter("scriptId"), uri.getQueryParameter("id"));

                            callback.returnAndSkip(newInstance(bti0Class, "spotify:home"));
                            return;
                        }
                    } catch (Exception e) {
                        logError(e);
                        return;
                    }

                }
                return;
            }

            if (drawerArrayMethods.containsKey(member)) {
                Field arrayField = drawerArrayMethods.get(member);
                Object[] items = (Object[]) arrayField.get(callback.getThisObject());
                if (items == null) return;
                Object[] originalItems = Arrays.stream(items).filter(Objects::nonNull).toArray();
                // Recognize the native Settings row rather than assuming a fixed drawer size.
                if (originalItems.length == 0) return;
                if (Arrays.stream(originalItems).anyMatch(item -> containsDrawerDestination(item, 4, new IdentityHashMap<>(), "spotify:null"))) return;
                Class<?> runtimeButtonClass = originalItems[0].getClass();
                if (Arrays.stream(originalItems).anyMatch(item -> !runtimeButtonClass.isInstance(item))) return;
                int settingsIndex = findSettingsItemIndex(originalItems);
                if (settingsIndex < 0) return;
                Object template = originalItems[settingsIndex];
                List<Object> additions = new ArrayList<>();
                for (var item : new ArrayList<>(scriptItems)) {
                    Object button = createSideDrawerButton(item.title, template, item.resourceId,
                            () -> openScriptSideDrawer(item.scriptId, item.id), item);
                    if (button != null) additions.add(button);
                }
                if (additions.isEmpty()) return;
                List<Object> updated = new ArrayList<>(Arrays.asList(originalItems));
                updated.addAll(settingsIndex + 1, additions);
                Object[] newArray = (Object[]) Array.newInstance(runtimeButtonClass, updated.size());
                arrayField.set(callback.getThisObject(), updated.toArray(newArray));
                try {
                    ReactManager.registerSurfaceSilent("sideDrawer", (ViewGroup) currentActivity.get().getWindow().getDecorView());
                } catch (Exception ignored) { }
                return;
            }

            if (member.getName().equals("invoke")) {

                Runnable runnable = clickHandlers.get(callback.getThisObject());
                if (runnable == null) return;

                try {
                    runnable.run();
                    callback.returnAndSkip(defaultValue(((Method) member).getReturnType()));
                } catch (Exception e) {
                    logError(e);
                }
            }
        } catch (Throwable t) {
            logError(t);
        }
    }


    @Override
    protected void afterHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        try {
            if (member == routeIntentMethod) {
                Object nav = callback.getArgs()[0];
                String raw = (String) getFieldValue(nav, "a");
                if (raw != null && raw.startsWith("spotifyplus:")) {
                    String path = raw.substring("spotifyplus:".length());
                    if (path.startsWith("side")) {
                        Map<String, String> params = new HashMap<>();

                        for (String part : path.split("\\?")[1].split("&")) {
                            int equals = part.indexOf("=");

                            if (equals >= 0) {
                                String key = Uri.decode(part.substring(0, equals));
                                String value = Uri.decode(part.substring(equals + 1));

                                params.put(key, value);
                                log(key + " | " + value);
                            } else {
                                params.put(Uri.decode(part), "");
                            }
                        }

                        String scriptId = params.get("scriptId");
                        String id = params.get("id");

                        openScriptSideDrawer(scriptId, id);

                        Intent newIntent = new Intent();
                        newIntent.setData(Uri.parse("spotify:null"));
                        newIntent.setClassName("com.spotify.music", "com.spotify.music.SpotifyMainActivity");

                        callback.setResult(newIntent);
                    }


                }
                return;
            }

        } catch (Throwable t) {
            logError(t);
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        if (command.equals("register")) {
            try {
                String itemId = (String) args[0];
                String scriptId = (String) args[1];
                String title = (String) args[2];
                String iconAssetId = args.length > 3 && args[3] instanceof String ? (String) args[3] : null;

                ScriptSideDrawerItem item = new ScriptSideDrawerItem(itemId, scriptId, title, iconAssetId);
                if (scriptItems.stream().anyMatch(existing -> existing.id.equals(itemId) && existing.scriptId.equals(scriptId))) return null;
                log("Registering " + itemId + " | " + scriptId);

                item.resourceId = resourceIdToUse;
                resourceIdToUse++;

                scriptItems.add(item);
            } catch (Exception ignored) {
            }
        } else if (command.equals("unregisterScript")) {
            try {
                String scriptId = (String) args[0];
                scriptItems.removeIf(item -> item.scriptId.equals(scriptId));
            } catch (Exception ignored) {
            }
        } else if (command.equals("surfaceClosed")) {
            if (args.length > 0 && "sideDrawer".equals(args[0])) {
                clearSideDrawerBackCallback();
            }
        }

        return null;
    }

    private static void openScriptSideDrawer(String scriptId, String id) {
        Activity activity = currentActivity.get();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                clearSideDrawerBackCallback();
                // Closing disposes the previous overlay; opening must attach a
                // fresh host before the extension sends its first React commit.
                SpotifyNativeBridge.attachSurfaceHost("sideDrawer", (ViewGroup) activity.getWindow().getDecorView());
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
                    android.window.OnBackInvokedCallback backCallback = () -> {
                        try {
                            JSONObject payload = new JSONObject();
                            payload.put("scriptId", scriptId);
                            payload.put("surfaceId", "sideDrawer");
                            SpotifyNativeBridge.sendEvent("android.backPressed", payload.toString());
                        } catch (Exception e) {
                            logError(e);
                        }
                    };
                    dispatcher.registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback);
                    sideDrawerBackDispatcher.set(dispatcher);
                    sideDrawerBackCallback.set(backCallback);
                }
                JSONObject payload = new JSONObject();
                payload.put("id", id);
                payload.put("scriptId", scriptId);
                SpotifyNativeBridge.sendEvent("side.press", payload.toString());
            } catch (Exception e) {
                clearSideDrawerBackCallback();
                logError(e);
            }
        });
    }

    private static void clearSideDrawerBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;

        Object dispatcherValue = sideDrawerBackDispatcher.getAndSet(null);
        Object callbackValue = sideDrawerBackCallback.getAndSet(null);
        if (!(dispatcherValue instanceof android.window.OnBackInvokedDispatcher)
            || !(callbackValue instanceof android.window.OnBackInvokedCallback)) return;

        Activity activity = currentActivity.get();
        if (activity == null) return;

        android.window.OnBackInvokedDispatcher dispatcher =
            (android.window.OnBackInvokedDispatcher) dispatcherValue;
        android.window.OnBackInvokedCallback callback =
            (android.window.OnBackInvokedCallback) callbackValue;
        activity.runOnUiThread(() -> dispatcher.unregisterOnBackInvokedCallback(callback));
    }

    private int findSettingsItemIndex(Object[] items) {
        for (int i = 0; i < items.length; i++) {
            if (containsSettingsDestination(items[i], 4, new IdentityHashMap<>())) return i;
        }
        return -1;
    }

    private boolean containsSettingsDestination(Object value, int remainingDepth, IdentityHashMap<Object, Boolean> visited) {
        return containsDrawerDestination(value, remainingDepth, visited, "spotify:settings", "spotify:preferences", "spotify:config");
    }

    private boolean containsDrawerDestination(Object value, int remainingDepth, IdentityHashMap<Object, Boolean> visited, String... destinations) {
        if (value instanceof String && Arrays.asList(destinations).contains(value)) return true;
        if (value == null || remainingDepth == 0 || visited.put(value, Boolean.TRUE) != null) return false;
        Class<?> valueClass = value.getClass();
        if (valueClass.isPrimitive() || valueClass.isEnum() || valueClass.isArray() || valueClass.getName().startsWith("java.") || valueClass.getName().startsWith("android.") || valueClass.getName().startsWith("kotlin.")) return false;
        for (Class<?> type = valueClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    if (containsDrawerDestination(field.get(value), remainingDepth - 1, visited, destinations)) return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private Object findDirectChildContainingSettings(Object owner) {
        if (owner == null) return null;
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (containsSettingsDestination(value, 3, new IdentityHashMap<>())) return value;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private Object createSideDrawerButton(String title, Object template, int resId, Runnable onClick, ScriptSideDrawerItem item) {
        try {
            Object originalContent = findDirectChildContainingSettings(template);
            Object originalProps = findDirectChildContainingSettings(originalContent);
            if (originalContent == null || originalProps == null) throw new IllegalStateException("[SideDrawerHook] Could not resolve the live Settings row content and props.");
            List<Field> propsFields = getInstanceFields(originalProps.getClass());
            int instrumentationIndex = findInstrumentationIndex(originalProps, propsFields);
            Object originalInstrumentation = readField(propsFields.get(instrumentationIndex), originalProps);
            List<Field> instrumentationFields = getInstanceFields(originalInstrumentation.getClass());
            Object[] instrumentationValues = readFieldValues(originalInstrumentation, instrumentationFields);
            int clickIndex = findClickIndex(instrumentationFields, instrumentationValues);
            Constructor<?> instrumentationConstructor = findCompatibleConstructor(originalInstrumentation.getClass(), instrumentationValues);
            instrumentationValues[clickIndex] = createClickCallback(instrumentationFields.get(clickIndex).getType(), instrumentationConstructor.getParameterTypes()[clickIndex], instrumentationValues[clickIndex], resId, onClick);
            Object newInstrumentation = instantiateLike(originalInstrumentation.getClass(), instrumentationValues);
            Object[] propsValues = readFieldValues(originalProps, propsFields);
            boolean replacedTitleResource = false;
            for (int i = 0; i < propsValues.length; i++) {
                if (i == instrumentationIndex) propsValues[i] = newInstrumentation;
                else if (propsValues[i] instanceof String && containsSettingsDestination(propsValues[i], 1, new IdentityHashMap<>())) propsValues[i] = "spotify:null";
                else if (propsValues[i] instanceof String && isSettingsTitle((String) propsValues[i])) propsValues[i] = title;
                else if (propsValues[i] instanceof Integer && isSettingsTitleResource((Integer) propsValues[i])) {
                    propsValues[i] = resId;
                    replacedTitleResource = true;
                }
            }
            if (!replacedTitleResource) {
                List<Integer> integerFields = new ArrayList<>();
                for (int i = 0; i < propsFields.size(); i++) if (propsFields.get(i).getType() == int.class || propsFields.get(i).getType() == Integer.class) integerFields.add(i);
                if (integerFields.size() == 1) propsValues[integerFields.get(0)] = resId;
            }
            if (item != null && item.iconAssetId != null) {
                Object icon = propsValues[0];
                if (item.icon == null) item.icon = createDrawerIcon(icon, item.iconAssetId);
                if (item.icon != null) propsValues[0] = item.icon;
            }
            Object newProps = instantiateLike(originalProps.getClass(), propsValues);
            Object newContent = cloneReplacingIdentity(originalContent, originalProps, newProps, null);
            Object newButton = cloneReplacingIdentity(template, originalContent, newContent, idToUse++);
            log("[SpotifyPlus] Injected " + title + " by cloning runtime classes " + template.getClass().getName() + " -> " + originalContent.getClass().getName() + " -> " + originalProps.getClass().getName() + " -> " + originalInstrumentation.getClass().getName());
            return newButton;
        } catch (Throwable throwable) {
            logError(throwable);
            return null;
        }
    }

    private List<Field> getInstanceFields(Class<?> type) {
        List<Field> fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).collect(java.util.stream.Collectors.toList());
        fields.forEach(field -> field.setAccessible(true));
        return fields;
    }

    private Object readField(Field field, Object owner) throws IllegalAccessException {
        field.setAccessible(true);
        return field.get(owner);
    }

    private Object[] readFieldValues(Object owner, List<Field> fields) throws IllegalAccessException {
        Object[] values = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) values[i] = readField(fields.get(i), owner);
        return values;
    }

    private int findInstrumentationIndex(Object props, List<Field> fields) throws IllegalAccessException {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Object value = readField(fields.get(i), props);
            if (value == null) continue;
            List<Field> childFields = getInstanceFields(value.getClass());
            if (childFields.size() < 2 || childFields.size() > 3) continue;
            Object[] childValues = readFieldValues(value, childFields);
            if (findClickIndexOrNegative(childFields, childValues) >= 0) candidates.add(i);
        }
        if (candidates.size() != 1) throw new IllegalStateException("[SideDrawerHook] Expected one live Settings instrumentation field in " + props.getClass().getName() + " but found " + candidates.size() + ": " + candidates);
        return candidates.get(0);
    }

    private int findClickIndex(List<Field> fields, Object[] values) {
        int index = findClickIndexOrNegative(fields, values);
        if (index < 0) throw new IllegalStateException("[SideDrawerHook] Could not identify the live Settings click callback.");
        return index;
    }

    private int findClickIndexOrNegative(List<Field> fields, Object[] values) {
        if (fields.size() > 1 && isInvokeCallback(fields.get(1).getType(), values[1])) return 1;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) if (isInvokeCallback(fields.get(i).getType(), values[i])) candidates.add(i);
        return candidates.size() == 1 ? candidates.get(0) : -1;
    }

    private boolean isInvokeCallback(Class<?> declaredType, Object value) {
        if (Arrays.stream(declaredType.getMethods()).anyMatch(method -> method.getName().equals("invoke"))) return true;
        return value != null && Arrays.stream(value.getClass().getMethods()).anyMatch(method -> method.getName().equals("invoke"));
    }

    private Object createClickCallback(Class<?> fieldType, Class<?> constructorType, Object originalClick, int resId, Runnable onClick) throws Exception {
        if (fieldType.isInterface() && constructorType.isInterface()) return java.lang.reflect.Proxy.newProxyInstance(classLoader, new Class[]{constructorType}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) runSideDrawerClick(resId, onClick);
            return defaultValue(method.getReturnType());
        });
        List<Field> clickFields = getInstanceFields(originalClick.getClass());
        Object clonedClick = instantiateCallbackLike(originalClick, readFieldValues(originalClick, clickFields));
        clickHandlers.put(clonedClick, () -> runSideDrawerClick(resId, onClick));
        for (Method method : clonedClick.getClass().getDeclaredMethods()) {
            if (method.getName().equals("invoke") && clickMethods.add(method)) hook(method);
        }
        return clonedClick;
    }

    private Object instantiateCallbackLike(Object originalClick, Object[] values) throws Exception {
        try {
            return instantiateLike(originalClick.getClass(), values);
        } catch (IllegalStateException ignored) {
            int invokeArity = Arrays.stream(originalClick.getClass().getDeclaredMethods()).filter(method -> method.getName().equals("invoke") && !method.isBridge()).mapToInt(Method::getParameterCount).max().orElse(0);
            List<Constructor<?>> candidates = Arrays.stream(originalClick.getClass().getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == values.length + 1 && wrapPrimitive(constructor.getParameterTypes()[0]) == Integer.class && parametersAccept(Arrays.copyOfRange(constructor.getParameterTypes(), 1, constructor.getParameterCount()), values)).collect(java.util.stream.Collectors.toList());
            if (candidates.size() != 1) throw new IllegalStateException("[SideDrawerHook] Could not clone concrete click callback " + originalClick.getClass().getName() + " from its captured fields; constructors: " + Arrays.toString(originalClick.getClass().getDeclaredConstructors()));
            candidates.get(0).setAccessible(true);
            Object[] constructorValues = new Object[values.length + 1];
            constructorValues[0] = invokeArity;
            System.arraycopy(values, 0, constructorValues, 1, values.length);
            return candidates.get(0).newInstance(constructorValues);
        }
    }

    private void runSideDrawerClick(int resId, Runnable onClick) {
        try {
            onClick.run();
        } catch (Throwable throwable) {
            logError(throwable);
        }
    }

    private Constructor<?> findCompatibleConstructor(Class<?> type, Object[] values) {
        List<Constructor<?>> candidates = Arrays.stream(type.getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == values.length).filter(constructor -> parametersAccept(constructor.getParameterTypes(), values)).collect(java.util.stream.Collectors.toList());
        if (candidates.size() != 1) throw new IllegalStateException("[SideDrawerHook] Expected one primary constructor in " + type.getName() + " for " + values.length + " live fields but found " + candidates.size() + ": " + Arrays.toString(type.getDeclaredConstructors()));
        candidates.get(0).setAccessible(true);
        return candidates.get(0);
    }

    private boolean parametersAccept(Class<?>[] parameterTypes, Object[] values) {
        for (int i = 0; i < parameterTypes.length; i++) if (values[i] == null ? parameterTypes[i].isPrimitive() : !wrapPrimitive(parameterTypes[i]).isInstance(values[i])) return false;
        return true;
    }

    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private Object instantiateLike(Class<?> type, Object[] values) throws Exception {
        return findCompatibleConstructor(type, values).newInstance(values);
    }

    private Object cloneReplacingIdentity(Object template, Object oldChild, Object newChild, Integer replacementId) throws Exception {
        List<Field> fields = getInstanceFields(template.getClass());
        Object[] values = readFieldValues(template, fields);
        boolean childReplaced = false;
        boolean hasIdField = fields.stream().anyMatch(field -> field.getType() == int.class || field.getType() == Integer.class) || Arrays.stream(values).anyMatch(value -> value instanceof Integer);
        boolean idReplaced = replacementId == null || !hasIdField;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == oldChild) {
                values[i] = newChild;
                childReplaced = true;
            } else if (!idReplaced && (fields.get(i).getType() == int.class || fields.get(i).getType() == Integer.class || values[i] instanceof Integer)) {
                values[i] = replacementId;
                idReplaced = true;
            }
        }
        if (!childReplaced || !idReplaced) throw new IllegalStateException("[SideDrawerHook] Could not clone " + template.getClass().getName() + ": childReplaced=" + childReplaced + ", idReplaced=" + idReplaced);
        return instantiateLike(template.getClass(), values);
    }

    private boolean isSettingsTitle(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("settings") || normalized.contains("privacy");
    }

    private boolean isSettingsTitleResource(int resourceId) {
        try {
            String entryName = currentActivity.get().getResources().getResourceEntryName(resourceId).toLowerCase(Locale.ROOT);
            if (entryName.contains("settings") || entryName.contains("privacy")) return true;
            return isSettingsTitle(currentActivity.get().getString(resourceId));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) return null;
        if (returnType == boolean.class) return false;
        if (returnType == char.class) return '\0';
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0.0f;
        return 0.0d;
    }

    private Object createDrawerIcon(Object templateIcon, String assetId) {
        try {
            File asset = ExtensionAssetRegistry.resolveFile(assetId);
            if (asset == null) return null;

            Bitmap bitmap = BitmapFactory.decodeFile(asset.getAbsolutePath());
            if (bitmap == null) return null;

            Field normalField = Arrays.stream(templateIcon.getClass().getSuperclass().getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .findFirst()
                    .orElseThrow();
            normalField.setAccessible(true);
            Object templateSizePair = normalField.get(templateIcon);
            Field[] sizeFields = Arrays.stream(templateSizePair.getClass().getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .toArray(Field[]::new);
            for (Field field : sizeFields) field.setAccessible(true);

            Object templateVector = sizeFields[0].get(templateSizePair);
            Object vector16 = createImageVector(templateVector, bitmap, 16);
            Object vector24 = createImageVector(templateVector, bitmap, 24);
            bitmap.recycle();

            Constructor<?> sizePairConstructor = templateSizePair.getClass().getDeclaredConstructor(templateVector.getClass(), templateVector.getClass());
            sizePairConstructor.setAccessible(true);
            Object sizePair = sizePairConstructor.newInstance(vector16, vector24);

            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method putObject = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
            Object icon = allocateInstance.invoke(unsafe, templateIcon.getClass());
            for (Field field : templateIcon.getClass().getSuperclass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != templateSizePair.getClass()) continue;
                field.setAccessible(true);
                long offset = (long) objectFieldOffset.invoke(unsafe, field);
                putObject.invoke(unsafe, icon, offset, sizePair);
            }
            return icon;
        } catch (Throwable t) {
            logError("Failed to create side drawer icon for " + assetId);
            logError(t);
            return null;
        }
    }

    private Object createImageVector(Object templateVector, Bitmap source, int size) throws Exception {
        Bitmap bitmap = Bitmap.createScaledBitmap(source, size, size, true);
        boolean hasTransparency = false;
        for (int y = 0; y < size && !hasTransparency; y++) {
            for (int x = 0; x < size; x++) {
                if ((bitmap.getPixel(x, y) >>> 24) < 64) {
                    hasTransparency = true;
                    break;
                }
            }
        }

        int background = bitmap.getPixel(0, 0);
        String packageName = templateVector.getClass().getPackage().getName();
        Class<?> moveClass = findClass("p.fsm0");
        Class<?> lineClass = findClass("p.esm0");
        Class<?> closeClass = findClass("p.bsm0");
        Constructor<?> moveConstructor = moveClass.getDeclaredConstructor(float.class, float.class);
        Constructor<?> lineConstructor = lineClass.getDeclaredConstructor(float.class, float.class);
        moveConstructor.setAccessible(true);
        lineConstructor.setAccessible(true);
        Field closeField = Arrays.stream(closeClass.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == closeClass)
                .findFirst()
                .orElseThrow();
        closeField.setAccessible(true);
        Object close = closeField.get(null);

        ArrayList<Object> commands = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            int x = 0;
            while (x < size) {
                while (x < size && !isIconPixel(bitmap.getPixel(x, y), background, hasTransparency)) x++;
                if (x >= size) break;
                int start = x;
                while (x < size && isIconPixel(bitmap.getPixel(x, y), background, hasTransparency)) x++;

                commands.add(moveConstructor.newInstance((float) start, (float) y));
                commands.add(lineConstructor.newInstance((float) x, (float) y));
                commands.add(lineConstructor.newInstance((float) x, (float) (y + 1)));
                commands.add(lineConstructor.newInstance((float) start, (float) (y + 1)));
                commands.add(close);
            }
        }
        if (bitmap != source) bitmap.recycle();

        Field rootField = Arrays.stream(templateVector.getClass().getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && Iterable.class.isAssignableFrom(field.getType()))
                .findFirst()
                .orElseThrow();
        rootField.setAccessible(true);
        Object templateRoot = rootField.get(templateVector);
        Object templatePath = ((Iterable<?>) templateRoot).iterator().next();

        Constructor<?> pathConstructor = Arrays.stream(templatePath.getClass().getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 14)
                .findFirst()
                .orElseThrow();
        pathConstructor.setAccessible(true);
        Class<?> brushClass = pathConstructor.getParameterTypes()[3];
        Field brushField = Arrays.stream(templatePath.getClass().getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == brushClass)
                .findFirst()
                .orElseThrow();
        brushField.setAccessible(true);
        Object brush = brushField.get(templatePath);
        Object path = pathConstructor.newInstance("", commands, 0, brush, 1.0f, null, 1.0f, 1.0f, 0, 0, 1.0f, 0.0f, 1.0f, 0.0f);

        Constructor<?> rootConstructor = Arrays.stream(templateRoot.getClass().getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 10)
                .findFirst()
                .orElseThrow();
        rootConstructor.setAccessible(true);
        Object root = rootConstructor.newInstance("", 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, Collections.emptyList(), new ArrayList<>(Collections.singletonList(path)));

        Constructor<?> vectorConstructor = Arrays.stream(templateVector.getClass().getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 9)
                .findFirst()
                .orElseThrow();
        vectorConstructor.setAccessible(true);
        return vectorConstructor.newInstance("SpotifyPlus.AssetIcon" + size, (float) size, (float) size, (float) size, (float) size, root, 0L, 0, false);
    }

    private boolean isIconPixel(int color, int background, boolean hasTransparency) {
        int alpha = color >>> 24;
        if (alpha < 64) return false;
        if (hasTransparency) return true;

        int red = Math.abs(((color >> 16) & 0xff) - ((background >> 16) & 0xff));
        int green = Math.abs(((color >> 8) & 0xff) - ((background >> 8) & 0xff));
        int blue = Math.abs((color & 0xff) - (background & 0xff));
        return red + green + blue >= 72;
    }

    private Object getFieldValue(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private Object newInstance(Class<?> cls, Object... args) throws Exception {
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length != args.length) continue;
            boolean ok = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null) continue;
                if (!wrap(types[i]).isAssignableFrom(args[i].getClass())) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        }
        throw new NoSuchMethodException("No constructor found for " + cls.getName());
    }

    private Class<?> wrap(Class<?> cls) {
        if (!cls.isPrimitive()) return cls;
        if (cls == int.class) return Integer.class;
        if (cls == long.class) return Long.class;
        if (cls == boolean.class) return Boolean.class;
        if (cls == float.class) return Float.class;
        if (cls == double.class) return Double.class;
        if (cls == byte.class) return Byte.class;
        if (cls == short.class) return Short.class;
        if (cls == char.class) return Character.class;
        return cls;
    }

}

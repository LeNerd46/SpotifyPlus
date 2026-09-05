package com.lenerd.spotifyplus.module.hooks;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.SpotifyPlusSettings;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.scripting.ScriptContextMenu;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

@XposedHooker
public class ContextMenuHook extends SpotifyHook {
    private static final String LAST_FM_MARKER = "spotifyplus_open_last_fm";
    private static final String GENERATE_LYRICS_MARKER = "spotifyplus_generate_lyrics";
    private static String trackTitle = "";
    private static String trackArtist = "";
    private static volatile String lastContextMenuUri;
    private static Object cachedOriginalViewModel;
    private static Object cachedSpotifyPlusTrf;
    private static Object cachedSpotifyPlusGenerateLyricsTrf;
    private static Class<?> interfaceClass;
    private static Constructor<?> directTextTitleConstructor;
    private Constructor<?> contextMenuConstructor;
    private Constructor<?> radioConstructor;
    private Method radioAccessor;
    private Method startServiceMethod;
    private Field headerTitleField;
    private Field headerSubtitleField;
    private Method iconRenderer;
    private Field renderBranch;
    private final Set<Method> rowRenderMethods = new HashSet<>();
    private final ThreadLocal<Deque<Optional<String>>> renderMarkers = ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<Class<?>, Method> menuItemViewModelAccessors = new ConcurrentHashMap<>();
    private final List<ScriptContextMenu> scriptMenus = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
            var contextMenuClassResults = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")));
            List<Class<?>> contextMenuClasses = new ArrayList<>();
            for (var classData : contextMenuClassResults) {
                Class<?> candidate = classData.getInstance(classLoader);
                if (Arrays.stream(candidate.getDeclaredConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 3 && List.class.isAssignableFrom(constructor.getParameterTypes()[1]) && constructor.getParameterTypes()[2] == boolean.class)) contextMenuClasses.add(candidate);
            }
            if (contextMenuClasses.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected one class using the ContextMenuViewModel duplicate-item diagnostic and declaring a (header, List, boolean) constructor but found " + contextMenuClasses.size() + ": " + contextMenuClasses.stream().map(Class::getName).collect(Collectors.joining(", ")));
            Class<?> headerObject = contextMenuClasses.get(0);
            List<Constructor<?>> contextMenuConstructors = Arrays.stream(headerObject.getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == 3 && List.class.isAssignableFrom(constructor.getParameterTypes()[1]) && constructor.getParameterTypes()[2] == boolean.class).collect(Collectors.toList());
            if (contextMenuConstructors.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected one (header, List, boolean) constructor in the ContextMenuViewModel class " + headerObject.getName() + " but found " + contextMenuConstructors.size() + ": " + contextMenuConstructors.stream().map(Constructor::toString).collect(Collectors.joining(", ")));
            contextMenuConstructor = contextMenuConstructors.get(0);
            Class<?> headerClass = contextMenuConstructor.getParameterTypes()[0];
            List<Constructor<?>> headerConstructors = Arrays.stream(headerClass.getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == 3 && constructor.getParameterTypes()[0] == String.class && !constructor.getParameterTypes()[1].isPrimitive() && constructor.getParameterTypes()[2] == String.class).collect(Collectors.toList());
            if (headerConstructors.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected the ContextMenuViewModel header type " + headerClass.getName() + " to have one (String, artwork, String) constructor but found " + headerConstructors.size() + ": " + headerConstructors.stream().map(Constructor::toString).collect(Collectors.joining(", ")));
            List<Field> headerTextFields = Arrays.stream(headerClass.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == String.class).collect(Collectors.toList());
            if (headerTextFields.size() != 2) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected the ContextMenuViewModel header type " + headerClass.getName() + " to have two String fields for title and subtitle but found " + headerTextFields.size() + ": " + headerTextFields.stream().map(Field::toString).collect(Collectors.joining(", ")));
            headerTitleField = headerTextFields.get(0);
            headerSubtitleField = headerTextFields.get(1);
            headerTitleField.setAccessible(true);
            headerSubtitleField.setAccessible(true);

            // Buttons
            Class<?> radioButtonClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("audiobook_supplementary_content"))).get(0).getInstance(classLoader);
            radioAccessor = findMenuItemViewModelAccessor(radioButtonClass);
            radioConstructor = radioButtonClass.getDeclaredConstructor(Context.class, String.class);
            radioConstructor.setAccessible(true);
            startServiceMethod = ContextWrapper.class.getDeclaredMethod("startService", Intent.class);
        hook(contextMenuConstructor);
        hook(radioAccessor);
        hook(startServiceMethod);
        SpotifyNativeBridge.registerHandler("menu", this);
        installIconRenderer();
    }

    private void installIconRenderer() {
        // Main also writes icons directly into the view model, so this renderer is optional.
        try {
            var iconClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingEqStrings("ContextMenuItem")));
            var methods = bridge.findMethod(FindMethod.create().searchInClass(iconClasses)
                    .matcher(MethodMatcher.create().paramCount(6)));
            var renderers = methods.stream().filter(method -> method.isMethod()
                    && method.getReturnTypeName().equals("void") && Modifier.isStatic(method.getModifiers())).collect(Collectors.toList());
            if (renderers.size() != 1) return;
            iconRenderer = renderers.get(0).getMethodInstance(classLoader);
            var rowClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .fields(FieldsMatcher.create().count(2).add(FieldMatcher.create().type(int.class)))
                    .usingStrings("CreateMenuItemElement")));
            if (rowClasses.size() != 1) return;
            Class<?> rowClass = rowClasses.get(0).getInstance(classLoader);
            renderBranch = Arrays.stream(rowClass.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
                    .findFirst().orElseThrow(() -> new NoSuchFieldException("Context-menu render branch"));
            renderBranch.setAccessible(true);
            for (Method method : rowClass.getDeclaredMethods()) {
                if (method.getName().equals("invoke") && rowRenderMethods.add(method)) hook(method);
            }
            hook(iconRenderer);
        } catch (Exception e) { logError(e); }
    }

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        ContextMenuHook hook = getHook(ContextMenuHook.class);
        if (hook != null) hook.beforeHook(buildCallback(callback));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
        if (rowRenderMethods.contains(callback.getMember())) {
            // One frame per invocation keeps nested native/custom rows balanced in both Xposed APIs.
            String marker = null;
            try {
                if (renderBranch.getInt(callback.getThisObject()) == 10 && callback.getArgs().length >= 2)
                    marker = findSpotifyPlusMarker(callback.getArgs()[1], 2, new IdentityHashMap<>());
            } catch (Exception e) { logError(e); }
            renderMarkers.get().push(Optional.ofNullable(marker));
            return;
        }
        try {
            if (callback.getMember().equals(iconRenderer)) {
                Deque<Optional<String>> markers = renderMarkers.get();
                if (!markers.isEmpty() && markers.peek().isPresent()) {
                    Object icon = getSpotifyPlusIcon(markers.peek().get());
                    if (icon != null) callback.getArgs()[1] = icon;
                }
            } else if (callback.getMember().equals(contextMenuConstructor)) {
                List<?> list = (List<?>) callback.getArgs()[1];
                if (list == null) return;
                String trackUri = getSingleTrackUri(findMenuItem(list, "queue_track"));
                lastContextMenuUri = trackUri != null ? trackUri : captureContextMenuUri(list);
                trackTitle = "";
                trackArtist = "";
                if (trackUri != null) updateLastFmHeader(callback.getArgs()[0], headerTitleField, headerSubtitleField, trackUri);
                if ((cachedOriginalViewModel == null || directTextTitleConstructor == null || interfaceClass == null) && list.size() >= 4) {
                    cachedOriginalViewModel = getMenuItemViewModel(list.get(3));
                    if (cachedOriginalViewModel == null) return;
                    List<Field> viewModelFields = Arrays.stream(cachedOriginalViewModel.getClass().getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).collect(Collectors.toList());
                    Class<?> titleType = viewModelFields.get(1).getType();
                    List<Constructor<?>> directTextTitleConstructors = Arrays.stream(cachedOriginalViewModel.getClass().getDeclaredConstructors()).flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())).filter(parameterType -> parameterType != titleType && titleType.isAssignableFrom(parameterType)).distinct().map(parameterType -> getStringConstructor(parameterType)).filter(Objects::nonNull).collect(Collectors.toList());
                    if (directTextTitleConstructors.size() != 1) throw new IllegalStateException("[ContextMenuHook] Expected the menu view-model constructors to reference one direct-text implementation of " + titleType.getName() + " but found " + directTextTitleConstructors.size() + ": " + directTextTitleConstructors.stream().map(constructor -> constructor.getDeclaringClass().getName()).collect(Collectors.joining(", ")));
                    directTextTitleConstructor = directTextTitleConstructors.get(0);
                    directTextTitleConstructor.setAccessible(true);
                    Field iconField = viewModelFields.get(3);
                    iconField.setAccessible(true);
                    Object icon = iconField.get(cachedOriginalViewModel);
                    if (icon == null || icon.getClass().getInterfaces().length == 0) throw new IllegalStateException("[ContextMenuHook] Could not identify the icon interface from " + cachedOriginalViewModel.getClass().getName());
                    interfaceClass = icon.getClass().getInterfaces()[0];

                }
                if (cachedOriginalViewModel == null || currentActivity == null) return;
                ArrayList<Object> updated = new ArrayList<>(list);
                if (trackUri != null && !hasMenuItem(list, LAST_FM_MARKER))
                    updated.add(0, radioConstructor.newInstance(currentActivity, LAST_FM_MARKER));
                if (getSpotifyTrackId(lastContextMenuUri) != null && !hasMenuItem(list, GENERATE_LYRICS_MARKER))
                    updated.add(0, radioConstructor.newInstance(currentActivity, GENERATE_LYRICS_MARKER));
                int scriptIndex = 0;
                for (ScriptContextMenu menu : scriptMenus) {
                    if (!hasMenuItem(updated, menu.id)) updated.add(scriptIndex++, radioConstructor.newInstance(currentActivity, menu.id));
                }
                if (updated.size() != list.size()) callback.getArgs()[1] = updated;
            } else if (callback.getMember().equals(radioAccessor)) {
                String marker = getSpotifyPlusItemMarker(callback.getThisObject());
                if (marker == null || cachedOriginalViewModel == null) return;
                String title;
                if (LAST_FM_MARKER.equals(marker)) title = "Open in Last.fm";
                else if (GENERATE_LYRICS_MARKER.equals(marker)) title = Utils.getString(currentActivity, R.string.generate_lyrics);
                else {
                    ScriptContextMenu menu = scriptMenus.stream().filter(item -> item.id.equals(marker)).findFirst().orElse(null);
                    if (menu == null) return;
                    title = menu.title;
                }
                callback.returnAndSkip(cloneMenuViewModel(cachedOriginalViewModel, marker, title));
            } else if (callback.getMember().equals(startServiceMethod)) {
                Intent intent = (Intent) callback.getArgs()[0];
                if (intent == null || intent.getComponent() == null || !intent.getComponent().getClassName().equals("com.spotify.radio.radio.formatlist.RadioFormatListService") || !intent.hasExtra(".seed_uri")) return;
                String marker = intent.getStringExtra(".seed_uri");
                if (!isCustomMarker(marker)) return;
                callback.returnAndSkip(null);
                if (LAST_FM_MARKER.equals(marker)) {
                    Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/music/" + URLEncoder.encode(trackArtist, "UTF-8") + "/_/" + URLEncoder.encode(trackTitle, "UTF-8")));
                    link.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ((Context) callback.getThisObject()).startActivity(link);
                } else if (GENERATE_LYRICS_MARKER.equals(marker)) {
                    String id = getSpotifyTrackId(lastContextMenuUri);
                    if (id != null) onGenerateLyricsPressed(id);
                } else {
                    ScriptContextMenu menu = scriptMenus.stream().filter(item -> item.id.equals(marker)).findFirst().orElse(null);
                    if (menu == null) return;
                    JSONObject json = new JSONObject();
                    json.put("id", menu.id);
                    json.put("scriptId", menu.scriptId);
                    json.put("uri", lastContextMenuUri);
                    SpotifyNativeBridge.sendEvent("menu.press", json.toString());
                }
            }
        } catch (Exception e) { logError(e); }
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        ContextMenuHook hook = getHook(ContextMenuHook.class);
        if (hook != null) hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        if (!rowRenderMethods.contains(callback.getMember())) return;
        Deque<Optional<String>> markers = renderMarkers.get();
        if (!markers.isEmpty()) markers.pop();
        if (markers.isEmpty()) renderMarkers.remove();
    }

    private String findSpotifyPlusMarker(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (isCustomMarker(value)) return (String) value;
        if (value == null || depth == 0 || visited.put(value, Boolean.TRUE) != null) return null;
        Class<?> valueClass = value.getClass();
        if (valueClass.isPrimitive() || valueClass.isEnum() || valueClass.isArray()
                || valueClass.getName().startsWith("java.") || valueClass.getName().startsWith("android.")
                || valueClass.getName().startsWith("kotlin.")) return null;
        for (Class<?> type = valueClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    String marker = findSpotifyPlusMarker(field.get(value), depth - 1, visited);
                    if (marker != null) return marker;
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private boolean isCustomMarker(Object value) {
        return LAST_FM_MARKER.equals(value) || GENERATE_LYRICS_MARKER.equals(value)
                || scriptMenus.stream().anyMatch(menu -> menu.id.equals(value));
    }

    private void updateLastFmHeader(Object header, Field titleField, Field subtitleField, String trackUri) {
        if (header == null || trackUri == null || !trackUri.startsWith("spotify:track:")) return;

        try {
            String title = (String) titleField.get(header);
            String subtitleTextFull = (String) subtitleField.get(header);
            if (title == null || title.isEmpty() || subtitleTextFull == null || subtitleTextFull.isEmpty()) return;

            String artist = subtitleTextFull.split(" â€¢ ")[0];
            trackTitle = title;
            trackArtist = artist;
            if (subtitleTextFull.contains("scrobbles")) return;

            String username = SpotifyPlusSettings.lastfmUsername;
            if (username == null || username.equals("null")) return;

            
            OkHttpClient client = new OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build();
            Request request;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8) + "&track=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&format=json&user=" + URLEncoder.encode(username, StandardCharsets.UTF_8)).build();
            } else {
                request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist) + "&track=" + URLEncoder.encode(title) + "&format=json&user=" + URLEncoder.encode(username)).build();
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> resultRef = new AtomicReference<>();
            AtomicReference<Exception> exceptionRef = new AtomicReference<>();
            new Thread(() -> {
                try (Response response = client.newCall(request).execute()) {
                    ResponseBody body = response.body();
                    resultRef.set(body != null ? body.string() : null);
                } catch (Exception e) {
                    exceptionRef.set(e);
                } finally {
                    latch.countDown();
                }
            }).start();
            try {
                if (!latch.await(3, TimeUnit.SECONDS)) return;
                if (exceptionRef.get() != null) throw new RuntimeException(exceptionRef.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (resultRef.get() == null) throw new IllegalStateException("Last.fm returned no response body");
            JsonObject root = JsonParser.parseString(resultRef.get()).getAsJsonObject();
            String scrobbles = root.getAsJsonObject("track").get("userplaycount").getAsString();
            subtitleField.set(header, subtitleTextFull + " â€¢ " + scrobbles + " scrobbles");
        } catch (Exception e) {
            logError("[SpotifyPlus] Failed to fetch scrobbles for " + trackUri);
            logError("[SpotifyPlus] " + e);
            
            toast("Failed to fetch scrobbles");
        }
    }

    private Object getSpotifyPlusIcon(String marker) {
        try {
            if (GENERATE_LYRICS_MARKER.equals(marker) && cachedSpotifyPlusGenerateLyricsTrf != null) return cachedSpotifyPlusGenerateLyricsTrf;
            if (!GENERATE_LYRICS_MARKER.equals(marker) && cachedSpotifyPlusTrf != null) return cachedSpotifyPlusTrf;

            Context appContext = currentActivity;
            if (appContext == null) {
                logError("[SpotifyPlus] appContext was null");
                return null;
            }

            int drawableId;
            if (LAST_FM_MARKER.equals(marker)) {
                drawableId = R.drawable.lastfm;
            } else if (GENERATE_LYRICS_MARKER.equals(marker)) {
                drawableId = R.drawable.lastfm;
            } else {
                drawableId = R.drawable.lastfm;
            }
            Drawable drawable = Utils.getDrawable(appContext, drawableId);

            if (drawable == null) {
                logError("[SpotifyPlus] module drawable was null");
                return null;
            }

            LayerDrawable layerDrawable;
            if (drawable instanceof LayerDrawable) {
                layerDrawable = (LayerDrawable) drawable;
            } else {
                layerDrawable = new LayerDrawable(new android.graphics.drawable.Drawable[]{drawable});
            }

            var classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().addInterface(interfaceClass.getName())));
            Object customIcon = null;
            for (var classData : classes) {
                Class<?> outerClass = classData.getInstance(classLoader);
                if (outerClass.isInterface() || Modifier.isAbstract(outerClass.getModifiers())) continue;
                for (Constructor<?> outerConstructor : outerClass.getDeclaredConstructors()) {
                    if (outerConstructor.getParameterCount() != 1) continue;
                    Class<?> outerParameter = outerConstructor.getParameterTypes()[0];
                    Object outerArgument = getDrawableConstructorArgument(outerParameter, drawable, layerDrawable);
                    if (outerArgument == null) {
                        for (Constructor<?> innerConstructor : outerParameter.getDeclaredConstructors()) {
                            if (innerConstructor.getParameterCount() != 1) continue;
                            Object innerArgument = getDrawableConstructorArgument(innerConstructor.getParameterTypes()[0], drawable, layerDrawable);
                            if (innerArgument == null) continue;
                            innerConstructor.setAccessible(true);
                            outerArgument = innerConstructor.newInstance(innerArgument);
                            break;
                        }
                    }
                    if (outerArgument == null) continue;
                    outerConstructor.setAccessible(true);
                    customIcon = outerConstructor.newInstance(outerArgument);
                    break;
                }
                if (customIcon != null) break;
            }
            if (customIcon == null) throw new IllegalStateException("[ContextMenuHook] Could not find a " + interfaceClass.getName() + " implementation backed by Drawable or LayerDrawable");
            if (GENERATE_LYRICS_MARKER.equals(marker)) cachedSpotifyPlusGenerateLyricsTrf = customIcon;
            else cachedSpotifyPlusTrf = customIcon;
            return customIcon;
        } catch (Throwable t) {
            logError("[SpotifyPlus] Failed to create custom trf: " + t);
            return null;
        }
    }

    private Object getDrawableConstructorArgument(Class<?> parameterType, Drawable drawable, LayerDrawable layerDrawable) {
        if (!Drawable.class.isAssignableFrom(parameterType)) return null;
        if (parameterType.isInstance(drawable)) return drawable;
        return parameterType.isInstance(layerDrawable) ? layerDrawable : null;
    }

    private String getSpotifyPlusItemMarker(Object item) {
        if (item == null) return null;
        for (Class<?> type = item.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (isCustomMarker(value)) return (String) value;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private Method findMenuItemViewModelAccessor(Class<?> itemClass) throws NoSuchMethodException {
        Method cached = menuItemViewModelAccessors.get(itemClass);
        if (cached != null) return cached;
        List<Method> candidates = Arrays.stream(itemClass.getMethods()).filter(method -> !Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && method.getDeclaringClass() != Object.class && !method.getReturnType().isPrimitive() && method.getReturnType() != String.class && Arrays.stream(method.getReturnType().getDeclaredFields()).anyMatch(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)).collect(Collectors.toList());
        Method accessor = candidates.stream().filter(method -> method.getName().equals("getViewModel")).findFirst().orElse(candidates.size() == 1 ? candidates.get(0) : null);
        if (accessor == null) throw new NoSuchMethodException("[ContextMenuHook] Could not identify the view-model accessor on " + itemClass.getName() + "; candidates: " + candidates.stream().map(Method::toString).collect(Collectors.joining(", ")));
        accessor.setAccessible(true);
        menuItemViewModelAccessors.put(itemClass, accessor);
        return accessor;
    }

    private Object getMenuItemViewModel(Object item) throws ReflectiveOperationException {
        if (item == null) return null;
        return findMenuItemViewModelAccessor(item.getClass()).invoke(item);
    }

    private Constructor<?> getStringConstructor(Class<?> type) {
        try {
            return type.getDeclaredConstructor(String.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object cloneMenuViewModel(Object template, String marker, String title) throws ReflectiveOperationException {
        List<Field> fields = Arrays.stream(template.getClass().getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).collect(Collectors.toList());
        Class<?>[] fieldTypes = fields.stream().map(Field::getType).toArray(Class<?>[]::new);
        Constructor<?> constructor = Arrays.stream(template.getClass().getDeclaredConstructors()).filter(candidate -> Arrays.equals(candidate.getParameterTypes(), fieldTypes)).findFirst().orElseThrow(() -> new NoSuchMethodException("[ContextMenuHook] No primary data-class constructor matches the fields of " + template.getClass().getName()));
        Object[] values = new Object[fields.size()];
        boolean markerReplaced = false;
        boolean titleReplaced = false;
        boolean iconReplaced = false;
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            field.setAccessible(true);
            Object value = field.get(template);
            if (i == 0 && field.getType() == String.class) {
                value = marker;
                markerReplaced = true;
            } else if (i == 1 && directTextTitleConstructor != null && field.getType().isAssignableFrom(directTextTitleConstructor.getDeclaringClass())) {
                value = directTextTitleConstructor.newInstance(title);
                titleReplaced = true;
            } else if (i == 3 && interfaceClass != null && field.getType().isAssignableFrom(interfaceClass)) {
                value = getSpotifyPlusIcon(marker);
                iconReplaced = value != null;
            }
            values[i] = value;
        }
        if (!markerReplaced || !titleReplaced || !iconReplaced) throw new IllegalStateException("[ContextMenuHook] Could not replace the marker, title, and icon while cloning " + template.getClass().getName());
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    private Object findMenuItem(List<?> items, String id) {
        if (items == null) return null;

        for (Object item : items) {
            if (id.equals(getMenuItemId(item))) {
                return item;
            }
        }
        return null;
    }

    private boolean hasMenuItem(List<?> items, String id) {
        return findMenuItem(items, id) != null;
    }

    private String getMenuItemId(Object item) {
        if (item == null) return null;

        try {
            Object viewModel = getMenuItemViewModel(item);
            if (viewModel == null) return null;
            for (Field field : viewModel.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
                field.setAccessible(true);
                Object id = field.get(viewModel);
                if (id instanceof String) return (String) id;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getSingleTrackUri(Object addToQueueItem) {
        if (addToQueueItem == null) return null;

        try {
            Object value = getField(addToQueueItem, List.class);
            if (!(value instanceof List)) return null;

            List<?> tracks = (List<?>) value;
            if (tracks.size() != 1 || tracks.get(0) == null) return null;

            Object uriValue = tracks.get(0).getClass().getMethod("uri").invoke(tracks.get(0));
            if (!(uriValue instanceof String)) return null;

            String uri = (String) uriValue;
            return uri.startsWith("spotify:track:") ? uri : null;
        } catch (Throwable ignored) {
            return null;
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

    private static String getSpotifyTrackId(String uri) {
        if (uri == null) return null;

        String[] parts = uri.split(":", -1);

        if (parts.length != 3) return null;
        if (!"spotify".equals(parts[0]) || !"track".equals(parts[1])) return null;
        if (parts[2].isBlank()) return null;

        return parts[2];
    }

    private void onGenerateLyricsPressed(String spotifyId) {
        log("Generate lyrics pressed for Spotify track " + spotifyId);
    }

    private String captureContextMenuUri(List<?> items) {
        if (items == null) return null;

        String[] preferredKeys = {
                "share",
                "add_to_playlist",
                "queue_track",
                "queue_album",
                "album_browse",
                "artist_browse",
                "radio_go_to_station",
                "jam_start"
        };

        for (String key : preferredKeys) {
            for (Object item : items) {
                try {
                    Object value = getMenuItemId(item);
                    if (!(value instanceof String vmKey)) continue;
                    if (!vmKey.equals(key)) continue;

                    String uri = findDirectSpotifyUri(item);
                    if (uri != null) return uri;
                } catch (Throwable ignored) { }
            }
        }

        return null;
    }

    private String findDirectSpotifyUri(Object item) {
        if (item == null) return null;

        Class<?> c = item.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(item);
                    if (v == null) continue;

                    String s = String.valueOf(v);
                    if (isEntityUri(s)) return s;
                } catch (Throwable ignored) { }
            }
            c = c.getSuperclass();
        }

        return null;
    }

    private boolean isEntityUri(String s) {
        if (s == null || !s.startsWith("spotify:")) return false;
        if (s.equals("spotify:debug")) return false;
        if (s.startsWith("spotify:now-playing-view")) return false;

        return s.startsWith("spotify:track:") ||
                s.startsWith("spotify:album:") ||
                s.startsWith("spotify:artist:") ||
                s.startsWith("spotify:playlist:") ||
                s.startsWith("spotify:episode:") ||
                s.startsWith("spotify:show:");
    }

    @Override
    public Object handle(String command, Object[] args) {
        if (command.equals("register")) {
            try {
                String menuId = (String) args[0];
                String scriptId = (String) args[1];
                String title = (String) args[2];

                ScriptContextMenu menu = new ScriptContextMenu(menuId, scriptId, title);
                if (isCustomMarker(menuId)) return null;

                scriptMenus.add(menu);
            } catch(Exception ignored) { }
        } else if (command.equals("unregisterScript")) {
            try {
                String scriptId = (String) args[0];
                scriptMenus.removeIf(menu -> menu.scriptId.equals(scriptId));
            } catch (Exception ignored) { }
        }

        return null;
    }

}

package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.*;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

// Spotify changed their context menu to use jetpack compose in newer versions
// So the method of adding a new button in this hook is quite different from the recycle view version
public class NewContextMenuHook extends SpotifyHook {
    private static final String LAST_FM_MARKER = "spotifyplus_open_last_fm";
    private static final String LYRICS_MARKER = "spotifyplus_open_lyrics";
    private static final String GENERATE_LYRICS_MARKER = "spotifyplus_generate_lyrics";
    private static volatile Object cachedOriginalViewModel = null;
    private static volatile Object cachedViewModel = null;
    private static volatile Object cachedLyricsViewModel = null;
    private static volatile Object cachedGenerateLyricsViewModel = null;

    private static String trackTitle = "";
    private static String trackArtist = "";
    private static volatile String contextTrackUri = null;

    private static final ThreadLocal<Integer> spotifyPlusRenderDepth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> spotifyPlusRenderMarker = new ThreadLocal<>();
    private static volatile Object cachedSpotifyPlusTrf = null;
    private static volatile Object cachedSpotifyPlusLyricsTrf = null;
    private static volatile Object cachedSpotifyPlusGenerateLyricsTrf = null;
    private static final Map<Object, NextUpAction> nextUpActions = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> nextUpClickHookClasses = Collections.synchronizedSet(new HashSet<>());
    private static final Map<Class<?>, Method> menuItemViewModelAccessors = new ConcurrentHashMap<>();

    private static Class<?> interfaceClass;
    private static Constructor<?> directTextTitleConstructor;

    @Override
    protected void hook() {
        try {
            // We have to do it here as well as down there somewhere, otherwise Spotify won't show it in the now playing context menu for some reason?
            SpotifyTitleOverride.install();

            var contextMenuClassResults = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")));
            List<Class<?>> contextMenuClasses = new ArrayList<>();
            for (var classData : contextMenuClassResults) {
                Class<?> candidate = classData.getInstance(lpparm.classLoader);
                if (Arrays.stream(candidate.getDeclaredConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 3 && List.class.isAssignableFrom(constructor.getParameterTypes()[1]) && constructor.getParameterTypes()[2] == boolean.class)) contextMenuClasses.add(candidate);
            }
            if (contextMenuClasses.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected one class using the ContextMenuViewModel duplicate-item diagnostic and declaring a (header, List, boolean) constructor but found " + contextMenuClasses.size() + ": " + contextMenuClasses.stream().map(Class::getName).collect(Collectors.joining(", ")));
            Class<?> headerObject = contextMenuClasses.get(0);
            List<Constructor<?>> contextMenuConstructors = Arrays.stream(headerObject.getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == 3 && List.class.isAssignableFrom(constructor.getParameterTypes()[1]) && constructor.getParameterTypes()[2] == boolean.class).collect(Collectors.toList());
            if (contextMenuConstructors.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected one (header, List, boolean) constructor in the ContextMenuViewModel class " + headerObject.getName() + " but found " + contextMenuConstructors.size() + ": " + contextMenuConstructors.stream().map(Constructor::toString).collect(Collectors.joining(", ")));
            Constructor<?> contextMenuConstructor = contextMenuConstructors.get(0);
            Class<?> headerClass = contextMenuConstructor.getParameterTypes()[0];
            List<Constructor<?>> headerConstructors = Arrays.stream(headerClass.getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == 3 && constructor.getParameterTypes()[0] == String.class && !constructor.getParameterTypes()[1].isPrimitive() && constructor.getParameterTypes()[2] == String.class).collect(Collectors.toList());
            if (headerConstructors.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected the ContextMenuViewModel header type " + headerClass.getName() + " to have one (String, artwork, String) constructor but found " + headerConstructors.size() + ": " + headerConstructors.stream().map(Constructor::toString).collect(Collectors.joining(", ")));
            List<Field> headerTextFields = Arrays.stream(headerClass.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == String.class).collect(Collectors.toList());
            if (headerTextFields.size() != 2) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected the ContextMenuViewModel header type " + headerClass.getName() + " to have two String fields for title and subtitle but found " + headerTextFields.size() + ": " + headerTextFields.stream().map(Field::toString).collect(Collectors.joining(", ")));
            Field headerTitleField = headerTextFields.get(0);
            Field headerSubtitleField = headerTextFields.get(1);
            headerTitleField.setAccessible(true);
            headerSubtitleField.setAccessible(true);

            // Buttons
            Class<?> radioButtonClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("audiobook_supplementary_content"))).get(0).getInstance(lpparm.classLoader);
            Method radioButtonViewModelAccessor = findMenuItemViewModelAccessor(radioButtonClass);
            XposedBridge.hookMethod(contextMenuConstructor, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        List<?> list = (List<?>) param.args[1];
                        if (list == null) return;
                        contextTrackUri = getSingleTrackUri(findMenuItem(list, "queue_track"));
                        trackTitle = "";
                        trackArtist = "";
                        if (contextTrackUri != null) updateLastFmHeader(param.args[0], headerTitleField, headerSubtitleField, contextTrackUri);

                        if (cachedOriginalViewModel == null && list.size() >= 4) {
                            Object probablyAddToPlaylist = list.get(3);
                            cachedOriginalViewModel = getMenuItemViewModel(probablyAddToPlaylist);

                            List<Field> viewModelFields = Arrays.stream(cachedOriginalViewModel.getClass().getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).collect(Collectors.toList());
                            Class<?> titleType = viewModelFields.get(1).getType();
                            List<Constructor<?>> directTextTitleConstructors = Arrays.stream(cachedOriginalViewModel.getClass().getDeclaredConstructors()).flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())).filter(parameterType -> parameterType != titleType && titleType.isAssignableFrom(parameterType)).distinct().map(parameterType -> getStringConstructor(parameterType)).filter(Objects::nonNull).collect(Collectors.toList());
                            if (directTextTitleConstructors.size() != 1) throw new IllegalStateException("[NewContextMenuHook] Expected the menu view-model constructors to reference one direct-text implementation of " + titleType.getName() + " but found " + directTextTitleConstructors.size() + ": " + directTextTitleConstructors.stream().map(constructor -> constructor.getDeclaringClass().getName()).collect(Collectors.joining(", ")));
                            directTextTitleConstructor = directTextTitleConstructors.get(0);
                            directTextTitleConstructor.setAccessible(true);
                            Field iconField = viewModelFields.get(3);
                            iconField.setAccessible(true);
                            Object icon = iconField.get(cachedOriginalViewModel);
                            if (icon == null || icon.getClass().getInterfaces().length == 0) throw new IllegalStateException("[NewContextMenuHook] Could not identify the icon interface from " + cachedOriginalViewModel.getClass().getName());
                            interfaceClass = icon.getClass().getInterfaces()[0];

//                            Object trfObject = XposedHelpers.getObjectField(list.get(1).getClass().getMethod("getViewModel").invoke(list.get(1)), "d");
//                            trfClass = trfObject.getClass();
//                            rsfClass = XposedHelpers.getObjectField(trfObject, "a").getClass();
//
//                            for(int i = 0; i < list.size() - 1; i++) {
//                                XposedBridge.log("[SpotifyPlus] Thing " + i + ": " + XposedHelpers.getObjectField(list.get(i).getClass().getMethod("getViewModel").invoke(list.get(i)), "d").getClass().getName());
//                            }
                        }

                        boolean hasLastFmItem = hasMenuItem(list, LAST_FM_MARKER);
                        boolean hasLyricsItem = hasMenuItem(list, LYRICS_MARKER);
                        boolean hasGenerateLyricsItem = hasMenuItem(list, GENERATE_LYRICS_MARKER);

                        ArrayList<Object> newList = new ArrayList<>(list);
                        boolean changed = false;

                        if (!hasMenuItem(list, "queue_play_next_track")) {
                            Object addToQueueItem = findMenuItem(list, "queue_track");
                            if (getSingleTrackUri(addToQueueItem) != null) {
                                Object playNextItem = createPlayNextTrackItem(addToQueueItem);
                                if (playNextItem != null) {
                                    int addToQueueIndex = newList.indexOf(addToQueueItem);
                                    newList.add(Math.max(0, addToQueueIndex), playNextItem);
                                    changed = true;
                                }
                            }
                        }

                        if (contextTrackUri != null && !hasLastFmItem) {
                            Context context = AndroidAppHelper.currentApplication();
                            if (context != null) {
                                Object radioButton = XposedHelpers.newInstance(radioButtonClass, context, LAST_FM_MARKER);
                                newList.add(0, radioButton);
                                changed = true;
                            }
                        }

                        if (contextTrackUri != null && !hasGenerateLyricsItem) {
//                            Context context = AndroidAppHelper.currentApplication();
//                            if (context != null) {
//                                Object radioButton = XposedHelpers.newInstance(radioButtonClass, context, GENERATE_LYRICS_MARKER);
//                                newList.add(0, radioButton);
//                                changed = true;
//                            }
                        }

                        if (!hasLyricsItem) {
                            Context context = AndroidAppHelper.currentApplication();
                            if (context != null) {
                                Object radioButton = XposedHelpers.newInstance(radioButtonClass, context, LYRICS_MARKER);
                                newList.add(0, radioButton);
                                changed = true;
                            }
                        }

                        if (changed) {
                            param.args[1] = newList;
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                }
            });

            XposedBridge.hookMethod(radioButtonViewModelAccessor, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String marker = getSpotifyPlusItemMarker(param.thisObject);
                    if (marker == null) return;

                    Object viewModel = null;

                    if (marker.equals(LYRICS_MARKER) && cachedLyricsViewModel != null) {
                        viewModel = cachedLyricsViewModel;
                    } else if (marker.equals(GENERATE_LYRICS_MARKER) && cachedGenerateLyricsViewModel != null) {
                        viewModel = cachedGenerateLyricsViewModel;
                    } else if (marker.equals(LAST_FM_MARKER) && cachedViewModel != null) {
                        viewModel = cachedViewModel;
                    } else {
                        if (cachedOriginalViewModel == null) return;

                        String title;
                        if (marker.equals(LYRICS_MARKER)) {
                            title = "Lyrics";
                        } else if (marker.equals(GENERATE_LYRICS_MARKER)) {
                            title = "Generate AI lyrics";
                        } else {
                            title = "Open in Last.fm";
                        }

                        viewModel = cloneMenuViewModel(cachedOriginalViewModel, marker, title);

                        if (marker.equals(LYRICS_MARKER)) cachedLyricsViewModel = viewModel;
                        else if (marker.equals(GENERATE_LYRICS_MARKER)) cachedGenerateLyricsViewModel = viewModel;
                        else cachedViewModel = viewModel;
                    }

                    param.setResult(viewModel);
                }
            });

            // Click Handler
            XposedHelpers.findAndHookMethod(ContextWrapper.class, "startService", Intent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Intent intent = (Intent) param.args[0];

                        if (intent.getComponent() != null && intent.getComponent().getClassName().equals("com.spotify.radio.radio.formatlist.RadioFormatListService") && intent.hasExtra(".seed_uri")) {
                            String seed = intent.getStringExtra(".seed_uri");

                            if (LYRICS_MARKER.equals(seed)) {
                                param.setResult(null);
                                Activity activity = References.currentActivity;
                                if (activity != null) {
                                    new Handler(Looper.getMainLooper()).post(() -> activity.getWindow().getDecorView().post(() -> BeautifulLyricsHook.showOverlay(activity, false)));
                                }
                            } else if (GENERATE_LYRICS_MARKER.equals(seed)) {
                                param.setResult(null);

                                String spotifyId = getSpotifyTrackId(contextTrackUri);
                                if (spotifyId != null) onGenerateLyricsPressed(spotifyId);
                            } else if (LAST_FM_MARKER.equals(seed)) {
                                Context context = (Context) param.thisObject;

                                Intent newIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/music/" + URLEncoder.encode(trackArtist) + "/_/" + URLEncoder.encode(trackTitle)));
                                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(newIntent);

                                param.setResult(null);
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                }
            });

            // Icon
            Class<?> iconClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingEqStrings("ContextMenuItem"))).get(0).getInstance(lpparm.classLoader);
            var methods = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(iconClass))).matcher(MethodMatcher.create().params(ParametersMatcher.create().count(6))));
            var iconRendererMethods = methods.stream().filter(methodData -> methodData.isMethod() && methodData.getReturnTypeName().equals("void") && Modifier.isStatic(methodData.getModifiers())).collect(Collectors.toList());
            if (iconRendererMethods.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected one static void six-parameter ContextMenuItem icon renderer in " + iconClass.getName() + " but found " + iconRendererMethods.size() + ": " + iconRendererMethods.stream().map(methodData -> methodData.getDescriptor()).collect(Collectors.joining(", ")) + "; all six-parameter members: " + methods.stream().map(methodData -> methodData.getDescriptor()).collect(Collectors.joining(", ")));
            Method method = iconRendererMethods.get(0).getMethodInstance(lpparm.classLoader);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!isRenderingSpotifyPlusRow()) return;

                            try {
                                Object customTrf = getSpotifyPlusIcon(spotifyPlusRenderMarker.get());
                                if (customTrf == null) return;

                                param.args[1] = customTrf;
                            } catch (Throwable t) {
                                XposedBridge.log("[SpotifyPlus] Failed swapping gc0.c icon: " + t);
                            }
                        }
                    }
            );

            var uweClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().fields(FieldsMatcher.create().count(2).add(FieldMatcher.create().type(int.class))).usingStrings("CreateMenuItemElement")));
            if (uweClasses.isEmpty()) {
                XposedBridge.log("[SpotifyPlus] Context-menu row-render marker fingerprint is unavailable; the custom view-model icons remain installed directly.");
            } else {
                Class<?> uweClass = uweClasses.get(0).getInstance(lpparm.classLoader);
                XposedBridge.hookAllMethods(uweClass, "invoke", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            int branch = bridge.findField(FindField.create().searchInClass(uweClasses).matcher(FieldMatcher.create().type(int.class))).get(0).getFieldInstance(lpparm.classLoader).getInt(param.thisObject);
                            if (branch != 10) return;
                            if (param.args.length < 2) return;
                            Object obj2 = param.args[1]; // psf in case 10
                            String marker = getSpotifyPlusRowMarker(obj2);
                            if (marker == null) return;
                            pushSpotifyPlusRender(marker);
                            param.setObjectExtra("spotifyplus_row_render", Boolean.TRUE);
                        } catch (Throwable t) {
                            XposedBridge.log("[SpotifyPlus] uwe invoke before failed: " + t);
                        }
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (Boolean.TRUE.equals(param.getObjectExtra("spotifyplus_row_render"))) popSpotifyPlusRender();
                        } catch (Throwable t) {
                            XposedBridge.log("[SpotifyPlus] uwe invoke after failed: " + t);
                        }
                    }
                });
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private void updateLastFmHeader(Object header, Field titleField, Field subtitleField, String trackUri) {
        if (header == null || trackUri == null || !trackUri.startsWith("spotify:track:")) return;

        try {
            String title = (String) titleField.get(header);
            String subtitleTextFull = (String) subtitleField.get(header);
            if (title == null || title.isEmpty() || subtitleTextFull == null || subtitleTextFull.isEmpty()) return;

            String artist = subtitleTextFull.split(" • ")[0];
            trackTitle = title;
            trackArtist = artist;
            if (subtitleTextFull.contains("scrobbles")) return;

            SharedPreferences ref = References.getPreferences();
            String username = ref.getString("last_fm_username", "null");
            if (username.equals("null")) return;

            Activity activity = References.currentActivity;
            OkHttpClient client = new OkHttpClient();
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
                latch.await();
                if (exceptionRef.get() != null) throw new RuntimeException(exceptionRef.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (resultRef.get() == null) throw new IllegalStateException("Last.fm returned no response body");
            JsonObject root = JsonParser.parseString(resultRef.get()).getAsJsonObject();
            String scrobbles = root.getAsJsonObject("track").get("userplaycount").getAsString();
            subtitleField.set(header, subtitleTextFull + " • " + scrobbles + " scrobbles");
        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Failed to fetch scrobbles for " + trackUri);
            XposedBridge.log("[SpotifyPlus] " + e);
            Activity activity = References.currentActivity;
            if (activity != null) new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show());
        }
    }

    private Object getSpotifyPlusIcon(String marker) {
        try {
            if (LYRICS_MARKER.equals(marker) && cachedSpotifyPlusLyricsTrf != null) return cachedSpotifyPlusLyricsTrf;
            if (GENERATE_LYRICS_MARKER.equals(marker) && cachedSpotifyPlusGenerateLyricsTrf != null) return cachedSpotifyPlusGenerateLyricsTrf;
            if (LAST_FM_MARKER.equals(marker) && cachedSpotifyPlusTrf != null) return cachedSpotifyPlusTrf;

            Context appContext = AndroidAppHelper.currentApplication();
            if (appContext == null) {
                XposedBridge.log("[SpotifyPlus] appContext was null");
                return null;
            }

            int drawableId;
            if (LAST_FM_MARKER.equals(marker)) {
                drawableId = R.drawable.lastfm;
            } else if (GENERATE_LYRICS_MARKER.equals(marker)) {
                drawableId = R.drawable.wand;
            } else {
                drawableId = R.drawable.music_note;
            }
            Drawable drawable = References.modResources.getDrawable(drawableId);

            if (drawable == null) {
                XposedBridge.log("[SpotifyPlus] module drawable was null");
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
                Class<?> outerClass = classData.getInstance(lpparm.classLoader);
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
            if (customIcon == null) throw new IllegalStateException("[NewContextMenuHook] Could not find a " + interfaceClass.getName() + " implementation backed by Drawable or LayerDrawable");
            if (LYRICS_MARKER.equals(marker)) cachedSpotifyPlusLyricsTrf = customIcon;
            else if (GENERATE_LYRICS_MARKER.equals(marker)) cachedSpotifyPlusGenerateLyricsTrf = customIcon;
            else cachedSpotifyPlusTrf = customIcon;
            return customIcon;
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Failed to create custom trf: " + t);
            return null;
        }
    }

    private Object getDrawableConstructorArgument(Class<?> parameterType, Drawable drawable, LayerDrawable layerDrawable) {
        if (!Drawable.class.isAssignableFrom(parameterType)) return null;
        if (parameterType.isInstance(drawable)) return drawable;
        return parameterType.isInstance(layerDrawable) ? layerDrawable : null;
    }

    private static void pushSpotifyPlusRender(String marker) {
        spotifyPlusRenderDepth.set(spotifyPlusRenderDepth.get() + 1);
        spotifyPlusRenderMarker.set(marker);
    }

    private static void popSpotifyPlusRender() {
        int depth = spotifyPlusRenderDepth.get() - 1;
        if (depth <= 0) {
            spotifyPlusRenderDepth.remove();
            spotifyPlusRenderMarker.remove();
        } else {
            spotifyPlusRenderDepth.set(depth);
        }
    }

    private static boolean isRenderingSpotifyPlusRow() {
        Integer depth = spotifyPlusRenderDepth.get();
        return depth != null && depth > 0;
    }

    private static String getSpotifyPlusRowMarker(Object obj) {
        return findSpotifyPlusMarker(obj, 2, new IdentityHashMap<>());
    }

    private static String findSpotifyPlusMarker(Object value, int remainingDepth, IdentityHashMap<Object, Boolean> visited) {
        if (LAST_FM_MARKER.equals(value) || LYRICS_MARKER.equals(value) || GENERATE_LYRICS_MARKER.equals(value)) return (String) value;
        if (value == null || remainingDepth == 0 || visited.put(value, Boolean.TRUE) != null) return null;
        Class<?> valueClass = value.getClass();
        if (valueClass.isPrimitive() || valueClass.isEnum() || valueClass.isArray() || valueClass.getName().startsWith("java.") || valueClass.getName().startsWith("android.") || valueClass.getName().startsWith("kotlin.")) return null;
        for (Class<?> type = valueClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    String marker = findSpotifyPlusMarker(field.get(value), remainingDepth - 1, visited);
                    if (marker != null) return marker;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static String getSpotifyPlusItemMarker(Object item) {
        if (item == null) return null;
        for (Class<?> type = item.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (LAST_FM_MARKER.equals(value) || LYRICS_MARKER.equals(value) || GENERATE_LYRICS_MARKER.equals(value)) return (String) value;
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
        if (accessor == null) throw new NoSuchMethodException("[NewContextMenuHook] Could not identify the view-model accessor on " + itemClass.getName() + "; candidates: " + candidates.stream().map(Method::toString).collect(Collectors.joining(", ")));
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
        Constructor<?> constructor = Arrays.stream(template.getClass().getDeclaredConstructors()).filter(candidate -> Arrays.equals(candidate.getParameterTypes(), fieldTypes)).findFirst().orElseThrow(() -> new NoSuchMethodException("[NewContextMenuHook] No primary data-class constructor matches the fields of " + template.getClass().getName()));
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
        if (!markerReplaced || !titleReplaced || !iconReplaced) throw new IllegalStateException("[NewContextMenuHook] Could not replace the marker, title, and icon while cloning " + template.getClass().getName());
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

    private static String getSpotifyTrackId(String uri) {
        String prefix = "spotify:track:";
        if (uri == null || !uri.startsWith(prefix)) return null;

        String spotifyId = uri.substring(prefix.length());
        if (spotifyId.isBlank() || spotifyId.contains(":")) return null;

        return spotifyId;
    }

    private static void onGenerateLyricsPressed(String spotifyId) {
        XposedBridge.log("[SpotifyPlus] Generate lyrics pressed for Spotify track " + spotifyId);
    }

    private Object createPlayNextTrackItem(Object addToQueueItem) {
        if (addToQueueItem == null) return null;

        for (Constructor<?> ctor : addToQueueItem.getClass().getDeclaredConstructors()) {
            try {
                Class<?>[] parameterTypes = ctor.getParameterTypes();
                if (!Arrays.asList(parameterTypes).contains(List.class)) continue;
                Object[] args = new Object[parameterTypes.length];
                boolean complete = true;

                for (int i = 0; i < parameterTypes.length; i++) {
                    args[i] = getField(addToQueueItem, parameterTypes[i]);
                    if (args[i] == null) {
                        complete = false;
                        break;
                    }
                }

                if (!complete) continue;

                ctor.setAccessible(true);
                Object candidate = ctor.newInstance(args);
                if ("queue_play_next_track".equals(getMenuItemId(candidate))) {
                    NextUpAction action = createNextUpAction(args, candidate);
                    if (action != null) {
                        installNextUpClickHook(candidate.getClass());
                        nextUpActions.put(candidate, action);
                        return candidate;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private NextUpAction createNextUpAction(Object[] roots, Object item) {
        try {
            Object value = getField(item, List.class);
            if (!(value instanceof List)) return null;

            List<?> tracks = (List<?>) value;
            if (tracks.size() != 1 || tracks.get(0) == null) return null;

            Class<?> setQueueCommandClass = XposedHelpers.findClass("com.spotify.player.model.command.SetQueueCommand", lpparm.classLoader);
            Object queueRepository = null;
            for (Object root : roots) {
                queueRepository = findQueueRepository(root, setQueueCommandClass, 2, new IdentityHashMap<>());
                if (queueRepository != null) break;
            }
            if (queueRepository == null) throw new IllegalStateException("Could not find a queue repository beneath any context-menu item dependency");
            Method queueDispatchMethod = findSingleArgumentMethod(queueRepository.getClass(), setQueueCommandClass);
            Class<?> flowableClass = XposedHelpers.findClass("io.reactivex.rxjava3.core.Flowable", lpparm.classLoader);
            Object queueStream = getField(queueRepository, flowableClass);
            if (queueStream == null) throw new IllegalStateException("Could not find the PlayerQueue Flowable in " + queueRepository.getClass().getName());
            Class<?> singleClass = XposedHelpers.findClass("io.reactivex.rxjava3.core.Single", lpparm.classLoader);
            List<Method> queueReadMethods = Arrays.stream(queueStream.getClass().getMethods()).filter(method -> !Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && singleClass.isAssignableFrom(method.getReturnType())).collect(Collectors.toList());
            if (queueReadMethods.size() != 1) throw new IllegalStateException("Expected one zero-parameter Single method on " + queueStream.getClass().getName() + " but found " + queueReadMethods.size() + ": " + queueReadMethods.stream().map(Method::toString).collect(Collectors.joining(", ")));
            Method setQueueFactory = Arrays.stream(setQueueCommandClass.getDeclaredMethods()).filter(method -> Modifier.isStatic(method.getModifiers()) && method.getReturnType() == setQueueCommandClass && Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class, List.class, List.class})).findFirst().orElseThrow(() -> new NoSuchMethodException("No (String, List, List) SetQueueCommand factory"));
            return new NextUpAction(queueRepository, queueStream, queueDispatchMethod, queueReadMethods.get(0), setQueueFactory, tracks.get(0));
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Failed resolving the Next Up queue action: " + t);
            return null;
        }
    }

    private void installNextUpClickHook(Class<?> itemClass) {
        synchronized (nextUpClickHookClasses) {
            if (nextUpClickHookClasses.contains(itemClass)) return;

            var clickMethodCandidates = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(itemClass))).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(void.class).paramCount(1)));
            if (clickMethodCandidates.size() != 1) throw new IllegalStateException("[NewContextMenuHook/DexKit] Expected one public final one-parameter void click method in " + itemClass.getName() + " but found " + clickMethodCandidates.size() + ": " + clickMethodCandidates.stream().map(methodData -> methodData.getDescriptor()).collect(Collectors.joining(", ")));
            Method clickMethod;
            try {
                clickMethod = clickMethodCandidates.get(0).getMethodInstance(lpparm.classLoader);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("[NewContextMenuHook/DexKit] Could not load the click method from " + itemClass.getName(), e);
            }
            XposedBridge.hookMethod(clickMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    NextUpAction action = nextUpActions.get(param.thisObject);
                    if (action == null) return;

                    param.setResult(null);
                    insertAtTopOfNextUp(action);
                }
            });
            nextUpClickHookClasses.add(itemClass);
        }
    }

    private void insertAtTopOfNextUp(NextUpAction action) {
        try {
            Object queueSingle = action.queueReadMethod.invoke(action.queueStream);
            Object onQueue = newRxConsumer(queue -> {
                try {
                    List<?> currentNextTracks = (List<?>) XposedHelpers.callMethod(queue, "nextTracks");
                    List<?> currentPrevTracks = (List<?>) XposedHelpers.callMethod(queue, "prevTracks");
                    String revision = (String) XposedHelpers.callMethod(queue, "revision");

                    ArrayList<Object> nextTracks = new ArrayList<>(currentNextTracks);
                    int nextUpStart = 0;
                    while (nextUpStart < nextTracks.size() && isQueuedTrack(nextTracks.get(nextUpStart))) {
                        nextUpStart++;
                    }
                    Object nextUpTrack = withoutQueuedFlag(action.track);
                    if (nextUpTrack == null) {
                        throw new IllegalStateException("Could not create an unqueued ContextTrack");
                    }
                    nextTracks.add(nextUpStart, nextUpTrack);

                    Object command = action.setQueueFactory.invoke(null, revision, nextTracks, new ArrayList<>(currentPrevTracks));
                    Object updateSingle = action.queueDispatchMethod.invoke(action.queueRepository, command);
                    XposedHelpers.callMethod(
                            updateSingle,
                            "subscribe",
                            newRxConsumer(ignored -> { }),
                            newRxConsumer(this::logNextUpError)
                    );
                } catch (Throwable t) {
                    logNextUpError(t);
                }
            });

            XposedHelpers.callMethod(
                    queueSingle,
                    "subscribe",
                    onQueue,
                    newRxConsumer(this::logNextUpError)
            );
        } catch (Throwable t) {
            logNextUpError(t);
        }
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
                lpparm.classLoader
        );
        return java.lang.reflect.Proxy.newProxyInstance(
                lpparm.classLoader,
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

    private static final class NextUpAction {
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

    private Boolean[] getFirstBooleans(Object obj) {
        try {
            ArrayList<Boolean> out = new ArrayList<>();
            for (Field f : obj.getClass().getDeclaredFields()) {
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    out.add(f.getBoolean(obj));
                    if (out.size() == 3) break;
                }
            }
            if (out.size() < 3) return null;
            return out.toArray(new Boolean[0]);
        } catch (Throwable t) {
            return null;
        }
    }
}

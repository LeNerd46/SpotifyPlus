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
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.SpotifyPlusSettings;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.scripting.ScriptContextMenu;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import kotlin.jvm.functions.Function0;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@XposedHooker
public class ContextMenuHook extends SpotifyHook {
    private static final String GENERATE_LYRICS_MENU_ID = "spotifyplus_generate_lyrics";

    private static volatile Object cachedOriginalViewModel;
    private static volatile Object cachedViewModel;
    private static volatile Object cachedSpotifyPlusTrf;

    private static String trackTitle;
    private static String trackArtist;
    private static boolean newContextMenu;

    private static final ThreadLocal<Integer> spotifyPlusRenderDepth = ThreadLocal.withInitial(() -> 0);
    private static Class<?> c8f;
    private static Class<?> interfaceClass;
    private static Class<?> jffClass;
    private static Class<?> headerObject;
    private static Class<?> radioButtonClass;
    private static Class<?> uweClass;
    private static Method iconMethod;
    private static Method replaceResourceIdMethod;

    private final List<ScriptContextMenu> scriptMenus = new ArrayList<>();
    private int idToUse = 2131957898;
    private int generateLyricsResourceId = -1;

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        try {
            Class<?> kyx0 = findClass("com.spotify.localfiles.mediastoreimpl.LocalFilesProperties$dataProps$2").getInterfaces()[0];

            var jffClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().methods(MethodsMatcher.create().count(3)
                    .add(MethodMatcher.create().returnType(boolean.class).name("equals").paramCount(1))
                    .add(MethodMatcher.create().returnType(int.class).name("hashCode").paramCount(0).usingNumbers(31, 0))
                    .add(MethodMatcher.create().name("<init>").paramCount(4))
            ).fields(FieldsMatcher.create().count(3)
                    .add(FieldMatcher.create().type(String.class))
                    .add(FieldMatcher.create().type(kyx0))
            )));

            if (jffClasses.isEmpty()) {
                jffClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().methods(MethodsMatcher.create().count(3)
                        .add(MethodMatcher.create().returnType(boolean.class).name("equals").paramCount(1))
                        .add(MethodMatcher.create().returnType(int.class).name("hashCode").paramCount(0).usingNumbers(31, 0))
                        .add(MethodMatcher.create().name("<init>").paramCount(3))
                ).fields(FieldsMatcher.create().count(2)
                        .add(FieldMatcher.create().type(Function0.class))
                )));

                newContextMenu = true;
            } else {
                newContextMenu = false;
            }

            jffClass = jffClasses.get(0).getInstance(classLoader);
            var c8fClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("mute_playback", "resume_pause_button", "hit", "item_to_be_resumed", "seek_bar")));

            if (newContextMenu) {
                c8fClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("block_confirmation_dialog", "request_received_view", "ui_reveal", "reject_confirmation_dialog")));
            }

            c8f = c8fClasses.get(0).getInstance(classLoader);
            hook(c8f.getDeclaredMethod("invoke", Object.class, Object.class, Object.class, Object.class));

            // Buttons

            headerObject = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id="))).get(0).getInstance(classLoader);
            radioButtonClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("audiobook_supplementary_content"))).get(0).getInstance(classLoader);
            for (Constructor<?> constructor : headerObject.getConstructors()) {
                hook(constructor);
            }

            hook(radioButtonClass.getDeclaredMethod("getViewModel"));
            hook(ContextWrapper.class.getDeclaredMethod("startService", Intent.class));

            // Icon

            Class<?> iconClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingEqStrings("ContextMenuItem"))).get(0).getInstance(classLoader);
            var methods = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(iconClass))).matcher(MethodMatcher.create().params(ParametersMatcher.create().count(6))));
            iconMethod = methods.get(0).getMethodInstance(classLoader);
            hook(iconMethod);

            var uweClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().fields(FieldsMatcher.create().count(2).add(FieldMatcher.create().type(int.class))).usingStrings("CreateMenuItemElement")));
            uweClass = uweClasses.get(0).getInstance(classLoader);
            hook(uweClass.getDeclaredMethod("invoke", Object.class, Object.class, Object.class, Object.class, Object.class));

            var resourceIdClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("your_library_tag_share_header_action_button")));
            replaceResourceIdMethod = bridge.findMethod(FindMethod.create().searchInClass(resourceIdClass).matcher(MethodMatcher.create().returnType(String.class))).stream().filter(x -> !x.getMethodName().equals("getDebugIdentifier")).collect(Collectors.toList()).get(0).getMethodInstance(classLoader);
            hook(replaceResourceIdMethod);

            SpotifyNativeBridge.registerHandler("menu", this);
        } catch (Exception e) {
            logError(e);
        }
    }

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        ContextMenuHook hook = getHook(ContextMenuHook.class);
        if(hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        try {
            if (member.getDeclaringClass() == c8f) {
                Object jff = callback.getArgs()[0];
                if (jff == null || jff.getClass() != jffClass) return;

                for (int i = 0; i < callback.getArgs().length; i++) {
                    var obj = callback.getArgs()[i];

                    if(obj.getClass().getName().equals("p.yqf")) {
                        Object vqf = obj.getClass().getDeclaredField("a").get(obj);

                        String a = (String) vqf.getClass().getDeclaredField("a").get(vqf);
                        String c = (String) vqf.getClass().getDeclaredField("c").get(vqf);
                    }
                }

                String username = SpotifyPlusSettings.lastfmUsername;
                if (username.equals("null")) return;

                Object gff = jff.getClass().getDeclaredField(newContextMenu ? "a" : "b").get(jff);
                if (gff == null) {
                    logError("It did not stay as b :(");
                }

                String title = (String) gff.getClass().getDeclaredField("a").get(gff);
                String subtitleTextFull = (String) gff.getClass().getDeclaredField("c").get(gff);

                if (subtitleTextFull.contains("scrobbles")) {
                    return;
                }

                String artist = subtitleTextFull.split(" • ")[0];

                trackTitle = title;
                trackArtist = artist;

                WeakReference<String> oldSubtitle = new WeakReference<>(subtitleTextFull);
                WeakReference<Object> subtitleObject = new WeakReference<>(gff);

                // If it takes longer than 3 seconds, just give up. You can try it again, but sometimes you just want to open the context menu quickly
                OkHttpClient client = new OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).writeTimeout(3, TimeUnit.SECONDS).build();
                Request request;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {                                      //  Yeah I know this is bad, but whatever
                    request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8) + "&track=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&format=json&user=" + URLEncoder.encode(username, StandardCharsets.UTF_8)).build();
                } else {
                    request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist) + "&track=" + URLEncoder.encode(title) + "&format=json&user=" + URLEncoder.encode(username)).build();
                }

                // Probably not the best way to do this, but you know what, it works
                // We're blocking the main UI thread here, which Android explicitly asks you not to do
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

                    if (exceptionRef.get() != null) {
                        throw new RuntimeException(exceptionRef.get());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (resultRef.get() != null) {
                    try {
                        JsonObject root = JsonParser.parseString(resultRef.get()).getAsJsonObject();
                        String scrobbles = root.getAsJsonObject("track").get("userplaycount").getAsString();

                        Object subtitle = subtitleObject.get();
                        String oldSubtitleText = oldSubtitle.get();

                        Field c = subtitle.getClass().getDeclaredField("c");
                        c.setAccessible(true);
                        c.set(subtitle, oldSubtitleText + " • " + scrobbles + " scrobbles");
                    } catch (Exception e) {
                        logError("Failed to fetch scrobbles", e);
                        toast("Failed to fetch scrobbles");
                    }
                }
            } else if (member.getDeclaringClass() == headerObject) {
                List<?> list = (List<?>) callback.getArgs()[1];
                if (list == null) return;

                String uri = captureContextMenuUri(list);
                lastContextMenuUri = uri;

                if (cachedOriginalViewModel == null && list.size() >= 3) {
                    Object probablyAddToPlaylist = list.get(3);
                    cachedOriginalViewModel = probablyAddToPlaylist.getClass().getMethod("getViewModel").invoke(probablyAddToPlaylist);

                    interfaceClass = Arrays.stream(cachedOriginalViewModel.getClass().getDeclaredFields()).filter(x -> x.getName().equals("d")).collect(Collectors.toList()).get(0).get(cachedOriginalViewModel).getClass().getInterfaces()[0];
                }

                if (list.stream().anyMatch(item -> {
                    try {
                        if (item == null || item.getClass() != radioButtonClass) return false;
                        Object markerValue = item.getClass().getDeclaredField("c").get(item);
                        return markerValue.equals("spotifyplus_open_last_fm");
                    } catch (Exception e) {
                        logError(e);
                        return false;
                    }
                })) return;

                if (currentActivity == null) {
                    logError("Current activity is null");
                    return;
                }

                Object radioButton = radioButtonClass.getDeclaredConstructor(Context.class, String.class).newInstance(currentActivity, "spotifyplus_open_last_fm");

                ArrayList<Object> newList = new ArrayList<>(list.size() + 2 + scriptMenus.size());
                newList.add(radioButton);

                if (getSpotifyTrackId(uri) != null) {
                    Object generateLyricsButton = radioButtonClass.getDeclaredConstructor(Context.class, String.class).newInstance(currentActivity, GENERATE_LYRICS_MENU_ID);
                    newList.add(generateLyricsButton);
                }

                // THIS IS WHERE YOU ADD BUTTONS
                for(var menu : scriptMenus) {
                    Object newButton = radioButtonClass.getDeclaredConstructor(Context.class, String.class).newInstance(currentActivity, menu.id);
                    newList.add(newButton);
                }
                newList.addAll(list);
                callback.getArgs()[1] = newList;
            } else if (member.getName().equals("getViewModel")) {
                String marker = callback.getThisObject().getClass().getDeclaredField("c").get(callback.getThisObject()).toString();
                boolean isGenerateLyrics = GENERATE_LYRICS_MENU_ID.equals(marker);
                var thing = scriptMenus.stream().filter(x -> x.id.equals(marker)).findFirst();

                if (!isGenerateLyrics && thing.isEmpty()) return;

                ScriptContextMenu menu = thing.orElse(null);
                int resourceId = idToUse;

                if (isGenerateLyrics) {
                    generateLyricsResourceId = resourceId;
                } else {
                    menu.resourceId = resourceId;
                }

                Object viewModel;

                if (cachedViewModel != null) {
                    viewModel = cachedViewModel;
                } else {
                    if (cachedOriginalViewModel == null) return;
                    Class<?> pgf = cachedOriginalViewModel.getClass();

                    Object oldTitleObject = cachedOriginalViewModel.getClass().getDeclaredField("b").get(cachedOriginalViewModel);
                    if (oldTitleObject == null) return;

                    Object title = oldTitleObject.getClass().getDeclaredConstructor(int.class).newInstance(resourceId);
                    Object template = cachedOriginalViewModel;
                    if (template == null) return;

                    Constructor<?> ctor = Arrays.stream(pgf.getDeclaredConstructors()).filter(x -> x.getParameterCount() == 10 && x.getParameterTypes()[x.getParameterCount() - 1] == int.class).collect(Collectors.toList()).get(0);
                    Class<?>[] parameters = ctor.getParameterTypes();

                    // I don't know what button we cached for sure, so we look for these fields reflectively
                    Object hgf = getField(template, parameters[3]);
                    Object y6y0 = getField(template, parameters[4]);
                    Object h0y0 = getField(template, parameters[8]);

                    Boolean[] booleans = getFirstBooleans(template);

                    viewModel = ctor.newInstance(marker, title, null, hgf, y6y0, booleans[0], booleans[1], booleans[2], h0y0, 1988);
                    idToUse++;
                }

                callback.returnAndSkip(viewModel);
            } else if (member.getName().equals("startService")) {
                Intent intent = (Intent) callback.getArgs()[0];

                if (intent.getComponent().getClassName().equals("com.spotify.radio.radio.formatlist.RadioFormatListService") && intent.hasExtra(".seed_uri")) {
                    String seed = intent.getStringExtra(".seed_uri");

                    if (GENERATE_LYRICS_MENU_ID.equals(seed)) {
                        String spotifyId = getSpotifyTrackId(lastContextMenuUri);

                        if (spotifyId != null) {
                            onGenerateLyricsPressed(spotifyId);
                        }

                        callback.returnAndSkip(null);
                        return;
                    }

                    var thing = scriptMenus.stream().filter(x -> x.id.equals(seed)).findFirst();
                    if(thing.isEmpty()) return;
                    ScriptContextMenu menu = thing.get();

                    JSONObject json = new JSONObject();
                    json.put("id", menu.id);
                    json.put("scriptId", menu.scriptId);
                    json.put("uri", lastContextMenuUri);

                    SpotifyNativeBridge.sendEvent("menu.press", json.toString());
//                    ScriptManager.send("", "event", "menu.press", json);

                    callback.returnAndSkip(null);

//                    if (seed.equals("spotifyplus_open_last_fm")) {
//                        Context context = (Context) callback.getThisObject();
//
//                        Intent newIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/music/" + URLEncoder.encode(trackArtist) + "/_/" + URLEncoder.encode(trackTitle)));
//                        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        context.startActivity(newIntent);
//
//                        callback.returnAndSkip(null);
//                    }
                }
            } else if (member == iconMethod) {
                if (!isRenderingSpotifyPlusRow()) return;

                Object customTrf = getLastfmIcon();
                if (customTrf == null) return;

                callback.getArgs()[1] = customTrf;
            } else if (member.getDeclaringClass() == uweClass) {
                int branch = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(uweClass))).matcher(FieldMatcher.create().type(int.class))).get(0).getFieldInstance(classLoader).getInt(callback.getThisObject());
                if (branch != 10) return;

                if (callback.getArgs().length < 2) return;
                Object obj2 = callback.getArgs()[1]; // psf in case 10
                if (!isSpotifyPlusRow(obj2)) return;

                pushSpotifyPlusRender();
            } else if (member == replaceResourceIdMethod) {
                try {
                    if (callback.getArgs().length < 2) return;
                    Object resourceObject = callback.getArgs()[1];
                    if (resourceObject == null) return;

                    int id = resourceObject.getClass().getDeclaredField("e").getInt(resourceObject);

                    if (id == generateLyricsResourceId) {
                        callback.returnAndSkip(Utils.getString(currentActivity.getApplicationContext(), R.string.generate_lyrics));
                        return;
                    }

                    var thing = scriptMenus.stream().filter(x -> x.resourceId == id).findFirst();
                    if(thing.isEmpty()) return;
                    ScriptContextMenu menu = thing.get();
                    callback.returnAndSkip(menu.title);

//                    if (id == 0x7f1313b2) {
//                        callback.returnAndSkip(Utils.getString(currentActivity.getApplicationContext(), R.string.open_last_fm));
//                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        ContextMenuHook hook = getHook(ContextMenuHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    protected void afterHook(SpotifyCallback callback) {
        try {
            if (callback.getMember().getDeclaringClass() == uweClass && spotifyPlusRenderDepth.get() > 0) {
                int branch = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(uweClass))).matcher(FieldMatcher.create().type(int.class))).get(0).getFieldInstance(classLoader).getInt(callback.getThisObject());
                if (branch != 10) return;

                if (callback.getArgs().length < 2) return;
                Object obj2 = callback.getArgs()[1]; // psf in case 10
                if (!isSpotifyPlusRow(obj2)) return;

                popSpotifyPlusRender();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        if (command.equals("register")) {
            try {
                String menuId = (String) args[0];
                String scriptId = (String) args[1];
                String title = (String) args[2];

                ScriptContextMenu menu = new ScriptContextMenu(menuId, scriptId, title);
                if(scriptMenus.contains(menu)) return null;

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

    private Object getLastfmIcon() {
        try {
            if (cachedSpotifyPlusTrf != null) return cachedSpotifyPlusTrf;

            if (currentActivity == null) {
                logError("Current activity is null");
                return null;
            }

            Drawable drawable = Utils.getDrawable(currentActivity, R.drawable.lastfm);

            if (drawable == null) {
                logError("module drawable was null");
                return null;
            }

            LayerDrawable layerDrawable;
            if (drawable instanceof LayerDrawable) {
                layerDrawable = (LayerDrawable) drawable;
            } else {
                layerDrawable = new LayerDrawable(new android.graphics.drawable.Drawable[]{drawable});
            }

            var classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().addInterface(interfaceClass.getName())));

            // I see no obvious way to differentiate the two, so why not try both? One of them should work
            Class<?> firstClass = classes.get(0).getInstance(classLoader);
            Class<?> secondClass = firstClass.getFields()[0].getType();

            for (var clazz : classes) {
                Class<?> tryClass = clazz.getInstance(classLoader);
                Class<?> a = tryClass.getFields()[0].getType();

                if (a.getFields()[0].getDeclaringClass() == LayerDrawable.class) {
                    firstClass = tryClass;
                    secondClass = a;
                }
            }

            Object rsf = secondClass.getDeclaredConstructor(LayerDrawable.class).newInstance(layerDrawable);
            Object trf = firstClass.getDeclaredConstructor(rsf.getClass()).newInstance(rsf);

            cachedSpotifyPlusTrf = trf;
            return trf;
        } catch (Throwable t) {
            logError(t);
            return null;
        }
    }

    private void pushSpotifyPlusRender() {
        spotifyPlusRenderDepth.set(spotifyPlusRenderDepth.get() + 1);
    }

    private void popSpotifyPlusRender() {
        int depth = spotifyPlusRenderDepth.get() - 1;
        if (depth <= 0) {
            spotifyPlusRenderDepth.remove();
        } else {
            spotifyPlusRenderDepth.set(depth);
        }
    }

    private boolean isRenderingSpotifyPlusRow() {
        Integer depth = spotifyPlusRenderDepth.get();
        return depth != null && depth > 0;
    }

    private boolean isSpotifyPlusRow(Object obj) {
        if (obj == null) return false;
        try {
            Object dsf = obj.getClass().getDeclaredField("a").get(obj);
            if (dsf == null) return false;
            Object key = dsf.getClass().getDeclaredField("a").get(dsf);
            return "spotifyplus_open_last_fm".equals(key);
        } catch (Throwable ignored) {
            return false;
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

    private static volatile String lastContextMenuUri;

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
                    Method getViewModel = item.getClass().getMethod("getViewModel");
                    Object vm = getViewModel.invoke(item);
                    if (vm == null) continue;

                    Field a = vm.getClass().getDeclaredField("a");
                    a.setAccessible(true);
                    Object value = a.get(vm);
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
}

package com.lenerd.spotifyplus.module;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyAlbum;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;
import com.lenerd.spotifyplus.module.scripting.entities.PlatformData;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {
    public static Resources mergedResources;
    public static Context wrappedContext;
    private static Typeface cachedTypeface;
    public static String token;
    public static String clientToken;
    public static String spotifyVersion;

    private static Resources moduleResources;
    private static Context moduleContext;
    private static ResourcesLoader moduleLoader;
    private static ResourcesProvider moduleProvider;

    public static DexKitBridge bridge;
    public static volatile String MODULE_APK_PATH;

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    public static Object playerState;
    private static Method hasTrackMethod;
    private static Method getContextTrack;
    public static Object playerStateWrapper;
    private static volatile Class<?> playbackPositionStateClass;
    private static volatile Field playbackPositionField;

    public static WeakReference<Pair<String, String>> currentContextTrack;
    private static boolean vectorCompatEnabled = false;
    public static PlatformData platformData;

    //region Loading Resources

    public static View inflate(Context context, int layoutId, ViewGroup parent) {
        if (context == null) return null;

        try {
            if (!vectorCompatEnabled) {
                AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
                vectorCompatEnabled = true;
            }

            ensureModuleResources(context.getApplicationContext());

            Context moduleBase = new ModuleBaseContext(context.getApplicationContext(), moduleResources, SpotifyLoader.class.getClassLoader());
            ContextThemeWrapper themed = new ContextThemeWrapper(moduleBase, R.style.Theme_SpotifyPlus);

//            if (moduleResources == null || moduleContext == null) {
//                Log.e("SpotifyPlus", "Failed to merge resources");
//                return null;
//            }

//            Context wrapped = new ModuleContextWrapper(baseContext, R.style.Theme_SpotifyPlus, moduleResources, SpotifyLoader.class.getClassLoader());

            LayoutInflater inflater = LayoutInflater.from(themed).cloneInContext(themed);
            View view = inflater.inflate(layoutId, parent, false);
            unloadResources();

            return view;
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
            return null;
        }
    }

    public static View inflate(Context context, String layoutName, ViewGroup parent) {
        if (context == null) return null;

        try {
            ensureModuleResources(context);

            if (mergedResources == null || wrappedContext == null) {
                Log.e("SpotifyPlus", "Failed to merge resources");
                return null;
            }

            LayoutInflater inflater = LayoutInflater.from(context.getApplicationContext()).cloneInContext(wrappedContext);
            return inflater.inflate(mergedResources.getLayout(mergedResources.getIdentifier(layoutName, "layout", "com.lenerd.spotifyplus")), parent, false);
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
            return null;
        }
    }

    public static String getString(Context context, int stringId, Object... formatArgs) {
        if (context == null) return null;

        try {
            ensureModuleResources(context);
            if (moduleResources == null) return null;

            String string = "";
            if (formatArgs != null && formatArgs.length > 0) {
                string = moduleResources.getString(stringId, formatArgs);
            } else {
                string = moduleResources.getString(stringId);
            }

            unloadResources();
            ;
            return string;
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
            return null;
        }
    }

    public static Drawable getDrawable(Context context, int drawableId) {
        if (context == null) return null;

        try {
            ensureModuleResources(context);

            if (moduleResources == null) {
                Log.e("SpotifyPlus", "Failed to merge resources");
                return null;
            }

            Drawable drawable = moduleResources.getDrawable(drawableId);
            unloadResources();

            return drawable;
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
            return null;
        }
    }

    private static void unloadResources() {
        try {
            if (moduleResources != null && moduleLoader != null) {
                moduleResources.removeLoaders(moduleLoader);
            }
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
        } finally {
            moduleResources = null;
            moduleContext = null;
            moduleLoader = null;

            if (moduleProvider != null) {
                try {
                    moduleProvider.close();
                } catch (Exception ignored) {
                }
                moduleProvider = null;
            }
        }
    }

//    private static synchronized void ensureModuleResources(Context context) throws Exception {
//        if (moduleResources != null && moduleContext != null) return;
//
//        File moduleApk = new File(MODULE_APK_PATH);
//        if (!moduleApk.exists()) {
//            Log.e("SpotifyPlus", "Could not find APK path!");
//            return;
//        }
//
//        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(moduleApk, ParcelFileDescriptor.MODE_READ_ONLY)) {
//            moduleProvider = ResourcesProvider.loadFromApk(pfd);
//        }
//
//        moduleLoader = new ResourcesLoader();
//        moduleLoader.addProvider(moduleProvider);
//
//        Resources hostRes = context.getResources();
//        moduleResources = new Resources(hostRes.getAssets(), hostRes.getDisplayMetrics(), hostRes.getConfiguration());
//        moduleResources.addLoaders(moduleLoader);
//
//        moduleContext = new ModuleContextWrapper(context, R.style.Theme_SpotifyPlus, moduleResources, SpotifyLoader.class.getClassLoader());
//    }

    private static AssetManager moduleAssetManager;

    public static synchronized void ensureModuleResources(Context context) throws Exception {
        if (moduleResources != null && moduleAssetManager != null) return;

        File moduleApk = new File(MODULE_APK_PATH);
        if (!moduleApk.exists()) throw new IllegalStateException("Could not find APK path: " + MODULE_APK_PATH);

        Resources hostRes = context.getResources();

        AssetManager am = AssetManager.class.getDeclaredConstructor().newInstance();
        Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        addAssetPath.setAccessible(true);

        int cookie = (Integer) addAssetPath.invoke(am, MODULE_APK_PATH);
        if (cookie == 0) throw new IllegalStateException("addAssetPath failed for " + MODULE_APK_PATH);

        moduleAssetManager = am;
        moduleResources = new Resources(am, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
    }

    private static void ensureWrappedContext(Context context) throws Exception {
//        ensureModuleResourcesOnly(context, true);
        if (wrappedContext != null || mergedResources == null) return;

        wrappedContext = new ModuleContextWrapper(context, R.style.Theme_SpotifyPlus, mergedResources, SpotifyLoader.class.getClassLoader());
    }

//    private static void ensureModuleResources(Context context) throws Exception {
//        if (mergedResources != null && wrappedContext != null) return;
//
//        File moduleApk = new File(MODULE_APK_PATH);
//        if (!moduleApk.exists()) {
//            Log.e("SpotifyPlus", "Could not find APK path!");
//            return;
//        }
//
//        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(moduleApk, ParcelFileDescriptor.MODE_READ_ONLY);
//        ResourcesProvider provider = ResourcesProvider.loadFromApk(pfd);
//
//        ResourcesLoader loader = new ResourcesLoader();
//        loader.addProvider(provider);
//
//        Resources hostRes = context.getApplicationContext().getResources();
//        Resources res = new Resources(hostRes.getAssets(), hostRes.getDisplayMetrics(), hostRes.getConfiguration());
//        res.addLoaders(loader);
//
////        mergedResources = res;
////        Context moduleContext = new ModuleResourceContext(context.getApplicationContext(), mergedResources, SpotifyLoader.class.getClassLoader());
////        wrappedContext = new ContextThemeWrapper(moduleContext, R.style.Theme_SpotifyPlus);
//

    /// /        mergedResources = ModuleContextWrapper.createMergedResources(context.getApplicationContext(), MODULE_APK_PATH);
    /// /        wrappedContext = new ModuleContextWrapper(context.getApplicationContext(), R.style.Theme_SpotifyPlus, mergedResources, ModuleContextWrapper.class.getClassLoader());
//
//        mergedResources = res;
//        wrappedContext = new ModuleContextWrapper(context, R.style.Theme_SpotifyPlus, mergedResources, SpotifyLoader.class.getClassLoader());
//    }
    public static Typeface loadTypeface(Context context, String font) {
        if (cachedTypeface != null) return cachedTypeface;
        if (context == null) return null;

        InputStream in;
        FileOutputStream out;

        try {
            ClassLoader classLoader = SpotifyLoader.class.getClassLoader();
            if (classLoader == null) {
                Log.e("SpotifyPlus", "Could not find SpotifyLoader class");
                return null;
            }

            in = classLoader.getResourceAsStream("fonts/" + font);

            if (in == null) {
                Log.e("SpotifyPlus", "Could not find font " + font);
                return null;
            }

            File outFile = new File(context.getCacheDir(), font);

            if (!outFile.exists() || outFile.length() == 0) {
                out = new FileOutputStream(outFile, false);

                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }

                out.flush();
            }

            cachedTypeface = new Typeface.Builder(outFile).build();
            return cachedTypeface;
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage());
            return null;
        }
    }

    public static AssetManager getModuleAssetManager() {
        try {
            AssetManager am = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);

            int cookie = (Integer) addAssetPath.invoke(am, MODULE_APK_PATH);
            if (cookie == 0) throw new IllegalStateException("addAssetPath failed for " + MODULE_APK_PATH);

            return am;
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to create module AssetManager", e);
            return null;
        }
    }

    public static InputStream openModuleAsset(Context context, String assetPath) {
        try {
            ensureModuleResources(context);
            if (moduleResources == null) return null;
            return moduleResources.getAssets().open(assetPath);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to open module asset: " + assetPath, e);
            return null;
        }
    }

    //endregion

    //region Spotify Playback

    public static SpotifyTrack getTrack(ClassLoader classLoader) {
        if (playerState == null) return null;

        try {
            Method getTrackMethod = playerState.getClass().getMethod("track");
            Object wrapper = getTrackMethod.invoke(playerState);
            if (wrapper == null) return null;

            if (hasTrackMethod == null)
                hasTrackMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(wrapper.getClass()))).matcher(MethodMatcher.create().paramCount(0).returnType(boolean.class))).get(0).getMethodInstance(classLoader);

            Object hasTrack = hasTrackMethod.invoke(wrapper);
            if (hasTrack == null) return null;

            if ((Boolean) hasTrack) {
                if (getContextTrack == null)
                    getContextTrack = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(wrapper.getClass()))).matcher(MethodMatcher.create().paramCount(0).returnType(Object.class))).get(0).getMethodInstance(classLoader);

                Object ct = getContextTrack.invoke(wrapper);
                if (ct == null) return null;

                Class<?> contextClass = Class.forName("com.spotify.player.model.ContextTrack", false, classLoader);
                if (contextClass.isInstance(ct)) {
                    Object track = contextClass.cast(ct);

                    Method uriMethod = track.getClass().getMethod("uri");
                    String uri = (String) uriMethod.invoke(track);

                    Method metadataMethod = track.getClass().getMethod("metadata");
                    Map<String, String> metadata = (Map<String, String>) metadataMethod.invoke(track);
                    if (metadata == null) return null;

//                    Log.d("SpotifyPlus", "==== METADATA ====");
//                    metadata.forEach((key, value) -> Log.d("SpotifyPlus", key + " | " + value));
//                    Log.d("SpotifyPlus", "==================");

                    String title = metadata.get("title");
                    String artist = metadata.get("artist_name");
                    String album = metadata.get("album_title");
                    String color = metadata.get("extracted_color");
                    String imageId = metadata.get("image_large_url");
                    String trackNumber = metadata.get("album_track_number");
                    long duration = Long.parseLong(metadata.get("duration"));
                    long position = 0;
                    long timestamp = 0;
                    boolean saved = false;
                    if (metadata.containsKey("collection.in_collection")) {
                        String savedValue = metadata.get("collection.in_collection");
                        saved = Boolean.parseBoolean(savedValue);
                    }

                    SpotifyAlbum albumObj = new SpotifyAlbum(album, artist, null, "https://i.scdn.co/image/" + imageId.split(":")[2]);
                    Method positionMethod = playerState.getClass().getMethod("positionAsOfTimestamp");
                    Object posOpt = positionMethod.invoke(playerState);
                    if (posOpt == null) {
                        return new SpotifyTrack(title, artist, new String[]{artist}, albumObj, uri, -1, color, -1, imageId, duration, saved, false, Integer.parseInt(trackNumber));
                    }

                    Matcher m = DIGITS.matcher(posOpt.toString());
                    if (m.find()) {
                        long basePos = Long.parseLong(m.group());
                        timestamp = (Long) playerState.getClass().getMethod("timestamp").invoke(playerState);
                        position = basePos + (System.currentTimeMillis() - timestamp);
                    }

                    return new SpotifyTrack(title, artist, new String[]{artist}, albumObj, uri, position, color, timestamp, imageId, duration, saved, false, Integer.parseInt(trackNumber));
                }
            }
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
            return null;
        }

        return null;
    }

    public static long getCurrentPlaybackPosition() {
        if (playerStateWrapper == null) return -1;

        try {
            Object state = playerStateWrapper.getClass().getMethod("getState").invoke(playerStateWrapper);
            if (state == null) return -1;

            Field positionField = resolvePlaybackPositionField(state.getClass());
            if (positionField == null) return -1;

            return positionField.getLong(state);
        } catch (Exception e) {
            playbackPositionStateClass = null;
            playbackPositionField = null;
            return -1;
        }
    }

    private static Field resolvePlaybackPositionField(Class<?> stateClass) throws NoSuchFieldException {
        Field cachedField = playbackPositionField;
        if (cachedField != null && playbackPositionStateClass == stateClass) return cachedField;

        synchronized (Utils.class) {
            cachedField = playbackPositionField;
            if (cachedField != null && playbackPositionStateClass == stateClass) return cachedField;
            if (bridge == null) return null;

            var progressList = bridge.findField(
                    FindField.create()
                            .searchInClass(Collections.singletonList(bridge.getClassData(stateClass)))
                            .matcher(FieldMatcher.create().type(long.class))
            );
            if (progressList.isEmpty()) return null;

            Field resolvedField = progressList.get(0).getFieldInstance(stateClass.getClassLoader());
            resolvedField.setAccessible(true);
            playbackPositionStateClass = stateClass;
            playbackPositionField = resolvedField;
            return resolvedField;
        }
    }

    //endregion

    private static final class ModuleBaseContext extends ContextWrapper {
        private final Resources resources;
        private final AssetManager assets;
        private final ClassLoader classLoader;

        ModuleBaseContext(Context base, Resources resources, ClassLoader classLoader) {
            super(base);
            this.resources = resources;
            this.assets = resources.getAssets();
            this.classLoader = classLoader;
        }

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public AssetManager getAssets() {
            return assets;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader != null ? classLoader : super.getClassLoader();
        }
    }

    private static final class ModuleResourceContext extends ContextWrapper {
        private final Resources resources;
        private final ClassLoader classLoader;

        ModuleResourceContext(Context base, Resources resources, ClassLoader classLoader) {
            super(base);
            this.resources = resources;
            this.classLoader = classLoader;
        }

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public AssetManager getAssets() {
            return resources.getAssets();
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader != null ? classLoader : super.getClassLoader();
        }
    }

    public static class ModuleContextWrapper extends android.view.ContextThemeWrapper {
        private final Resources res;
        private final AssetManager am;
        private final ClassLoader cl;

        public ModuleContextWrapper(Context base, int themeResId, Resources moduleRes, ClassLoader moduleCl) {
            super(base, themeResId);

            this.res = moduleRes;
            this.am = moduleRes.getAssets();
            this.cl = moduleCl;
        }

        @Override
        public Resources getResources() {
            return res;
        }

        @Override
        public AssetManager getAssets() {
            return am;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }

        public static Resources createMergedResources(Context hostContext, String... apkPaths) throws Exception {
            AssetManager mergedAm = AssetManager.class.getDeclaredConstructor().newInstance();

            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            boolean any = false;
            for (String p : apkPaths) {
                int cookie = (Integer) addAssetPath.invoke(mergedAm, p);
                if (cookie != 0) any = true;
            }

            if (!any) {
                throw new IllegalStateException("None of the provided asset paths were added successfully");
            }

            Resources host = hostContext.getResources();
            return new Resources(mergedAm, host.getDisplayMetrics(), host.getConfiguration());
        }
    }
}

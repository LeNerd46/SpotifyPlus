package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.hooks.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import org.luckypray.dexkit.DexKitBridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XposedLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    static {
        System.loadLibrary("dexkit");
    }

    private DexKitBridge bridge;
    private String modulePath = null;
    private static final String MODULE_VERSION = "0.7.1";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.spotify.music"))
            return;
        XposedBridge.log("[SpotifyPlus] Loading SpotifyPlus v" + MODULE_VERSION);

        if (bridge == null) {
            try {
                bridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }

        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                References.currentActivity = activity;
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int requestCode = (int) param.args[0];
                        Intent data = (Intent) param.args[2];

                        if (requestCode == 9072022 && data != null) {
                            Uri tree = data.getData();
                            ContentResolver content = ((Activity) param.thisObject).getContentResolver();
                            content.takePersistableUriPermission(tree,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                            SharedPreferences prefs = ((Activity) param.thisObject).getSharedPreferences("SpotifyPlus",
                                    Context.MODE_PRIVATE);
                            prefs.edit().putString("scripts_directory", tree.toString()).apply();
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Typeface beautifulFont = References.beautifulFont.get();

                if (beautifulFont != null)
                    return;

                try {
                    Resources resources = XModuleResources.createInstance(modulePath, null);
                    // beautifulFont = Typeface.createFromAsset(resources.getAssets(),
                    // "fonts/lyrics_medium.ttf");
                    beautifulFont = Typeface.createFromAsset(resources.getAssets(), "fonts/sf-pro-display-bold.ttf");

                    XposedBridge.log("[SpotifyPlus] Successfully loaded font!");
                } catch (Throwable t) {
                    XposedBridge.log("[SpotifyPlus] Failed to load font (error)");
                    XposedBridge.log(t);
                }

                if (beautifulFont != null) {
                    References.beautifulFont = new WeakReference<>(beautifulFont);
                }

                // MlKit.initialize(activity);

                navigateToStartupPage(activity);

                if (hasInternet(activity)) {
                    checkForUpdates(activity);
                }
            }
        });

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                cleanUpCache(context);

                SpotifyBottomSheet.initialize(bridge, lpparam.classLoader);
                // new ScriptManager().init(context, lpparam.classLoader);
                ScriptManager.getInstance().init(context, lpparam.classLoader);
                new BeautifulLyricsHook().init(lpparam, bridge);
                new NowPlayingLyricsGradientHook().init(lpparam, bridge);
//                new NowPlayingLandscapeHook().init(lpparam, bridge);
//                new NowPlayingSwipeHook().init(lpparam, bridge);
//                new NowPlayingCardsHook().init(lpparam, bridge);
//                new NowPlayingControlsHook().init(lpparam, bridge);
                new RemoveCreateButtonHook(context).init(lpparam, bridge);
                new NetworkHook(context).init(lpparam, bridge);
                new LastFmHook().init(lpparam, bridge);
                new ContextMenu_AddButton().init(lpparam, bridge);
//                new HomePageHook().init(lpparam, bridge);
                new AnimatedAlbumArtwork().init(lpparam, bridge);
                new TestingHook().init(lpparam, bridge);
                new NewContextMenuHook().init(lpparam, bridge);
                new SleepTimerHook(context).init(lpparam, bridge);
                new PrivateSessionHook(context).init(lpparam, bridge);
                new ThemeHook((Application) param.thisObject).init(lpparam, bridge);
                // new ThemeTest().init(lpparam, bridge);
                // new LikedSongHook().init(lpparam, bridge);
                // new KaraokeHook().init(lpparam, bridge);
            }
        });
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        modulePath = startupParam.modulePath;
    }

    private void navigateToStartupPage(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        String page = prefs.getString("startup_page", "HOME");

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage("com.spotify.music");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        switch (page) {
            case "HOME":
                intent.setData(Uri.parse("spotify:home"));
                break;

            case "SEARCH":
                intent.setData(Uri.parse("spotify:search"));
                break;

            case "EXPLORE":
                intent.setData(Uri.parse("spotify:find"));
                break;

            case "LIBRARY":
                intent.setData(Uri.parse("spotify:collection"));
                break;
        }

        activity.startActivity(intent);
    }

    private void checkForUpdates(Activity activity) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        if (prefs.getBoolean("general_check_updates", true)) {
            executor.execute(() -> {
                String thisContent = "";

                try {
                    URL url = new URL("https://api.github.com/repos/lenerd46/spotifyplus/releases/latest");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                        String inputLine;
                        StringBuilder response = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }

                        in.close();
                        thisContent = response.toString();
                    }
                } catch (Exception e) {
                    XposedBridge.log(e);
                }

                String content = thisContent;
                handler.post(() -> {
                    if (content.isEmpty())
                        return;

                    JsonObject json = new JsonParser().parseString(content).getAsJsonObject();
                    String latest = json.get("tag_name").getAsString().replace("v", "");

                    if (isVersionGreater(latest, MODULE_VERSION)) {
                        // New update available!

                        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                        if (root == null)
                            return;

                        XModuleResources modResources = References.modResources;
                        int themeOverlayLast = R.style.Theme_SpotifyPlus;
                        Context themedCtx = new ModuleContextWrapper(activity.getApplicationContext(), themeOverlayLast,
                                modResources, ModuleContextWrapper.class.getClassLoader());
                        LayoutInflater inflater = LayoutInflater.from(activity.getApplicationContext())
                                .cloneInContext(themedCtx);
                        View updateWindow = inflater.inflate(
                                modResources.getIdentifier("update_view", "layout", "com.lenerd46.spotifyplus"), root,
                                false);
                        root.addView(updateWindow);

                        FrameLayout background = updateWindow.findViewById(
                                modResources.getIdentifier("update_popup_root", "id", "com.lenerd46.spotifyplus"));
                        TextView versionText = updateWindow.findViewById(
                                modResources.getIdentifier("update_popup_version", "id", "com.lenerd46.spotifyplus"));
                        MaterialButton updateButton = updateWindow.findViewById(
                                modResources.getIdentifier("btn_download_update", "id", "com.lenerd46.spotifyplus"));
                        MaterialButton dismissButton = updateWindow.findViewById(
                                modResources.getIdentifier("btn_dismiss_update", "id", "com.lenerd46.spotifyplus"));

                        versionText.setText("Current: v" + MODULE_VERSION + "  •  Latest: v" + latest);

                        background.setOnClickListener(layout -> {
                            root.removeView(updateWindow);
                        });

                        updateButton.setOnClickListener(v -> {
                            root.removeView(updateWindow);

                            Intent intent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/LeNerd46/SpotifyPlus/releases"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                        });

                        dismissButton.setOnClickListener(v -> root.removeView(updateWindow));

                        // LayoutInflater inflater = LayoutInflater.from(activity);
                        // View dialogueView =
                        // inflater.inflate(modResources.getLayout(R.layout.dialogue_update),
                        // (ViewGroup) activity.getWindow().getDecorView(), false);
                        //
                        // Button download =
                        // dialogueView.findViewById(modResources.getIdentifier("download_button", "id",
                        // "com.lenerd46.spotifyplus"));
                        // Button later =
                        // dialogueView.findViewById(modResources.getIdentifier("later_button", "id",
                        // "com.lenerd46.spotifyplus"));
                        //
                        // AlertDialog dialogue = new
                        // AlertDialog.Builder(activity).setView(dialogueView).create();
                        //
                        // later.setOnClickListener(v -> dialogue.dismiss());
                        //
                        // download.setOnClickListener(v -> {
                        // Intent intent = new Intent(Intent.ACTION_VIEW,
                        // Uri.parse("https://github.com/LeNerd46/SpotifyPlus/releases"));
                        // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        // activity.startActivity(intent);
                        // dialogue.dismiss();
                        // });
                        //
                        // dialogue.show();
                        //
                        // Window dialogueWindow = dialogue.getWindow();
                        // if (dialogueWindow != null) {
                        // int width = activity.getResources().getDisplayMetrics().widthPixels;
                        // dialogueWindow.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
                        // }
                    }
                });
            });
        }
    }

    private final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

    public boolean isVersionGreater(String latest, String current) {
        if (latest == null || current == null)
            return false;

        String l = normalize(latest);
        String c = normalize(current);

        String[] la = l.split("\\.");
        String[] ca = c.split("\\.");

        int len = Math.max(la.length, ca.length);
        for (int i = 0; i < len; i++) {
            long lv = i < la.length ? parseSegment(la[i]) : 0L;
            long cv = i < ca.length ? parseSegment(ca[i]) : 0L;
            if (lv > cv)
                return true;
            if (lv < cv)
                return false;
        }
        // equal
        return false;
    }

    private String normalize(String s) {
        s = s.trim();
        if (s.startsWith("v") || s.startsWith("V"))
            s = s.substring(1);
        // drop pre-release / build metadata (e.g. -beta, +build)
        s = s.split("[-+]")[0];
        return s;
    }

    private long parseSegment(String seg) {
        seg = seg.trim();
        Matcher m = LEADING_NUMBER.matcher(seg);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                // extremely large number; fallback
                return 0L;
            }
        }
        return 0L;
    }

    public boolean hasInternet(Context ctx) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null)
                return false;

            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.net.Network nw = cm.getActiveNetwork();
                if (nw == null)
                    return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
                if (caps == null)
                    return false;
                // INTERNET = can reach the internet, VALIDATED = actually has connectivity (not
                // just a Wi‑Fi w/o backhaul)
                return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            } else {
                @SuppressWarnings("deprecation")
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                @SuppressWarnings("deprecation")
                boolean connected = (ni != null && ni.isConnected());
                return connected;
            }
        } catch (Throwable t) {
            // Never crash due to OEM weirdness
            de.robv.android.xposed.XposedBridge.log("[SpotifyPlus] hasInternet() failed: " + t);
            return false;
        }
    }

    private void cleanUpCache(Context context) {
        File[] files = context.getCacheDir().listFiles();

        for (File file : files) {
            if (file.getName().endsWith(".apk")) {
                file.delete();
            }
        }
    }

    private static final int COLOR_BACKGROUND_PRIMARY = 0xFF000000;
    private static final int COLOR_BACKGROUND_SECONDARY = 0xFF121212;
    private static final int COLOR_ACCENT = 0xFF1ED760;
    private static final int COLOR_ACCENT_PRESSED = 0xFF1ABC54;

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam)
            throws Throwable {
        if (!"com.spotify.music".equals(resparam.packageName)) {
            return;
        }

        References.modResources = XModuleResources.createInstance(modulePath, resparam.res);
        References.xresources = resparam.res;

        // final int primaryBackground = 0xFF000000; // AMOLED black
        // final int secondaryBackground = 0xFF121212; // elevated cards / surfaces
        // final int accentColor = 0xFF1ED760; // Spotify green
        // final int accentPressed = 0xFF1ABC54; // darker pressed green
        //
        // final boolean overridePlayerGradientColor = true;
        //
        // // Main background color
        // replaceColor(resparam, "gray_7", primaryBackground);
        // replaceColor(resparam, "gray_10", primaryBackground);
        // replaceColor(resparam, "dark_base_background_base", primaryBackground);
        // replaceColor(resparam, "dark_base_background_elevated_base",
        // primaryBackground);
        // replaceColor(resparam, "sthlm_blk", primaryBackground);
        // replaceColor(resparam, "sthlm_blk_grad_start", primaryBackground);
        // replaceColor(resparam, "image_placeholder_color", primaryBackground);
        //
        // // Player gradient:
        // // ReVanced skips bg_gradient_start_color unless overridePlayerGradientColor
        // is enabled,
        // // but always themes the end color.
        // if (overridePlayerGradientColor) {
        // replaceColor(resparam, "bg_gradient_start_color", primaryBackground);
        // }
        // replaceColor(resparam, "bg_gradient_end_color", primaryBackground);
        //
        // // Secondary background color
        // replaceColor(resparam, "gray_15", secondaryBackground);
        // replaceColor(resparam, "track_credits_card_bg", secondaryBackground);
        // replaceColor(resparam, "benefit_list_default_color", secondaryBackground);
        // replaceColor(resparam, "merch_card_background", secondaryBackground);
        // replaceColor(resparam, "opacity_white_10", secondaryBackground);
        // replaceColor(resparam, "dark_base_background_tinted_highlight",
        // secondaryBackground);
        //
        // // Accent color
        // replaceColor(resparam, "dark_brightaccent_background_base", accentColor);
        // replaceColor(resparam, "dark_base_text_brightaccent", accentColor);
        // replaceColor(resparam, "green_light", accentColor);
        // replaceColor(resparam, "spotify_green_157", accentColor);
        //
        // // Pressed accent color
        // replaceColor(resparam, "dark_brightaccent_background_press", accentPressed);
        //
        // XposedBridge.log("[SpotifyPlus] Custom theme resources applied");
    }

    private void replaceColor(XC_InitPackageResources.InitPackageResourcesParam resparam, String name, int color) {
        try {
            resparam.res.setReplacement("com.spotify.music", "color", name, color);
            XposedBridge.log("[SpotifyPlus] Replaced color resource: " + name);
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Failed to replace color resource: " + name);
            XposedBridge.log(t);
        }
    }
}

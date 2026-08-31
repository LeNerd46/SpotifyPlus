package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.module.*;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.module.scripting.entities.PlatformData;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@XposedHooker
public class NetworkHook extends SpotifyHook {
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
    private Method httpBuildMethod;
    private boolean checkedForUpdates = false;
    private static boolean initialized = false;

    @Override
    protected void hookSetup() throws NoSuchMethodException {
        var requestClassData = bridge.getClassData((findClass("okhttp3.Request$Builder")));
        Method httpRequest = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(requestClassData)).matcher(MethodMatcher.create().returnType(void.class).paramTypes(String.class, String.class))).get(1).getMethodInstance(classLoader);
        hook(httpRequest);

        httpBuildMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(requestClassData)).matcher(MethodMatcher.create().usingStrings("ws").paramTypes(String.class))).get(0).getMethodInstance(classLoader);
        hook(httpBuildMethod);

        Class<?> mainActivity = findClass("com.spotify.music.SpotifyMainActivity");
        hook(mainActivity.getDeclaredMethod("onCreate", Bundle.class));
        hook(mainActivity.getDeclaredMethod("onResume"));

//        hook(LayoutInflater.class.getDeclaredMethod("inflate", int.class, ViewGroup.class));
//        hook(LayoutInflater.class.getDeclaredMethod("inflate", int.class, ViewGroup.class, boolean.class));
//        hook(LayoutInflater.class.getDeclaredMethod("inflate", XmlPullParser.class, ViewGroup.class));
//        hook(LayoutInflater.class.getDeclaredMethod("inflate", XmlPullParser.class, ViewGroup.class, boolean.class));
    }

    @BeforeInvocation
    public static void beforeHook(XposedInterface.BeforeHookCallback callback) {
        NetworkHook hook = getHook(NetworkHook.class);
        if (hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
        if (callback.getMember().getName().equals("onCreate") || callback.getMember().getName().equals("onResume")) {
            try {
                // I know this doesn't really fit, but I'm not sure where else to put it. I'm not creating an entire new class just for this
                Activity activity = (Activity) callback.getThisObject();
                SpotifyHook.currentActivity = activity;
                PackageManager pm = activity.getPackageManager();
                PackageInfo info = pm.getPackageInfo(activity.getPackageName(), 0);
                Utils.spotifyVersion = info.versionName;

                Utils.platformData = new PlatformData(info.versionName, "android", Build.VERSION.RELEASE, Build.VERSION.SDK_INT);

                if (!initialized) {
                    initialized = true;

                    try {
                        ScriptManager manager = new ScriptManager(activity);
                        manager.start();
                    } finally {
//                        SpotifyNativeBridge.sendEvent("ready", platform.toString());
                    }

//                    ScriptManager.send("", "event", "event.ready", platform);
                }

//                ReactManager.registerSurface("root-view", (ViewGroup) currentActivity.getWindow().getDecorView());

                if (!checkedForUpdates) {
                    loadPreferences(activity);
                    // Checking for updates is kind of a network thing, right?
                    checkForUpdates(activity);
                }
            } catch (Exception e) {
                logError(e);
            }
        } else if (callback.getMember() == httpBuildMethod) {
            String url = (String) callback.getArgs()[0];

            if (SpotifyPlusSettings.blockAds) {
                if (url.contains("/ads")) {
                    callback.getArgs()[0] = "https://127.0.0.1:404/";
                }
            } else if (url.contains("gabo-receiver-service") || url.contains("net-fortune") || url.contains("darwin-experiments") || url.contains("speechless-sharing") || url.contains("pendragon")) {
                callback.getArgs()[0] = "https://127.0.0.1:404/";
            }
        } else if (callback.getArgs().length >= 2) {
            try {
                String headerName = (String) callback.getArgs()[0];
                String headerValue = (String) callback.getArgs()[1];

                if (headerName != null && headerName.equals("Authorization") && headerValue != null && !headerValue.isEmpty()) {
                    String token = headerValue.replace("Bearer", "").trim();
                    if (Utils.token == null || !Utils.token.equals(token)) {
                        Utils.token = token;
                        log("Access-Token: " + token);

                        SpotifyNativeBridge.sendEvent("event.updateToken", new JSONObject().put("accessToken", token).toString());
                    }
                } else if (headerName != null && headerName.equals("client-token") && headerValue != null && !headerValue.isEmpty()) {
                    if (Utils.clientToken == null || !Utils.clientToken.equals(headerValue)) {
                        Utils.clientToken = headerValue;
                        log("Client-Token: " + headerValue);
                    }
                }
            } catch (Exception e) {
                logError(e);
            }
        }
    }

//        if (SpotifyPlusSettings.blockAds) {
//            try {
//                Object thisObject = callback.getThisObject();
//                Object urlObject = thisObject.getClass().getDeclaredField("a").get(thisObject);
//                String url = urlObject.toString();
//
//                if(url.contains("/ads")) {
//                    urlObject
//                }
//            } catch(Exception e) {
//                logError(e);
//            }
//        }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        NetworkHook hook = getHook(NetworkHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        try {
            if (callback.getThrowable() == null) return;

            Throwable throwable = callback.getThrowable();
            LayoutInflater inflater = (LayoutInflater) callback.getThisObject();
            Context context = inflater.getContext();

            callback.returnAndSkip(new View(context.getApplicationContext()));
        } catch (Exception e) {
            logError(e);
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        return null;
    }

    private void loadPreferences(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

        // General
        SpotifyPlusSettings.checkForUpdates = prefs.getBoolean("general_check_updates", true);
        SpotifyPlusSettings.removeCreateButton = prefs.getBoolean("remove_create", false);
        SpotifyPlusSettings.lastfmUsername = prefs.getString("last_fm_username", "null");
        SpotifyPlusSettings.startupPage = SpotifyPlusSettings.StartupPage.valueOf(prefs.getString("startup_page", "HOME"));
        SpotifyPlusSettings.animatedAlbumArtworkEnabled = prefs.getBoolean("animated_art", true);
        SpotifyPlusSettings.blockAds = prefs.getBoolean("block_ads", false);
        SpotifyPlusSettings.blockTelemetry = prefs.getBoolean("block_telemetry", false);

        // Lyrics
        SpotifyPlusSettings.animationStyle = SpotifyPlusSettings.AnimationStyle.valueOf(prefs.getString("lyric_animation_style", "DEFAULT"));
        SpotifyPlusSettings.userSelectedFont = SpotifyPlusSettings.LyricsFont.valueOf(prefs.getString("lyrics_font", "APPLE"));
        SpotifyPlusSettings.interludeDuration = SpotifyPlusSettings.InterludeDuration.valueOf(prefs.getString("lyric_interlude_duration", "SPOTIFY_PLUS"));
        SpotifyPlusSettings.lineSpacing = SpotifyPlusSettings.LineSpacing.valueOf(prefs.getString("lyric_spacing", "DEFAULT"));
        SpotifyPlusSettings.backgroundQuality = SpotifyPlusSettings.BackgroundQuality.valueOf(prefs.getString("lyric_background_quality", "HIGH"));
        SpotifyPlusSettings.enabledBackground = prefs.getBoolean("lyric_enable_background", true);
        SpotifyPlusSettings.lineGradient = prefs.getBoolean("lyric_enable_line_gradient", true);
    }

    private void checkForUpdates(Activity activity) {
        if (!SpotifyPlusSettings.checkForUpdates) return;

        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        var capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://api.github.com/repos/lenerd46/spotifyplus/releases/latest").build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("SpotifyPlus", "Failed to check for updates | " + response.code() + " | ");
                    return;
                }

                String content = response.body().string();
                if (content.isEmpty()) return;

                JsonObject json = new JsonParser().parseString(content).getAsJsonObject();
                String latest = json.get("tag_name").getAsString().replace("v", "");

                String l = latest.substring(1);
                String c = BuildConfig.VERSION_NAME;

                boolean updateAvailable = false;

                String[] la = l.split("\\.");
                String[] ca = c.split("\\.");

                int len = Math.max(la.length, ca.length);
                for (int i = 0; i < len; i++) {
                    long lv = i < la.length ? parseSegment(la[i]) : 0L;
                    long cv = i < ca.length ? parseSegment(ca[i]) : 0L;

                    if (lv > cv) {
                        updateAvailable = true;
                        break;
                    } else if (lv < cv) {
                        break;
                    }
                }

                if (updateAvailable) {
                    activity.runOnUiThread(() -> {
                        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                        if (root == null) return;

                        View updateModal = Utils.inflate(activity, R.layout.update_view, root);
                        root.addView(updateModal);

                        FrameLayout background = updateModal.findViewById(R.id.update_popup_root);
                        TextView versionText = updateModal.findViewById(R.id.update_popup_version);
                        MaterialButton updateButton = updateModal.findViewById(R.id.btn_download_update);
                        MaterialButton dismissButton = updateModal.findViewById(R.id.btn_dismiss_update);

                        versionText.setText(Utils.getString(activity, R.string.update_modal_compare, BuildConfig.VERSION_NAME, latest));

                        background.setOnClickListener(layout -> {
                            root.removeView(updateModal);
                        });

                        updateButton.setOnClickListener(v -> {
                            root.removeView(updateModal);

                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeNerd46/SpotifyPlus/releases"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                        });

                        dismissButton.setOnClickListener(v -> root.removeView(updateModal));
                    });
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e("SpotifyPlus", "Failed to check for updates");
            }
        });
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
}

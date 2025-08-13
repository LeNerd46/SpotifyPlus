package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.Button;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

public class XposedLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    static {
        System.loadLibrary("dexkit");
    }

    private DexKitBridge bridge;
    private String modulePath = null;
    private static final String MODULE_VERSION = "0.5.2";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if(!lpparam.packageName.equals("com.spotify.music")) return;
        XposedBridge.log("[SpotifyPlus] Loading SpotifyPlus v" + MODULE_VERSION);

        if(bridge == null) {
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

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int requestCode = (int) param.args[0];
                Intent data = (Intent) param.args[2];

                if(requestCode == 9072022 && data != null) {
                    Uri tree = data.getData();
                    ContentResolver content = ((Activity) param.thisObject).getContentResolver();
                    content.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    SharedPreferences prefs = ((Activity) param.thisObject).getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
                    prefs.edit().putString("scripts_directory", tree.toString()).apply();
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Typeface beautifulFont = References.beautifulFont.get();

                if (beautifulFont != null) return;

                try {
                    Resources resources = XModuleResources.createInstance(modulePath, null);
                    beautifulFont = Typeface.createFromAsset(resources.getAssets(), "fonts/lyrics_medium.ttf");

                    XposedBridge.log("[SpotifyPlus] Successfully loaded font!");
                } catch (Throwable t) {
                    XposedBridge.log("[SpotifyPlus] Failed to load font (error)");
                    XposedBridge.log(t);
                }

                if (beautifulFont != null) {
                    References.beautifulFont = new WeakReference<>(beautifulFont);
                }

                navigateToStartupPage(activity);

                if(hasInternet(activity)) {
                    checkForUpdates(activity);
                }
            }
        });

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                cleanUpCache(context);

//                new SettingsFlyoutHook(context).init(lpparam, bridge);
//                new ScriptManager().init(context, lpparam.classLoader);
                ScriptManager.getInstance().init(context, lpparam.classLoader);
                new BeautifulLyricsHook().init(lpparam, bridge);
                new SocialHook().init(lpparam, bridge);
                new RemoveCreateButtonHook(context).init(lpparam, bridge);
                new ContextMenuHook().init(lpparam, bridge);
//                new PremiumHook().init(lpparam, bridge);
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

        switch(page) {
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
                    if(responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                        String inputLine;
                        StringBuilder response = new StringBuilder();
                        while((inputLine = in.readLine()) != null) {
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
                    JsonObject json = new JsonParser().parseString(content).getAsJsonObject();
                    String latest = json.get("tag_name").getAsString().replace("v", "");
                    String current = MODULE_VERSION;

                    String[] latestParts = latest.split("\\.");
                    String[] currentParts = current.split("\\.");

                    for(int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
                        int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                        int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

                        if(latestNum > currentNum) {
                            // New update available!

                            XModuleResources modResources = References.modResources;
                            LayoutInflater inflater = LayoutInflater.from(activity);
                            View dialogueView = inflater.inflate(modResources.getLayout(R.layout.dialogue_update), (ViewGroup) activity.getWindow().getDecorView(), false);

                            Button download = dialogueView.findViewById(modResources.getIdentifier("download_button", "id", "com.lenerd46.spotifyplus"));
                            Button later = dialogueView.findViewById(modResources.getIdentifier("later_button", "id", "com.lenerd46.spotifyplus"));

                            AlertDialog dialogue = new AlertDialog.Builder(activity).setView(dialogueView).create();

                            later.setOnClickListener(v -> dialogue.dismiss());

                            download.setOnClickListener(v -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeNerd46/SpotifyPlus/releases"));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                                dialogue.dismiss();
                            });

                            dialogue.show();

                            Window dialogueWindow = dialogue.getWindow();
                            if(dialogueWindow != null) {
                                int width = activity.getResources().getDisplayMetrics().widthPixels;
                                dialogueWindow.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
                            }
                        }
                    }
                });
            });
        }
    }

    public boolean hasInternet(Context ctx) {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.net.Network nw = cm.getActiveNetwork();
                if (nw == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
                if (caps == null) return false;
                // INTERNET = can reach the internet, VALIDATED = actually has connectivity (not just a Wi‑Fi w/o backhaul)
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

        for(File file : files) {
            if (file.getName().endsWith(".apk")) {
                file.delete();
            }
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if(resparam.packageName.equals("com.spotify.music")) {
            References.modResources = XModuleResources.createInstance(modulePath, resparam.res);
        }
    }
}
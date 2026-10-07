package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.lenerd.spotifyplus.module.*;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.module.scripting.entities.PlatformData;
import org.json.JSONObject;


public class NetworkHook extends SpotifyHook {
    private static boolean initialized = false;

    @Override
    protected void hookSetup() throws NoSuchMethodException {
        Class<?> headersBuilder = findClass("p.y6p");
        hook(headersBuilder.getDeclaredMethod("B", String.class, String.class));
        hook(headersBuilder.getDeclaredMethod("c", String.class, String.class));

        Class<?> mainActivity = findClass("com.spotify.music.SpotifyMainActivity");
        hook(mainActivity.getDeclaredMethod("onCreate", Bundle.class));
        hook(mainActivity.getDeclaredMethod("onResume"));

    }


    @Override
    protected void beforeHook(SpotifyCallback callback) {
        if (callback.getMember().getName().equals("onCreate") || callback.getMember().getName().equals("onResume")) {
            try {
                Activity activity = (Activity) callback.getThisObject();
                SpotifyHook.currentActivity = activity;
                PackageManager pm = activity.getPackageManager();
                PackageInfo info = pm.getPackageInfo(activity.getPackageName(), 0);
                Utils.spotifyVersion = info.versionName;

                Utils.platformData = new PlatformData(info.versionName, "android", Build.VERSION.RELEASE, Build.VERSION.SDK_INT);

                if (!initialized) {
                    initialized = true;

                    ScriptManager manager = new ScriptManager(activity);
                    manager.start();
                }
            } catch (Exception e) {
                logError(e);
            }
        } else if (callback.getArgs().length >= 2) {
            try {
                String headerName = (String) callback.getArgs()[0];
                String headerValue = (String) callback.getArgs()[1];

                if (headerName != null && headerName.equalsIgnoreCase("Authorization") && headerValue != null && !headerValue.isEmpty()) {
                    String token = headerValue.replace("Bearer", "").trim();
                    if (Utils.token == null || !Utils.token.equals(token)) {
                        Utils.token = token;


                        SpotifyNativeBridge.sendEvent("event.updateToken", new JSONObject().put("accessToken", token).toString());
                    }
                } else if (headerName != null && headerName.equalsIgnoreCase("client-token") && headerValue != null && !headerValue.isEmpty()) {
                    if (Utils.clientToken == null || !Utils.clientToken.equals(headerValue)) {
                        Utils.clientToken = headerValue;

                    }
                }
            } catch (Exception e) {
                logError(e);
            }
        }
    }


    @Override
    protected void afterHook(SpotifyCallback callback) { }

    @Override
    public Object handle(String command, Object[] args) {
        return null;
    }

}

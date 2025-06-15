package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.ref.WeakReference;

public class SocialHook extends SpotifyHook{
    @Override
    protected void hook() {
        XposedHelpers.findAndHookMethod("okhttp3.Request$Builder", lpparm.classLoader, "d", String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String headerName = (String) param.args[0];
                String headerValue = (String) param.args[1];

                if(headerName != null && headerName.equalsIgnoreCase("authorization")) {
                    String token = headerValue.replace("Bearer ", "").trim();
                    References.accessToken = new WeakReference<>(token);
                }
            }
        });
    }
}

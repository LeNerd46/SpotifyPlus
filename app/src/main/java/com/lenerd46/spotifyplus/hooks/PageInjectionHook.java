package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;

public class PageInjectionHook extends SpotifyHook {
    @Override
    protected void hook() {
        try {
            var list = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify:artist:", "Failed requirement.", "spotify:concept:", "spotify:list:", "podcast-chapters", "spotify:show:")));
            Class<?> clazz = list.get(0).getInstance(lpparm.classLoader);

            Class<?> id30 = XposedHelpers.findClass("p.id30", lpparm.classLoader);
            XposedBridge.hookAllMethods(id30, "a", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object nav = param.args[0]; // hd30
                    String raw = (String) XposedHelpers.getObjectField(nav, "a");
                    if (raw != null && raw.startsWith("spotifyplus:")) {
                        Intent intent = (Intent) param.getResult();

                        // Make the route canonical and *without* query
                        intent.setData(Uri.parse("spotify:settings"));

                        // Attach your flags/metadata as extras (survive through the pipeline)
                        intent.putExtra("is_internal_navigation", true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                        // Marker for your overlay logic
                        intent.putExtra("spx", "spotifyplus");
                        intent.putExtra("spx_src", raw);

                        // Keep Spotify's explicit component targeting
                        Context appCtx = (Context) XposedHelpers.getObjectField(param.thisObject, "b");
                        String activityClass = (String) XposedHelpers.getObjectField(param.thisObject, "a");
                        intent.setClassName(appCtx, activityClass);

                        param.setResult(intent);
                        XposedBridge.log("[SpotifyPlus][id30.a] rewrote to spotify:settings with extras");
                    }
                }
            });

            Class<?> ysi0 = XposedHelpers.findClass("p.ysi0", lpparm.classLoader);
            Class<?> bti0 = XposedHelpers.findClass("p.bti0", lpparm.classLoader);

            XposedBridge.hookAllMethods(ysi0, "g", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String s = (String) param.args[0];
//                    XposedBridge.log("[SpotifyPlus] " + s);
                    if (s != null && s.startsWith("spotifyplus:")) {
                        // Rewrite to a *real* internal route so the router pushes Settings
                        String rewritten = "spotify:settings?spx=spotifyplus&src=" + Uri.encode(s);

                        // IMPORTANT: construct bti0 directly (constructor), not via ysi0.g()
                        Object bt = XposedHelpers.newInstance(bti0, rewritten);
                        param.setResult(bt);
                    }
                }
            });

            Class<?> main = XposedHelpers.findClass("com.spotify.music.SpotifyMainActivity", lpparm.classLoader);

            XposedBridge.hookAllMethods(main, "onNewIntent", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.app.Activity act = (android.app.Activity) param.thisObject;
                    Intent it = (Intent) param.args[0];
                    if (it != null && "spotifyplus".equals(it.getStringExtra("spx"))) {
                        act.runOnUiThread(() -> {
                            android.view.ViewGroup decor = (android.view.ViewGroup) act.getWindow().getDecorView();
                            if (decor.findViewWithTag("spx_overlay") != null) return;

                            android.widget.FrameLayout overlay = new android.widget.FrameLayout(act);
                            overlay.setTag("spx_overlay");
                            overlay.setClickable(true);
                            overlay.setFocusable(true);
                            overlay.setFocusableInTouchMode(true);
                            overlay.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));

                            // TODO inflate your real overlay layout here
                            overlay.setBackgroundColor(0x66000000);

                            decor.addView(overlay);
                            overlay.requestFocus();
                        });
                    } else {
                        // Any other navigation: remove overlay
                        act.runOnUiThread(() -> {
                            android.view.View v = act.getWindow().getDecorView().findViewWithTag("spx_overlay");
                            if (v != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
                        });
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }
}

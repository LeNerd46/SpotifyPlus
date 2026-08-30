package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.WeakHashMap;

public class NowPlayingLandscapeHook extends SpotifyHook {
    private static final String NOW_PLAYING_ACTIVITY = "com.spotify.nowplaying.musicinstallation.NowPlayingActivity";
    private static final String PEEK_SCROLL_VIEW = "com.spotify.nowplaying.scroll.view.PeekScrollView";
    private final WeakHashMap<View, Activity> attachedPlayers = new WeakHashMap<>();
    private final WeakHashMap<Activity, Boolean> launchedPlayers = new WeakHashMap<>();

    @Override
    protected void hook() {
        try {
            Class<?> nowPlayingActivity = lpparm.classLoader.loadClass(NOW_PLAYING_ACTIVITY);
            Class<?> peekScrollView = lpparm.classLoader.loadClass(PEEK_SCROLL_VIEW);
            XposedHelpers.findAndHookMethod(nowPlayingActivity, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity)param.thisObject;
                    makeWindowTransparent(activity);
                    requestOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
                }
            });
            XposedHelpers.findAndHookMethod(nowPlayingActivity, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(Boolean.TRUE.equals(param.args[0])) makeWindowTransparent((Activity)param.thisObject);
                }
            });
            XposedBridge.hookAllConstructors(peekScrollView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!(param.thisObject instanceof View)) return;
                    View player = (View)param.thisObject;
                    player.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View view) {
                            Activity activity = activity(view.getContext());
                            if(activity == null) activity = References.currentActivity;
                            if(activity == null) return;
                            attachedPlayers.put(view, activity);
                            Activity hostActivity = activity;
                            if(!NOW_PLAYING_ACTIVITY.equals(hostActivity.getClass().getName())) view.post(() -> openDedicatedPlayer(hostActivity));
                        }

                        @Override
                        public void onViewDetachedFromWindow(View view) {
                            Activity activity = attachedPlayers.remove(view);
                            if(activity != null) activity.getWindow().getDecorView().postDelayed(() -> clearLaunchGuard(activity), 300L);
                        }
                    });
                }
            });
            XposedBridge.hookAllMethods(Activity.class, "setRequestedOrientation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if(param.thisObject != null && NOW_PLAYING_ACTIVITY.equals(param.thisObject.getClass().getName())) param.args[0] = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
                }
            });
            XposedBridge.log("[SpotifyPlus][NowPlayingLandscape] Now Playing rotation enabled");
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingLandscape] Could not enable Now Playing rotation");
            XposedBridge.log(throwable);
        }
    }

    private void requestOrientation(Activity activity, int orientation) {
        if("com.spotify.music.SpotifyMainActivity".equals(activity.getClass().getName())) XposedHelpers.callMethod(activity, "x", orientation);
        else activity.setRequestedOrientation(orientation);
    }

    private void makeWindowTransparent(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void openDedicatedPlayer(Activity activity) {
        if(activity.isFinishing() || Boolean.TRUE.equals(launchedPlayers.get(activity))) return;
        launchedPlayers.put(activity, Boolean.TRUE);
        Intent intent = new Intent();
        intent.setClassName(activity, NOW_PLAYING_ACTIVITY);
        activity.onBackPressed();
        activity.startActivity(intent);
    }

    private void clearLaunchGuard(Activity activity) {
        for(Activity attachedActivity : attachedPlayers.values()) if(attachedActivity == activity) return;
        launchedPlayers.remove(activity);
    }

    private Activity activity(Context context) {
        Context current = context;
        while(current instanceof ContextWrapper) {
            if(current instanceof Activity) return (Activity)current;
            Context next = ((ContextWrapper)current).getBaseContext();
            if(next == current) break;
            current = next;
        }
        return null;
    }
}

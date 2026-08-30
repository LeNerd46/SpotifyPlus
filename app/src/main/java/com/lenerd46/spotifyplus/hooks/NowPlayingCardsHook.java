package com.lenerd46.spotifyplus.hooks;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class NowPlayingCardsHook extends SpotifyHook {
    private static final String PEEK_SCROLL_VIEW = "com.spotify.nowplaying.scroll.view.PeekScrollView";

    @Override
    protected void hook() {
        try {
            Class<?> peekScrollView = lpparm.classLoader.loadClass(PEEK_SCROLL_VIEW);
            XposedBridge.hookAllConstructors(peekScrollView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!(param.thisObject instanceof View)) return;
                    hideSupplementalCards((View) param.thisObject);
                }
            });
            XposedBridge.log("[SpotifyPlus][NowPlayingCards] Supplemental Now Playing cards disabled");
        } catch (Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingCards] Could not disable supplemental Now Playing cards");
            XposedBridge.log(throwable);
        }
    }

    private void hideSupplementalCards(View root) {
        int containerId = root.getResources().getIdentifier("touch_blocking_container", "id", "com.spotify.music");
        View container = containerId == 0 ? null : root.findViewById(containerId);
        if(container == null) return;
        container.setVisibility(View.GONE);
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
}

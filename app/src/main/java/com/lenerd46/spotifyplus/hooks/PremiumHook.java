package com.lenerd46.spotifyplus.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

// Based on Revanced Patches!
// https://github.com/ReVanced/revanced-patches/blob/main/patches/src/main/kotlin/app/revanced/patches/spotify/misc/UnlockPremiumPatch.kt
public class PremiumHook extends SpotifyHook {
    private final List<SpotifyAttribute> overrides = List.of(
            new SpotifyAttribute("ads", FALSE),
            new SpotifyAttribute("player-license", "premium"),
            new SpotifyAttribute("shuffle", FALSE),
            new SpotifyAttribute("on-demand", TRUE),
            new SpotifyAttribute("streaming", TRUE),
            new SpotifyAttribute("pick-and-shuffle", FALSE),
            new SpotifyAttribute("streaming-rules", ""),
            new SpotifyAttribute("nft-disabled", "1"),
            new SpotifyAttribute("type", "premium"),
            new SpotifyAttribute("can_use_superbird", TRUE, false),
            new SpotifyAttribute("tablet-free", FALSE, false)
    );

    @Override
    protected void hook() {
        XposedHelpers.findAndHookMethod("com.spotify.remoteconfig.internal.ProductStateProto", lpparm.classLoader, "D", "com.spotify.remoteconfig.internal.ProductStateProto", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Map<String, Object> map = (Map<String, Object>) param.getResult();

                if(map != null) {
                    for(var override : overrides) {
                        var attribute = map.get(override.key);

                        if(attribute == null) {
                            if(override.required) {
                                XposedBridge.log("[SpotifyPlus] Required attribute missing: " + override.key);
                            }
                        } else {
                            Object overrideValue = override.value;
                            map.put(override.key, overrideValue);
                        }
                    }
                } else {
                    XposedBridge.log("[SpotifyPlus] Could not find map");
                }

                param.setResult(map);
            }
        });
    }
    private static class SpotifyAttribute {
        final String key;
        final Object value;
        final boolean required;

        public SpotifyAttribute(String key, Object value) {
            this.key = key;
            this.value = value;
            this.required = true;
        }

        public SpotifyAttribute(String key, Object value, boolean required) {
            this.key = key;
            this.value = value;
            this.required = required;
        }
    }
}


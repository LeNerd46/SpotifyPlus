package com.lenerd46.spotifyplus.hooks;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.luckypray.dexkit.DexKitBridge;

public abstract class SpotifyHook {
    protected XC_LoadPackage.LoadPackageParam lpparm;
    protected DexKitBridge bridge;

    public void init(XC_LoadPackage.LoadPackageParam lpparm, DexKitBridge bridge) {
        this.lpparm = lpparm;
        this.bridge = bridge;
        try {
            hook();
        } catch (Throwable error) {
            // One changed Spotify feature must not prevent subsequent hooks from loading.
            de.robv.android.xposed.XposedBridge.log("[SpotifyPlus][" + getClass().getSimpleName() + "] Initialization failed: " + error);
        }
    }

    protected abstract void hook() throws Exception;
}

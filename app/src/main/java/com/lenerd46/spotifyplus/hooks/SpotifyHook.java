package com.lenerd46.spotifyplus.hooks;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public abstract class SpotifyHook {
    protected XC_LoadPackage.LoadPackageParam lpparm;

    public void init(XC_LoadPackage.LoadPackageParam lpparm) {
        this.lpparm = lpparm;
        hook();
    }

    protected abstract void hook();
}

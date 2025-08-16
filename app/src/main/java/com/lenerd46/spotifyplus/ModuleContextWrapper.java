package com.lenerd46.spotifyplus;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.lang.reflect.Method;

public class ModuleContextWrapper extends android.view.ContextThemeWrapper {
    private final Resources res;
    private final AssetManager am;
    private final ClassLoader cl;

    public ModuleContextWrapper(Context base, int themeResId, Resources moduleRes, ClassLoader moduleCl) {
        super(base, themeResId);

        this.res = moduleRes;
        this.am = moduleRes.getAssets();
        this.cl = moduleCl;
    }

    @Override public Resources getResources() { return res; }
    @Override public AssetManager getAssets() { return am; }
    @Override public ClassLoader getClassLoader() { return cl; }

    public static Resources createMergedResources(Context hostContext, String... apkPaths) throws Exception {
        AssetManager mergedAm = AssetManager.class.getDeclaredConstructor().newInstance();

        Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
        boolean any = false;
        for(String p : apkPaths) {
            int cookie = (Integer) addAssetPath.invoke(hostContext, p);
            if(cookie != 0) any = true;
        }

        if(!any) {
            throw new IllegalStateException("None of the provided asset paths were added successfully");
        }

        Resources host = hostContext.getResources();
        return new Resources(mergedAm, host.getDisplayMetrics(), host.getConfiguration());
    }
}

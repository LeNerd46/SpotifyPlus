package com.lenerd.spotifyplus.module.legacy;

import android.util.Log;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.hooks.*;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.luckypray.dexkit.DexKitBridge;

import static com.lenerd.spotifyplus.module.Utils.bridge;

public class LegacySpotifyLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    static {
        System.loadLibrary("dexkit");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if(!lpparam.packageName.equals("com.spotify.music")) return;
        Log.d("SpotifyPlus", "[Legacy] Loading Spotify Plus v" + BuildConfig.VERSION_NAME);

        if(bridge == null) {
            try {
                bridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
            } catch(Exception e) {
                Log.e("SpotifyPlus", e.getMessage(), e);
            }
        }

        new LyricsHook().initLegacy(lpparam, bridge);
        new NetworkHook().initLegacy(lpparam, bridge);
        new SideDrawerHook().initLegacy(lpparam, bridge);
        new LegacyContextMenuHook().initLegacy(lpparam, bridge);
        new ContextMenuHook().initLegacy(lpparam, bridge);
        new AnimatedAlbumArtwork().initLegacy(lpparam, bridge);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        Utils.MODULE_APK_PATH = startupParam.modulePath;
    }
}
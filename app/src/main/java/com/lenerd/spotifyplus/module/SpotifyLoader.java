package com.lenerd.spotifyplus.module;

import android.util.Log;
import androidx.annotation.NonNull;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.module.hooks.*;
import com.lenerd.spotifyplus.module.fingerprint.HookFingerprints;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import org.jetbrains.annotations.NotNull;
import org.luckypray.dexkit.DexKitBridge;

import static com.lenerd.spotifyplus.module.Utils.bridge;

public class SpotifyLoader extends XposedModule {
    static {
        System.loadLibrary("dexkit");
    }
    public static volatile boolean bridgeInitialized = false;

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        Utils.MODULE_APK_PATH = getModuleApplicationInfo().sourceDir;
    }

    @Override
    public void onPackageReady(@NonNull @NotNull XposedModuleInterface.PackageReadyParam param) {
        if (!param.getPackageName().equals("com.spotify.music") || !param.isFirstPackage()) return;
        Log.d("SpotifyPlus", "Loading Spotify Plus v" + BuildConfig.VERSION_NAME);

        if (bridge == null) {
            try {
                bridge = DexKitBridge.create(param.getApplicationInfo().sourceDir);
            } catch (Exception e) {
                Log.e("SpotifyPlus", e.getMessage(), e);
            }
        }

        HookFingerprints.initialize(bridge, param.getClassLoader(),
                param.getApplicationInfo().sourceDir, param.getApplicationInfo().dataDir);

        new ServicesHook().init(this, param, bridge);
        new PlayerHook().init(this, param, bridge);
        new NetworkHook().init(this, param, bridge);
        new SideDrawerHook().init(this, param, bridge);
        new ContextMenuHook().init(this, param, bridge);
        new ComposeHeaderTarget().init(this, param, bridge);
        new MenuUITargets().init(this, param, bridge);
        new NowPlayingUITarget().init(this, param, bridge);
        new PageUITargets().init(this, param, bridge);
        new DebugHook().init(this, param, bridge);
        new StorageHook().init(this, param, bridge);
        new LocalExtensionHook().init(this, param, bridge);
        new ReactManager().init(this, param, bridge);
        new SpotifyApi().init(this, param, bridge);
    }
}

package com.lenerd.spotifyplus.module;

import android.util.Log;
import androidx.annotation.NonNull;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.hooks.*;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import io.github.libxposed.api.XposedInterface;
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

    public SpotifyLoader(@NonNull @NotNull XposedInterface base, @NonNull @NotNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);

        Utils.MODULE_APK_PATH = getApplicationInfo().sourceDir;
    }

    @Override
    public void onPackageLoaded(@NonNull @NotNull XposedModuleInterface.PackageLoadedParam param) {
        if (!param.getPackageName().equals("com.spotify.music") || !param.isFirstPackage()) return;
        Log.d("SpotifyPlus", "Loading Spotify Plus v" + BuildConfig.VERSION_NAME);

        if (bridge == null) {
            try {
                bridge = DexKitBridge.create(param.getApplicationInfo().sourceDir);
            } catch (Exception e) {
                Log.e("SpotifyPlus", e.getMessage(), e);
            }
        }

//        new ScriptManager();

        new LyricsHook().init(this, param, bridge);
        new NetworkHook().init(this, param, bridge);
        new SideDrawerHook().init(this, param, bridge);
        new LegacyContextMenuHook().init(this, param, bridge);
        new ContextMenuHook().init(this, param, bridge);
//        new AnimatedAlbumArtwork().init(this, param, bridge);
        new DebugHook().init(this, param, bridge);
        new PlayerHook().init(this, param, bridge);
        new StorageHook().init(this, param, bridge);
        new LocalExtensionHook().init(this, param, bridge);
        new ReactManager().init(this, param, bridge);
        new TestHook().init(this, param, bridge);
        new SpotifyApi().init(this, param, bridge);
    }
}

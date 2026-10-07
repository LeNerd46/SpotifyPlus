package com.lenerd.spotifyplus.module;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import org.luckypray.dexkit.DexKitBridge;
import com.lenerd.spotifyplus.module.fingerprint.FingerprintMapping;
import com.lenerd.spotifyplus.module.fingerprint.HookFingerprints;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public abstract class SpotifyHook implements XposedInterface.Hooker {
    protected static XposedModule module;
    protected static DexKitBridge bridge;
    protected static ClassLoader classLoader;
    public static Activity currentActivity;

    private static final Map<Class<? extends SpotifyHook>, SpotifyHook> instances = new HashMap<>();

    public void init(XposedModule module, XposedModuleInterface.PackageReadyParam lpparam, DexKitBridge bridge) {
        SpotifyHook.module = module;
        SpotifyHook.bridge = bridge;
        SpotifyHook.classLoader = lpparam.getClassLoader();
        instances.put(this.getClass(), this);

        try {
            hookSetup();
        } catch (Exception e) {
            logError(e);
        }
    }

    protected abstract void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException;

    protected abstract void beforeHook(SpotifyCallback callback);

    protected abstract void afterHook(SpotifyCallback callback);

    /// Handle incoming messages from scripts
    public abstract Object handle(String command, Object[] args);

    /** Adapt the interceptor chain to the extension helpers' before/after callbacks. */
    @Override
    public final Object intercept(XposedInterface.Chain chain) throws Throwable {
        return SpotifyCallback.intercept(chain, callback -> {
            try {
                beforeHook(callback);
            } catch (Throwable error) {
                logError(error);
            }
        }, callback -> {
            try {
                afterHook(callback);
            } catch (Throwable error) {
                logError(error);
            }
        });
    }

    protected static SpotifyHook getHookInstance(Class<? extends SpotifyHook> clazz) {
        return instances.get(clazz);
    }

    protected Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            logError(e);
            return null;
        }
    }

    /** Resolve and cache a logical Spotify class, method, constructor, or field mapping. */
    protected <T> T resolve(FingerprintMapping<T> mapping) {
        return HookFingerprints.get().resolve(mapping);
    }

    protected void hook(Method member) {
        module.hook(member).intercept(this);
    }

    protected void hook(Constructor<?> member) {
        module.hook(member).intercept(this);
    }

    protected static void log(String message) {
        Log.d("SpotifyPlus", message);
    }

    protected static void logError(String message) {
        Log.e("SpotifyPlus", message);
    }

    protected static void logError(Exception e) {
        Log.e("SpotifyPlus", e.getMessage(), e);
    }

    protected static void logError(Throwable t) {
        Log.e("SpotifyPlus", t.getMessage(), t);
    }

    protected static void logError(String message, Exception e) {
        Log.e("SpotifyPlus", message, e);
    }

    protected static void toast(String message) {
        if (currentActivity == null) return;

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(currentActivity, message, Toast.LENGTH_SHORT).show());
    }

    protected static <T extends SpotifyHook> T getHook(Class<T> clazz) {
        return clazz.cast(instances.get(clazz));
    }
}

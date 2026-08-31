package com.lenerd.spotifyplus.module;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;

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
    private boolean legacy = false;
    private static SpotifyCallback callbackBefore;
    private static SpotifyCallback callbackAfter;

    public void init(XposedModule module, XposedModuleInterface.PackageLoadedParam lpparam, DexKitBridge bridge) {
        SpotifyHook.module = module;
        SpotifyHook.bridge = bridge;
        SpotifyHook.classLoader = lpparam.getClassLoader();
        instances.put(this.getClass(), this);

        try {
            hookSetup();
        } catch (NoSuchMethodException | ClassNotFoundException | NoSuchFieldException e) {
            logError(e);
        }
    }

    public void initLegacy(XC_LoadPackage.LoadPackageParam lpparam, DexKitBridge bridge) {
        legacy = true;
        SpotifyHook.bridge = bridge;
        SpotifyHook.classLoader = lpparam.classLoader;

        try {
            hookSetup();
        } catch(NoSuchMethodException | ClassNotFoundException | NoSuchFieldException e) {
            logError(e);
        }
    }

    protected abstract void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException;

    protected abstract void beforeHook(SpotifyCallback callback);

    protected abstract void afterHook(SpotifyCallback callback);

    /// Handle incoming messages from scripts
    public abstract Object handle(String command, Object[] args);

    protected static SpotifyCallback buildCallback(XposedInterface.BeforeHookCallback callback) {
        try {
            callbackBefore = new SpotifyCallback(callback.getMember(), callback.getThisObject(), callback.getArgs(), null, callback, callback.getClass().getDeclaredMethod("returnAndSkip", Object.class), null, null);
            return callbackBefore;
        } catch (NoSuchMethodException e) {
            logError(e);
            return null;
        }
    }

    protected static SpotifyCallback buildCallback(XposedInterface.AfterHookCallback callback) {
        try {
            callbackAfter = new SpotifyCallback(callback.getMember(), callback.getThisObject(), callback.getArgs(), callback.getResult(), callback, null, callback.getClass().getDeclaredMethod("setResult", Object.class), callback.getThrowable());
            return callbackAfter;
        } catch (NoSuchMethodException e) {
            logError(e);
            return null;
        }
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

    protected void hook(Method member) {
        if (!legacy) {
            module.hook(member, this.getClass());
        } else {
            XposedBridge.hookMethod(member, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    beforeHook(new SpotifyCallback(param.method, param.thisObject, param.args, null, param, param.getClass().getDeclaredMethod("setResult", Object.class), null, null));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    afterHook(new SpotifyCallback(param.method, param.thisObject, param.args, param.getResult(), param, null, param.getClass().getDeclaredMethod("setResult", Object.class), param.getThrowable()));
                }
            });
        }
    }

    protected static void hook(Method member, Class<? extends SpotifyHook> clazz) {
        module.hook(member, clazz);
    }

    protected void hook(Constructor<?> member) {
        if (!legacy) {
            module.hook(member, this.getClass());
        } else {
            XposedBridge.hookMethod(member, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    beforeHook(new SpotifyCallback(param.method, param.thisObject, param.args, null, param, param.getClass().getDeclaredMethod("setResult", Object.class), null, null));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    afterHook(new SpotifyCallback(param.method, param.thisObject, param.args, param.getResult(), param, null, param.getClass().getDeclaredMethod("setResult", Object.class), param.getThrowable()));
                }
            });
        }
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
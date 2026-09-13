package com.lenerd46.spotifyplus.hooks;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XResources;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import java.util.concurrent.Callable;

public class SpotifyTitleOverride {
    private static final ThreadLocal<Override> tl = new ThreadLocal<>();
    private static volatile boolean installed = false;
    private static final java.util.Map<Integer, String> moduleTitles = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Integer> titleIds = new java.util.HashMap<>();
    private static int nextTitleId = 0x53510000;

    public static synchronized int registerTitle(String title) {
        install();
        Integer id = titleIds.get(title);
        if (id == null) {
            id = nextTitleId++;
            titleIds.put(title, id);
            moduleTitles.put(id, title);
        }
        return id;
    }

    public static void overrideSpotifyStringById(int resId, String newValue) {
        try {
            Resources res = AndroidAppHelper.currentApplication().getResources();
            final String entry = res.getResourceEntryName(resId);

            References.xresources.setReplacement("com.spotify.music", "string", entry, newValue);
        } catch (Throwable t) {
            // XposedBridge.log(t);
        }
    }

    private static final class Override {
        final String targetPkg;
        final int resId;
        final String title;

        Override(String targetPkg, int resId, String title) {
            this.targetPkg = targetPkg;
            this.resId = resId;
            this.title = title;
        }
    }

    public static synchronized void install() {
        if (installed)
            return;
        installed = true;

        XposedBridge.hookAllMethods(Resources.class, "getString", new XC_MethodHook() {
            @java.lang.Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                if (param.args.length < 1 || !(param.args[0] instanceof Integer))
                    return;

                String moduleTitle = moduleTitles.get((Integer) param.args[0]);
                if (moduleTitle != null) { param.setResult(moduleTitle); return; }

                Override ov = tl.get();
                if (ov == null)
                    return;

                final int id = (Integer) param.args[0];
                XposedBridge.log("[SpotifyPlus] LOOK A THING: " + id);
                if (!idMatchesPackage(param, id, ov.targetPkg))
                    return;

                if (id == ov.resId) {
                    param.setResult(ov.title);
                }
            }
        });

        XposedBridge.hookAllMethods(Resources.class, "getText", new XC_MethodHook() {
            @java.lang.Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length < 1 || !(param.args[0] instanceof Integer))
                    return;

                String moduleTitle = moduleTitles.get((Integer) param.args[0]);
                if (moduleTitle != null) { param.setResult(moduleTitle); return; }

                Override ov = tl.get();
                if (ov == null)
                    return;

                final int id = (Integer) param.args[0];
                if (!idMatchesPackage(param, id, ov.targetPkg))
                    return;

                if (id == ov.resId) {
                    param.setResult(ov.title);
                }
            }
        });
    }

    public static <T> T withTitleOverride(String targetPkg, int resId, String title, Callable<T> action) {
        tl.set(new Override(targetPkg, resId, title));

        try {
            return action.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tl.remove();
        }
    }

    public static void withTitleOverrideVoid(String targetPkg, int resId, String title, Runnable action) {
        tl.set(new Override(targetPkg, resId, title));

        try {
            action.run();
        } finally {
            tl.remove();
        }
    }

    private static boolean idMatchesPackage(XC_MethodHook.MethodHookParam param, int id, String targetPkg) {
        try {
            Resources res = (Resources) param.thisObject;
            String pkg = res.getResourcePackageName(id);

            return targetPkg.equals(pkg);
        } catch (Resources.NotFoundException e) {
            return false;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }
}

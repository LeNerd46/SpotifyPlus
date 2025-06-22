package com.lenerd46.spotifyplus.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Method;

public class SeekHook extends SpotifyHook {
    public static Object icdInstance;

    @Override
    protected void hook() {
        try {
            XposedHelpers.findAndHookMethod("p.icd", lpparm.classLoader, "apply", Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    icdInstance = param.thisObject;
                    XposedBridge.log("[SpotifyPlus] icd.apply() called with " + param.thisObject.getClass().getName());

                    Class<?> duwClass = XposedHelpers.findClass("p.duw", lpparm.classLoader);
                    Object duw = XposedHelpers.newInstance(duwClass, 60000L);

                    Class<?> oj60Class = XposedHelpers.findClass("p.oj60", lpparm.classLoader);
                    Object qo20 = XposedHelpers.newInstance(oj60Class, duw);

                    Class<?> handlerClass = XposedHelpers.findClass("p.icd", lpparm.classLoader); // replace with real class name from param.thisObject
                    Class<?> qo20Class = XposedHelpers.findClass("p.qo20", lpparm.classLoader);
                    Method apply = handlerClass.getMethod("apply", qo20Class); // or explicitly `p.qo20`
                    apply.invoke(icdInstance, qo20);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
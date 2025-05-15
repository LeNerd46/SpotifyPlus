package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.lenerd46.spotifyplus.hooks.PremiumHook;
import com.lenerd46.spotifyplus.hooks.ScriptManager;
import com.lenerd46.spotifyplus.hooks.SettingsFlyoutHook;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.ref.WeakReference;

public class XposedLoader implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if(!lpparam.packageName.equals("com.spotify.music")) return;
        XposedBridge.log("[SpotifyPlus] Loading SpotifyPlus v0.1");

        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                References.currentActivity = new WeakReference<Activity>(activity);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int requestCode = (int) param.args[0];
                Intent data = (Intent) param.args[2];

                if(requestCode == 9072022 && data != null) {
                    Uri tree = data.getData();
                    ContentResolver content = ((Activity) param.thisObject).getContentResolver();
                    content.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    SharedPreferences prefs = ((Activity) param.thisObject).getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
                    prefs.edit().putString("scripts_directory", tree.toString()).apply();
                }
            }
        });

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                new SettingsFlyoutHook(context).init(lpparam);
                new ScriptManager().init(context, lpparam.classLoader);
//                new PremiumHook().init(lpparam);
            }
        });

    }
}
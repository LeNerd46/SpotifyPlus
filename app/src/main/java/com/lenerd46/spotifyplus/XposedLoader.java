package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.core.content.res.ResourcesCompat;
import com.lenerd46.spotifyplus.hooks.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class XposedLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    static {
        System.loadLibrary("dexkit");
    }

    private DexKitBridge bridge;
    private String modulePath = null;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if(!lpparam.packageName.equals("com.spotify.music")) return;
        XposedBridge.log("[SpotifyPlus] Loading SpotifyPlus v0.2");

        if(bridge == null) {
            try {
                bridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }

        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                References.currentActivity = new WeakReference<>(activity);
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

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Typeface beautifulFont = References.beautifulFont.get();

                if(beautifulFont != null) return;

                try {
                    Resources resources = XModuleResources.createInstance(modulePath, null);
                    beautifulFont = Typeface.createFromAsset(resources.getAssets(), "fonts/lyrics_medium.ttf");

                    XposedBridge.log("[SpotifyPlus] Successfully loaded font!");
                } catch(Throwable t) {
                    XposedBridge.log("[SpotifyPlus] Failed to load font (error)");
                    XposedBridge.log(t);
                }

                if(beautifulFont != null) {
                    References.beautifulFont = new WeakReference<>(beautifulFont);
                }
            }
        });

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                new SettingsFlyoutHook(context).init(lpparam, bridge);
//                new ScriptManager().init(context, lpparam.classLoader);
                ScriptManager.getInstance().init(context, lpparam.classLoader);
                new BeautifulLyricsHook().init(lpparam, bridge);
                new SocialHook().init(lpparam, bridge);
                //                new PremiumHook().init(lpparam);
            }
        });
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        modulePath = startupParam.modulePath;
    }
}
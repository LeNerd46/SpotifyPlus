package com.lenerd.spotifyplus.module.scripting;

import android.content.Context;
import android.util.Log;
import com.lenerd.spotifyplus.sdk.SpotifyPlusPlugin;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

import java.io.File;

public class ScriptDexLoader {
    public final Context context;

    public ScriptDexLoader(Context context) {
        this.context = context;
    }

    public ClassLoader loadDex(File dexFIle, File optimizedDir, ClassLoader parent) {
        return new DexClassLoader(dexFIle.getAbsolutePath(), optimizedDir.getAbsolutePath(), null, parent);
    }

    public SpotifyPlusPlugin loadPluginFromDexFile(File dexFile, File optimizedDir, ClassLoader parent, String pluginClassName) throws Exception {
        try {

            File optimizedFile = new File(optimizedDir, dexFile.getName() + ".odex");

            DexFile dex = DexFile.loadDex(dexFile.getAbsolutePath(), optimizedFile.getAbsolutePath(), 0);
            Class<?> clazz = dex.loadClass(pluginClassName, parent);

            if (clazz == null) {
                throw new ClassNotFoundException(pluginClassName + " was listed in dex but DexFile.loadClass returned null");
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (!(instance instanceof SpotifyPlusPlugin)) {
                throw new IllegalStateException(pluginClassName + " does not implement SpotifyPlusPlugin. instanceLoader=" + clazz.getClassLoader() + " sdkLoader=" + SpotifyPlusPlugin.class.getClassLoader());
            }

            return (SpotifyPlusPlugin) instance;
        } catch(Exception e) {
            Log.e("DexLoader", e.getMessage(), e);
            return null;
        }
    }

    public SpotifyPlusPlugin loadPlugin(ClassLoader classLoader, String pluginClassName) throws Exception {
        Log.d("DexLoader", "Loading plugin class=" + pluginClassName);
        Log.d("DexLoader", "Using classLoader=" + classLoader);
        Log.d("DexLoader", "Using parent=" + classLoader.getParent());

        Class<?> clazz = classLoader.loadClass(pluginClassName);
        Object instance = clazz.getDeclaredConstructor().newInstance();

        if (!(instance instanceof SpotifyPlusPlugin)) {
            throw new IllegalStateException(pluginClassName + " does not implement SpotifyPlusPlugin. instanceLoader=" + instance.getClass().getClassLoader() + " sdkLoader=" + SpotifyPlusPlugin.class.getClassLoader());
        }

        return (SpotifyPlusPlugin) instance;
    }
}

package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.lenerd46.spotifyplus.scripting.Debug;
import com.lenerd46.spotifyplus.scripting.SettingsExtensionManager;
import com.lenerd46.spotifyplus.scripting.SpotifyPlayer;
import com.lenerd46.spotifyplus.scripting.SpotifyPlusApi;
import com.lenerd46.spotifyplus.scripting.entities.ScriptableSettingItem;
import com.lenerd46.spotifyplus.scripting.entities.ScriptableSettingSection;
import com.lenerd46.spotifyplus.scripting.entities.ScriptableSpotifyTrack;
import com.lenerd46.spotifyplus.scripting.events.EventManager;
import de.robv.android.xposed.XposedBridge;
import com.faendir.rhino_android.RhinoAndroidHelper;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class ScriptManager extends SpotifyHook {
    public static ScriptManager instance = null;
    private Scriptable scriptScope;

    private ScriptManager() { }

    public static synchronized ScriptManager getInstance() {
        if(instance == null) {
            instance = new ScriptManager();
        }

        return instance;
    }

    public Scriptable getScope() {
        return this.scriptScope;
    }

    public void runScript(String code, String name) {
        org.mozilla.javascript.Context context = new RhinoAndroidHelper().enterContext();

        try {
            context.setOptimizationLevel(-1);
            this.scriptScope = context.initStandardObjects();

            Object eventManager = org.mozilla.javascript.Context.javaToJS(EventManager.getInstance(), this.scriptScope);
            ScriptableObject.putProperty(this.scriptScope, "events", eventManager);

            ScriptableObject.defineClass(this.scriptScope, ScriptableSpotifyTrack.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableSettingSection.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableSettingItem.class);

            List<SpotifyPlusApi> apis = Arrays.asList(
                    new SpotifyPlayer(this.scriptScope, lpparm),
                    new Debug(),
                    new SettingsExtensionManager()
            );

            for(SpotifyPlusApi api : apis) {
                api.register(this.scriptScope, context, name);
            }

            context.evaluateString(this.scriptScope, code, name, 1, null);
        } catch(Exception e) {
            XposedBridge.log("[SpotifyPlus] ScriptManager: Failed to load " + name);
            XposedBridge.log(e);
        }
    }

    public void init(Context ctx, ClassLoader loader) {
        XposedBridge.log("[SpotifyPlus] Starting script engine!");

        SharedPreferences prefs = ctx.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        String uriString = prefs.getString("scripts_directory", null);

        if(uriString == null) {
            XposedBridge.log("[SpotifyPlus] Failed to get script directory. Please set a directory first");
            return;
        }

        Uri path = Uri.parse(uriString);

        DocumentFile directory = DocumentFile.fromTreeUri(ctx, path);
        if(directory == null || !directory.isDirectory()) {
            XposedBridge.log("[SpotifyPlus] No scripts directory found!");
            return;
        }

        DocumentFile[] children = directory.listFiles();
        XposedBridge.log("[SpotifyPlus] " + children.length + " scripts loaded!");

        for(DocumentFile script : children) {
            String name = script.getName();

            if(name != null && name.endsWith(".js") && script.isFile()) {
                try (InputStream in = ctx.getContentResolver().openInputStream(script.getUri())) {
                    String code = readStreamAsString(in);
                    if(!code.isBlank()) {
                        XposedBridge.log("[SpotifyPlus] " + name + " loaded!");
                        runScript(code, name);
                        // runScriptS(code, ctx, name);
                    }
                } catch(Exception ex) {
                    XposedBridge.log("[SpotifyPlus] Error loading " + name + ": " + ex);
                }
            }
        }
    }

    // Hot reload scripts?
    public void loadOrReloadScript(String code, String name) {
        EventManager.getInstance().clearAllListeners();
        runScript(code, name);
    }

    private void runScriptS(String code, Context ctx, String name) {
        var rhino = new RhinoAndroidHelper().enterContext();
        rhino.setOptimizationLevel(-1);

        try {
            ScriptableObject scope = rhino.initStandardObjects();
            ScriptableObject.putProperty(scope, "events", EventManager.getInstance());
            ScriptableObject.defineClass(scope, ScriptableSpotifyTrack.class);

            List<SpotifyPlusApi> apis = Arrays.asList(
                    new SpotifyPlayer(scope, lpparm),
                    new Debug()
            );

            for(SpotifyPlusApi api : apis) {
                api.register(scope, rhino, name);
            }

            rhino.evaluateString(scope, code, name, 1, null);
        } catch(Exception e) {
        } finally{
            org.mozilla.javascript.Context.exit();
        }
    }

    private String readStreamAsString(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;

        while((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }

        return sb.toString();
    }

    // Not actually doing any hooking, I just need the LoadPackageParam
    @Override
    protected void hook() { }
}

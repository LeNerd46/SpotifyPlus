package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.google.gson.Gson;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.scripting.*;
import com.lenerd46.spotifyplus.scripting.entities.*;
import com.lenerd46.spotifyplus.scripting.EventManager;
import de.robv.android.xposed.XposedBridge;
import com.faendir.rhino_android.RhinoAndroidHelper;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.*;
import java.util.*;

public class ScriptManager extends SpotifyHook {
    public static ScriptManager instance = null;
    public List<ScriptMetadata> loadedScripts = new ArrayList<>();
    private Scriptable scriptScope;
    private int nextId = 4;
    private final Map<String, Integer> scriptIds = new HashMap<>();
    private Context moduleContext;

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

    public void runScript(String code, String name, String fileName) {
        org.mozilla.javascript.Context context = new RhinoAndroidHelper().enterContext();

        try {
            context.setOptimizationLevel(-1);
            this.scriptScope = context.initStandardObjects();

            if(!scriptIds.containsKey(name)) {
                scriptIds.put(name, nextId++);
            }

            context.putThreadLocal("id", scriptIds.get(name));
            context.putThreadLocal("name", name);
            context.putThreadLocal("file_name", fileName);
            context.putThreadLocal("prefs", References.getScriptPreferences(name, moduleContext));

            Object eventManager = org.mozilla.javascript.Context.javaToJS(EventManager.getInstance(), this.scriptScope);
            ScriptableObject.putProperty(this.scriptScope, "events", eventManager);

            ScriptableObject.defineClass(this.scriptScope, ScriptableSpotifyTrack.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableSettingSection.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableSettingItem.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableSideDrawerItem.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableScriptUI.class);
            ScriptableObject.defineClass(this.scriptScope, ScriptableContextMenuItem.class);

            List<SpotifyPlusApi> apis = Arrays.asList(
                    new SpotifyPlayer(this.scriptScope, lpparm, bridge),
                    new Debug(),
                    new PreferencesApi()
            );

            for(SpotifyPlusApi api : apis) {
                api.register(this.scriptScope, context);
            }

            context.evaluateString(this.scriptScope, code, name, 1, null);
        } catch(Exception e) {
            XposedBridge.log("[SpotifyPlus] ScriptManager: Failed to load " + name);
            XposedBridge.log(e);
        }
    }

    public void init(Context ctx, ClassLoader loader) {
        XposedBridge.log("[SpotifyPlus] Starting script engine!");

        moduleContext = ctx;
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
            if(script.isDirectory()) {
                String scriptName = script.getName();
                DocumentFile manifest = script.findFile("manifest.json");
                ScriptMetadata metadata = null;

                if(manifest != null && manifest.exists()) {
                    Gson gson = new Gson();
                    try (InputStream in = ctx.getContentResolver().openInputStream(manifest.getUri())) {
                        String code = readStreamAsString(in);
                        if(!code.isBlank()) {
                            metadata = gson.fromJson(code, ScriptMetadata.class);
                            loadedScripts.add(metadata);
                        }
                    } catch(Exception ex) {
                        XposedBridge.log("[SpotifyPlus] Error loading manifest: " + ex);
                    }
                } else {
                    XposedBridge.log("[SpotifyPlus] No manifest found");
                    continue;
                }

                for(DocumentFile child : script.listFiles()) {
                    String name = child.getName();

                    if(name != null && name.endsWith(".js") && child.isFile()) {
                        try (InputStream in = ctx.getContentResolver().openInputStream(child.getUri())) {
                            String code = readStreamAsString(in);
                            if(!code.isBlank()) {
                                XposedBridge.log("[SpotifyPlus] " + metadata.name + " loaded!");
                                runScript(code, metadata.name, scriptName);
                            }
                        } catch(Exception ex) {
                            XposedBridge.log("[SpotifyPlus] Error loading " + metadata.name + ": " + ex);
                        }
                    }
                }
            }
        }
    }

    // Hot reload scripts?
    public void loadOrReloadScript(String code, String name) {
        EventManager.getInstance().clearAllListeners();
        runScript(code, name, name); // Update to use manifest name
    }

    private void runScriptS(String code, Context ctx, String name) {
        var rhino = new RhinoAndroidHelper().enterContext();
        rhino.setOptimizationLevel(-1);

        try {
            ScriptableObject scope = rhino.initStandardObjects();
            ScriptableObject.putProperty(scope, "events", EventManager.getInstance());
            ScriptableObject.defineClass(scope, ScriptableSpotifyTrack.class);

            List<SpotifyPlusApi> apis = Arrays.asList(
                    new SpotifyPlayer(scope, lpparm, bridge),
                    new Debug()
            );

            for(SpotifyPlusApi api : apis) {
                api.register(scope, rhino);
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

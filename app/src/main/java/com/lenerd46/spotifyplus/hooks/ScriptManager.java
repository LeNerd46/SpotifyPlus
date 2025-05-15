package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.lenerd46.spotifyplus.scripting.Debug;
import com.lenerd46.spotifyplus.scripting.EventBus;
import com.lenerd46.spotifyplus.scripting.SpotifyPlayer;
import com.lenerd46.spotifyplus.scripting.SpotifyPlusApi;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import com.faendir.rhino_android.RhinoAndroidHelper;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ScriptManager {
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
                        runScript(code, ctx, name);
                    }
                } catch(Exception ex) {
                    XposedBridge.log("[SpotifyPlus] Error loading " + name + ": " + ex);
                }
            }
        }
    }

    private void runScript(String code, Context ctx, String name) {
        var rhino = new RhinoAndroidHelper().enterContext();
        rhino.setOptimizationLevel(-1);

        try {
            ScriptableObject scope = rhino.initStandardObjects();
            EventBus.init(rhino, scope);

            List<SpotifyPlusApi> apis = Arrays.asList(
                    new SpotifyPlayer(),
                    new Debug()
            );

            for(SpotifyPlusApi api : apis) {
                api.register(scope, rhino, name);
            }

            rhino.evaluateString(scope, code, "test.js", 1, null);
        } finally {
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
}

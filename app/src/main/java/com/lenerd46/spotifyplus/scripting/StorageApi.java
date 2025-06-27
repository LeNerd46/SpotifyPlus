package com.lenerd46.spotifyplus.scripting;

import android.app.Activity;
import com.google.gson.Gson;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class StorageApi implements SpotifyPlusApi {
    private File storageDir;
    private String name;
    private Scriptable scope;
    private android.content.Context moduleContext;

    public StorageApi(android.content.Context context) {
        this.moduleContext= context;
    }

    @Override
    public void register(Scriptable scope, Context ctx) {
        ScriptableObject.putProperty(scope, "Storage", Context.javaToJS(this, scope));
        this.name = ctx.getThreadLocal("name").toString();
        String storageName = name.trim().toLowerCase().replace(" ", "_").replaceAll("[^a-z0-9_\\-]", ""); // Two scripts cannot have the same name

        File dataDir = moduleContext.getDataDir();
        File scriptDir = new File(dataDir, "scripts/" + storageName);

        if(!scriptDir.exists()) {
            scriptDir.mkdirs();
        }

        this.storageDir = scriptDir;
        this.scope = scope;
    }

    public boolean exists(String path) {
        return new File(storageDir, path).exists();
    }

    public void write(String path, String content) {
        File file = new File(storageDir, path);

        try {
            File parent = file.getParentFile();
            if(parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.write(content);
            }
        } catch(IOException e) {
            XposedBridge.log("[SpotifyPlus] [" + this.name + "] Failed to write to file: " + file.getAbsolutePath());
            XposedBridge.log(e);
        }
    }

    public void append(String path, String content) {
        File file = new File(storageDir, path);

        try {
            File parent = file.getParentFile();
            if(parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try(BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                writer.write(content);
            }
        } catch(IOException e) {
            XposedBridge.log("[SpotifyPlus] [" + this.name + "] Failed to write to file: " + file.getAbsolutePath());
            XposedBridge.log(e);
        }
    }

    public String read(String path) {
        File file = new File(storageDir, path);

        if(!file.exists()) {
            XposedBridge.log("[SpotifyPlus] [" + this.name + "] Could not find file: " + file.getAbsolutePath());
            return null;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            XposedBridge.log("[SpotifyPlus] [" + this.name + "] Failed to read file: " + file.getAbsolutePath());
            XposedBridge.log(e);
            return null;
        }

        return content.toString();
    }

    public boolean delete(String path) {
        File file = new File(storageDir, path);
        return file.delete();
    }

    public String[] list(String path) {
        File dir = new File(storageDir, path);

        if(dir.isDirectory()) {
            return dir.list();
        }

        return new String[0];
    }

    public boolean makeDirectory(String path) {
        File file = new File(storageDir, path);
        return file.mkdirs();
    }

    public String getAbsolutePath(String path) {
        return new File(storageDir, path).getAbsolutePath();
    }

    public boolean rename(String path, String newName) {
        File from = new File(storageDir, path);
        File to = new File(storageDir, newName);
        return from.renameTo(to);
    }
}

package com.lenerd46.spotifyplus.scripting.entities;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import androidx.documentfile.provider.DocumentFile;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ScriptableScriptUI extends ScriptableObject {
    private XModuleResources res;
    private Activity activity;
    private String packageName;
    private View root;

    public ScriptableScriptUI() { }

    @Override
    public String getClassName() {
        return "ScriptUI";
    }

    @JSConstructor
    public void jsConstructor(String path, String packageName) {
        org.mozilla.javascript.Context ctx = org.mozilla.javascript.Context.getCurrentContext();
        String name = ctx.getThreadLocal("file_name").toString();
        this.packageName = packageName;

        this.activity = References.currentActivity;
        SharedPreferences prefs = References.getPreferences();

        String direcotryUri = prefs.getString("scripts_directory", "");
        Uri uri = Uri.parse(direcotryUri);
        DocumentFile directory = DocumentFile.fromTreeUri(activity, uri);

        DocumentFile scriptFolder = directory.findFile(name);

        if(scriptFolder != null && scriptFolder.exists() && scriptFolder.isDirectory()) {
            DocumentFile apk = scriptFolder.findFile(path + ".apk");
            String path1 = getAboslutePath(apk, activity);
            XposedBridge.log("[SpotifyPlus] " + path);

            this.res = XModuleResources.createInstance(path1, null);
        }
    }

    @JSFunction
    public void show(String resource) {
        try {
            activity.runOnUiThread(() -> {
                var layout = res.getLayout(res.getIdentifier(resource, "layout", this.packageName));
                LayoutInflater inflater = LayoutInflater.from(activity);
                root = inflater.inflate(layout, (ViewGroup) activity.getWindow().getDecorView(), false);
                ((ViewGroup) activity.getWindow().getDecorView()).addView(root);
            });
        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Failed to show UI: " + e);
        }
    }

    @JSFunction
    public void hide() {
        try {
            activity.runOnUiThread(() -> {
                ((ViewGroup)activity.getWindow().getDecorView()).removeView(root);
            });
        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Failed to hide UI: " + e);
        }
    }

    @JSFunction
    public void setImage(String id, String resource) {
        try {
            activity.runOnUiThread(() -> {
                View view = root.findViewById(res.getIdentifier(id, "id", this.packageName));
                if(view instanceof ImageView) {
                    ((ImageView) view).setImageDrawable(res.getDrawable(res.getIdentifier(resource, "drawable", this.packageName)));
                } else {
                    XposedBridge.log("[SpotifyPlus] View '" + id + "' is not an ImageView");
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Failed to set image: " + e);
        }
    }

    @JSFunction
    public void onClick(String resource, Function callback) {
        View item = root.findViewById(res.getIdentifier(resource, "id", this.packageName));

        item.setOnClickListener(view -> {
            org.mozilla.javascript.Context ctx = org.mozilla.javascript.Context.enter();

            try {
                callback.call(ctx, getParentScope(), getParentScope(), new Object[]{ });
            } catch(Exception e) {
                XposedBridge.log(e);
            } finally {
                org.mozilla.javascript.Context.exit();
            }
        });
    }

    private String getAboslutePath(DocumentFile file, Context context) {
        Uri uri = file.getUri();

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            File tempFile = new File(context.getCacheDir(), "test.apk");

            try (OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int len;

                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }
}

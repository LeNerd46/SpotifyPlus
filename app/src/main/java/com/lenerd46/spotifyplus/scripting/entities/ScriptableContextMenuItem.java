package com.lenerd46.spotifyplus.scripting.entities;

import com.lenerd46.spotifyplus.ContextMenuItem;
import com.lenerd46.spotifyplus.hooks.ContextMenuHook;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

public class ScriptableContextMenuItem extends ScriptableObject {
    private String title;
    private String type;

    private String name;
    private ContextMenuItem item;

    public ScriptableContextMenuItem() { }

    @Override
    public String getClassName() {
        return "ContextMenuItem";
    }

    @JSConstructor
    public void jsConstructor(String title, String type, Function callback) {
        this.title = title;

        Context ctx = Context.getCurrentContext();
        this.name = ctx.getThreadLocal("name").toString();
        int id = (int) ctx.getThreadLocal("id");

        if(!type.toLowerCase().equals("track") && !type.toLowerCase().equals("album") && !type.toLowerCase().equals("artist")) {
            XposedBridge.log("[SpotifyPlus] [" + name + "] Invalid Context Menu Type");
            this.type = "track";
        } else {
            this.type = type;
        }

        this.item = new ContextMenuItem(id, title, type.toLowerCase(), () -> {
            Context context = Context.enter();

            try {
                callback.call(context, getParentScope(), getParentScope(), new Object[]{ ContextMenuHook.currentUri.split(":")[2] });
            } catch (Exception e) {
                XposedBridge.log(e);
            } finally {
                Context.exit();
            }
        });
    }

    @JSGetter
    public String getTitle() {
        return title;
    }

    @JSSetter
    public void setTitle(String title) {
        this.title = title;
    }

    @JSGetter
    public String getType() {
        return type;
    }

    @JSSetter
    public void setType(String type) {
        if(!type.toLowerCase().equals("track") || !type.toLowerCase().equals("album") || !type.toLowerCase().equals("artist")) {
            XposedBridge.log("[SpotifyPlus] [" + name + "] Invalid Context Menu Type");
            this.type = "track";
        } else {
            this.type = type;
        }
    }

    @JSFunction
    public void register() {
        ContextMenuHook.scriptItems.add(item);
    }
}

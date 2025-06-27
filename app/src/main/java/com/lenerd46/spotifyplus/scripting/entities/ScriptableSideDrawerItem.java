package com.lenerd46.spotifyplus.scripting.entities;

import com.lenerd46.spotifyplus.hooks.RemoveCreateButtonHook;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

public class ScriptableSideDrawerItem extends ScriptableObject {

    private String title;
    private Runnable callback;
    private Function originalCallback;

    public ScriptableSideDrawerItem() { }

    @Override
    public String getClassName() {
        return "SideDrawerItem";
    }

    @JSConstructor
    public void jsConstructor(String title, Function callback) {
        this.title = title;
        this.originalCallback = callback;

        this.callback = () -> {
            Context ctx = Context.enter();

            try {
                callback.call(ctx, getParentScope(), getParentScope(), new Object[]{ });
            } catch(Exception e) {
                XposedBridge.log(e);
            } finally {
                Context.exit();
            }
        };
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
    public Function getCallback() {
        return originalCallback;
    }

    @JSSetter
    public void setCallback(Function callback) {
        this.callback = () -> {
            Context ctx = Context.enter();

            try {
                callback.call(ctx, getParentScope(), getParentScope(), new Object[]{ });
            } catch(Exception e) {
                XposedBridge.log(e);
            } finally {
                Context.exit();
            }
        };
    }

    @JSFunction
    public void register() {
        Integer id = (Integer) Context.getCurrentContext().getThreadLocal("id");
        RemoveCreateButtonHook.registerSideButton(this.title, id, callback);
    }
}

package com.lenerd46.spotifyplus.scripting.events;

import com.lenerd46.spotifyplus.hooks.ScriptManager;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {
    private static EventManager instance = null;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Function>> listeners;

    private EventManager() {
        listeners = new ConcurrentHashMap<>();
    }

    public static synchronized EventManager getInstance() {
        if(instance == null) {
            instance = new EventManager();
        }

        return instance;
    }

    public void dispatchEvent(String eventName, Object data) {
        var eventListeners = this.listeners.get(eventName);
        if(eventListeners == null || eventListeners.isEmpty()) return;

        Scriptable scope = ScriptManager.getInstance().getScope();
        if(scope == null) return;

        Context context = Context.enter();
        for(Function function : eventListeners) {
            try {
                Object jsData = Context.javaToJS(data, scope);
                Object[] args = new Object[] { jsData };

                function.call(context, scope, scope, args);
            } catch(Exception e) {
                XposedBridge.log("[SpotifyPlus] Error dispatching event: " + eventName);
                XposedBridge.log(e);
            } finally {
                Context.exit();
            }
        }
    }

    public void subscribe(String eventName, Function callback) {
        var eventListeners = this.listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>());
        eventListeners.add(callback);
    }

    public void unsubscribe(String eventName, Function callback) {
        var eventListeners = this.listeners.get(eventName);
        if(eventListeners != null) {
            eventListeners.remove(callback);
        }
    }

    public void clearAllListeners() {
        listeners.clear();
    }
}
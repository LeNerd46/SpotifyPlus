package com.lenerd46.spotifyplus.scripting;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventBus implements SpotifyPlusApi {
    private final Map<String, List> listeners = new HashMap<>();
    private final Context context;
    private final Scriptable scope;

    private static EventBus instance;

    public EventBus(Context context, Scriptable scope) {
        this.context = context;
        this.scope = scope;
    }

    public static void init(Context context, Scriptable scope) {
        instance = new EventBus(context, scope);
        ScriptableObject.putProperty(scope, "SpotifyEvents", Context.javaToJS(instance, scope));
    }

    @Override
    public void register(ScriptableObject scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "SpotifyEvents", Context.javaToJS(this, scope));
    }

    public static EventBus getInstance() {
        return instance;
    }

    public void on(String eventName, Function callback) {
        listeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(callback);
    }

    public void emit(String eventName, Object... args) {
        List<Function> callbacks = listeners.get(eventName);

        if(callbacks != null) {
            for(Function function : callbacks) {
                function.call(context, scope, scope, args);
            }
        }
    }
}

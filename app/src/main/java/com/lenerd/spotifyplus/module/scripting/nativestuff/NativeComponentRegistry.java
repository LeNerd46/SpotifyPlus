package com.lenerd.spotifyplus.module.scripting.nativestuff;

import android.util.Log;
import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.SpotifyPlusRegistry;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NativeComponentRegistry implements SpotifyPlusRegistry {
    private SpotifyPlusContext context;
    private final Map<String, NativeComponentEntry> components = new HashMap<>();
    private final Map<String, String> componentOwners = new HashMap<>();
    private String registeringScriptId;

    public void setContext(SpotifyPlusContext context) {
        this.context = context;
    }

    @Override
    public synchronized void registerComponent(SpotifyPlusComponent<?> component) {
        components.put(component.getName(), new  NativeComponentEntry(component, context));
        if (registeringScriptId != null) {
            componentOwners.put(component.getName(), registeringScriptId);
        }
        Log.d("DexLoader", "Registered " + component.getName());
    }

    public synchronized void beginScriptRegistration(String scriptId) {
        registeringScriptId = scriptId;
    }

    public synchronized void endScriptRegistration() {
        registeringScriptId = null;
    }

    public synchronized void unregisterScript(String scriptId) {
        Set<String> removed = new HashSet<>();
        for (Map.Entry<String, String> entry : componentOwners.entrySet()) {
            if (!scriptId.equals(entry.getValue())) continue;
            removed.add(entry.getKey());
        }

        for (String componentName : removed) {
            components.remove(componentName);
            componentOwners.remove(componentName);
            Log.d("DexLoader", "Unregistered " + componentName + " from " + scriptId);
        }
    }

    public NativeComponentEntry getComponent(String name) {
        return components.get(name);
    }

    public boolean hasComponent(String name) {
        return components.containsKey(name);
    }
}

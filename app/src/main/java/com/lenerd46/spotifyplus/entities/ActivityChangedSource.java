package com.lenerd46.spotifyplus.entities;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ActivityChangedSource {
    private final List<ActivityChangedListener> listeners = new ArrayList<>();

    public void addListener(ActivityChangedListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ActivityChangedListener listener) {
        listeners.remove(listener);
    }

    public void invoke(View view) {
        for(ActivityChangedListener listener : listeners) {
            listener.onActivityChanged(view);
        }
    }
}

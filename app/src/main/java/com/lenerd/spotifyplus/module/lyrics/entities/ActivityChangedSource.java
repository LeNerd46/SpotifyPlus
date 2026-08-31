package com.lenerd.spotifyplus.module.lyrics.entities;

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

    public void invoke(ScrollInformation info) {
        for(ActivityChangedListener listener : listeners) {
            listener.onActivityChanged(info);
        }
    }
}

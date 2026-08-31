package com.lenerd.spotifyplus.sdk;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;
import org.json.JSONObject;

public abstract class SpotifyPlusComponent<T extends View> {
    public abstract String getName();
    public abstract T createView(Context context, SpotifyPlusContext spotifyPlusContext);
    public final void updateViewProps(View view, JSONObject oldProps, JSONObject newProps) {
        updateProps((T)view, oldProps, newProps);
    }
    public void updateProps(T view, JSONObject oldProps, JSONObject newProps) { }
    public void onDropView(View view) { }
    public ViewGroup getChildren(T view) {
        return view instanceof ViewGroup viewGroup ? viewGroup : null;
    }
}

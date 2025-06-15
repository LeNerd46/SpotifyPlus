package com.lenerd46.spotifyplus.scripting;

import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import com.lenerd46.spotifyplus.scripting.entities.ScriptableSpotifyTrack;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;

public class SpotifyPlayer implements SpotifyPlusApi {
    XC_LoadPackage.LoadPackageParam lpparam;
    private final Scriptable scope;

    public SpotifyPlayer(Scriptable scope, XC_LoadPackage.LoadPackageParam lpparam) {
        this.scope = scope;
        this.lpparam = lpparam;
    }

    @Override
    public void register(Scriptable scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "SpotifyPlayer", Context.javaToJS(this, scope));
    }

    public Object getCurrentTrack() {
        ScriptableSpotifyTrack track = (ScriptableSpotifyTrack) Context.getCurrentContext().newObject(scope, "SpotifyTrack");

        SpotifyTrack spotifyTrack = References.getTrackTitle(lpparam);
        track.setTrack(spotifyTrack);

        return track;
    }
}
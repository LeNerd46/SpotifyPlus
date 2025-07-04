package com.lenerd46.spotifyplus.scripting;

import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import com.lenerd46.spotifyplus.scripting.entities.ScriptableSpotifyTrack;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.luckypray.dexkit.DexKitBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;

public class SpotifyPlayer implements SpotifyPlusApi {
    XC_LoadPackage.LoadPackageParam lpparam;
    DexKitBridge bridge;
    private final Scriptable scope;

    public SpotifyPlayer(Scriptable scope, XC_LoadPackage.LoadPackageParam lpparam, DexKitBridge bridge) {
        this.scope = scope;
        this.lpparam = lpparam;
        this.bridge = bridge;
    }

    @Override
    public void register(Scriptable scope, Context ctx) {
        ScriptableObject.putProperty(scope, "SpotifyPlayer", Context.javaToJS(this, scope));
    }

    @JSFunction
    public Object getCurrentTrack() {
        ScriptableSpotifyTrack track = (ScriptableSpotifyTrack) Context.getCurrentContext().newObject(scope, "SpotifyTrack");

        SpotifyTrack spotifyTrack = References.getTrackTitle(lpparam, bridge);
        track.setTrack(spotifyTrack);

        return track;
    }
}
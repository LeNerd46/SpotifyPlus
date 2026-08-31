package com.lenerd.lyricsnative;

import android.content.Context;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;
import org.json.JSONObject;

public class SyllableLine extends SpotifyPlusComponent<SyllableFlexboxLayout> {
    public SyllableFlexboxLayout view;

    @Override
    public String getName() {
        return "SyllableLine";
    }

    @Override
    public SyllableFlexboxLayout createView(Context context, SpotifyPlusContext spotifyPlusContext) {
        view = new SyllableFlexboxLayout(context);
        view.setFlexDirection(FlexDirection.ROW);
        view.setFlexWrap(FlexWrap.WRAP);

        LyricsView.lines.add(this);

        return view;
    }

    @Override
    public void updateProps(SyllableFlexboxLayout view, JSONObject oldProps, JSONObject newProps) {
        double startTime = newProps.optDouble("startTime");

        if(!Double.isNaN(startTime)) this.view.startTime = startTime;
    }
}

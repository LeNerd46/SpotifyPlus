package com.lenerd.lyricsnative;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;

import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

import org.json.JSONArray;
import org.json.JSONObject;

public class GradientText extends SpotifyPlusComponent<GradientTextView> {
    @Override
    public String getName() {
        return "GradientText";
    }

    @Override
    public GradientTextView createView(Context context, SpotifyPlusContext spotifyPlusContext) {
        return new GradientTextView(context);
    }

    @Override
    public void updateProps(
            GradientTextView view,
            JSONObject oldProps,
            JSONObject newProps) {
        if (newProps.has("text")) {
            view.setText(String.valueOf(newProps.opt("text")));
        }
        if (newProps.has("textSizeSp") || newProps.has("fontSize")) {
            double textSizeSp = newProps.has("textSizeSp") ? newProps.optDouble("textSizeSp", 14)
                    : newProps.optDouble("fontSize", 14);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float) textSizeSp);
        }
        if (newProps.has("textAlign")) {
            applyTextAlignment(view, newProps.optString("textAlign", "left"));
        }
        if (newProps.has("startTime")) {
            view.startTime = newProps.optDouble("startTime", 0);
        }
        if (newProps.has("duration")) {
            view.duration = newProps.optDouble("duration", 0);
        }
        if (newProps.has("startScale")) {
            view.startScale = newProps.optDouble("startScale", 0);
        }
        if (newProps.has("durationScale")) {
            view.durationScale = newProps.optDouble("durationScale", 0);
        }

        int[] colors = parseColors(newProps.optJSONArray("gradientColors"));
        if (colors != null) {
            view.setGradientColors(colors);
        }
        String direction = newProps.optString(
                "gradientDirection",
                "leftToRight");
        view.setLineState(
                "topToBottom".equals(direction)
                        || "bottomToTop".equals(direction));

        double progress = newProps.optDouble("progress", Double.NaN);
        if (!Double.isNaN(progress)) {
            view.setProgress((float) progress * 100f);
        }
    }

    private static int[] parseColors(JSONArray rawColors) {
        if (rawColors == null || rawColors.length() < 2)
            return null;

        int[] colors = new int[rawColors.length()];

        for (int index = 0; index < rawColors.length(); index += 1) {
            Object rawColor = rawColors.opt(index);

            try {
                colors[index] = rawColor instanceof Number ? ((Number) rawColor).intValue()
                        : Color.parseColor(String.valueOf(rawColor));
            } catch (IllegalArgumentException error) {
                return null;
            }
        }
        return colors;
    }

    private static void applyTextAlignment(GradientTextView view, String textAlign) {
        int verticalGravity = view.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        switch (textAlign) {
            case "center":
                view.setGravity(verticalGravity | Gravity.CENTER_HORIZONTAL);
                break;
            case "right":
                view.setGravity(verticalGravity | Gravity.END);
                break;
            case "left":
            default:
                view.setGravity(verticalGravity | Gravity.START);
                break;
        }
    }

    public enum LyricState {
        IDLE,
        ACTIVE,
        SUNG
    }
}

package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.*;

public final class LyricsReportDialog {
    private final LyricsSyncDialog dialog;
    private final RadioGroup reasons;
    private final EditText details;
    private final String trackId;
    private final View lyricsContent;
    private int lyricsVisibility;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String[] reasonValues = {"timings", "lyrics", "missing", "other"};
    private Call submission;
    private boolean sending, disposed;

    public LyricsReportDialog(Context context, String trackId, View songBackground, View lyricsContent) {
        this.lyricsContent = lyricsContent;
        this.trackId = trackId;

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        reasons = new RadioGroup(context);
        String[] labels = {"Timings are inaccurate", "Lyrics are wrong", "Lyrics are missing", "Other"};

        for (String label : labels) {
            RadioButton reason = new RadioButton(context);
            reason.setId(View.generateViewId()); reason.setText(label); reason.setTextColor(Color.WHITE); reason.setTextSize(16);
            reason.setMinimumHeight(Math.round(48 * context.getResources().getDisplayMetrics().density));
            reasons.addView(reason);
        }

        panel.addView(reasons);
        details = new EditText(context);
        details.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        details.setHint("Details (required for Other)"); details.setMinLines(2); details.setMaxLines(4);
        details.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        LyricsSyncDialog.styleInput(details);
        panel.addView(details);

        dialog = new LyricsSyncDialog(context, "Report community lyrics", panel, "Send report", "Cancel", false, this::submit);
        dialog.setSongBackground(songBackground);
        dialog.setOnDismissListener(d -> {
            if (lyricsContent != null) lyricsContent.setVisibility(lyricsVisibility);
            disposed = true;
            if (submission != null) submission.cancel();
        });
    }

    public void show() {
        dialog.show();
        if (lyricsContent != null) {
            lyricsVisibility = lyricsContent.getVisibility();
            lyricsContent.setVisibility(View.INVISIBLE);
        }
    }
    public boolean isShowing() { return dialog.isShowing(); }
    public void dispose() { dialog.dismiss(); }

    private void submit() {
        if (sending || disposed) return;
        int selected = reasons.indexOfChild(reasons.findViewById(reasons.getCheckedRadioButtonId()));
        String explanation = details.getText().toString().trim();

        if (selected < 0) {
            dialog.error("Choose a reason for the report.");
            return;
        }
        if (reasonValues[selected].equals("other") && explanation.isEmpty()) {
            dialog.error("Please describe the issue.");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("reason", reasonValues[selected]); body.addProperty("details", explanation);
        sending = true;
        dialog.setPrimaryState("Sending…", false);
        setEnabledRecursively(reasons, false); details.setEnabled(false);
        Request request = new Request.Builder().url("https://spotifyplus-api.devon-shoutz.workers.dev/api/lyrics/" + trackId + "/reports")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString())).build();
        submission = new OkHttpClient().newCall(request);
        submission.enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                handler.post(() -> failed("Could not send the report. Check your connection and retry."));
            }

            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        handler.post(() -> {
                            if (disposed) return;
                            Toast.makeText(dialog.getContext(), "Report sent. Thank you!", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        });
                    } else {
                        String message = "Could not send the report (" + r.code() + "). Please retry.";
                        if (r.body() != null) try {
                            JsonObject error = com.google.gson.JsonParser.parseString(r.body().string()).getAsJsonObject();
                            if (error.has("error")) message = error.get("error").getAsString();
                        } catch (Exception ignored) {
                        }

                        String detail = message;
                        handler.post(() -> failed(detail));
                    }
                }
            }
        });
    }

    private void failed(String message) {
        if (disposed) return;
        sending = false;
        dialog.setPrimaryState("Send report", true);
        setEnabledRecursively(reasons, true); details.setEnabled(true);
        dialog.error(message);
    }

    private void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursively(group.getChildAt(i), enabled);
        }
    }
}

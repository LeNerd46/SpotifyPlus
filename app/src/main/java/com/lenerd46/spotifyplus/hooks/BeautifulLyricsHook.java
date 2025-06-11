package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import com.lenerd46.spotifyplus.entities.LyricUtilities;
import com.lenerd46.spotifyplus.entities.SyllableVocals;
import com.lenerd46.spotifyplus.entities.SyncableVocals;
import com.lenerd46.spotifyplus.entities.interludes.InterludeVisual;
import com.lenerd46.spotifyplus.entities.lyrics.*;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BeautifulLyricsHook extends SpotifyHook {

    private static Map<FlexboxLayout, List<SyncableVocals>> vocalGroups;
    private volatile  boolean stop = false;

    @Override
    protected void hook() {
        XposedHelpers.findAndHookMethod("com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity", lpparm.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    XposedBridge.log("[SpotifyPlus] Loading Beautiful Lyrics ✨");

                    final Activity activity = (Activity) param.thisObject;

                    activity.runOnUiThread(() -> {
                        try {
                            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();

                            FrameLayout overlay = new FrameLayout(activity);
                            overlay.setLayoutParams(new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                            overlay.setBackgroundColor(0xFF000000);

                            // Add Beautiful Lyrics UI!
                            // TextView myText = new TextView(activity);
                            // myText.setText("🎉 My custom UI!");
                            // myText.setTextColor(0xFF000000);
                            // myText.setTextSize(28f);

                            SpotifyTrack track = References.getTrackTitle(lpparm);
                            if(track == null) { XposedBridge.log("[SpotifyPlus] Failed to get current track"); return; }

                            XposedBridge.log("[SpotifyPlus] Title: " + track.title);
                            XposedBridge.log("[SpotifyPlus] Artist: " + track.artist);
                            XposedBridge.log("[SpotifyPlus] Album: " + track.album);
                            XposedBridge.log("[SpotifyPlus] Position: " + track.position / 1000);

                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                            params.gravity = Gravity.CENTER;
                            // myText.setLayoutParams(params);

                            ScrollView scrollView = new ScrollView(activity);
                            scrollView.setLayoutParams(params);

                            LinearLayout layout = new LinearLayout(activity);
                            layout.setOrientation(LinearLayout.VERTICAL);
                            FrameLayout.LayoutParams matchParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                            layout.setLayoutParams(matchParams);
                            scrollView.addView(layout);

                            overlay.addView(scrollView);

                            // overlay.addView(myText);
                            root.addView(overlay);
                            XposedBridge.log("[SpotifyPlus] Loaded Beautiful Lyrics UI");

                            RenderLyrics(activity, track, layout);
                        }
                        catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    });
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        });
    }

    private void RenderLyrics(Activity activity, SpotifyTrack track, LinearLayout lyricsContainer) {
        List<Double> vocalGroupStartTimes = new ArrayList<>();
        List<View> lines = new ArrayList<>();
        boolean staticLyrics = false;
        vocalGroups = new HashMap<>();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            String finalContent = "";

            try {
                String id = track.uri.split(":")[2];
                URL url = new URL("https://beautiful-lyrics.socalifornian.live/lyrics/" + id);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                connection.setRequestProperty("Authorization", "Bearer BQDLdaKMJYKr8LRep_MvqrQfV72ty55wFQ4oXYuPM9AaPVcEOjqbLh3UAcSzQpOckxn4cfWn9hfDFJ-1W0scDjl214UjytYJYG-fOsqNOYvWbttLWLegqW9o8EoIZecBZbqVSeaa9rUI7qQg4has3p2WD80daDugR2KNU89EVefoFySCVPYSPk9eBKUFgVmOMUCYr8Q7TOj05Jb5Mn2gbKfEkPXOODXjG60pspeOC4jxScu9-Xay4r-ks7bZwKsinu6kvYnUGWbhe-ST2PFmebcDwJxS");

                int responseCode = connection.getResponseCode();
                if(responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    finalContent = response.toString();
                }
            } catch(Exception e) {
                XposedBridge.log(e);
            }

            String content = finalContent;
            handler.post(() -> {
                JsonObject jsonObject = new JsonParser().parseString(content).getAsJsonObject();
                String type = jsonObject.get("Type").getAsString();

                if(type.equals("Syllable")) {
                    Gson gson = new Gson();

                    SyllableSyncedLyrics providerLyrics = gson.fromJson(content, SyllableSyncedLyrics.class);

                    ProviderLyrics providedLyrics = new ProviderLyrics();
                    providedLyrics.syllableLyrics = providerLyrics;

                    TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providedLyrics);
                    SyllableSyncedLyrics lyrics = transformedLyrics.lyrics.syllableLyrics;

                    int i = 0;
                    for (var vocalGroup : lyrics.content) {
                        if(vocalGroup instanceof Interlude) {
                            Interlude interlude = (Interlude) vocalGroup;
                            FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);
                            if(interlude.time.startTime == 0) {
                                FlexboxLayout.MarginLayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
                                float scale = activity.getResources().getDisplayMetrics().density;
                                params.setMargins(0, (int)(10 * scale + 0.5f), 0, 0);
                                vocalGroupContainer.setLayoutParams(params);
                            }

                            List<SyncableVocals> visual = new ArrayList<>();
                            visual.add(new InterludeVisual(vocalGroupContainer, interlude));
                            vocalGroups.put(vocalGroupContainer, visual);

                            vocalGroupStartTimes.add(interlude.time.startTime);

                            // Check opposite alignment

                            lines.add(vocalGroupContainer);
                        } else {
                            if(vocalGroup instanceof SyllableVocalSet) {
                                SyllableVocalSet set = (SyllableVocalSet) vocalGroup;

                                LinearLayout topGroup = new LinearLayout(activity);
                                topGroup.setOrientation(LinearLayout.VERTICAL);

                                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);

                                topGroup.addView(vocalGroupContainer);
                                lines.add(topGroup);

                                List<SyllableVocals> vocals = new ArrayList<>();
                                double startTime = set.lead.startTime;

                                // Event for auto scrolling
                                SyllableVocals sv = new SyllableVocals(vocalGroupContainer, set.lead.syllables, false, false, set.oppositeAligned, activity);

                                vocals.add(sv);

                                if(set.background != null && !set.background.isEmpty()) {
                                    FlexboxLayout backgroundVocalGroupContainer = new FlexboxLayout(activity);
                                    topGroup.addView(backgroundVocalGroupContainer);

                                    for(var backgroundVocal : set.background) {
                                        startTime = Math.min(startTime, backgroundVocal.startTime);
                                        vocals.add(new SyllableVocals(backgroundVocalGroupContainer, backgroundVocal.syllables, true, false, set.oppositeAligned, activity));
                                    }
                                }

                                List<SyncableVocals> syncedVocals = new ArrayList<>(vocals);
                                vocalGroups.put(vocalGroupContainer, syncedVocals);
                                vocalGroupStartTimes.add(startTime);
                            }
                        }

                        i++;
                    }
                } else if(type.equals("Line")) {

                } else if(type.equals("Static")) {

                }

                XposedBridge.log("[SpotifyPlus] Loading lyrics with " + lines.size() + " lines");

                // lyricsContainer.removeAllViews();
                lines.forEach(lyricsContainer::addView);

                XposedBridge.log("[SpotifyPlus] Finished loading lyrics!");

                if(staticLyrics) { return; }

                update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);

                updateProgress(track.position, System.currentTimeMillis(), vocalGroups);
            });
        });
    }

    private void update(Map<FlexboxLayout, List<SyncableVocals>> vocalGroups, double timestamp, double deltaTime, boolean skipped) {
        try {
            for(var vocalGroup : new ArrayList<>(vocalGroups.values())) {
                for(var vocal : vocalGroup) {
                    vocal.animate(timestamp, deltaTime, false);
                }
            }
        } catch(Exception e) {
            XposedBridge.log(e);
        }
    }

    private static long lastUpdatedAt = 0;
    private static double lastTimestamp = 0;

    private void updateProgress(long initialPosition, double startedSyncAt, Map<FlexboxLayout, List<SyncableVocals>> vocalGroups) {
        Thread thread = new Thread(() -> {
            try {
                int[] syncTimings = { 50, 100, 150, 750 };
                int syncIndex = 0;
                long nextSyncAt = syncTimings[0];

                while(!stop) {
                    long updatedAt = System.currentTimeMillis();

                    // If the song is currently playing
                    if(updatedAt > startedSyncAt + nextSyncAt) {
                        // Get the current position from Spotify
                    }

                    double syncedTimestamp = (initialPosition + (updatedAt - startedSyncAt)) / 1000d;
                    double deltaTime = (updatedAt - lastUpdatedAt) / 1000d;

                    update(vocalGroups, syncedTimestamp, deltaTime, Math.abs(initialPosition / 1000d - lastTimestamp) > 0.8d);
                    lastTimestamp = syncedTimestamp;

                    lastUpdatedAt = updatedAt;
                    Thread.sleep(16);
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        });

        thread.start();
    }
}

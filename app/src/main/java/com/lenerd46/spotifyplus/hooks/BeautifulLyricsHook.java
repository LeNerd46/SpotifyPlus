package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import com.lenerd46.spotifyplus.beautifullyrics.entities.AnimatedBackgroundView;
import com.lenerd46.spotifyplus.beautifullyrics.entities.LyricUtilities;
import com.lenerd46.spotifyplus.beautifullyrics.entities.SyllableVocals;
import com.lenerd46.spotifyplus.beautifullyrics.entities.SyncableVocals;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.*;
import com.lenerd46.spotifyplus.beautifullyrics.entities.interludes.InterludeVisual;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
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
    private Thread mainLoop;

    @Override
    protected void hook() {
        XposedHelpers.findAndHookMethod("com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity", lpparm.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    XposedBridge.log("[SpotifyPlus] Loading Beautiful Lyrics ✨");

                    final Activity activity = (Activity) param.thisObject;

                    stop = false;
                    lastUpdatedAt = 0;
                    lastTimestamp = 0;

                    activity.runOnUiThread(() -> {
                        try {
                            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);

                            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();

                            FrameLayout overlay = new FrameLayout(activity);
                            overlay.setLayoutParams(new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                            overlay.setClipToPadding(false);
                            overlay.setClipChildren(false);

                            SpotifyTrack track = References.getTrackTitle(lpparm);
                            if(track == null) { XposedBridge.log("[SpotifyPlus] Failed to get current track"); return; }

                            XposedBridge.log("[SpotifyPlus] Title: " + track.title);
                            XposedBridge.log("[SpotifyPlus] Artist: " + track.artist);
                            XposedBridge.log("[SpotifyPlus] Album: " + track.album);
                            XposedBridge.log("[SpotifyPlus] Position: " + track.position / 1000);
                            XposedBridge.log("[SpotifyPlus] Color: " + track.color);
                            // overlay.setBackgroundColor(Color.parseColor("#" + track.color));
                            overlay.setBackgroundColor(Color.TRANSPARENT);

                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                            params.gravity = Gravity.CENTER;
                            // myText.setLayoutParams(params);

                            ScrollView scrollView = new ScrollView(activity);
                            scrollView.setLayoutParams(params);
                            scrollView.setClipToPadding(false);
                            scrollView.setClipChildren(false);

                            LinearLayout layout = new LinearLayout(activity);
                            layout.setOrientation(LinearLayout.VERTICAL);
                            FrameLayout.LayoutParams matchParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                            layout.setLayoutParams(matchParams);
                            layout.setClipToPadding(false);
                            layout.setClipChildren(false);
                            scrollView.addView(layout);

                            overlay.addView(scrollView, -2);
                            FrameLayout blackBox = new FrameLayout(activity);
                            blackBox.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                            blackBox.setBackgroundColor(Color.BLACK);
                            blackBox.setAlpha(0.2f);

                            overlay.addView(blackBox, -1);

                            // overlay.addView(myText);
                            root.addView(overlay, -2);
                            XposedBridge.log("[SpotifyPlus] Loaded Beautiful Lyrics UI");

                            RenderLyrics(activity, track, layout, overlay);
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

        XposedHelpers.findAndHookMethod("com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity", lpparm.classLoader, "onPause", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                stop = true;

                if(mainLoop != null && mainLoop.isAlive()) {
                    mainLoop.interrupt();
                    mainLoop = null;
                }

                XposedBridge.log("[SpotifyPlus] Stopped!");
            }
        });

        XposedHelpers.findAndHookMethod("p.lrh", lpparm.classLoader, "getState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                References.playerStateWrapper = new WeakReference<>(param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod("com.spotify.player.model.AutoValue_PlayerState$Builder", lpparm.classLoader, "build", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object state = param.getResult();
                References.playerState = new WeakReference<>(state);
                References.notifyPlayerStateChanged(state);
            }
        });
    }

    private void RenderLyrics(Activity activity, SpotifyTrack track, LinearLayout lyricsContainer, FrameLayout root) {
        List<Double> vocalGroupStartTimes = new ArrayList<>();
        List<View> lines = new ArrayList<>();
        vocalGroups = new HashMap<>();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            String finalContent = "";

            try {
                Bitmap albumArt = getBitmap(track.imageId);

                if(albumArt != null) {
                    AnimatedBackgroundView background = new AnimatedBackgroundView(activity, albumArt);
                    background.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

                    activity.runOnUiThread(() -> root.addView(background, 0));
                }
                SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
                boolean sendAccessToken = prefs.getBoolean("sendAccessToken", true);

                String id = track.uri.split(":")[2];
                URL url = new URL("https://beautiful-lyrics.socalifornian.live/lyrics/" + id);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                String token = References.accessToken.get();
                connection.setRequestProperty("Authorization", "Bearer " + (((token != null && !token.isEmpty()) && sendAccessToken) ? token : "BQDLdaKMJYKr8LRep_MvqrQfV72ty65wFQ4oXYuPM9AaPVcEOjqbLh3UAcSzQpOckxn4cfWn9hfDFJ-1W0scDjl214UjytYJYG-fOsqNOYvWbttLWLegqW9o8EoIZecBZbqVSeaa9rUI7qQg4has3p2WD80daDugR2KNU89EVefoFySCVPYSPk9eBKUFgVmOMUCYr8Q7TOj05Jb5Mn2gbKfEkPXOODXjG60pspeOC4jxScu9-Xay4r-ks7bZwKsinu6kvYnUGWbhe-ST2PFmebcDwJxS"));

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
                boolean isStatic = false;

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
                            RelativeLayout topGroup = new RelativeLayout(activity);
                            topGroup.setClipToPadding(false);
                            topGroup.setClipChildren(false);

                            FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);
                            vocalGroupContainer.setClipToPadding(false);
                            vocalGroupContainer.setClipChildren(false);

                            if(interlude.time.startTime == 0) {
                                RelativeLayout.MarginLayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                params.setMargins(dpToPx(15, activity), dpToPx(40, activity), 0, 0);
                                vocalGroupContainer.setLayoutParams(params);
                            } else {
                                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                params.setMargins(dpToPx(15, activity), dpToPx(20, activity), 0, 0);
                                vocalGroupContainer.setLayoutParams(params);

                                if(i != lyrics.content.size() - 1 && ((SyllableVocalSet)lyrics.content.get(i - 1)).oppositeAligned && ((SyllableVocalSet)lyrics.content.get(i + 1)).oppositeAligned) {
                                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                                    params.setMargins(0, dpToPx(20, activity), dpToPx(15, activity), 0);
                                }
                            }

                            List<SyncableVocals> visual = new ArrayList<>();
                            visual.add(new InterludeVisual(vocalGroupContainer, interlude, activity));
                            vocalGroups.put(vocalGroupContainer, visual);

                            vocalGroupStartTimes.add(interlude.time.startTime);

                            // Check opposite alignment

                            topGroup.addView(vocalGroupContainer);
                            lines.add(topGroup);
                        } else {
                            if(vocalGroup instanceof SyllableVocalSet) {
                                SyllableVocalSet set = (SyllableVocalSet) vocalGroup;

                                RelativeLayout evenMoreTopGroup = new RelativeLayout(activity);
                                evenMoreTopGroup.setClipToPadding(false);
                                evenMoreTopGroup.setClipChildren(false);

                                LinearLayout topGroup = new LinearLayout(activity);
                                topGroup.setOrientation(LinearLayout.VERTICAL);
                                topGroup.setClipToPadding(false);
                                topGroup.setClipChildren(false);
                                RelativeLayout.LayoutParams parms = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                parms.setMargins(dpToPx(15, activity), dpToPx(20, activity), dpToPx(15, activity), 0);

                                if(set.oppositeAligned) {
                                    parms.addRule(RelativeLayout.ALIGN_PARENT_END);
                                    parms.setMargins(dpToPx(15, activity), dpToPx(20, activity), dpToPx(15, activity), 0);
                                }

                                topGroup.setLayoutParams(parms);

                                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);
                                vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                                vocalGroupContainer.setClipToPadding(false);
                                vocalGroupContainer.setClipChildren(false);
                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                vocalGroupContainer.setLayoutParams(params);

                                topGroup.addView(vocalGroupContainer);
                                evenMoreTopGroup.addView(topGroup);
                                lines.add(evenMoreTopGroup);

                                List<SyllableVocals> vocals = new ArrayList<>();
                                double startTime = set.lead.startTime;

                                // Event for auto scrolling
                                SyllableVocals sv = new SyllableVocals(vocalGroupContainer, set.lead.syllables, false, false, set.oppositeAligned, activity);
                                sv.activityChanged.addListener(view -> {
                                    View lineView = (View) view.getParent().getParent();
                                    ScrollView scrollView = (ScrollView) lyricsContainer.getParent();
                                    scrollView.post(() -> {
                                        int scrollY = lineView.getTop() - (scrollView.getHeight() / 2) + (lineView.getHeight() / 2);
                                        scrollView.smoothScrollTo(0, Math.max(scrollY, 0));
                                    });
                                });

                                vocals.add(sv);

                                if(set.background != null && !set.background.isEmpty()) {
                                    FlexboxLayout backgroundVocalGroupContainer = new FlexboxLayout(activity);
                                    backgroundVocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                                    backgroundVocalGroupContainer.setClipToPadding(false);
                                    backgroundVocalGroupContainer.setClipChildren(false);
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
                    Gson gson = new Gson();

                    LineSyncedLyrics providerLyrics = gson.fromJson(content, LineSyncedLyrics.class);

                    ProviderLyrics providerLyricsThing = new ProviderLyrics();
                    providerLyricsThing.lineLyrics = providerLyrics;
                    TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyricsThing);

                    LineSyncedLyrics lyrics = transformedLyrics.lyrics.lineLyrics;

                    int i = 0;
                    for(var vocalGroup : lyrics.content) {
                        if(vocalGroup instanceof Interlude) {
                            Interlude interlude = (Interlude) vocalGroup;

                            RelativeLayout topGroup = new RelativeLayout(activity);
                            topGroup.setClipToPadding(false);
                            topGroup.setClipChildren(false);

                            FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);
                            vocalGroupContainer.setClipToPadding(false);
                            vocalGroupContainer.setClipChildren(false);

                            if(interlude.time.startTime == 0) {
                                RelativeLayout.MarginLayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                params.setMargins(dpToPx(15, activity), dpToPx(40, activity), 0, 0);
                                vocalGroupContainer.setLayoutParams(params);
                            } else {
                                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                params.setMargins(dpToPx(15, activity), dpToPx(20, activity), 0, 0);
                                vocalGroupContainer.setLayoutParams(params);

                                if(i != lyrics.content.size() - 1 && ((LineVocal)lyrics.content.get(i - 1)).oppositeAligned && ((LineVocal)lyrics.content.get(i + 1)).oppositeAligned) {
                                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                                    params.setMargins(0, dpToPx(20, activity), dpToPx(15, activity), 0);
                                }
                            }

                            List<SyncableVocals> visual = new ArrayList<>();
                            visual.add(new InterludeVisual(vocalGroupContainer, interlude, activity));
                            vocalGroups.put(vocalGroupContainer, visual);

                            vocalGroupStartTimes.add(interlude.time.startTime);

                            topGroup.addView(vocalGroupContainer);
                            lines.add(topGroup);
                        } else if (vocalGroup instanceof LineVocal){
                            LineVocal vocal = (LineVocal) vocalGroup;

                            RelativeLayout topGroup = new RelativeLayout(activity);
                            topGroup.setClipToPadding(false);
                            topGroup.setClipChildren(false);

                            FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);
                            vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                            vocalGroupContainer.setClipToPadding(false);
                            vocalGroupContainer.setClipChildren(false);
                            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                            params.setMargins(dpToPx(15, activity), dpToPx(20, activity), dpToPx(15, activity), 0);

                            if(vocal.oppositeAligned) {
                                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                            }

                            vocalGroupContainer.setLayoutParams(params);
                            topGroup.addView(vocalGroupContainer);

                            LineVocals lv = new LineVocals(vocalGroupContainer, vocal, false, activity);
                            lv.activityChanged.addListener(view -> {
                                View lineView = (View) view.getParent();
                                ScrollView scrollView = (ScrollView) lyricsContainer.getParent();
                                scrollView.post(() -> {
                                    int scrollY = lineView.getTop() - (scrollView.getHeight() / 2) + (lineView.getHeight() / 2);
                                    scrollView.smoothScrollTo(0, Math.max(scrollY, 0));
                                });
                            });

                            vocalGroups.put(vocalGroupContainer, List.of(lv));
                            vocalGroupStartTimes.add(lv.startTime);

                            lines.add(topGroup);
                        }
                        i++;
                    }
                } else if(type.equals("Static")) {
                    Gson gson = new Gson();

                    StaticSyncedLyrics providerLyrics = gson.fromJson(content, StaticSyncedLyrics.class);

                    ProviderLyrics providerLyricsThing = new ProviderLyrics();
                    providerLyricsThing.staticLyrics = providerLyrics;

                    TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyricsThing);
                    StaticSyncedLyrics lyrics = transformedLyrics.lyrics.staticLyrics;
                    isStatic = true;

                    for(var line : lyrics.lines) {
                        FlexboxLayout layout = new FlexboxLayout(activity);
                        layout.setFlexWrap(FlexWrap.WRAP);

                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        params.setMargins(dpToPx(15, activity), dpToPx(20, activity), dpToPx(15, activity), 0);
                        layout.setLayoutParams(params);

                        TextView text = new TextView(activity);
                        text.setText(line.text);

                        text.setTextColor(Color.WHITE);
                        text.setTextSize(26f);

                        layout.addView(text);
                        lines.add(layout);
                    }
                }

                XposedBridge.log("[SpotifyPlus] Loading lyrics with " + lines.size() + " lines");

                // lyricsContainer.removeAllViews();
                lines.forEach(lyricsContainer::addView);

                XposedBridge.log("[SpotifyPlus] Finished loading lyrics!");

                if(isStatic) { return; }

                update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);

                updateProgress(track.position, System.currentTimeMillis(), vocalGroups);
            });
        });
    }

    private void update(Map<FlexboxLayout, List<SyncableVocals>> vocalGroups, double timestamp, double deltaTime, boolean skipped) {
        try {
            for(var vocalGroup : new ArrayList<>(vocalGroups.values())) {
                for(var vocal : vocalGroup) {
                    vocal.animate(timestamp, deltaTime, skipped);
                }
            }
        } catch(Exception e) {
            XposedBridge.log(e);
        }
    }

    private long lastUpdatedAt = 0;
    private double lastTimestamp = 0;

    private void updateProgress(long initialPositionS, double startedSyncAtS, Map<FlexboxLayout, List<SyncableVocals>> vocalGroups) {
        mainLoop = new Thread(() -> {
            try {
                int[] syncTimings = { 50, 100, 150, 750 };
                int syncIndex = 0;
                long nextSyncAt = syncTimings[0];
                long initialPosition = initialPositionS;
                double startedSyncAt = startedSyncAtS;

                while(!stop) {
                    long updatedAt = System.currentTimeMillis();

                    // If the song is currently playing
                    if(updatedAt > startedSyncAt + nextSyncAt) {
                        // Get the current position from Spotify
                        long position = References.getCurrentPlaybackPosition();
                        if(position != -1) {
                            initialPosition = position;
                            startedSyncAt = updatedAt;

                            syncIndex++;

                            if(syncIndex < syncTimings.length) {
                                nextSyncAt = syncTimings[syncIndex];
                            } else {
                                nextSyncAt = 33;
                            }
                        }
                    }

                    double syncedTimestamp = (initialPosition + (updatedAt - startedSyncAt)) / 1000d;
                    double deltaTime = (updatedAt - lastUpdatedAt) / 1000d;

                    update(vocalGroups, syncedTimestamp, deltaTime, Math.abs(syncedTimestamp - lastTimestamp) > 0.8d);
                    lastTimestamp = syncedTimestamp;

                    lastUpdatedAt = updatedAt;
                    Thread.sleep(16);
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        });

        mainLoop.start();
    }

    private Bitmap getBitmap(String id) {
        HttpURLConnection connection = null;
        InputStream input = null;

        try {
            URL url = new URL("https://i.scdn.co/image/" + id);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch(IOException e) {
            XposedBridge.log(e);
            return null;
        } finally {
            if(input != null) {
                try { input.close(); } catch(IOException e) {}
            }
            if(connection != null) {
                connection.disconnect();
            }
        }
    }

    int dpToPx(int dp, Activity activity) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                activity.getResources().getDisplayMetrics()
        );
    }
}
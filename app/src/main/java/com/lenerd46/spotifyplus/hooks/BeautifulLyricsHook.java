package com.lenerd46.spotifyplus.hooks;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XModuleResources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import com.lenerd46.spotifyplus.beautifullyrics.entities.*;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.*;
import com.lenerd46.spotifyplus.beautifullyrics.entities.interludes.InterludeVisual;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class BeautifulLyricsHook extends SpotifyHook {

    private static Map<FlexboxLayout, List<SyncableVocals>> vocalGroups;
    private volatile  boolean stop = false;
    private Thread mainLoop;
    private Handler closeButtonHandler = new Handler(Looper.getMainLooper());
    private Runnable closeButtonRunnable;
    private ImageView closeButton;
    private LinearLayout rightContainer;
    private ImageView syncButton;
    private Constructor<?> ctor = null;
    private Object seekInstance = null;
    private LineSyncedLyrics lineLyrics = null;

    private static final float MAX_SCALE = 1.008f;
    private static final float MIN_SCALE = 1.0f;
    private static final float SCROLL_POSITION_RATIO = 0.4f;
    private static final long ANIMATION_DURATION = 400;
    private static final long SCROLL_ANIMATION_DURATION = 400;

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

                            XModuleResources res = References.modResources;

                            GridLayout grid = new GridLayout(activity);
                            grid.setRowCount(2);
                            grid.setColumnCount(1);
                            grid.setElevation(10f);
                            grid.setClickable(true);
                            grid.setFocusable(true);

                            SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
                            if(track == null) { XposedBridge.log("[SpotifyPlus] Failed to get current track"); return; }

                            XposedBridge.log("[SpotifyPlus] Title: " + track.title);
                            XposedBridge.log("[SpotifyPlus] Artist: " + track.artist);
                            XposedBridge.log("[SpotifyPlus] Album: " + track.album);
                            XposedBridge.log("[SpotifyPlus] Position: " + track.position / 1000);
                            XposedBridge.log("[SpotifyPlus] Color: " + track.color);
                            // overlay.setBackgroundColor(Color.parseColor("#" + track.color));
//                            overlay.setBackgroundColor(Color.TRANSPARENT);

                            // Header

                            FrameLayout headerContainer = new FrameLayout(activity);
                            GridLayout.LayoutParams headerParams = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(0));
                            headerParams.width = GridLayout.LayoutParams.MATCH_PARENT;
                            headerParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
                            headerContainer.setLayoutParams(headerParams);

                            LinearLayout header = new LinearLayout(activity);
                            header.setOrientation(LinearLayout.HORIZONTAL);
                            header.setGravity(Gravity.CENTER_VERTICAL);
                            header.setPadding(dpToPx(22, activity), dpToPx(32, activity), dpToPx(22, activity), dpToPx(18, activity));

                            FrameLayout.LayoutParams headerParms = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                            header.setLayoutParams(headerParms);

                            ImageView cover = new ImageView(activity);
                            int coverSize = dpToPx(56, activity);
                            LinearLayout.LayoutParams coverParms = new LinearLayout.LayoutParams(coverSize, coverSize);
                            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            cover.setLayoutParams(coverParms);

                            LinearLayout titleAndArtist = new LinearLayout(activity);
                            titleAndArtist.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            titleAndArtist.setOrientation(LinearLayout.VERTICAL);
                            titleAndArtist.setGravity(Gravity.CENTER_VERTICAL);
                            titleAndArtist.setPadding(dpToPx(12, activity), 0, 0, 0);

                            TextView titleText = new TextView(activity);
                            titleText.setText(track.title);
                            titleText.setTextColor(Color.WHITE);
                            titleText.setTextSize(20f);

                            TextView artistText = new TextView(activity);
                            artistText.setText(track.artist);
                            artistText.setTextColor(Color.LTGRAY);
                            artistText.setTextSize(16f);

                            titleAndArtist.addView(titleText);
                            titleAndArtist.addView(artistText);

                            header.addView(cover);
                            header.addView(titleAndArtist);

                            rightContainer = new LinearLayout(activity);
                            rightContainer.setOrientation(LinearLayout.HORIZONTAL);
                            rightContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                            FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
                            rightParams.setMargins(0, dpToPx(8, activity), dpToPx(22, activity), 0);
                            rightContainer.setLayoutParams(rightParams);
                            rightContainer.setAlpha(0f);

                            closeButton = new ImageView(activity);
                            int closeSize = dpToPx(36, activity);
                            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(closeSize, closeSize);
                            closeButton.setLayoutParams(closeParams);
                            closeButton.setImageDrawable(createChevronDownIcon(activity));
                            closeButton.setClickable(true);
                            closeButton.setFocusable(true);

                            closeButton.setOnClickListener(v -> {
                                activity.onBackPressed();
                            });

                            syncButton = new ImageView(activity);
                            int syncSize = dpToPx(24, activity);
                            LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(syncSize, syncSize);
                            syncParams.setMargins(0, 0, dpToPx(8, activity), 0);
                            syncButton.setLayoutParams(syncParams);
                            syncButton.setImageDrawable(ResourcesCompat.getDrawable(res, R.drawable.add_circle, null));
                            syncButton.setClickable(true);
                            syncButton.setFocusable(true);

                            rightContainer.addView(syncButton);
                            rightContainer.addView(closeButton);

                            headerContainer.addView(header);
                            headerContainer.addView(rightContainer);

                            // Lyrics Content

                            ScrollView scrollView = new ScrollView(activity);
                            GridLayout.LayoutParams scrollParams = new GridLayout.LayoutParams(GridLayout.spec(1), GridLayout.spec(0));
                            scrollParams.width = GridLayout.LayoutParams.MATCH_PARENT;
                            scrollParams.height = GridLayout.LayoutParams.MATCH_PARENT;

                            scrollView.setLayoutParams(scrollParams);
                            scrollView.setClipToPadding(false);
                            scrollView.setClipChildren(false);

                            LinearLayout layout = new LinearLayout(activity);
                            layout.setOrientation(LinearLayout.VERTICAL);
                            ScrollView.LayoutParams matchParams = new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT);
                            layout.setLayoutParams(matchParams);
                            layout.setClipToPadding(false);
                            layout.setClipChildren(false);
                            scrollView.addView(layout);

                            FrameLayout blackBox = new FrameLayout(activity);
                            GridLayout.LayoutParams blackParams = new GridLayout.LayoutParams(GridLayout.spec(0, 2), GridLayout.spec(0));
                            blackParams.width = GridLayout.LayoutParams.MATCH_PARENT;
                            blackParams.height = GridLayout.LayoutParams.MATCH_PARENT;
                            blackBox.setLayoutParams(blackParams);
                            blackBox.setBackgroundColor(Color.BLACK);
                            blackBox.setAlpha(0.2f);

                            closeButtonRunnable = () -> rightContainer.animate().alpha(0f).setDuration(300).start();
                            grid.setOnTouchListener((v, event) -> {
                                closeButtonHandler.removeCallbacksAndMessages(closeButtonRunnable);

                                rightContainer.animate().alpha(0.8f).setDuration(200).withEndAction(() -> {
                                    closeButtonHandler.postDelayed(closeButtonRunnable, 3000);
                                }).start();

                                return false;
                            });

                            syncButton.setOnClickListener(v -> {
                                LayoutInflater inflater = LayoutInflater.from(activity);
                                View newView = inflater.inflate(res.getLayout(R.layout.editor_layout), (ViewGroup) activity.getWindow().getDecorView(), false);
                                grid.removeView(scrollView);
                                grid.addView(newView);

                                LinearLayout lyricsContainer = newView.findViewById(res.getIdentifier("lyricsContainer", "id", "com.lenerd46.spotifyplus"));
                                ScrollView scroller = newView.findViewById(res.getIdentifier("scroller", "id", "com.lenerd46.spotifyplus"));

                                View controls = inflater.inflate(res.getLayout(R.layout.editor_controls), (ViewGroup) activity.getWindow().getDecorView(), false);
                                grid.addView(controls);

                                RenderSyncLyrics(activity, lyricsContainer, scroller, track.uri.split(":")[2]);
                            });

                            SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

                            if(prefs.getBoolean("lyric_enable_background", true)) {
                                grid.addView(blackBox);
                            }

                            grid.addView(headerContainer);
                            grid.addView(scrollView);
                            root.addView(grid, -2);
                            XposedBridge.log("[SpotifyPlus] Loaded Beautiful Lyrics UI");

                            RenderLyrics(activity, track, layout, root, cover);
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

                lyricsScrollAnimator.cancel();
                vocalGroups = null;

                XposedBridge.log("[SpotifyPlus] Stopped!");
            }
        });

        try {
            var whateverThisClassEvenDoes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).fields(FieldsMatcher.create()
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL))
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(String.class))
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(ArrayList.class))
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object.class))
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Bundle.class))
            )));

            Method getStateMethod = bridge.findMethod(FindMethod.create().searchInClass(whateverThisClassEvenDoes).matcher(MethodMatcher.create().name("getState"))).get(0).getMethodInstance(lpparm.classLoader);
            XposedBridge.hookMethod(getStateMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    References.playerStateWrapper = new WeakReference<>(param.thisObject);
                }
            });
        } catch(Exception e) {
            XposedBridge.log(e);
        }


        XposedHelpers.findAndHookMethod("com.spotify.player.model.AutoValue_PlayerState$Builder", lpparm.classLoader, "build", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object state = param.getResult();
                References.playerState = new WeakReference<>(state);
                References.notifyPlayerStateChanged(state);
            }
        });

        try {
            Class<?> hzc = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify.player.esperanto.proto.ContextPlayer", "SetOptions"))).get(0).getInstance(lpparm.classLoader);
            Class<?> seek = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).methodCount(3).fields(FieldsMatcher.create()
                            .count(3)
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(hzc))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(boolean.class))
                    ))).get(0).getInstance(lpparm.classLoader);

            XposedBridge.hookAllConstructors(seek, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    seekInstance = param.thisObject;
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private void RenderLyrics(Activity activity, SpotifyTrack track, LinearLayout lyricsContainer, ViewGroup root, ImageView albumView) {
        List<Double> vocalGroupStartTimes = new ArrayList<>();
        List<View> lines = new ArrayList<>();
        vocalGroups = new HashMap<>();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            String finalContent = "";

            try {
                SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

                Bitmap albumArt = getBitmap(track.imageId);
                albumView.post(() -> albumView.setImageBitmap(albumArt));

                if(prefs.getBoolean("lyric_enable_background", true)) {
                    if(albumArt != null) { // Clearly I don't seem to care if it's null or not (line 290)
                        AnimatedBackgroundView background = new AnimatedBackgroundView(activity, albumArt, root);
                        background.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

                        activity.runOnUiThread(() -> root.addView(background));
                    }
                } else {
                    FrameLayout background = new FrameLayout(activity);
                    background.setBackgroundColor(Color.parseColor("#" + track.color));

                    activity.runOnUiThread(() -> root.addView(background));
                }

                boolean sendAccessToken = prefs.getBoolean("sendAccessToken", true);

                String id = track.uri.split(":")[2];
                URL url = new URL("https://beautiful-lyrics.socalifornian.live/lyrics/" + id);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                String token = References.accessToken.get();
                connection.setRequestProperty("Authorization", "Bearer " + (((token != null && !token.isEmpty()) && sendAccessToken) ? token : "0"));

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

                try {
                    Class<?> requestClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).fieldCount(1).addField(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(long.class)).methods(MethodsMatcher.create()
                                            .count(6)
                                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).paramCount(13))
                                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(void.class).paramCount(12))
                                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).addParamType(Object.class))
                            ))).get(0).getInstance(lpparm.classLoader);

                    ctor = requestClass.getConstructor(long.class);
                    ctor.setAccessible(true);
                } catch(Exception e) {
                    XposedBridge.log(e);
                }

                if(type.equals("Syllable")) {
                    rightContainer.removeView(syncButton);
                    renderSyllableLyrics(activity, content, lyricsContainer, track);
                } else if(type.equals("Line")) {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url("https://spotifyplus.lenerd.tech/api/lyrics/" + track.uri.split(":")[2]).get().build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                            if(response.isSuccessful()) {
                                XposedBridge.log("[SpotifyPlus] Loading lyrics from SpotifyPlus server");
                                String content = response.body().string();

                                lyricsContainer.post(() -> {
                                    renderSyllableLyrics(activity, content, lyricsContainer, track);
                                });
                            } else {
                                // Otherwise, no lyrics found. Continue
                                lyricsContainer.post(() -> {
                                    renderLineLyrics(activity, content, lyricsContainer, track);
                                });
                            }
                        }

                        @Override
                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                            XposedBridge.log(e);
                        }
                    });
                } else if(type.equals("Static")) {
                    Gson gson = new Gson();
                    // This is pretty pointless
                    // If Spotify doesn't have lyrics, you can't open this page
                    // And it's very likely that if a song has static lyrics, Spotify won't have the lryics

                    StaticSyncedLyrics providerLyrics = gson.fromJson(content, StaticSyncedLyrics.class);

                    ProviderLyrics providerLyricsThing = new ProviderLyrics();
                    providerLyricsThing.staticLyrics = providerLyrics;

                    TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyricsThing, activity);
                    StaticSyncedLyrics lyrics = transformedLyrics.lyrics.staticLyrics;

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
                        text.setTypeface(References.beautifulFont.get());

                        layout.addView(text);
                        lines.add(layout);
                    }
                }
            });
        });
    }

    private void renderSyllableLyrics(Activity activity, String content, LinearLayout lyricsContainer, SpotifyTrack track) {
        List<View> lines = new ArrayList<>();
        vocalGroups = new HashMap<>();

        Gson gson = new Gson();
        SyllableSyncedLyrics providerLyrics = gson.fromJson(content, SyllableSyncedLyrics.class);

        ProviderLyrics providedLyrics = new ProviderLyrics();
        providedLyrics.syllableLyrics = providerLyrics;

        TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providedLyrics, activity);
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
                    params.setMargins(dpToPx(30, activity), dpToPx(40, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);
                } else {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(20, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);

                    if(i != lyrics.content.size() - 1 && ((SyllableVocalSet)lyrics.content.get(i - 1)).oppositeAligned && ((SyllableVocalSet)lyrics.content.get(i + 1)).oppositeAligned) {
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        params.setMargins(0, dpToPx(20, activity), dpToPx(30, activity), 0);
                    }
                }

                List<SyncableVocals> visual = new ArrayList<>();
                visual.add(new InterludeVisual(vocalGroupContainer, interlude, activity));
                vocalGroups.put(vocalGroupContainer, visual);

                // Check opposite alignment

                topGroup.addView(vocalGroupContainer);
                lines.add(topGroup);
            } else if(vocalGroup instanceof SyllableVocalSet) {
                SyllableVocalSet set = (SyllableVocalSet) vocalGroup;

                RelativeLayout evenMoreTopGroup = new RelativeLayout(activity);
                evenMoreTopGroup.setClipToPadding(false);
                evenMoreTopGroup.setClipChildren(false);

                LinearLayout topGroup = new LinearLayout(activity);
                topGroup.setOrientation(LinearLayout.VERTICAL);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);
                RelativeLayout.LayoutParams parms = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                parms.setMargins(dpToPx(25, activity), dpToPx(30, activity), dpToPx(35, activity), 0);

                topGroup.setLayoutParams(parms);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity);
                vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                vocalGroupContainer.setLayoutParams(params);
                vocalGroupContainer.setPadding(dpToPx(6, activity), dpToPx(4, activity), dpToPx(6, activity), dpToPx(4, activity));

                if(set.oppositeAligned) {
                    parms.addRule(RelativeLayout.ALIGN_PARENT_END);
                    parms.setMargins(dpToPx(35, activity), dpToPx(30, activity), dpToPx(25, activity), 0);

                    vocalGroupContainer.setJustifyContent(JustifyContent.FLEX_END);
                }

                topGroup.addView(vocalGroupContainer);
                evenMoreTopGroup.addView(topGroup);
                lines.add(evenMoreTopGroup);

                List<SyllableVocals> vocals = new ArrayList<>();
                double startTime = set.lead.startTime;

                // Event for auto scrolling
                SyllableVocals sv = new SyllableVocals(vocalGroupContainer, set.lead.syllables, false, false, set.oppositeAligned, activity);
                sv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent().getParent();
                    ScrollView scrollView = (ScrollView) lyricsContainer.getParent();
                    scrollToNewLine(lineView, scrollView, info.immediate);
                });

                vocals.add(sv);

                if(set.background != null && !set.background.isEmpty()) {
                    FlexboxLayout backgroundVocalGroupContainer = new FlexboxLayout(activity);
                    backgroundVocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                    backgroundVocalGroupContainer.setClipToPadding(false);
                    backgroundVocalGroupContainer.setClipChildren(false);
                    backgroundVocalGroupContainer.setJustifyContent(set.oppositeAligned ? JustifyContent.FLEX_END : JustifyContent.FLEX_START);
                    topGroup.addView(backgroundVocalGroupContainer);
                    backgroundVocalGroupContainer.setPadding(dpToPx(6, activity), 0, dpToPx(6, activity), 0);

                    for(var backgroundVocal : set.background) {
                        startTime = Math.min(startTime, backgroundVocal.startTime);
                        vocals.add(new SyllableVocals(backgroundVocalGroupContainer, backgroundVocal.syllables, true, false, set.oppositeAligned, activity));
                    }
                }

                final double finalStartTime = startTime;
                int radius = dpToPx(8, activity);
                final GradientDrawable highlightBackground = new GradientDrawable();
                highlightBackground.setColor(Color.WHITE);
                highlightBackground.setCornerRadius(radius);
                highlightBackground.setAlpha(0);
                highlightBackground.mutate();
                vocalGroupContainer.setBackground(highlightBackground);

                vocalGroupContainer.setOnTouchListener((v, event) -> {
                    switch(event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            ObjectAnimator startAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha", highlightBackground.getAlpha(), 50).setDuration(400);
                            startAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            startAnimation.start();
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            ObjectAnimator endAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha", highlightBackground.getAlpha(), 0).setDuration(400);
                            endAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            endAnimation.start();
                            break;
                    }

                    return false;
                });

                vocalGroupContainer.setOnClickListener((v) -> {
                    try {
                        Object seekArg = ctor.newInstance((long)(finalStartTime * 1000));

                        if(seekInstance != null) {
                            // Don't do this every time, just load it once and keep a reference to it
                            Method method = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(seekInstance.getClass()))).matcher(MethodMatcher.create().paramTypes(ctor.getDeclaringClass().getSuperclass()))).get(0).getMethodInstance(lpparm.classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            XposedHelpers.callMethod(block, "blockingGet");
                        } else {
                            XposedBridge.log("[SpotifyPlus] p.mmm is null :(");
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                });

                List<SyncableVocals> syncedVocals = new ArrayList<>(vocals);
                vocalGroups.put(vocalGroupContainer, syncedVocals);
            }

            i++;
        }

        lines.forEach(lyricsContainer::addView);

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dpToPx(180, activity));
        spacer.setLayoutParams(spacerParams);
        lyricsContainer.addView(spacer);

        update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);
        updateProgress(track.position, System.currentTimeMillis(), vocalGroups);
    }

    private void renderLineLyrics(Activity activity, String content, LinearLayout lyricsContainer, SpotifyTrack track) {
        List<View> lines = new ArrayList<>();
        vocalGroups = new HashMap<>();

        Gson gson = new Gson();

        LineSyncedLyrics providerLyrics = gson.fromJson(content, LineSyncedLyrics.class);

        ProviderLyrics providerLyricsThing = new ProviderLyrics();
        providerLyricsThing.lineLyrics = providerLyrics;
        TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyricsThing, activity);

        LineSyncedLyrics lyrics = transformedLyrics.lyrics.lineLyrics;
        lineLyrics = lyrics;

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
                params.setMargins(dpToPx(25, activity), dpToPx(40, activity), dpToPx(30, activity), 0);

                if(vocal.oppositeAligned) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                }

                vocalGroupContainer.setLayoutParams(params);
                topGroup.addView(vocalGroupContainer);

                LineVocals lv = new LineVocals(vocalGroupContainer, vocal, false, activity);
                lv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent();
                    ScrollView scrollView = (ScrollView) lyricsContainer.getParent();

                    scrollToNewLine(lineView, scrollView, info.immediate);
                });

                vocalGroups.put(vocalGroupContainer, List.of(lv));

                final double finalStartTime = lv.startTime;
                int radius = dpToPx(8, activity);
                final GradientDrawable highlightBackground = new GradientDrawable();
                highlightBackground.setColor(Color.WHITE);
                highlightBackground.setCornerRadius(radius);
                highlightBackground.setAlpha(0);
                highlightBackground.mutate();
                vocalGroupContainer.setBackground(highlightBackground);

                vocalGroupContainer.setOnTouchListener((v, event) -> {
                    switch(event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            ObjectAnimator startAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha", highlightBackground.getAlpha(), 50).setDuration(400);
                            startAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            startAnimation.start();
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            ObjectAnimator endAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha", highlightBackground.getAlpha(), 0).setDuration(400);
                            endAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            endAnimation.start();
                            break;
                    }

                    return false;
                });

                vocalGroupContainer.setOnClickListener((v) -> {
                    try {
                        Object seekArg = ctor.newInstance((long)(finalStartTime * 1000));

                        if(seekInstance != null) {
                            Method method = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(seekInstance.getClass()))).matcher(MethodMatcher.create().paramTypes(ctor.getDeclaringClass().getSuperclass()))).get(0).getMethodInstance(lpparm.classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            XposedHelpers.callMethod(block, "blockingGet");
                        } else {
                            XposedBridge.log("[SpotifyPlus] p.mmm is null :(");
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                });

                lines.add(topGroup);
            }
            i++;
        }

        lines.forEach(lyricsContainer::addView);

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dpToPx(180, activity));
        spacer.setLayoutParams(spacerParams);
        lyricsContainer.addView(spacer);

        update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);
        updateProgress(track.position, System.currentTimeMillis(), vocalGroups);
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
                        long position = References.getCurrentPlaybackPosition(bridge, lpparm);
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

                    update(vocalGroups, syncedTimestamp, deltaTime, Math.abs(syncedTimestamp - lastTimestamp) > 0.075d);
                    lastTimestamp = syncedTimestamp;

                    lastUpdatedAt = updatedAt;

                    try {
                        Thread.sleep(16);
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }  catch (Exception e) {
                XposedBridge.log(e);
            }
        });

        mainLoop.start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void RenderSyncLyrics(Activity activity, LinearLayout lyricsContainer, ScrollView scroller, String id) {
        List<Object> vocals = new ArrayList<>();
        AtomicInteger index = new AtomicInteger();
        AtomicInteger wordIndex = new AtomicInteger();
        AtomicInteger lineIndex = new AtomicInteger();
        AtomicLong startedAt = new AtomicLong();
        AtomicInteger lineCount = new AtomicInteger();
        final SyllableVocal[] currentLine = {null};

        final boolean[] started = {false};
        List<LineVocal> lines = lineLyrics.content.stream().filter(x -> x instanceof LineVocal).map(x -> (LineVocal) x).collect(Collectors.toList());
        lineCount.set(lines.size());

        lyricsContainer.post(() -> {
            for(int i = 0; i < lines.size(); i++) {
                LineVocal line = lines.get(i);
                String content = line.text;

                SyllableVocalSet set = new SyllableVocalSet();
                set.type = "Vocal";
                set.oppositeAligned = line.oppositeAligned;
                SyllableVocal vocalThing = new SyllableVocal();
                vocalThing.syllables = new ArrayList<>();
                set.lead = vocalThing;

                if(line.text.contains("(")) {
                    // Filter out backing vocals
                    set.background = new ArrayList<>();

                    var split = content.split("\\(");
                    var splitAfter = content.split("\\)");

                    if(split.length == 2) {
                        String lineBefore = split[1];
                        // backgroundVocals = lineBefore.split("\\)")[0];

                        String outputBefore = split[0].trim();
                        String outputAfter = "";

                        if(splitAfter.length == 2) {
                            outputAfter = splitAfter[1].trim();
                        }

                        content = (outputBefore + " " + outputAfter).trim();
                    } else {
                        // (Hey!) I don't know about you (I don't know about you) but I'm feeling 22

                        String first = split[1];
                        String second = first.split("\\)")[0]; // Hey!
                        String third = split[2];
                        String fourth = third.split("\\)")[0]; // I don't know about you

                        // backgroundVocals = second + " " + fourth; // Hey! I don't know about you

                        String leadFirst = split[0].trim(); // ""
                        String leadSecond = splitAfter[1]; // I don't know about you (I don't know about you
                        String leadThird = splitAfter[2]; // but I'm feeling 22

                        content = (leadFirst + " " + second.split("\\(")[0] + " " + third).trim();
                    }
                }

                if(content.isEmpty()) continue;

                String[] words = content.split(" ");

                FlexboxLayout layout = new FlexboxLayout(activity);
                layout.setFlexDirection(FlexDirection.ROW);
                layout.setFlexWrap(FlexWrap.WRAP);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(dpToPx(25, activity), dpToPx(40, activity), dpToPx(30, activity), 0);
                layout.setLayoutParams(params);

                for(int j = 0; j < words.length; j++) {
                    TextView text = new TextView(activity);
                    text.setText(words[j]);
                    text.setTextColor(0x3CFFFFFF);
                    text.setTextSize(26f);
                    text.setTypeface(References.beautifulFont.get());
                    FlexboxLayout.LayoutParams textParams = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
                    textParams.setMargins(0, 0, dpToPx(8, activity), 0);
                    text.setLayoutParams(textParams);

                    SyllableMetadata metadata = new SyllableMetadata();
                    metadata.text = words[j];
                    metadata.isPartOfWord = false;

                    layout.addView(text);
                }

                lyricsContainer.addView(layout);
            }


            View spacer = new View(activity);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dpToPx(180, activity));
            spacer.setLayoutParams(spacerParams);
            lyricsContainer.addView(spacer);

            Toast.makeText(activity, "Tap when you're ready", Toast.LENGTH_SHORT).show();
        });


        lyricsContainer.setOnTouchListener((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                if(!started[0]) return true;

                FlexboxLayout container = (FlexboxLayout) lyricsContainer.getChildAt(lineIndex.get());
                TextView textView = (TextView) container.getChildAt(wordIndex.get());

                double seconds = (System.currentTimeMillis() - startedAt.get()) / 1000.0;

                if(currentLine[0] == null) {
                    SyllableVocal temp = new SyllableVocal();
                    temp.startTime = seconds;
                    temp.syllables = new ArrayList<>();

                    currentLine[0] = temp;
                }

                SyllableMetadata metadata = new SyllableMetadata();
                metadata.startTime = seconds;
                metadata.text = textView.getText().toString();
                metadata.isPartOfWord = false;

                currentLine[0].syllables.add(metadata);

                textView.setShadowLayer(4f, 2f, 2f, Color.WHITE);
                textView.animate().scaleX(1.05f).scaleY(1.05f).translationY(-dpToPx(2, activity)).setInterpolator(new OvershootInterpolator()).setDuration(250).start();
            } else if(event.getAction() == MotionEvent.ACTION_UP) {
                if(!started[0]) {
                    started[0] = true;

                    try {
                        Object seekArg = ctor.newInstance(0L);

                        if(seekInstance != null) {
                            Method method = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(seekInstance.getClass()))).matcher(MethodMatcher.create().paramTypes(ctor.getDeclaringClass().getSuperclass()))).get(0).getMethodInstance(lpparm.classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            XposedHelpers.callMethod(block, "blockingGet");
                        } else {
                            XposedBridge.log("[SpotifyPlus] p.mmm is null :(");
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }

                    startedAt.set(System.currentTimeMillis());
                    return true;
                }

                FlexboxLayout container = (FlexboxLayout) lyricsContainer.getChildAt(lineIndex.get());
                TextView textView = (TextView) container.getChildAt(wordIndex.get());
                LineVocal line = lines.get(lineIndex.get());

                double seconds = (System.currentTimeMillis() - startedAt.get()) / 1000.0;

                currentLine[0].syllables.get(currentLine[0].syllables.size() - 1).endTime = seconds;

                textView.animate().scaleX(1f).scaleY(1f).translationY(0f).setInterpolator(new OvershootInterpolator()).setDuration(250).start();
                textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);

                wordIndex.getAndIncrement();
                index.getAndIncrement();

                // We've reached the last word in the line
                if(wordIndex.get() == container.getChildCount()) {
                    wordIndex.set(0);
                    lineIndex.getAndIncrement();

                    currentLine[0].endTime = seconds;

                    SyllableVocalSet newSet = new SyllableVocalSet();
                    newSet.type = "Vocal";
                    newSet.oppositeAligned = line.oppositeAligned;
                    newSet.lead = currentLine[0];

                    vocals.add(newSet);

                    currentLine[0] = null;
                    scrollToNewLine(lyricsContainer.getChildAt(lineIndex.get()), scroller, false);
                }

                // We've reached the end of the song
                if(lineIndex.get() == lineCount.get()) {

                    SyllableSyncedLyrics syllableLyrics = new SyllableSyncedLyrics();
                    syllableLyrics.content = vocals;
                    syllableLyrics.startTime = ((SyllableVocalSet)(vocals.get(0))).lead.startTime;
                    syllableLyrics.endTime = ((SyllableVocalSet)(vocals.get(vocals.size() - 1))).lead.endTime;

                    Gson gson = new Gson();
                    String json = gson.toJson(syllableLyrics);

                    OkHttpClient client = new OkHttpClient();
                    MediaType jsonType = MediaType.get("application/json; charset=utf-8");
                    RequestBody bodyRequest = RequestBody.create(json, jsonType);

                    MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", id + ".json", bodyRequest).build();
                    Request request = new Request.Builder().url("https://spotifyplus.lenerd.tech/api/lyrics/" + id).post(body).build();
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                            if(response.isSuccessful()) {
                                Toast.makeText(activity, "Lyrics Uploaded!", Toast.LENGTH_LONG).show();
                                XposedBridge.log("[SpotifyPlus] Success!");

                                activity.onBackPressed();
                            } else {
                                Toast.makeText(activity, "Failed to upload lyrics", Toast.LENGTH_LONG).show();
                                XposedBridge.log("[SpotifyPlus] " + response.code() + " " + response.message());
                            }
                        }

                        @Override
                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                            XposedBridge.log("[SpotifyPlus] Failed to upload file!");
                            XposedBridge.log(e);
                        }
                    });
                }
            }

            return true;
        });
    }

    private ValueAnimator lyricsScrollAnimator = new ValueAnimator();

    private void scrollToNewLine(View activeLine, ScrollView scrollView, boolean immediate) {
        scrollView.post(() -> {
            final int scrollViewHeight = scrollView.getHeight();
            final int lineHeight = activeLine.getHeight();
            final int lineTopInSv = activeLine.getTop();
            final int targetScrollY = lineTopInSv - (scrollViewHeight / 3) + (lineHeight / 2);
            final int scrollY = scrollView.getScrollY();
            final int lineBottom = lineTopInSv + activeLine.getHeight();

            final View content = scrollView.getChildAt(0);
            final int maxScrollY = content.getHeight() - scrollViewHeight;
            final int targetScroll = Math.max(0, Math.min(targetScrollY, maxScrollY));

            if(lyricsScrollAnimator != null && lyricsScrollAnimator.isRunning()) {
                lyricsScrollAnimator.cancel();
            }

            // Check if we should scroll at all (is the current line within view)
            if(immediate || (!scrollView.isPressed() && lineTopInSv >= scrollY && lineBottom <= scrollY + scrollViewHeight)) {
                lyricsScrollAnimator = ValueAnimator.ofFloat(scrollView.getScrollY(), targetScroll);
                lyricsScrollAnimator.setDuration(SCROLL_ANIMATION_DURATION);
                lyricsScrollAnimator.setInterpolator(new DecelerateInterpolator());

                lyricsScrollAnimator.addUpdateListener(animation -> {
                    float value = (float) animation.getAnimatedValue();
                    scrollView.scrollTo(0, (int) value);
                });

                lyricsScrollAnimator.start();
            }

            activeLine.setPivotY(activeLine.getHeight() / 2.0f);
            activeLine.animate().scaleX(MAX_SCALE).scaleY(MAX_SCALE).setDuration(ANIMATION_DURATION).setInterpolator(new OvershootInterpolator());
        });
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

    private Drawable createChevronDownIcon(Activity context) {
        int size = dpToPx(24, context);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#B3B3B3"));
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2, context));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float scale = size / 24f;

        // Draw chevron down
        Path path = new Path();
        path.moveTo(6f * scale, 9f * scale);
        path.lineTo(12f * scale, 15f * scale);
        path.lineTo(18f * scale, 9f * scale);

        canvas.drawPath(path, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    int dpToPx(int dp, Activity activity) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                activity.getResources().getDisplayMetrics()
        );
    }
}
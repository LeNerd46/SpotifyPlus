package com.lenerd.spotifyplus.module.hooks;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.*;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;
import com.lenerd.spotifyplus.module.lyrics.AnimatedBackgroundView;
import com.lenerd.spotifyplus.module.lyrics.LyricUtilities;
import com.lenerd.spotifyplus.module.lyrics.Spring;
import com.lenerd.spotifyplus.module.lyrics.entities.SyllableVocals;
import com.lenerd.spotifyplus.module.lyrics.entities.SyncableVocals;
import com.lenerd.spotifyplus.module.lyrics.entities.interlude.InterludeVisual;
import com.lenerd.spotifyplus.module.lyrics.entities.lyrics.*;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

@XposedHooker
public class LyricsHook extends SpotifyHook {
    private Method buildMethod;
    private Method getStateMethod;

    private static Map<FlexboxLayout, List<SyncableVocals>> vocalGroups;

    private long lastUpdatedAt;
    private double lastTimestamp;
    private double targetScrollOffset;
    private boolean isPlaying = true;
    private volatile boolean stop = false;
    private Thread mainLoop;

    private View currentActiveLineView;
    private int activeLineIndex = -1;
    private double viewportHeight = 0;
    private double contentHeight = 0;
    private final Map<View, Spring> lineSprings = new HashMap<>();
    private final Map<View, Integer> lineIndex = new WeakHashMap<>();
    private final List<View> lineRoots = new ArrayList<>();
    private final Map<View, Long> lineAnimationStartTimes = new HashMap<>();
    private final Map<View, Integer> logicalLineTops = new HashMap<>();

    private View experimentalTouchSurface;
    private View headerFadeAnchor;
    private ValueAnimator inertiaAnimator;
    private boolean isUserInteracting = false;
    private boolean isFollowingPlayback = true;
    private GestureDetector gestureDetector;

    private boolean lastBlurAllowed = true;
    private int lastAppliedActiveIndex = Integer.MIN_VALUE;
    private static final float[] BLUR_LEVELS_DP = new float[]{
            0f,
            0.6f,
            1.4f,
            2f
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Constructor<?> ctor;
    private Object seekInstance;
    private Constructor<?> seekConstructor;

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException {
        Class<?> lyricsPageClass = findClass("com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity");
        hook(lyricsPageClass.getDeclaredMethod("onCreate", Bundle.class));
        hook(lyricsPageClass.getDeclaredMethod("finish"));

        Class<?> playerStateBuilder = findClass("com.spotify.player.model.AutoValue_PlayerState$Builder");
        buildMethod = playerStateBuilder.getDeclaredMethod("build");
        hook(buildMethod);

        Class<?> hzc = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify.player.esperanto.proto.ContextPlayer", "SetOptions"))).get(0).getInstance(classLoader);
        Class<?> seek = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).methodCount(3).fields(FieldsMatcher.create()
                .count(3)
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(hzc))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(boolean.class))
        ))).get(0).getInstance(classLoader);
        seekConstructor = seek.getConstructors()[0];
        hook(seekConstructor);

        var whateverThisClassEvenDoes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).fields(FieldsMatcher.create()
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(String.class))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(ArrayList.class))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object.class))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Bundle.class))
        )));

        getStateMethod = bridge.findMethod(FindMethod.create().searchInClass(whateverThisClassEvenDoes).matcher(MethodMatcher.create().name("getState"))).get(0).getMethodInstance(classLoader);
        hook(getStateMethod);
    }

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        LyricsHook hook = getHook(LyricsHook.class);
        if (hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        if (member.getName().equals("onCreate")) {
            final Activity activity = (Activity) callback.getThisObject();
            if (activity == null) {
                logError("Lyrics activity is null");
                return;
            }

            stop = false;
            lastUpdatedAt = 0;
            lastTimestamp = 0;
            targetScrollOffset = 0;

            try {
                ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                SpotifyNativeBridge.sendEvent("lyrics", new JSONObject().put("message", "Hello from the lyrics page!").toString());

                ReactManager.registerSurface("lyrics-view", root);
//                SpotifyTrack track = Utils.getTrack(activity.getClassLoader());

//                View view = Utils.inflate(activity, R.layout.lyrics_page, root);
//                TextView titleText = view.findViewById(R.id.text_title);
//                TextView artistText = view.findViewById(R.id.text_artist);
//                ImageView albumImage = view.findViewById(R.id.album_cover);
//                LinearLayout lyricsContainer = view.findViewById(R.id.lyrics_container);
//
//                getAlbumArtwork(track.imageId, new AlbumArtworkCallback() {
//                    @Override
//                    public void onSuccess(Bitmap albumArt) {
//                        albumImage.setImageBitmap(albumArt);
//
//                        if (albumArt != null) {
//                            AnimatedBackgroundView background = new AnimatedBackgroundView(activity, albumArt, root);
//                            background.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//
//                            activity.runOnUiThread(() -> root.addView(background));
//                        }
//                    }
//
//                    @Override
//                    public void onError(Exception e) {
//                        logError(e);
//                    }
//                });
//
//                titleText.setText(track.title);
//                artistText.setText(track.artist);
//
//                root.addView(view, -2);
//
//                if (SpotifyPlusSettings.appleMusicScroll) {
//                    View viewport = view.findViewById(R.id.apple_music_viewport);
//                    LinearLayout container = view.findViewById(R.id.apple_music_container);
//                    viewport.setVisibility(View.VISIBLE);
//                    lyricsContainer = container;
//
//                    setupGestureDetector(activity, viewport, container);
//                    experimentalTouchSurface = viewport;
//                } else {
//                    View container = view.findViewById(R.id.lyrics_scroller);
//                    container.setVisibility(View.VISIBLE);
//                }
//
//                renderLyrics(activity, track, lyricsContainer, root);
            } catch (Exception e) {
                logError(e);
            }
        } else if (member.getName().equals("finish")) {
            try {
                SpotifyNativeBridge.sendEvent("react.surfaceClose", new JSONObject().put("surfaceId", "lyrics-view").toString());

                stop = true;
                lineSprings.clear();
                lineAnimationStartTimes.clear();
                if (mainLoop != null) mainLoop.interrupt();
                vocalGroups = null;
                if (inertiaAnimator != null) inertiaAnimator.cancel();
            } catch (Exception e) {
                logError(e);
            }
        } else if (member == seekConstructor) {
            seekInstance = callback.getThisObject();
        }
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        LyricsHook hook = getHook(LyricsHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        if (member == buildMethod) {
            Utils.playerState = callback.getResult();
            // Notify state changed
        } else if (member == getStateMethod) {
            Utils.playerStateWrapper = callback.getThisObject();
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        return null;
    }

    private void renderLyrics(Activity activity, SpotifyTrack track, LinearLayout lyricsContainer, ViewGroup root) {
        vocalGroups = new HashMap<>();

        new Thread(() -> {
            String finalContent = "";

            try {
                String id = track.uri.split(":")[2];

                if (!SpotifyPlusSettings.enabledBackground) {
                    FrameLayout background = new FrameLayout(activity);
                    background.setBackgroundColor(Color.parseColor("#" + track.color));

                    activity.runOnUiThread(() -> root.addView(background));
                }

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create("{ \"queries\": [ { \"operation\": \"lyrics\", \"variables\": { \"id\": \"" + id + "\", \"auth\": \"SpicyLyrics-WebAuth\" } } ], \"client\": { \"version\": \"5.21.5\" } }", MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder().url("https://api.spicylyrics.org/query").post(body).header("Spicylyrics-Webauth", "Bearer " + Utils.token).header("Spicylyrics-Version", "5.21.5").header("Origin", "https://xpui.app.spotify.com").build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        if (response.body() == null) return;
                        String contentFull = response.body().string();

                        JsonObject jsonObject = new JsonParser().parseString(contentFull).getAsJsonObject().get("queries").getAsJsonArray().get(0).getAsJsonObject().get("result").getAsJsonObject().get("data").getAsJsonObject();
                        String content = jsonObject.toString();
                        String type = jsonObject.get("Type").getAsString();
                        var writers = jsonObject.get("SongWriters");
                        String writtenBy;
                        if (writers != null) {
                            writtenBy = writers.getAsJsonArray().asList().stream().map(JsonElement::getAsString).collect(Collectors.joining(", "));
                        } else {
                            writtenBy = "";
                        }

                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> {
                            try {
                                Class<?> requestClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).fieldCount(1).addField(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(long.class)).methods(MethodsMatcher.create()
                                        .count(6)
                                        .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).paramCount(13))
                                        .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(void.class).paramCount(12))
                                        .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).addParamType(Object.class))
                                ))).get(0).getInstance(classLoader);

                                ctor = requestClass.getConstructor(long.class);
                                ctor.setAccessible(true);
                            } catch (Exception e) {
                                logError(e);
                                Toast.makeText(activity, "Failed to load lyrics", Toast.LENGTH_LONG).show();
                                return;
                            }

                            if (type.equals("Syllable")) {
                                renderSyllableLyrics(activity, content, lyricsContainer, track, writtenBy);
                            } else if (type.equals("Line")) {
                                renderLineLyrics(activity, content, lyricsContainer, track, writtenBy);
                            } else if (type.equals("Static")) {
                                Gson gson = new Gson();

                                ProviderLyrics providerLyrics = new ProviderLyrics();
                                providerLyrics.staticLyrics = gson.fromJson(content, StaticSyncedLyrics.class);

                                TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyrics, activity);
                                StaticSyncedLyrics lyrics = transformedLyrics.lyrics.staticLyrics;

                                for (var line : lyrics.lines) {
                                    FlexboxLayout layout = new FlexboxLayout(activity.getApplicationContext());
                                    layout.setFlexWrap(FlexWrap.WRAP);

                                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                    params.setMargins(dpToPx(15, activity), dpToPx(20, activity), dpToPx(15, activity), 0);
                                    layout.setLayoutParams(params);

                                    TextView text = new TextView(activity);
                                    text.setText(line.text);

                                    text.setTextColor(Color.WHITE);
                                    text.setTextSize(26f);
                                    text.setTypeface(Utils.loadTypeface(activity, SpotifyPlusSettings.activeFont));

                                    layout.addView(text);
                                    lyricsContainer.addView(layout);
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> Toast.makeText(activity, "Failed to load lyrics", Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception e) {
                logError(e);

                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> Toast.makeText(activity, "Failed to load lyrics", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void renderSyllableLyrics(Activity activity, String content, LinearLayout lyricsContainer, SpotifyTrack track, String writtenBy) {
        List<View> lines = new ArrayList<>();

        int lineSpacing;
        int fontSize = switch (SpotifyPlusSettings.lineSpacing) {
            case COMPACT -> {
                lineSpacing = 32;
                yield 28;
            }

            case SPACIOUS -> {
                lineSpacing = 42;
                yield 36;
            }

            case MORE, MAX -> {
                lineSpacing = 46;
                yield 38;
            }

            default -> {
                lineSpacing = 36;
                yield 34;
            }
        };

        Gson gson = new Gson();
        ProviderLyrics providerLyrics = new ProviderLyrics();
        providerLyrics.syllableLyrics = gson.fromJson(content, SyllableSyncedLyrics.class);

        TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyrics, activity);
        SyllableSyncedLyrics lyrics = transformedLyrics.lyrics.syllableLyrics;

        int i = 0;
        for (var vocalGroup : lyrics.content) {
            if (vocalGroup instanceof Interlude interlude) {
                RelativeLayout topGroup = new RelativeLayout(activity);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);

                if (interlude.time.startTime == 0) {
                    ViewGroup.MarginLayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(40, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);
                } else {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(20, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);

                    if (i != lyrics.content.size() - 1 && ((SyllableVocalSet) lyrics.content.get(i + 1)).oppositeAligned) {
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        params.setMargins(0, dpToPx(20, activity), dpToPx(30, activity), 0);
                    }
                }

                List<SyncableVocals> visual = new ArrayList<>();
                InterludeVisual iv = new InterludeVisual(vocalGroupContainer, interlude, activity);
                iv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent().getParent();
                    View scrollView = (View) lyricsContainer.getParent();

                    currentActiveLineView = lineView;
                    onActiveLineChanged(info.view, lyricsContainer, true);

                    if (SpotifyPlusSettings.appleMusicScroll) {
                        appleScrollToNewLine(lineView, lyricsContainer, info.immediate);
                    } else {
                        scrollToNewLine(lineView, (ScrollView) scrollView, info.immediate);
                    }
                });

                visual.add(iv);
                vocalGroups.put(vocalGroupContainer, visual);

                topGroup.addView(vocalGroupContainer);
                lines.add(topGroup);
            } else if (vocalGroup instanceof SyllableVocalSet set) {
                RelativeLayout evenMoreTopGroup = new RelativeLayout(activity);
                evenMoreTopGroup.setClipToPadding(false);
                evenMoreTopGroup.setClipChildren(false);

                LinearLayout topGroup = new LinearLayout(activity);
                topGroup.setOrientation(LinearLayout.VERTICAL);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);
                RelativeLayout.LayoutParams parms = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                parms.setMargins(dpToPx(25, activity), dpToPx(lineSpacing, activity), dpToPx(35, activity), 0);

                topGroup.setLayoutParams(parms);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                vocalGroupContainer.setLayoutParams(params);
                vocalGroupContainer.setPadding(dpToPx(6, activity), dpToPx(4, activity), dpToPx(6, activity), dpToPx(4, activity));

                if (set.oppositeAligned) {
                    parms.addRule(RelativeLayout.ALIGN_PARENT_END);
                    parms.setMargins(dpToPx(35, activity), dpToPx(lineSpacing, activity), dpToPx(25, activity), 0);

                    vocalGroupContainer.setJustifyContent(JustifyContent.FLEX_END);
                }

                topGroup.addView(vocalGroupContainer);
                evenMoreTopGroup.addView(topGroup);
                lines.add(evenMoreTopGroup);

                List<SyllableVocals> vocals = new ArrayList<>();
                double startTime = set.lead.startTime;

                SyllableVocals sv = new SyllableVocals(vocalGroupContainer, set.lead.syllables, false, activity, fontSize);
                sv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent().getParent();
                    View scrollView = (View) lyricsContainer.getParent();

                    currentActiveLineView = lineView;
                    onActiveLineChanged(info.view, lyricsContainer, false);

                    if (SpotifyPlusSettings.appleMusicScroll) {
                        appleScrollToNewLine(lineView, lyricsContainer, info.immediate);
                    } else {
                        scrollToNewLine(lineView, (ScrollView) scrollView, info.immediate);
                    }
                });

                vocals.add(sv);

                if (set.background != null && !set.background.isEmpty()) {
                    FlexboxLayout backgroundVocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                    backgroundVocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                    backgroundVocalGroupContainer.setClipToPadding(false);
                    backgroundVocalGroupContainer.setClipChildren(false);
                    backgroundVocalGroupContainer.setJustifyContent(set.oppositeAligned ? JustifyContent.FLEX_END : JustifyContent.FLEX_START);
                    topGroup.addView(backgroundVocalGroupContainer);
                    backgroundVocalGroupContainer.setPadding(dpToPx(6, activity), 0, dpToPx(6, activity), 0);

                    for (var backgroundVocal : set.background) {
                        startTime = Math.min(startTime, backgroundVocal.startTime);
                        vocals.add(new SyllableVocals(backgroundVocalGroupContainer, backgroundVocal.syllables, true, activity, fontSize));
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

                final float touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
                final float[] downX = new float[1];
                final float[] downY = new float[1];
                final boolean[] isDragging = new boolean[1];

                vocalGroupContainer.setOnTouchListener((v, event) -> {
                    if (experimentalTouchSurface != null) {
                        forwardTouchToSurface(v, experimentalTouchSurface, lyricsContainer, event);
                    }

                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX[0] = event.getX();
                            downY[0] = event.getY();
                            isDragging[0] = false;

                            ObjectAnimator startAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha", highlightBackground.getAlpha(), 50).setDuration(400);
                            startAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            startAnimation.start();
                            break;

                        case MotionEvent.ACTION_MOVE: {
                            if (!isDragging[0]) {
                                float dx = event.getX() - downX[0];
                                float dy = event.getY() - downY[0];
                                if (Math.hypot(dx, dy) > touchSlop) {
                                    isDragging[0] = true;
                                }
                            }
                            break;
                        }

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            ObjectAnimator endAnimation =
                                    ObjectAnimator.ofInt(highlightBackground, "alpha",
                                                    highlightBackground.getAlpha(), 0)
                                            .setDuration(400);
                            endAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            endAnimation.start();

                            if (event.getActionMasked() == MotionEvent.ACTION_UP && !isDragging[0]) {
                                isUserInteracting = false;
                                isFollowingPlayback = true;

                                if (inertiaAnimator != null) {
                                    inertiaAnimator.cancel();
                                    inertiaAnimator = null;
                                }

                                for (View line : lineRoots) {
                                    Spring spring = lineSprings.get(line);
                                    if (spring != null) {
                                        spring.set(targetScrollOffset);
                                    }
                                }

                                lyricsContainer.post(() -> {
                                    for (View line : lineRoots) {
                                        Spring spring = lineSprings.get(line);
                                        float base = spring != null ? (float) spring.position : (float) targetScrollOffset;
                                        line.setTranslationY(base + getFocusedPairOffsetPx(line));
                                    }

                                    applyLineFocusEffects();
                                    applyHeaderFadeToLines();
                                });

                                v.performClick();
                            } else {
                                isUserInteracting = false;
                            }
                            break;
                    }

                    return true;
                });

                vocalGroupContainer.setOnClickListener((v) -> {
                    isUserInteracting = false;
                    isFollowingPlayback = true;

                    if (inertiaAnimator != null) {
                        inertiaAnimator.cancel();
                        inertiaAnimator = null;
                    }

                    for (View line : lineRoots) {
                        Spring spring = lineSprings.get(line);
                        if (spring != null) {
                            spring.set(targetScrollOffset);
                        }
                    }

                    lyricsContainer.post(() -> {
                        for (View line : lineRoots) {
                            Spring spring = lineSprings.get(line);
                            float base = spring != null ? (float) spring.position : (float) targetScrollOffset;
                            line.setTranslationY(base + getFocusedPairOffsetPx(line));
                        }

                        applyLineFocusEffects();
                        applyHeaderFadeToLines();
                    });

                    try {
                        Object seekArg = ctor.newInstance((long) (finalStartTime * 1000));

                        if (seekInstance != null) {
                            Method method = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(seekInstance.getClass()))).matcher(MethodMatcher.create().paramTypes(ctor.getDeclaringClass().getSuperclass()))).get(0).getMethodInstance(classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            if (block == null) {
                                logError("Could not find seek class :(");
                                return;
                            }
                            block.getClass().getMethod("blockingGet").invoke(block);
                        } else {
                            logError("Could not find seek class");
                        }
                    } catch (Exception e) {
                        logError(e);
                    }
                });

                List<SyncableVocals> syncedVocals = new ArrayList<>(vocals);
                vocalGroups.put(vocalGroupContainer, syncedVocals);
            }

            i++;
        }

        lines.forEach(lyricsContainer::addView);

        lineRoots.clear();
        lineIndex.clear();

        for (int j = 0; j < lines.size(); j++) {
            View v = lines.get(j);
            lineRoots.add(v);
            lineIndex.put(v, j);
        }

        activeLineIndex = -1;
        lastAppliedActiveIndex = Integer.MIN_VALUE;
        lastBlurAllowed = true;

        if (!writtenBy.isBlank()) {
            TextView writtenByTextView = new TextView(activity.getApplicationContext());
            writtenByTextView.setText(Utils.getString(activity, R.string.written_by, writtenBy));
            writtenByTextView.setTextSize(16f);
            writtenByTextView.setTypeface(Utils.loadTypeface(activity, SpotifyPlusSettings.activeFont));
            writtenByTextView.setTextColor(Color.parseColor("#f5f5f5"));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            p.setMargins(dpToPx(30, activity), dpToPx(20, activity), dpToPx(30, activity), 0);
            writtenByTextView.setLayoutParams(p);

            lyricsContainer.addView(writtenByTextView);
        }

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dpToPx(180, activity));
        spacer.setLayoutParams(spacerParams);
        lyricsContainer.addView(spacer, spacerParams);

        lyricsContainer.post(() -> {
            computeLogicalLayout(lyricsContainer);

            update(vocalGroups, track.position / 1000d, 1.0d / 60.0d, true);
            updateProgress(track.position, System.currentTimeMillis(), vocalGroups, (View) lyricsContainer.getParent());
            applyHeaderFadeToLines();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void renderLineLyrics(Activity activity, String content, LinearLayout lyricsContainer, SpotifyTrack track, String writtenBy) {
        List<View> lines = new ArrayList<>();

        int lineSpacing;
        int fontSize = switch (SpotifyPlusSettings.lineSpacing) {
            case COMPACT -> {
                lineSpacing = 32;
                yield 28;
            }
            case SPACIOUS -> {
                lineSpacing = 42;
                yield 36;
            }
            case MORE, MAX -> {
                lineSpacing = 46;
                yield 38;
            }
            default -> {
                lineSpacing = 36;
                yield 34;
            }
        };

        Gson gson = new Gson();
        ProviderLyrics providerLyrics = new ProviderLyrics();
        providerLyrics.lineLyrics = gson.fromJson(content, LineSyncedLyrics.class);

        TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(providerLyrics, activity);
        LineSyncedLyrics lyrics = transformedLyrics.lyrics.lineLyrics;

        int i = 0;
        for (var vocalGroup : lyrics.content) {
            if (vocalGroup instanceof Interlude interlude) {

                RelativeLayout topGroup = new RelativeLayout(activity);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);

                if (interlude.time.startTime == 0) {
                    RelativeLayout.MarginLayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(15, activity), dpToPx(40, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);
                } else {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(15, activity), dpToPx(20, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);

                    if (i != lyrics.content.size() - 1 && ((LineVocal) lyrics.content.get(i + 1)).oppositeAligned) {
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        params.setMargins(0, dpToPx(20, activity), dpToPx(15, activity), 0);
                    }
                }

                List<SyncableVocals> visual = new ArrayList<>();
                InterludeVisual iv = new InterludeVisual(vocalGroupContainer, interlude, activity);
                iv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent();
                    View scrollView = (View) lyricsContainer.getParent();

                    currentActiveLineView = lineView;
                    onActiveLineChanged(info.view, lyricsContainer, true);

                    if (SpotifyPlusSettings.appleMusicScroll) {
                        appleScrollToNewLine(lineView, lyricsContainer, info.immediate);
                    } else {
                        scrollToNewLine(lineView, (ScrollView) scrollView, info.immediate);
                    }
                });

                visual.add(iv);
                vocalGroups.put(vocalGroupContainer, visual);

                topGroup.addView(vocalGroupContainer);
                lines.add(topGroup);
            } else if (vocalGroup instanceof LineVocal vocal) {
                RelativeLayout topGroup = new RelativeLayout(activity);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(dpToPx(25, activity), dpToPx(lineSpacing, activity), dpToPx(30, activity), 0);

                if (vocal.oppositeAligned) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                }

                vocalGroupContainer.setLayoutParams(params);
                topGroup.addView(vocalGroupContainer);

                LineVocals lv = new LineVocals(vocalGroupContainer, vocal, activity, fontSize);
                lv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent();
                    View scrollView = (View) lyricsContainer.getParent();

                    currentActiveLineView = lineView;
                    onActiveLineChanged(info.view, lyricsContainer, true);

                    if (SpotifyPlusSettings.appleMusicScroll) {
                        appleScrollToNewLine(lineView, lyricsContainer, info.immediate);
                    } else {
                        scrollToNewLine(lineView, (ScrollView) scrollView, info.immediate);
                    }
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

                final float touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
                final float[] downX = new float[1];
                final float[] downY = new float[1];
                final boolean[] isDragging = new boolean[1];

                vocalGroupContainer.setOnTouchListener((v, event) -> {
                    if (experimentalTouchSurface != null) {
                        forwardTouchToSurface(v, experimentalTouchSurface, lyricsContainer, event);
                    }

                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX[0] = event.getX();
                            downY[0] = event.getY();
                            isDragging[0] = false;

                            ObjectAnimator startAnimation =
                                    ObjectAnimator.ofInt(highlightBackground, "alpha",
                                                    highlightBackground.getAlpha(), 50)
                                            .setDuration(400);
                            startAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            startAnimation.start();
                            break;

                        case MotionEvent.ACTION_MOVE: {
                            if (!isDragging[0]) {
                                float dx = event.getX() - downX[0];
                                float dy = event.getY() - downY[0];
                                if (Math.hypot(dx, dy) > touchSlop) {
                                    isDragging[0] = true;
                                }
                            }
                            break;
                        }

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            ObjectAnimator endAnimation =
                                    ObjectAnimator.ofInt(highlightBackground, "alpha",
                                                    highlightBackground.getAlpha(), 0)
                                            .setDuration(400);
                            endAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            endAnimation.start();

                            if (event.getActionMasked() == MotionEvent.ACTION_UP && !isDragging[0]) {
                                isUserInteracting = false;
                                isFollowingPlayback = true;

                                if (inertiaAnimator != null) {
                                    inertiaAnimator.cancel();
                                    inertiaAnimator = null;
                                }

                                for (View line : lineRoots) {
                                    Spring spring = lineSprings.get(line);
                                    if (spring != null) {
                                        spring.set(targetScrollOffset);
                                    }
                                }

                                lyricsContainer.post(() -> {
                                    for (View line : lineRoots) {
                                        Spring spring = lineSprings.get(line);
                                        float base = spring != null ? (float) spring.position : (float) targetScrollOffset;
                                        line.setTranslationY(base + getFocusedPairOffsetPx(line));
                                    }

                                    applyLineFocusEffects();
                                    applyHeaderFadeToLines();
                                });

                                v.performClick();
                            } else {
                                isUserInteracting = false;
                            }
                            break;
                    }

                    return true;
                });

                vocalGroupContainer.setOnClickListener((v) -> {
                    isUserInteracting = false;
                    isFollowingPlayback = true;

                    if (inertiaAnimator != null) {
                        inertiaAnimator.cancel();
                        inertiaAnimator = null;
                    }

                    for (View line : lineRoots) {
                        Spring spring = lineSprings.get(line);
                        if (spring != null) {
                            spring.set(targetScrollOffset);
                        }
                    }

                    lyricsContainer.post(() -> {
                        for (View line : lineRoots) {
                            Spring spring = lineSprings.get(line);
                            float base = spring != null ? (float) spring.position : (float) targetScrollOffset;
                            line.setTranslationY(base + getFocusedPairOffsetPx(line));
                        }

                        applyLineFocusEffects();
                        applyHeaderFadeToLines();
                    });

                    try {
                        Object seekArg = ctor.newInstance((long) (finalStartTime * 1000));

                        if (seekInstance != null) {
                            Method method = bridge.findMethod(
                                    FindMethod.create()
                                            .searchInClass(Collections.singletonList(bridge.getClassData(seekInstance.getClass())))
                                            .matcher(MethodMatcher.create().paramTypes(ctor.getDeclaringClass().getSuperclass()))
                            ).get(0).getMethodInstance(classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            if (block == null) {
                                logError("Could not find seek class :(");
                                return;
                            }
                            block.getClass().getMethod("blockingGet").invoke(block);
                        } else {
                            logError("Could not find seek class");
                        }
                    } catch (Exception e) {
                        logError(e);
                    }
                });

                lines.add(topGroup);
            }

            i++;
        }

        lines.forEach(lyricsContainer::addView);

        lineRoots.clear();
        lineIndex.clear();

        for (int j = 0; j < lines.size(); j++) {
            View v = lines.get(j);
            lineRoots.add(v);
            lineIndex.put(v, j);
        }

        activeLineIndex = -1;
        lastAppliedActiveIndex = Integer.MIN_VALUE;
        lastBlurAllowed = true;

        if (!writtenBy.isBlank()) {
            TextView writtenByTextView = new TextView(activity.getApplicationContext());
            writtenByTextView.setText(Utils.getString(activity, R.string.written_by, writtenBy));
            writtenByTextView.setTextSize(16f);
            writtenByTextView.setTypeface(Utils.loadTypeface(activity, SpotifyPlusSettings.activeFont));
            writtenByTextView.setTextColor(Color.parseColor("#f5f5f5"));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            p.setMargins(dpToPx(30, activity), dpToPx(20, activity), dpToPx(30, activity), 0);
            writtenByTextView.setLayoutParams(p);

            lyricsContainer.addView(writtenByTextView);
        }

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dpToPx(180, activity));
        spacer.setLayoutParams(spacerParams);
        lyricsContainer.addView(spacer);

        lyricsContainer.post(() -> {
            computeLogicalLayout(lyricsContainer);

            update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);
            updateProgress(track.position, System.currentTimeMillis(), vocalGroups, (View) lyricsContainer.getParent());
            applyHeaderFadeToLines();
        });
    }

    private void update(Map<FlexboxLayout, List<SyncableVocals>> vocalGroups, double timestamp, double deltaTime, boolean skipped) {
        for (var vocalGroup : vocalGroups.values()) {
            for (var vocal : vocalGroup) {
                vocal.animate(timestamp, deltaTime, skipped);
            }
        }
    }

    private void updateProgress(long initialPositionS, double startedSyncAtS, Map<FlexboxLayout, List<SyncableVocals>> vocalGroups, View scrollView) {
        mainLoop = new Thread(() -> {
            int[] syncTimings = {50, 100, 150, 750};
            int syncIndex = 0;
            long nextSyncAt = syncTimings[0];
            long initialPosition = initialPositionS;
            double startedSyncAt = startedSyncAtS;

            while (!stop) {
                long updatedAt = System.currentTimeMillis();

                if (isPlaying) {
                    if (updatedAt > startedSyncAt + nextSyncAt) {
                        long position = Utils.getCurrentPlaybackPosition();

                        if (position != -1) {
                            initialPosition = position;
                            startedSyncAt = updatedAt;

                            syncIndex++;

                            if (syncIndex < syncTimings.length) {
                                nextSyncAt = syncTimings[syncIndex];
                            } else {
                                nextSyncAt = 33;
                            }
                        }
                    }

                    double syncedTimestamp = (initialPosition + (updatedAt - startedSyncAt)) / 1000d;
                    double deltaTime = (updatedAt - lastUpdatedAt) / 1000d;

                    update(vocalGroups, syncedTimestamp, deltaTime, Math.abs(syncedTimestamp - lastTimestamp) > 0.075d);

                    long currentTime = System.currentTimeMillis();
                    List<View> views = new ArrayList<>(lineSprings.keySet());

                    scrollView.post(() -> {
                        if (isUserInteracting) {
                            applyHeaderFadeToLines();
                            return;
                        }

                        for (View line : views) {
                            Spring spring = lineSprings.get(line);
                            if (spring == null) continue;

                            Long startTime = lineAnimationStartTimes.get(line);
                            if (startTime != null) {
                                if (currentTime < startTime) {
                                    continue;
                                } else {
                                    lineAnimationStartTimes.remove(line);
                                }
                            }

                            line.setTranslationY((float) spring.update(deltaTime) + getFocusedPairOffsetPx(line));
                        }

                        applyHeaderFadeToLines();
                    });

                    lastTimestamp = syncedTimestamp;
                }

                lastUpdatedAt = updatedAt;

                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        mainLoop.start();
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

            if (lyricsScrollAnimator != null && lyricsScrollAnimator.isRunning()) {
                lyricsScrollAnimator.cancel();
            }

            if (immediate || (!scrollView.isPressed() && lineTopInSv >= scrollY && lineBottom <= scrollY + scrollViewHeight)) {
                lyricsScrollAnimator = ValueAnimator.ofFloat(scrollView.getScrollY(), targetScroll);
                lyricsScrollAnimator.setDuration(400);
                lyricsScrollAnimator.setInterpolator(new DecelerateInterpolator());

                lyricsScrollAnimator.addUpdateListener(animation -> {
                    float value = (float) animation.getAnimatedValue();
                    scrollView.scrollTo(0, (int) value);
                });

                lyricsScrollAnimator.start();
            }

            activeLine.setPivotY(activeLine.getHeight() / 2.0f);
            activeLine.animate().scaleX(1.008f).scaleY(1.008f).setDuration(400).setInterpolator(new OvershootInterpolator());
        });
    }

    private void appleScrollToNewLine(View activeLine, LinearLayout lyricsContainer, boolean immediate) {
        if (isUserInteracting || !isFollowingPlayback) return;

        List<View> allLines = new ArrayList<>();
        for (int i = 0; i < lyricsContainer.getChildCount(); i++) {
            allLines.add(lyricsContainer.getChildAt(i));
        }

        View finalLine = activeLine;
        while (finalLine.getParent() != lyricsContainer && finalLine.getParent() != null) {
            finalLine = (View) finalLine.getParent();
        }

        int activeIndex = allLines.indexOf(finalLine);
        if (activeIndex == -1) return;

        final int finalActiveIndex = activeIndex;
        final View finalLineForCalc = finalLine;

        lyricsContainer.post(() -> {
            try {
                if (lyricsContainer.getParent() instanceof View) {
                    viewportHeight = ((View) lyricsContainer.getParent()).getHeight();
                }
                if (viewportHeight <= 0) return;

                int activeLineTop = finalLineForCalc.getTop();
                int activeLineHeight = finalLineForCalc.getHeight();

                int screenTargetY =
                        (int) (viewportHeight * 0.18f) - (activeLineHeight / 2);

                double newGlobalOffset = screenTargetY - activeLineTop;
                targetScrollOffset = newGlobalOffset;

                limitScrollBounds(lyricsContainer);

                long currentTime = System.currentTimeMillis();

                for (int i = 0; i < allLines.size(); i++) {
                    View line = allLines.get(i);
                    Spring spring = lineSprings.get(line);

                    if (spring == null) {
                        spring = new Spring(targetScrollOffset, 0.85f, 2.5f);
                        lineSprings.put(line, spring);
                    }

                    if (immediate) {
                        spring.set(targetScrollOffset);
                        line.setTranslationY((float) targetScrollOffset + getFocusedPairOffsetPx(line));
                        lineAnimationStartTimes.remove(line);
                    } else {
                        long delay;
                        int distance = Math.abs(i - finalActiveIndex);

                        if (i == finalActiveIndex) {
                            delay = 0;
                        } else if (i < finalActiveIndex) {
                            delay = (long) (distance * 25 * 0.3);
                        } else {
                            delay = (long) (distance * 25L);
                        }

                        spring.finalPosition = targetScrollOffset;

                        if (delay > 0) {
                            lineAnimationStartTimes.put(line, currentTime + delay);
                        } else {
                            lineAnimationStartTimes.remove(line);
                        }
                    }
                }
            } catch (Exception e) {
                logError(e);
            }
        });
    }

    private void onActiveLineChanged(View line, LinearLayout lyricsContainer, boolean interlude) {
        View root = interlude ? (View) line.getParent() : (View) line.getParent().getParent();
        if (root == null) return;

        Integer idx = lineIndex.get(root);
        if (idx == null) idx = lineRoots.indexOf(root);
        if (idx == null || idx < 0) return;

        activeLineIndex = idx;

        lyricsContainer.post(() -> {
            for (View lineRoot : lineRoots) {
                Spring spring = lineSprings.get(lineRoot);
                double base = spring != null ? spring.position : targetScrollOffset;
                lineRoot.setTranslationY((float) base + getFocusedPairOffsetPx(line));
            }

            applyHeaderFadeToLines();
            applyLineFocusEffects();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestureDetector(Context context, View touchSurface, LinearLayout contentContainer) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                isUserInteracting = true;

                if (inertiaAnimator != null) {
                    inertiaAnimator.cancel();
                    inertiaAnimator = null;
                }

                lineAnimationStartTimes.clear();

                for (View line : lineRoots) {
                    Spring spring = lineSprings.get(line);
                    if (spring != null) {
                        spring.set(targetScrollOffset);
                    }
                }

                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                isFollowingPlayback = false;

                touchSurface.post(LyricsHook.this::applyLineFocusEffects);

                contentHeight = contentContainer.getHeight();
                viewportHeight = touchSurface.getHeight();

                double maxScroll = 0;
                double minScroll = Math.min(0, -(contentHeight - viewportHeight));

                double effectiveDistanceY = distanceY;
                if (targetScrollOffset > maxScroll || targetScrollOffset < minScroll) {
                    effectiveDistanceY *= 0.3;
                }

                double newOffset = targetScrollOffset - effectiveDistanceY;

                targetScrollOffset = newOffset;
                limitScrollBounds(contentContainer);

                for (View line : lineRoots) {
                    line.setTranslationY((float) targetScrollOffset);

                    Spring spring = lineSprings.get(line);
                    if (spring != null) {
                        spring.set(targetScrollOffset);
                    }
                }

                applyHeaderFadeToLines();

                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                for (View line : lineRoots) {
                    Spring spring = lineSprings.get(line);
                    if (spring != null) {
                        spring.set(targetScrollOffset);
                    }
                }

                double velocity = velocityY * 0.001;

                inertiaAnimator = ValueAnimator.ofFloat((float) velocity, 0f);
                inertiaAnimator.setDuration(800);
                inertiaAnimator.setInterpolator(new DecelerateInterpolator());
                inertiaAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    if (!isUserInteracting) {
                        targetScrollOffset += v * 16;
                        limitScrollBounds(contentContainer);

                        for (Spring spring : lineSprings.values()) {
                            spring.finalPosition = targetScrollOffset;
                        }
                    }
                });
                inertiaAnimator.start();
                return true;
            }
        });

        touchSurface.setOnTouchListener((v, event) -> {
            boolean result = gestureDetector.onTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                isUserInteracting = false;
                limitScrollBounds(contentContainer);
                touchSurface.post(this::applyHeaderFadeToLines);

                if (!isFollowingPlayback && currentActiveLineView != null) {
                    View parent = (View) contentContainer.getParent();
                    if (parent != null) {
                        float viewportH = parent.getHeight();

                        float activeTop = currentActiveLineView.getTop() + currentActiveLineView.getTranslationY();
                        float activeBottom = activeTop + currentActiveLineView.getHeight();

                        boolean visible = activeBottom > 0 && activeTop < viewportH;

                        if (visible) {
                            isFollowingPlayback = true;
                            contentContainer.post(this::applyLineFocusEffects);
                        }
                    }
                }
            }

            return result;
        });
    }

    private float getFocusedPairOffsetPx(View line) {
        if (!SpotifyPlusSettings.lineSpacing.equals("max")) return 0f;
        if (activeLineIndex < 0) return 0f;

        Integer idxObj = lineIndex.get(line);
        if (idxObj == null) {
            int idx = lineRoots.indexOf(line);
            if (idx < 0) return 0f;
            idxObj = idx;
        }

        int idx = idxObj;
        int nextIndex = Math.min(activeLineIndex + 1, lineRoots.size() - 1);

        float vh = (float) viewportHeight;

        if (vh <= 0f) {
            ViewParent parent = line.getParent();
            if (parent instanceof View) {
                vh = ((View) parent).getHeight();
            }
        }

        float min = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, line.getResources().getDisplayMetrics());
        float max = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320f, line.getResources().getDisplayMetrics());

        if (vh <= 0f) return min;

        float preferred = vh * 0.35f;
        float isolation = Math.max(min, Math.min(max, preferred));

        if (idx < activeLineIndex) {
            return -isolation;
        }

        if (idx > nextIndex) {
            return isolation;
        }

        return 0f;
    }

    private void applyHeaderFadeToLines() {
        if (headerFadeAnchor == null || lineRoots.isEmpty()) return;
        if (!headerFadeAnchor.isAttachedToWindow()) return;

        float fadeDistancePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 96f, headerFadeAnchor.getResources().getDisplayMetrics());

        int[] headerLoc = new int[2];
        headerFadeAnchor.getLocationInWindow(headerLoc);
        float headerBottomInWindow = headerLoc[1] + headerFadeAnchor.getHeight();

        for (View line : lineRoots) {
            if (line == null || !line.isAttachedToWindow()) continue;

            int[] lineLoc = new int[2];
            line.getLocationInWindow(lineLoc);

            float lineTopInWindow = lineLoc[1];
            float lineBottomInWindow = lineTopInWindow + line.getHeight();
            float lineCenterInWindow = (lineTopInWindow + lineBottomInWindow) * 0.5f;

            float distanceBelowHeader = lineCenterInWindow - headerBottomInWindow;

            float alpha;
            if (distanceBelowHeader <= 0f) {
                alpha = 0.12f;
            } else if (distanceBelowHeader >= fadeDistancePx) {
                alpha = 1f;
            } else {
                float t = distanceBelowHeader / fadeDistancePx;
                t = t * t * (3f - 2f * t);
                alpha = 0.12f + ((1f - 0.12f) * t);
            }

            line.setAlpha(alpha);
        }
    }

    private void applyLineFocusEffects() {
        boolean blurAllowed = isFollowingPlayback && !isUserInteracting;

        // if user is scrolling or we aren't following playback, clear everything
        if (!blurAllowed) {
            if (lastBlurAllowed) {
                lineRoots.forEach(RenderEffectCompat::clear);
                lastBlurAllowed = false;
            }
            return;
        }

        lastBlurAllowed = true;

        if (activeLineIndex < 0) {
            lineRoots.forEach(RenderEffectCompat::clear);
            return;
        }

        // no need to redo if same active line and blur is allowed
        if (activeLineIndex == lastAppliedActiveIndex) return;
        lastAppliedActiveIndex = activeLineIndex;

        for (int i = 0; i < lineRoots.size(); i++) {
            View line = lineRoots.get(i);

            int dist = Math.abs(i - activeLineIndex);
            int bucket = Math.min(dist, 3);
            float radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BLUR_LEVELS_DP[bucket], line.getResources().getDisplayMetrics());

            RenderEffectCompat.blur(line, radiusPx);
        }
    }

    private void computeLogicalLayout(LinearLayout container) {
        logicalLineTops.clear();

        int width = container.getWidth();
        if (width == 0) {
            width = container.getResources().getDisplayMetrics().widthPixels;
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int y = container.getPaddingTop();

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) child.getLayoutParams();

            y += lp.topMargin;
            logicalLineTops.put(child, y);
            y += child.getMeasuredHeight() + lp.bottomMargin;
        }

        contentHeight = y + container.getPaddingBottom();

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Integer logicalTop = logicalLineTops.get(child);
            if (logicalTop == null) continue;

            int actualTop = child.getTop();
        }

        View parent = (View) container.getParent();
        if (parent != null) {
            viewportHeight = parent.getHeight();
        }
    }

    private void forwardTouchToSurface(View child, View touchSurface, LinearLayout contentContainer, MotionEvent event) {
        MotionEvent forwarded = MotionEvent.obtain(event);

        int[] childLoc = new int[2];
        int[] surfaceLoc = new int[2];
        child.getLocationOnScreen(childLoc);
        touchSurface.getLocationOnScreen(surfaceLoc);

        float newX = event.getRawX() - surfaceLoc[0];
        float newY = event.getRawY() - surfaceLoc[1];
        forwarded.setLocation(newX, newY);

        gestureDetector.onTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isUserInteracting = false;
            limitScrollBounds(contentContainer);
            touchSurface.post(this::applyHeaderFadeToLines);

            if (!isFollowingPlayback && currentActiveLineView != null) {
                View parent = (View) contentContainer.getParent();
                if (parent != null) {
                    float viewportH = parent.getHeight();

                    float activeTop = currentActiveLineView.getTop() + currentActiveLineView.getTranslationY();
                    float activeBottom = activeTop + currentActiveLineView.getHeight();

                    boolean visible = activeBottom > 0 && activeTop < viewportH;

                    if (visible) {
                        isFollowingPlayback = true;
                        contentContainer.post(this::applyLineFocusEffects);
                    }
                }
            }
        }

        forwarded.recycle();
    }

    private void limitScrollBounds(View contentContainer) {
        if (contentContainer == null) return;

        contentHeight = contentContainer.getHeight();

        View parent = (View) contentContainer.getParent();
        if (parent != null) {
            viewportHeight = parent.getHeight();
        }

        if (contentHeight == 0 || viewportHeight == 0) return;

        double maxScroll = 0;
        double minScroll = Math.min(0, -(contentHeight - viewportHeight));

        if (targetScrollOffset > maxScroll) {
            targetScrollOffset = maxScroll;
        } else if (targetScrollOffset < minScroll) {
            targetScrollOffset = minScroll;
        }
    }

    private void getAlbumArtwork(String id, AlbumArtworkCallback callback) {
        OkHttpClient client = new OkHttpClient();
        Request albumRequest = new Request.Builder().url("https://i.scdn.co/image/" + id).build();

        client.newCall(albumRequest).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    postError(callback, new IOException("HTTP " + response.code()));
                    return;
                }

                try (InputStream in = response.body().byteStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);

                    if (bitmap == null) {
                        postError(callback, new IOException("Failed to decode bitmap"));
                        return;
                    }

                    postSuccess(callback, bitmap);
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                postError(callback, e);
            }
        });
    }

    private int dpToPx(int dp, Activity activity) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                activity.getResources().getDisplayMetrics()
        );
    }

    private void postSuccess(AlbumArtworkCallback callback, Bitmap bitmap) {
        mainHandler.post(() -> callback.onSuccess(bitmap));
    }

    private void postError(AlbumArtworkCallback callback, Exception e) {
        mainHandler.post(() -> callback.onError(e));
    }

    public interface AlbumArtworkCallback {
        void onSuccess(Bitmap albumArt);

        void onError(Exception e);
    }

    private static final class RenderEffectCompat {
        private static final Map<Integer, RenderEffect> blurCache = new HashMap<>();

        public static void blur(View v, float radiusPx) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) return;

            try {
                int key = Math.max(1, Math.round(radiusPx));
                RenderEffect effect = blurCache.get(key);
                if (effect == null) {
                    effect = android.graphics.RenderEffect.createBlurEffect(key, key, Shader.TileMode.CLAMP);
                    blurCache.put(key, effect);
                }
                v.setRenderEffect(effect);
            } catch (Throwable ignored) {
            }
        }

        static void clear(View v) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) return;

            try {
                v.setRenderEffect(null);
            } catch (Throwable ignored) {
            }
        }
    }
}

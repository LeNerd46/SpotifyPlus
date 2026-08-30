package com.lenerd46.spotifyplus.hooks;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.google.gson.*;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import com.lenerd46.spotifyplus.beautifullyrics.entities.*;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.*;
import com.lenerd46.spotifyplus.beautifullyrics.entities.interludes.InterludeVisual;
import com.lenerd46.spotifyplus.beautifullyrics.translation.*;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BeautifulLyricsHook extends SpotifyHook {

    private static final String LYRICS_ACTIVITY = "com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity";
    private static BeautifulLyricsHook instance;
    private static Map<FlexboxLayout, List<SyncableVocals>> vocalGroups;
    private volatile boolean stop = false;
    private Thread mainLoop;
    private ImageView closeButton;
    private LinearLayout rightContainer;
    private Constructor<?> ctor = null;
    private Object seekInstance = null;
    private boolean isPlaying = true;
    private final AtomicInteger translationSession = new AtomicInteger();
    private final List<TranslationBinding> translationBindings = new ArrayList<>();
    private final List<View> translationViews = new ArrayList<>();
    private LyricsTranslationService translationService;
    private ImageView translationButton;
    private boolean translationsReady;
    private LinearLayout translationLyricsContainer;
    private boolean translationUsesExperimentalScroll;

    private static final float SCROLL_POSITION_RATIO = 0.18f;
    private static final long LINE_ANIMATION_DELAY_MS = 25L;
    private static final long LINE_ANIMATION_DURATION_MS = 420L;

    private View experimentalTouchSurface;
    private View headerFadeAnchor;
    private TopFadeLayout embeddedLyricsViewport;
    private static final float HEADER_FADE_DISTANCE_DP = 96f;
    private static final float HEADER_FADE_MIN_ALPHA = 0.12f;
    private static final float HEADER_PIXEL_FADE_DP = 72f;

    private GestureDetector gestureDetector;
    private double targetScrollOffset = 0;
    private double contentHeight = 0;
    private double viewportHeight = 0;
    private boolean isUserInteracting = false;
    private boolean isFollowingPlayback = true;
    private View currentActiveLineView = null;
    private String currentLineSpacingMode = "default";

    private final Runnable finishLineScrollEffects = () -> {
        applyHeaderFadeToLines();
        applyLineFocusEffects();
    };

    private final Map<View, Integer> logicalLineTops = new HashMap<>();
    private final Map<View, Double> lineBaseOffsets = new HashMap<>();

    private final List<View> lineRoots = new ArrayList<>();
    private final List<View> scrollFollowers = new ArrayList<>();
    private final Map<View, Integer> lineIndex = new WeakHashMap<>();
    private int activeLineIndex = -1;

    private static final int BLUR_MAX_DISTANCE = 3;

    private static final float[] BLUR_LEVELS_DP = new float[]{
            0f,
            0.6f,
            1.4f,
            2f
    };

    private int lastAppliedActiveIndex = Integer.MIN_VALUE;
    private boolean lastBlurAllowed = true;
    private ValueAnimator inertiaAnimator;
    private WeakReference<Activity> lastHostActivity = new WeakReference<>(null);
    private Activity overlayActivity;
    private FrameLayout overlayHost;
    private FrameLayout backgroundContainer;
    private LinearLayout overlayLyricsContainer;
    private ImageView overlayCover;
    private TextView overlayTitle;
    private TextView overlayArtist;
    private ScrollView overlayScrollView;
    private AnimatedBackgroundView animatedBackground;
    private View backgroundScrim;
    private Call activeLyricsCall;
    private ExecutorService lyricsExecutor;
    private String currentTrackUri;
    private int previousStatusBarColor;
    private int previousNavigationBarColor;
    private int previousSystemUiVisibility;
    private boolean previousStatusBarContrastEnforced;
    private boolean previousNavigationBarContrastEnforced;
    private boolean previousEmbeddedFitsSystemWindows;
    private final Map<ViewGroup, Boolean> embeddedClipChildren = new LinkedHashMap<>();
    private final Map<ViewGroup, Boolean> embeddedClipToPadding = new LinkedHashMap<>();
    private boolean embeddedWindowAdjusted;
    private boolean dismissing;
    private OnBackInvokedCallback backCallback;
    private View underlyingContent;
    private int underlyingVisibility;
    private int underlyingAccessibility;
    private boolean underlyingSuspended;
    private boolean embeddedMode;
    private Runnable embeddedCloseRequest;
    private Runnable embeddedInteraction;

    public static void showOverlay(Activity activity, boolean scrollToTop) {
        BeautifulLyricsHook hook = instance;
        if (hook == null) return;
        new Handler(Looper.getMainLooper()).post(() -> hook.openOverlay(activity, scrollToTop));
    }

    public static boolean isOverlayShowing(Activity activity) {
        BeautifulLyricsHook hook = instance;
        return hook != null && !hook.embeddedMode && hook.overlayHost != null && hook.overlayActivity == activity && !hook.dismissing;
    }

    public static boolean isOverlayAttached(Activity activity) {
        BeautifulLyricsHook hook = instance;
        return hook != null && !hook.embeddedMode && hook.overlayHost != null && hook.overlayActivity == activity;
    }

    public static boolean showEmbedded(Activity activity, ViewGroup parent, Runnable closeRequest, Runnable interaction) {
        BeautifulLyricsHook hook = instance;
        return hook != null && hook.openEmbedded(activity, parent, closeRequest, interaction);
    }

    public static void removeEmbedded(boolean animated) {
        BeautifulLyricsHook hook = instance;
        if(hook == null || !hook.embeddedMode || hook.overlayHost == null) return;
        hook.removeEmbeddedInternal(animated);
    }

    public static boolean isEmbeddedShowing() {
        BeautifulLyricsHook hook = instance;
        return hook != null && hook.embeddedMode && hook.overlayHost != null && !hook.dismissing;
    }

    public static ImageView getEmbeddedCover() {
        BeautifulLyricsHook hook = instance;
        return hook != null && hook.embeddedMode ? hook.overlayCover : null;
    }

    public static void setEmbeddedControlOcclusion(View control, boolean visible) {
        BeautifulLyricsHook hook = instance;
        if(hook != null) hook.updateEmbeddedControlOcclusion(control, visible);
    }

    @Override
    protected void hook() {
        instance = this;
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!LYRICS_ACTIVITY.equals(activity.getClass().getName())) lastHostActivity = new WeakReference<>(activity);
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onBackPressed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (overlayHost == null || overlayActivity != param.thisObject || dismissing) return;
                param.setResult(null);
                if(embeddedMode) {
                    requestEmbeddedClose();
                    return;
                }
                dismissOverlay(true);
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if(overlayActivity != param.thisObject) return;
                if(embeddedMode && overlayHost != null) finishDismiss(overlayHost);
                else dismissOverlay(false);
            }
        });
        XposedHelpers.findAndHookMethod(LYRICS_ACTIVITY, lpparm.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity lyricsActivity = (Activity) param.thisObject;
                Activity host = lastHostActivity.get();
                lyricsActivity.overridePendingTransition(0, 0);
                lyricsActivity.finish();
                if (host != null) showOverlay(host, true);
            }
        });

        try {
            var whateverThisClassEvenDoes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).fields(FieldsMatcher.create()
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(String.class))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                    .type(ArrayList.class))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object.class))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Bundle.class)))));

            Method getStateMethod = bridge
                    .findMethod(FindMethod.create().searchInClass(whateverThisClassEvenDoes)
                            .matcher(MethodMatcher.create().name("getState")))
                    .get(0).getMethodInstance(lpparm.classLoader);
            XposedBridge.hookMethod(getStateMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    References.playerStateWrapper = new WeakReference<>(param.thisObject);
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }

        try {

            // Class<?> playerStateChangedClass =
            // bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("PlayerStateChanged(trackUri="))).get(0).getInstance(lpparm.classLoader);
            // XposedHelpers.findAndHookConstructor("p.xb90", lpparm.classLoader,
            // String.class, boolean.class, boolean.class, boolean.class, boolean.class,
            // boolean.class, boolean.class, XposedHelpers.findClass("p.gqf",
            // lpparm.classLoader), new XC_MethodHook() {
            // protected void afterHookedMethod(MethodHookParam p) {
            //// XposedBridge.log("[SpotifyPlus] Object: " + p.thisObject.toString());
            // References.notifyTrackStateChanged(p.thisObject);
            // isPlaying = !XposedHelpers.getBooleanField(p.thisObject, "c");
            // }
            // });

            XposedHelpers.findAndHookMethod("android.media.session.MediaSession", lpparm.classLoader,
                    "setPlaybackState",
                    XposedHelpers.findClass("android.media.session.PlaybackState", lpparm.classLoader),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            PlaybackState playbackState = (PlaybackState) param.args[0];
                            if (playbackState == null)
                                return;

                            isPlaying = playbackState.getState() == PlaybackState.STATE_PLAYING;
                        }
                    });
        } catch (Exception e) {
            XposedBridge.log(e);
        }

        XposedHelpers.findAndHookMethod("com.spotify.player.model.AutoValue_PlayerState$Builder", lpparm.classLoader,
                "build", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object state = param.getResult();
                        References.playerState = new WeakReference<>(state);
                        References.notifyPlayerStateChanged(state);
                        handleTrackChange();
                    }
                });

        try {
            Class<?> hzc = bridge
                    .findClass(
                            FindClass.create()
                                    .matcher(ClassMatcher.create().usingStrings(
                                            "spotify.player.esperanto.proto.ContextPlayer", "SetOptions")))
                    .get(0).getInstance(lpparm.classLoader);
            Class<?> seek = bridge.findClass(FindClass.create()
                            .matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1)
                                    .methodCount(3).fields(FieldsMatcher.create()
                                            .count(3)
                                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(hzc))
                                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                                    .type(boolean.class)))))
                    .get(0).getInstance(lpparm.classLoader);

            XposedBridge.hookAllConstructors(seek, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    seekInstance = param.thisObject;
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }

        try {
            var okHttp = bridge.findClass(
                    FindClass.create().matcher(ClassMatcher.create().className(("okhttp3.Request$Builder"))));
            Method request = bridge
                    .findMethod(FindMethod.create().searchInClass((okHttp))
                            .matcher(MethodMatcher.create().returnType(void.class)
                                    .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                    .paramTypes(String.class, String.class)))
                    .get(1).getMethodInstance((lpparm.classLoader));

            XposedBridge.hookMethod(request, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String headerName = (String) param.args[0];
                    String headerValue = (String) param.args[1];

                    if (headerName != null && headerName.equalsIgnoreCase("authorization") && headerValue != null
                            && !headerValue.isEmpty()) {
                        String token = headerValue.replace("Bearer", "").trim();
                        String accessToken = References.accessToken;

                        if (accessToken != null && !accessToken.isEmpty() && accessToken.equals(token))
                            return;
                        References.accessToken = token;
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private void openOverlay(Activity activity, boolean scrollToTop) {
        if (activity == null || activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) return;
        if (overlayHost != null && overlayActivity == activity && overlayHost.isAttachedToWindow()) {
            overlayHost.bringToFront();
            if (scrollToTop) scrollOverlayToTop();
            return;
        }
        if (overlayHost != null) dismissOverlay(false);
        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if (track == null) {
            Toast.makeText(activity, "No song is currently playing", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            buildOverlay(activity);
            loadTrack(track);
            overlayHost.post(() -> {
                if (overlayHost == null) return;
                FrameLayout host = overlayHost;
                host.setTranslationY(host.getHeight());
                host.animate().translationY(0f).setDuration(320).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> suspendUnderlyingContent(host)).start();
            });
            XposedBridge.log("[SpotifyPlus] Opened Beautiful Lyrics overlay");
        } catch (Throwable t) {
            XposedBridge.log(t);
            dismissOverlay(false);
        }
    }

    private boolean openEmbedded(Activity activity, ViewGroup parent, Runnable closeRequest, Runnable interaction) {
        if(activity == null || parent == null || activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) return false;
        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if(track == null) {
            Toast.makeText(activity, "No song is currently playing", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            if(overlayHost != null) finishDismiss(overlayHost);
            embeddedMode = true;
            embeddedCloseRequest = closeRequest;
            embeddedInteraction = interaction;
            buildEmbedded(activity, parent);
            loadTrack(track);
            XposedBridge.log("[SpotifyPlus] Opened embedded Beautiful Lyrics page");
            return true;
        } catch(Throwable throwable) {
            XposedBridge.log(throwable);
            if(overlayHost != null) finishDismiss(overlayHost);
            return false;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildEmbedded(Activity activity, ViewGroup parent) {
        boolean landscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int leftPaneWidth = landscape ? Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.39f) : 0;
        overlayActivity = activity;
        previousStatusBarColor = activity.getWindow().getStatusBarColor();
        previousNavigationBarColor = activity.getWindow().getNavigationBarColor();
        previousSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            previousStatusBarContrastEnforced = activity.getWindow().isStatusBarContrastEnforced();
            previousNavigationBarContrastEnforced = activity.getWindow().isNavigationBarContrastEnforced();
        }
        previousEmbeddedFitsSystemWindows = parent.getFitsSystemWindows();
        embeddedWindowAdjusted = true;
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setStatusBarContrastEnforced(false);
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.getWindow().setDecorFitsSystemWindows(false);
        activity.getWindow().getDecorView().setSystemUiVisibility(previousSystemUiVisibility | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        parent.setFitsSystemWindows(false);
        View clipView = parent;
        while(clipView instanceof ViewGroup) {
            ViewGroup clipGroup = (ViewGroup) clipView;
            embeddedClipChildren.put(clipGroup, clipGroup.getClipChildren());
            embeddedClipToPadding.put(clipGroup, clipGroup.getClipToPadding());
            clipGroup.setClipChildren(false);
            clipGroup.setClipToPadding(false);
            ViewParent clipParent = clipGroup.getParent();
            clipView = clipParent instanceof View ? (View) clipParent : null;
        }
        parent.requestApplyInsets();
        overlayHost = new InteractionFrameLayout(activity, () -> {
            Runnable interaction = embeddedInteraction;
            if(interaction != null) interaction.run();
        });
        int statusBarResource = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight = statusBarResource == 0 ? 0 : activity.getResources().getDimensionPixelSize(statusBarResource);
        int navigationBarResource = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int navigationBarHeight = navigationBarResource == 0 ? 0 : activity.getResources().getDimensionPixelSize(navigationBarResource);
        overlayHost.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, parent.getHeight() > 0 ? parent.getHeight() + statusBarHeight + navigationBarHeight : ViewGroup.LayoutParams.MATCH_PARENT));
        overlayHost.setTranslationY(-statusBarHeight);
        overlayHost.setBackgroundColor(Color.BLACK);
        overlayHost.setClickable(true);
        overlayHost.setFocusable(true);
        backgroundContainer = new FrameLayout(activity);
        backgroundContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        backgroundContainer.setBackgroundColor(Color.BLACK);
        overlayHost.addView(backgroundContainer);

        GridLayout grid = new GridLayout(activity);
        grid.setRowCount(2);
        grid.setColumnCount(1);
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        if(landscape) gridParams.setMargins(leftPaneWidth, 0, 0, 0);
        grid.setLayoutParams(gridParams);
        if(landscape) grid.setPadding(dpToPx(16, activity), 0, dpToPx(24, activity), 0);
        grid.setClipChildren(true);
        grid.setClipToPadding(false);

        FrameLayout headerContainer = new FrameLayout(activity);
        GridLayout.LayoutParams headerParams = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(0));
        headerParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        headerParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
        headerContainer.setLayoutParams(headerParams);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(22, activity), dpToPx(landscape ? 18 : 32, activity), dpToPx(70, activity), dpToPx(landscape ? 8 : 18, activity));
        header.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        overlayCover = new ImageView(activity);
        int coverSize = dpToPx(landscape ? 228 : 56, activity);
        overlayCover.setLayoutParams(new LinearLayout.LayoutParams(coverSize, coverSize));
        overlayCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        overlayCover.setContentDescription("Return to Now Playing");
        overlayCover.setOnClickListener(view -> requestEmbeddedClose());
        LinearLayout titleAndArtist = new LinearLayout(activity);
        titleAndArtist.setOrientation(LinearLayout.VERTICAL);
        titleAndArtist.setPadding(dpToPx(landscape ? 0 : 12, activity), landscape ? dpToPx(14, activity) : 0, 0, 0);
        titleAndArtist.setLayoutParams(landscape ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        overlayTitle = new TextView(activity);
        overlayTitle.setTextColor(Color.WHITE);
        overlayTitle.setTextSize(landscape ? 16f : 20f);
        overlayTitle.setSingleLine(true);
        overlayTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overlayArtist = new TextView(activity);
        overlayArtist.setTextColor(Color.LTGRAY);
        overlayArtist.setTextSize(landscape ? 13f : 16f);
        overlayArtist.setSingleLine(true);
        overlayArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleAndArtist.addView(overlayTitle);
        titleAndArtist.addView(overlayArtist);
        if(landscape) {
            LinearLayout leftPanel = new LinearLayout(activity);
            leftPanel.setOrientation(LinearLayout.VERTICAL);
            leftPanel.setGravity(Gravity.CENTER);
            leftPanel.setPadding(dpToPx(24, activity), dpToPx(18, activity), dpToPx(18, activity), dpToPx(18, activity));
            leftPanel.setLayoutParams(new FrameLayout.LayoutParams(leftPaneWidth, FrameLayout.LayoutParams.MATCH_PARENT));
            overlayTitle.setGravity(Gravity.CENTER);
            overlayArtist.setGravity(Gravity.CENTER);
            leftPanel.addView(overlayCover);
            leftPanel.addView(titleAndArtist);
            overlayHost.addView(leftPanel);
        } else {
            header.addView(overlayCover);
            header.addView(titleAndArtist);
        }
        rightContainer = new LinearLayout(activity);
        rightContainer.setOrientation(LinearLayout.HORIZONTAL);
        rightContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        rightParams.setMargins(0, dpToPx(8, activity), dpToPx(14, activity), 0);
        rightContainer.setLayoutParams(rightParams);
        translationButton = new ImageView(activity);
        translationButton.setImageDrawable(ResourcesCompat.getDrawable(References.modResources, R.drawable.translate, null));
        translationButton.setContentDescription("Toggle lyric translations");
        translationButton.setVisibility(View.GONE);
        translationButton.setPadding(dpToPx(12, activity), dpToPx(12, activity), dpToPx(12, activity), dpToPx(12, activity));
        translationButton.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48, activity), dpToPx(48, activity)));
        translationButton.setOnClickListener(view -> toggleTranslations(activity));
        rightContainer.addView(translationButton);
        headerContainer.addView(header);
        headerContainer.addView(rightContainer);
        headerFadeAnchor = headerContainer;
        ((InteractionFrameLayout) overlayHost).setHeader(headerContainer);

        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        TopFadeLayout fadeWrapper = new TopFadeLayout(activity, dpToPx((int) HEADER_PIXEL_FADE_DP, activity));
        GridLayout.LayoutParams scrollParams = new GridLayout.LayoutParams(GridLayout.spec(1), GridLayout.spec(0));
        scrollParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        scrollParams.height = GridLayout.LayoutParams.MATCH_PARENT;
        fadeWrapper.setLayoutParams(scrollParams);
        fadeWrapper.setClipChildren(false);
        fadeWrapper.setClipToPadding(false);
        embeddedLyricsViewport = fadeWrapper;
        overlayLyricsContainer = new LinearLayout(activity);
        overlayLyricsContainer.setOrientation(LinearLayout.VERTICAL);
        if(prefs.getBoolean("experiment_scroll", true)) {
            overlayLyricsContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(40000, activity)));
            overlayLyricsContainer.setClipChildren(true);
            overlayLyricsContainer.setClipToPadding(false);
            fadeWrapper.addView(overlayLyricsContainer);
            setupGestureDetector(activity, fadeWrapper, overlayLyricsContainer);
            experimentalTouchSurface = fadeWrapper;
            overlayScrollView = null;
        } else {
            overlayScrollView = new ScrollView(activity);
            overlayScrollView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            overlayScrollView.setClipToPadding(false);
            overlayScrollView.setClipChildren(false);
            overlayScrollView.setVerticalScrollBarEnabled(false);
            overlayLyricsContainer.setLayoutParams(new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            overlayLyricsContainer.setClipToPadding(false);
            overlayLyricsContainer.setClipChildren(false);
            overlayScrollView.addView(overlayLyricsContainer);
            overlayScrollView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                applyHeaderFadeToLines();
            });
            fadeWrapper.addView(overlayScrollView);
            experimentalTouchSurface = null;
        }
        grid.addView(headerContainer);
        grid.addView(fadeWrapper);
        overlayHost.addView(grid);
        parent.addView(overlayHost, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.post(() -> layoutEmbeddedEdgeToEdge(parent));
    }

    private void layoutEmbeddedEdgeToEdge(ViewGroup parent) {
        if(!embeddedMode || overlayHost == null || overlayHost.getParent() != parent) return;
        View decor = overlayActivity == null ? null : overlayActivity.getWindow().getDecorView();
        if(decor == null || parent.getHeight() == 0 || decor.getHeight() == 0) return;
        int[] parentLocation = new int[2];
        int[] decorLocation = new int[2];
        parent.getLocationInWindow(parentLocation);
        decor.getLocationInWindow(decorLocation);
        int statusBarResource = parent.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight = statusBarResource == 0 ? 0 : parent.getResources().getDimensionPixelSize(statusBarResource);
        int topGap = Math.max(statusBarHeight, Math.max(0, parentLocation[1] - decorLocation[1]));
        int bottomGap = Math.max(0, decor.getHeight() - topGap - parent.getHeight());
        ViewGroup.LayoutParams params = overlayHost.getLayoutParams();
        params.height = parent.getHeight() + topGap + bottomGap;
        overlayHost.setLayoutParams(params);
        overlayHost.setTranslationY(-topGap);
    }

    private void updateEmbeddedControlOcclusion(View control, boolean visible) {
        TopFadeLayout viewport = embeddedLyricsViewport;
        if(!embeddedMode || viewport == null) return;
        if(!visible || control == null) {
            viewport.setBottomClipInset(0);
            return;
        }
        viewport.post(() -> {
            if(!embeddedMode || embeddedLyricsViewport != viewport || !control.isAttachedToWindow()) return;
            int[] viewportLocation = new int[2];
            int[] controlLocation = new int[2];
            viewport.getLocationInWindow(viewportLocation);
            control.getLocationInWindow(controlLocation);
            if(controlLocation[0] + control.getWidth() <= viewportLocation[0] || controlLocation[0] >= viewportLocation[0] + viewport.getWidth()) {
                viewport.setBottomClipInset(0);
                return;
            }
            int clipMargin = Math.round(12f * viewport.getResources().getDisplayMetrics().density);
            int clipBottom = Math.max(0, viewport.getHeight() - (controlLocation[1] - viewportLocation[1] - clipMargin));
            viewport.setBottomClipInset(clipBottom);
        });
    }

    private void requestEmbeddedClose() {
        Runnable closeRequest = embeddedCloseRequest;
        if(closeRequest != null) closeRequest.run();
    }

    private void removeEmbeddedInternal(boolean animated) {
        if(overlayHost == null || dismissing) return;
        dismissing = true;
        FrameLayout host = overlayHost;
        if(animated && host.isAttachedToWindow()) host.animate().alpha(0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> finishDismiss(host)).start();
        else finishDismiss(host);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildOverlay(Activity activity) {
        overlayActivity = activity;
        previousStatusBarColor = activity.getWindow().getStatusBarColor();
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        underlyingContent = activity.findViewById(android.R.id.content);
        if (underlyingContent != null) {
            underlyingVisibility = underlyingContent.getVisibility();
            underlyingAccessibility = underlyingContent.getImportantForAccessibility();
        }
        overlayHost = new FrameLayout(activity);
        overlayHost.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayHost.setBackgroundColor(Color.BLACK);
        overlayHost.setClickable(true);
        overlayHost.setFocusable(true);
        overlayHost.setElevation(dpToPx(32, activity));
        backgroundContainer = new FrameLayout(activity);
        backgroundContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        backgroundContainer.setBackgroundColor(Color.BLACK);
        overlayHost.addView(backgroundContainer);

        GridLayout grid = new GridLayout(activity);
        grid.setRowCount(2);
        grid.setColumnCount(1);
        grid.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        grid.setClipChildren(true);
        grid.setClipToPadding(false);

        FrameLayout headerContainer = new FrameLayout(activity);
        GridLayout.LayoutParams headerParams = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(0));
        headerParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        headerParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
        headerContainer.setLayoutParams(headerParams);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(22, activity), dpToPx(32, activity), dpToPx(116, activity), dpToPx(18, activity));
        header.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        overlayCover = new ImageView(activity);
        int coverSize = dpToPx(56, activity);
        overlayCover.setLayoutParams(new LinearLayout.LayoutParams(coverSize, coverSize));
        overlayCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout titleAndArtist = new LinearLayout(activity);
        titleAndArtist.setOrientation(LinearLayout.VERTICAL);
        titleAndArtist.setPadding(dpToPx(12, activity), 0, 0, 0);
        titleAndArtist.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        overlayTitle = new TextView(activity);
        overlayTitle.setTextColor(Color.WHITE);
        overlayTitle.setTextSize(20f);
        overlayTitle.setSingleLine(true);
        overlayTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overlayArtist = new TextView(activity);
        overlayArtist.setTextColor(Color.LTGRAY);
        overlayArtist.setTextSize(16f);
        overlayArtist.setSingleLine(true);
        overlayArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleAndArtist.addView(overlayTitle);
        titleAndArtist.addView(overlayArtist);
        header.addView(overlayCover);
        header.addView(titleAndArtist);

        rightContainer = new LinearLayout(activity);
        rightContainer.setOrientation(LinearLayout.HORIZONTAL);
        rightContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        rightParams.setMargins(0, dpToPx(8, activity), dpToPx(14, activity), 0);
        rightContainer.setLayoutParams(rightParams);
        translationButton = new ImageView(activity);
        translationButton.setImageDrawable(ResourcesCompat.getDrawable(References.modResources, R.drawable.translate, null));
        translationButton.setContentDescription("Toggle lyric translations");
        translationButton.setVisibility(View.GONE);
        translationButton.setPadding(dpToPx(12, activity), dpToPx(12, activity), dpToPx(12, activity), dpToPx(12, activity));
        translationButton.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48, activity), dpToPx(48, activity)));
        translationButton.setOnClickListener(v -> toggleTranslations(activity));
        closeButton = new ImageView(activity);
        closeButton.setImageDrawable(createChevronDownIcon(activity));
        closeButton.setContentDescription("Close lyrics");
        closeButton.setPadding(dpToPx(12, activity), dpToPx(12, activity), dpToPx(12, activity), dpToPx(12, activity));
        closeButton.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48, activity), dpToPx(48, activity)));
        closeButton.setOnClickListener(v -> dismissOverlay(true));
        rightContainer.addView(translationButton);
        rightContainer.addView(closeButton);
        headerContainer.addView(header);
        headerContainer.addView(rightContainer);
        headerFadeAnchor = headerContainer;
        View.OnTouchListener dragListener = new HeaderDragDismissListener();
        header.setOnTouchListener(dragListener);
        headerContainer.setOnTouchListener(dragListener);

        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        int fadePx = dpToPx((int) HEADER_PIXEL_FADE_DP, activity);
        TopFadeLayout fadeWrapper = new TopFadeLayout(activity, fadePx);
        GridLayout.LayoutParams scrollParams = new GridLayout.LayoutParams(GridLayout.spec(1), GridLayout.spec(0));
        scrollParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        scrollParams.height = GridLayout.LayoutParams.MATCH_PARENT;
        fadeWrapper.setLayoutParams(scrollParams);
        fadeWrapper.setClipChildren(false);
        fadeWrapper.setClipToPadding(false);
        overlayLyricsContainer = new LinearLayout(activity);
        overlayLyricsContainer.setOrientation(LinearLayout.VERTICAL);
        if (prefs.getBoolean("experiment_scroll", true)) {
            overlayLyricsContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(40000, activity)));
            overlayLyricsContainer.setClipChildren(true);
            overlayLyricsContainer.setClipToPadding(false);
            fadeWrapper.addView(overlayLyricsContainer);
            setupGestureDetector(activity, fadeWrapper, overlayLyricsContainer);
            experimentalTouchSurface = fadeWrapper;
            overlayScrollView = null;
        } else {
            overlayScrollView = new ScrollView(activity);
            overlayScrollView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            overlayScrollView.setClipToPadding(false);
            overlayScrollView.setClipChildren(false);
            overlayScrollView.setVerticalScrollBarEnabled(false);
            overlayLyricsContainer.setLayoutParams(new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            overlayLyricsContainer.setClipToPadding(false);
            overlayLyricsContainer.setClipChildren(false);
            overlayScrollView.addView(overlayLyricsContainer);
            overlayScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> applyHeaderFadeToLines());
            fadeWrapper.addView(overlayScrollView);
            experimentalTouchSurface = null;
        }
        grid.addView(headerContainer);
        grid.addView(fadeWrapper);
        overlayHost.addView(grid);
        decor.addView(overlayHost);
        registerBackCallback(activity);
    }

    private void loadTrack(SpotifyTrack track) {
        if (overlayHost == null || overlayActivity == null || track == null) return;
        clearTrackResources();
        int session = translationSession.incrementAndGet();
        currentTrackUri = track.uri;
        stop = false;
        lastUpdatedAt = 0;
        lastTimestamp = 0;
        targetScrollOffset = 0;
        overlayTitle.setText(track.title);
        overlayArtist.setText(track.artist);
        overlayCover.setImageDrawable(null);
        overlayLyricsContainer.removeAllViews();
        translationButton.setVisibility(View.GONE);
        scrollOverlayToTop();
        renderLyrics(overlayActivity, track, overlayLyricsContainer, overlayCover, session);
    }

    private void handleTrackChange() {
        Activity activity = overlayActivity;
        if (activity == null || overlayHost == null) return;
        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if (track == null || Objects.equals(track.uri, currentTrackUri)) return;
        activity.runOnUiThread(() -> {
            if (overlayHost == null || overlayActivity != activity || Objects.equals(track.uri, currentTrackUri)) return;
            XposedBridge.log("[SpotifyPlus] Beautiful Lyrics track changed to " + track.uri);
            loadTrack(track);
        });
    }

    private boolean isOverlaySessionActive(Activity activity, SpotifyTrack track, int session) {
        return overlayHost != null && !dismissing && overlayActivity == activity && session == translationSession.get() && Objects.equals(currentTrackUri, track.uri);
    }

    private void scrollOverlayToTop() {
        if (overlayScrollView != null) overlayScrollView.scrollTo(0, 0);
        if (overlayLyricsContainer != null && experimentalTouchSurface != null) applyImmediateScrollOffset(overlayLyricsContainer, 0);
    }

    private void dismissOverlay(boolean animated) {
        if (overlayHost == null || dismissing) return;
        dismissing = true;
        FrameLayout host = overlayHost;
        resumeUnderlyingContent();
        if (animated && host.isAttachedToWindow()) {
            float destination = Math.max(host.getHeight(), host.getResources().getDisplayMetrics().heightPixels);
            host.animate().translationY(destination).alpha(0.92f).setDuration(260).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> finishDismiss(host)).start();
        } else {
            finishDismiss(host);
        }
    }

    private void finishDismiss(FrameLayout host) {
        Activity activity = overlayActivity;
        boolean wasEmbedded = embeddedMode;
        ViewParent hostParent = host.getParent();
        clearTrackResources();
        if(!wasEmbedded) unregisterBackCallback(activity);
        if(hostParent instanceof ViewGroup) ((ViewGroup) hostParent).removeView(host);
        if(activity != null && !activity.isFinishing() && (!wasEmbedded || embeddedWindowAdjusted)) activity.getWindow().setStatusBarColor(previousStatusBarColor);
        if(wasEmbedded && embeddedWindowAdjusted && activity != null && !activity.isFinishing()) activity.getWindow().setNavigationBarColor(previousNavigationBarColor);
        if(wasEmbedded && embeddedWindowAdjusted && activity != null && !activity.isFinishing() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) activity.getWindow().setStatusBarContrastEnforced(previousStatusBarContrastEnforced);
        if(wasEmbedded && embeddedWindowAdjusted && activity != null && !activity.isFinishing() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) activity.getWindow().setNavigationBarContrastEnforced(previousNavigationBarContrastEnforced);
        if(wasEmbedded && embeddedWindowAdjusted && activity != null && !activity.isFinishing() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.getWindow().setDecorFitsSystemWindows(true);
        if(wasEmbedded && embeddedWindowAdjusted && activity != null && !activity.isFinishing()) activity.getWindow().getDecorView().setSystemUiVisibility(previousSystemUiVisibility);
        if(wasEmbedded && embeddedWindowAdjusted && hostParent instanceof ViewGroup) {
            ((ViewGroup) hostParent).setFitsSystemWindows(previousEmbeddedFitsSystemWindows);
            ((ViewGroup) hostParent).requestApplyInsets();
        }
        if(wasEmbedded && embeddedWindowAdjusted) {
            for(Map.Entry<ViewGroup, Boolean> entry : embeddedClipChildren.entrySet()) entry.getKey().setClipChildren(entry.getValue());
            for(Map.Entry<ViewGroup, Boolean> entry : embeddedClipToPadding.entrySet()) entry.getKey().setClipToPadding(entry.getValue());
            embeddedClipChildren.clear();
            embeddedClipToPadding.clear();
        }
        overlayHost = null;
        backgroundContainer = null;
        overlayLyricsContainer = null;
        overlayCover = null;
        overlayTitle = null;
        overlayArtist = null;
        overlayScrollView = null;
        animatedBackground = null;
        backgroundScrim = null;
        overlayActivity = null;
        currentTrackUri = null;
        headerFadeAnchor = null;
        experimentalTouchSurface = null;
        embeddedLyricsViewport = null;
        underlyingContent = null;
        underlyingSuspended = false;
        embeddedMode = false;
        embeddedCloseRequest = null;
        embeddedInteraction = null;
        embeddedWindowAdjusted = false;
        dismissing = false;
    }

    private void suspendUnderlyingContent(FrameLayout host) {
        if (overlayHost != host || dismissing || underlyingContent == null || underlyingContent == overlayHost) return;
        underlyingContent.setVisibility(View.INVISIBLE);
        underlyingContent.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        underlyingSuspended = true;
    }

    private void resumeUnderlyingContent() {
        if (!underlyingSuspended || underlyingContent == null) return;
        underlyingContent.setVisibility(underlyingVisibility);
        underlyingContent.setImportantForAccessibility(underlyingAccessibility);
        underlyingSuspended = false;
    }

    private void clearTrackResources() {
        stop = true;
        translationSession.incrementAndGet();
        if (mainLoop != null) mainLoop.interrupt();
        mainLoop = null;
        if (activeLyricsCall != null) activeLyricsCall.cancel();
        activeLyricsCall = null;
        if (lyricsExecutor != null) lyricsExecutor.shutdownNow();
        lyricsExecutor = null;
        cancelTranslationRequests();
        translationBindings.clear();
        translationViews.clear();
        translationsReady = false;
        translationLyricsContainer = null;
        vocalGroups = null;
        if(overlayLyricsContainer != null) overlayLyricsContainer.removeCallbacks(finishLineScrollEffects);
        logicalLineTops.clear();
        lineBaseOffsets.clear();
        for(View line : lineRoots) line.animate().cancel();
        for(View follower : scrollFollowers) follower.animate().cancel();
        lineRoots.clear();
        scrollFollowers.clear();
        lineIndex.clear();
        currentActiveLineView = null;
        activeLineIndex = -1;
        lastAppliedActiveIndex = Integer.MIN_VALUE;
        if (inertiaAnimator != null) inertiaAnimator.cancel();
        inertiaAnimator = null;
        if (lyricsScrollAnimator != null) lyricsScrollAnimator.cancel();
    }

    private void registerBackCallback(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        backCallback = () -> dismissOverlay(true);
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback);
    }

    private void unregisterBackCallback(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || activity == null || backCallback == null) return;
        try {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        } catch (Throwable ignored) {
        }
        backCallback = null;
    }

    private final class HeaderDragDismissListener implements View.OnTouchListener {
        private float downY;
        private boolean dragging;
        private VelocityTracker velocityTracker;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (overlayHost == null || dismissing) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = event.getRawY();
                    dragging = false;
                    velocityTracker = VelocityTracker.obtain();
                    velocityTracker.addMovement(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    velocityTracker.addMovement(event);
                    float distance = Math.max(0f, event.getRawY() - downY);
                    if (!dragging && distance > ViewConfiguration.get(view.getContext()).getScaledTouchSlop()) dragging = true;
                    if (dragging) {
                        resumeUnderlyingContent();
                        overlayHost.setTranslationY(distance);
                        overlayHost.setAlpha(1f - Math.min(0.12f, distance / Math.max(1f, overlayHost.getHeight()) * 0.12f));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    float velocityY = velocityTracker.getYVelocity();
                    velocityTracker.recycle();
                    velocityTracker = null;
                    float threshold = overlayHost.getHeight() * 0.22f;
                    if (dragging && (overlayHost.getTranslationY() > threshold || velocityY > 1200f)) {
                        dismissOverlay(true);
                    } else {
                        FrameLayout host = overlayHost;
                        host.animate().translationY(0f).alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> suspendUnderlyingContent(host)).start();
                    }
                    dragging = false;
                    return true;
                default:
                    return false;
            }
        }
    }

    private void renderStaticLyrics(Activity activity, TransformedLyrics transformedLyrics,
                                    LinearLayout lyricsContainer) {
        for (var line : transformedLyrics.lyrics.staticLyrics.lines) {
            LinearLayout lineStack = new LinearLayout(activity);
            lineStack.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(dpToPx(15, activity), dpToPx(20, activity), dpToPx(15, activity), 0);
            lineStack.setLayoutParams(params);

            TextView text = new TextView(activity);
            text.setText(line.text);
            text.setTextColor(Color.WHITE);
            text.setTextSize(26f);
            text.setTypeface(References.beautifulFont.get());
            lineStack.addView(text);

            TextView translation = new TextView(activity);
            translation.setTextColor(Color.argb(190, 255, 255, 255));
            translation.setTextSize(18f);
            translation.setTypeface(References.beautifulFont.get());
            translation.setPadding(0, dpToPx(2, activity), 0, 0);
            translation.setVisibility(View.GONE);
            lineStack.addView(translation);

            lyricsContainer.addView(lineStack);
            translationBindings.add(TranslationBinding.forStatic(line.text, lineStack, text, translation));
        }
    }

    private void beginTranslations(Activity activity, TransformedLyrics transformedLyrics, LinearLayout lyricsContainer, int pageTranslationSession) {
        if (pageTranslationSession != translationSession.get()) {
            return;
        }

        String language = transformedLyrics.language == null ? "" : transformedLyrics.language.trim();
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        String targetLanguage = LyricsTranslationService.normalizeTargetLanguage(prefs.getString("lyrics_translation_language", LyricsTranslationService.DEFAULT_TARGET_LANGUAGE));
        if (targetLanguage.isEmpty()) targetLanguage = LyricsTranslationService.DEFAULT_TARGET_LANGUAGE;
        if (!LyricsTranslationService.shouldTranslateLanguage(language, targetLanguage) || translationBindings.isEmpty()) {
            if (translationButton != null) {
                translationButton.setVisibility(View.GONE);
            }
            return;
        }

        translationLyricsContainer = lyricsContainer;
        translationUsesExperimentalScroll = prefs.getBoolean("experiment_scroll", false);
        translationsReady = false;
        translationButton.setVisibility(View.VISIBLE);
        translationButton.setEnabled(false);
        updateTranslationButtonState(prefs.getBoolean("lyrics_translations_enabled", false), true);

        List<String> lines = translationBindings.stream()
                .map(binding -> binding.sourceText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.toList());

        cancelTranslationRequests();
        translationService = new LyricsTranslationService(targetLanguage);
        translationService.translateLines(lines, translations -> activity.runOnUiThread(() -> {
            if (pageTranslationSession != translationSession.get() || activity.isFinishing()) {
                return;
            }

            applyTranslations(activity, translations);
            if (translationViews.isEmpty()) {
                translationButton.setVisibility(View.GONE);
                translationsReady = false;
                return;
            }

            translationsReady = true;
            translationButton.setEnabled(true);
            applyTranslationVisibility(activity);
        }));
    }

    private void applyTranslations(Activity activity, Map<String, TranslationResult> translations) {
        translationViews.clear();

        for (TranslationBinding binding : translationBindings) {
            TranslationResult result = translations.get(binding.sourceText);
            if (result == null || result.translatedText.isBlank()) {
                continue;
            }

            if (binding.kind == TranslationKind.SYLLABLE) {
                List<TimedTranslationToken> tokens = LyricsTranslationMapper.mapTokens(
                        binding.sourceSyllables, result.translatedText);
                if (tokens.isEmpty()) {
                    continue;
                }

                List<SyllableMetadata> translatedSyllables = new ArrayList<>();
                for (TimedTranslationToken token : tokens) {
                    SyllableMetadata metadata = new SyllableMetadata();
                    metadata.text = token.text;
                    metadata.startTime = token.startTime;
                    metadata.endTime = token.endTime;
                    metadata.isPartOfWord = false;
                    translatedSyllables.add(metadata);
                }

                SyllableVocals translatedVocals = new SyllableVocals(binding.translationContainer,
                        translatedSyllables, false, false, binding.oppositeAligned, activity,
                        binding.fontSize, true);
                binding.translatedSyllableVocals = translatedVocals;
                binding.hasTranslation = true;
                binding.animationGroup.add(translatedVocals);
                translationViews.add(binding.translationContainer);
            } else if (binding.kind == TranslationKind.LINE) {
                LineVocal translatedLine = new LineVocal();
                translatedLine.text = result.translatedText;
                translatedLine.startTime = binding.lineVocal.startTime;
                translatedLine.endTime = binding.lineVocal.endTime;
                translatedLine.oppositeAligned = binding.lineVocal.oppositeAligned;

                LineVocals translatedVocals = new LineVocals(binding.translationContainer, translatedLine,
                        false, activity, binding.fontSize, true);
                binding.translatedLineVocals = translatedVocals;
                binding.hasTranslation = true;
                binding.animationGroup.add(translatedVocals);
                translationViews.add(binding.translationContainer);
            } else {
                binding.staticTranslation.setText(result.translatedText);
                binding.hasTranslation = true;
                translationViews.add(binding.staticTranslation);
            }
        }
    }

    private void toggleTranslations(Activity activity) {
        if (!translationsReady) {
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        boolean enabled = !prefs.getBoolean("lyrics_translations_enabled", false);
        prefs.edit().putBoolean("lyrics_translations_enabled", enabled).apply();
        applyTranslationVisibility(activity);
    }

    private void applyTranslationVisibility(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("lyrics_translations_enabled", false);
        boolean swapTranslations = enabled && prefs.getBoolean("lyrics_swap_translations", false);
        boolean hideOriginal = enabled && prefs.getBoolean("lyrics_hide_original", false);

        for (TranslationBinding binding : translationBindings) {
            boolean showTranslation = enabled && binding.hasTranslation;
            boolean swapThisLine = showTranslation && (swapTranslations || hideOriginal);

            binding.originalView.setVisibility(showTranslation && hideOriginal ? View.GONE : View.VISIBLE);
            binding.getTranslationView().setVisibility(showTranslation ? View.VISIBLE : View.GONE);
            ensureTranslationOrder(binding, swapThisLine);

            if (binding.kind == TranslationKind.SYLLABLE) {
                binding.originalSyllableVocals.setSecondary(swapThisLine);
                if (binding.translatedSyllableVocals != null) {
                    binding.translatedSyllableVocals.setSecondary(!swapThisLine);
                }
            } else if (binding.kind == TranslationKind.LINE) {
                binding.originalLineVocals.setSecondary(swapThisLine);
                if (binding.translatedLineVocals != null) {
                    binding.translatedLineVocals.setSecondary(!swapThisLine);
                }
            } else {
                applyStaticLyricStyle(binding.staticOriginal, swapThisLine);
                applyStaticLyricStyle(binding.staticTranslation, !swapThisLine);
            }
        }
        updateTranslationButtonState(enabled, false);

        if (translationLyricsContainer != null) {
            translationLyricsContainer.requestLayout();
            translationLyricsContainer.post(() -> refreshLayoutAfterTranslationToggle(translationLyricsContainer));
        }
    }

    private void ensureTranslationOrder(TranslationBinding binding, boolean swapped) {
        ViewGroup parent = binding.lineStack;
        int originalIndex = parent.indexOfChild(binding.originalView);
        int translationIndex = parent.indexOfChild(binding.getTranslationView());
        if (originalIndex < 0 || translationIndex < 0
                || (swapped && translationIndex < originalIndex)
                || (!swapped && originalIndex < translationIndex)) {
            return;
        }

        int firstIndex = Math.min(originalIndex, translationIndex);
        int secondIndex = Math.max(originalIndex, translationIndex);
        View first = swapped ? binding.getTranslationView() : binding.originalView;
        View second = swapped ? binding.originalView : binding.getTranslationView();

        parent.removeViewAt(secondIndex);
        parent.removeViewAt(firstIndex);
        parent.addView(first, firstIndex);
        parent.addView(second, secondIndex);
    }

    private void applyStaticLyricStyle(TextView text, boolean secondary) {
        text.setTextSize(secondary ? 18f : 26f);
        text.setTextColor(secondary ? Color.argb(190, 255, 255, 255) : Color.WHITE);
    }

    private void updateTranslationButtonState(boolean enabled, boolean pending) {
        if (translationButton == null) {
            return;
        }
        translationButton.setAlpha(pending ? 0.35f : enabled ? 1f : 0.55f);
        translationButton.setContentDescription(enabled ? "Hide lyric translations" : "Show lyric translations");
    }

    private void refreshLayoutAfterTranslationToggle(LinearLayout lyricsContainer) {
        computeLogicalLayout(lyricsContainer);
        if (currentActiveLineView != null) {
            if (translationUsesExperimentalScroll) {
                experimentalScrollToNewLine(currentActiveLineView, lyricsContainer, true);
            } else if (lyricsContainer.getParent() instanceof ScrollView) {
                scrollToNewLine(currentActiveLineView, (ScrollView) lyricsContainer.getParent(), true);
            }
        }
        applyHeaderFadeToLines();
    }

    private FlexboxLayout createTranslationContainer(Activity activity, boolean oppositeAligned) {
        FlexboxLayout container = new FlexboxLayout(activity.getApplicationContext());
        container.setFlexWrap(FlexWrap.WRAP);
        container.setClipToPadding(false);
        container.setClipChildren(false);
        container.setJustifyContent(oppositeAligned ? JustifyContent.FLEX_END : JustifyContent.FLEX_START);
        container.setPadding(dpToPx(6, activity), dpToPx(2, activity), dpToPx(6, activity), 0);
        container.setVisibility(View.GONE);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return container;
    }

    private void cancelTranslationRequests() {
        if (translationService != null) {
            translationService.cancel();
            translationService = null;
        }
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

                clearHeaderFade();
                clearAllLineBlur();
                lastAppliedActiveIndex = Integer.MIN_VALUE;
                for(View line : lineRoots) line.animate().cancel();
                for(View follower : scrollFollowers) follower.animate().cancel();
                if(currentActiveLineView != null) targetScrollOffset = currentActiveLineView.getTranslationY() - getFocusedPairOffsetPx(currentActiveLineView);

                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                isFollowingPlayback = false;

                contentHeight = contentContainer.getHeight();
                viewportHeight = touchSurface.getHeight();

                double maxScroll = 0;
                double minScroll = Math.min(0, -(contentHeight - viewportHeight));

                double effectiveDistanceY = distanceY;
                if (targetScrollOffset > maxScroll || targetScrollOffset < minScroll) {
                    effectiveDistanceY *= 0.3;
                }

                double newOffset = targetScrollOffset - effectiveDistanceY;
                applyImmediateScrollOffset(contentContainer, newOffset);

                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                double velocity = velocityY * 0.001;

                inertiaAnimator = ValueAnimator.ofFloat((float) velocity, 0f);
                inertiaAnimator.setDuration(800);
                inertiaAnimator.setInterpolator(new DecelerateInterpolator());
                inertiaAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    if (!isUserInteracting) {
                        targetScrollOffset += v * 16;
                        applyImmediateScrollOffset(contentContainer, targetScrollOffset);
                    }
                });
                inertiaAnimator.start();
                return true;
            }
        });

        if(touchSurface instanceof TopFadeLayout) ((TopFadeLayout) touchSurface).setGestureTouchListener((v, event) -> handleTouchSurfaceEvent(touchSurface, contentContainer, event));
    }

    private void limitScrollBounds(View contentContainer) {
        if (contentContainer == null)
            return;

        contentHeight = contentContainer.getHeight();

        View parent = (View) contentContainer.getParent();
        if (parent != null) {
            viewportHeight = parent.getHeight();
        }

        if (contentHeight == 0 || viewportHeight == 0)
            return;

        double maxScroll = 0;
        double minScroll = Math.min(0, -(contentHeight - viewportHeight));

        if (targetScrollOffset > maxScroll) {
            targetScrollOffset = maxScroll;
        } else if (targetScrollOffset < minScroll) {
            targetScrollOffset = minScroll;
        }
    }

    private void renderLyrics(Activity activity, SpotifyTrack track, LinearLayout lyricsContainer, ImageView albumView, int pageTranslationSession) {
        vocalGroups = new HashMap<>();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        lyricsExecutor = executorService;
        executorService.execute(() -> {
            try {
                SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

                String id = track.uri.split(":")[2];
                Bitmap albumArt = getBitmap(track.imageId);
                activity.runOnUiThread(() -> {
                    if (!isOverlaySessionActive(activity, track, pageTranslationSession)) return;
                    albumView.setImageBitmap(albumArt);
                    updateOverlayBackground(activity, track, albumArt, prefs.getBoolean("lyric_enable_background", true));
                });
                if (!isOverlaySessionActive(activity, track, pageTranslationSession)) return;

                // URL url = new URL("https://beautiful-lyrics.socalifornian.live/lyrics/" +
                // id);
                // HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                // connection.setRequestMethod("GET");
                //
                // String token = References.accessToken.get();
                // connection.setRequestProperty("Authorization", "Bearer " + (((token != null
                // && !token.isEmpty()) && sendAccessToken) ? token : "0"));

                // int responseCode = connection.getResponseCode();
                // if (responseCode == HttpURLConnection.HTTP_OK) {
                // BufferedReader in = new BufferedReader(new
                // InputStreamReader(connection.getInputStream()));
                //
                // String inputLine;
                // StringBuilder response = new StringBuilder();
                // while ((inputLine = in.readLine()) != null) {
                // response.append(inputLine);
                // }
                // in.close();
                // finalContent = response.toString();
                // }
                // } catch (Exception e) {
                // XposedBridge.log(e);
                // Handler mainHandler = new Handler(Looper.getMainLooper());
                // Runnable runnable = () -> {
                // Toast.makeText(activity, "Failed to get lyrics", Toast.LENGTH_LONG).show();
                // };
                //
                // mainHandler.post(runnable);
                // return;
                // }

                String token = References.accessToken;
                OkHttpClient lyricsClient = new OkHttpClient();

                RequestBody body = RequestBody.create(
                        "{\"queries\":[{\"operation\":\"lyrics\",\"variables\":{\"id\":\"" + id
                                + "\",\"auth\":\"SpicyLyrics-WebAuth\"}}],\"client\":{\"version\":\"5.22.3\"}}",
                        MediaType.parse("application/json; charset=utf-8"));
//                Request lyricsRequest = new Request.Builder().url("https://api.spicylyrics.org/query").post(body)
//                        .header("Spicylyrics-Webauth", "Bearer " + token)
//                        .header("Spicylyrics-Version", "5.22.3")
//                        .header("Origin", "https://xpui.app.spotify.com")
//                        .header("Referer", "https://xpui.app.spotify.com/")
//                        .header("Accept", "*/*")
//                        .header("Content-Type", "application/json")
//                        .header("Sec-Fetch-Mode", "cors")
//                        .header("Sec-Fetch-Site", "cross-site")
//                        .header("User-Agent",
//                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.179 Spotify/1.2.88.483 Safari/537.36")
//                        .header("Sec-Ch-Ua", "\"Not-A.Brand\";v=\"24\", \"Chromium\";v=\"146\"")
//                        .header("Sec-Fetch-Dest", "empty")
//                        .header("Priority", "u=1, i")
//                        .header("Accept-Language", "en-Latn-US,en-US;q=0.9,en-Latn;q=0.8,en;q=0.7")
//                        .header("Sec-Ch-Ua-Mobile", "?0")
//                        .header("Sec-Ca-Ua-Platform", "\"Windows\"")
//                        .build();
                Request lyricsRequest = new Request.Builder().url("https://spotifyplus-api.devon-shoutz.workers.dev/api/lyrics/" + id).build();

                Call lyricsCall = lyricsClient.newCall(lyricsRequest);
                if (!isOverlaySessionActive(activity, track, pageTranslationSession)) {
                    lyricsCall.cancel();
                    return;
                }
                activeLyricsCall = lyricsCall;
                lyricsCall.enqueue(new Callback() {
                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        assert response.body() != null;
                        String contentFull = response.body().string();
                        XposedBridge.log("[SpotifyPlus] " + contentFull);

//                        JsonArray array = new JsonParser().parseString(contentFull).getAsJsonObject().get("queries").getAsJsonArray();

//                        if (array == null || array.isEmpty()) {
//                            XposedBridge.log("Lyrics queries not found!");
//                            return;
//                        }
//
//                        var thing = array.asList().stream().filter(x -> {
//                            JsonObject obj = x.getAsJsonObject();
//                            return obj.get("operation") != null || obj.get("operationId") != null
//                                    || obj.get("result") != null;
//                        }).collect(Collectors.toList());
//
//                        if (thing.isEmpty()) {
//                            XposedBridge.log("No lyrics result found!");
//                            return;
//                        }
//
                        JsonObject jsonObject = JsonParser.parseString(contentFull).getAsJsonObject();
                        String content = contentFull;
                        XposedBridge.log("[SpotifyPlus] " + content);
                        String type = jsonObject.get("Type").getAsString();
                        var writers = jsonObject.get("SongWriters");
                        String writtenBy;
                        if (writers != null) {
                            writtenBy = writers.getAsJsonArray().asList().stream().map(JsonElement::getAsString)
                                    .collect(Collectors.joining(", "));
                        } else {
                            writtenBy = "";
                        }

                        if (!isOverlaySessionActive(activity, track, pageTranslationSession)) return;
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> {
                            if (!isOverlaySessionActive(activity, track, pageTranslationSession)) return;
                            try {
                                Class<?> requestClass = bridge
                                        .findClass(FindClass.create()
                                                .matcher(ClassMatcher.create()
                                                        .modifiers(Modifier.PUBLIC | Modifier.FINAL).fieldCount(
                                                                1)
                                                        .addField(FieldMatcher
                                                                .create().modifiers(
                                                                        Modifier.PUBLIC | Modifier.FINAL)
                                                                .type(long.class))
                                                        .methods(MethodsMatcher.create()
                                                                .count(6)
                                                                .add(MethodMatcher.create()
                                                                        .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                                                        .returnType(Object.class).paramCount(13))
                                                                .add(MethodMatcher.create()
                                                                        .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                                                        .returnType(void.class).paramCount(12))
                                                                .add(MethodMatcher.create()
                                                                        .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                                                        .returnType(boolean.class)
                                                                        .addParamType(Object.class)))))
                                        .get(0).getInstance(lpparm.classLoader);

                                ctor = requestClass.getConstructor(long.class);
                                ctor.setAccessible(true);
                            } catch (Exception e) {
                                XposedBridge.log(e);
                                Toast.makeText(activity, "Failed to load lyrics", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (type.equals("Syllable")) {
                                Gson gson = new Gson();
                                SyllableSyncedLyrics providerLyrics = gson.fromJson(content,
                                        SyllableSyncedLyrics.class);
                                ProviderLyrics lyrics = new ProviderLyrics();
                                lyrics.syllableLyrics = providerLyrics;
                                XposedBridge.log("[SpotifyPlus] Line Count: " + lyrics.syllableLyrics.content.size());

                                TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(lyrics, activity);

                                renderSyllableLyrics(activity, transformedLyrics, lyricsContainer, track, writtenBy);
                                beginTranslations(activity, transformedLyrics, lyricsContainer, pageTranslationSession);
                            } else if (type.equals("Line")) {
                                Gson gson = new Gson();
                                LineSyncedLyrics providerLyrics = gson.fromJson(content, LineSyncedLyrics.class);
                                ProviderLyrics lyrics = new ProviderLyrics();
                                lyrics.lineLyrics = providerLyrics;

                                TransformedLyrics transformedLyrics = LyricUtilities.transformLyrics(lyrics, activity);

                                renderLineLyrics(activity, transformedLyrics, lyricsContainer, track, writtenBy);
                                beginTranslations(activity, transformedLyrics, lyricsContainer,
                                        pageTranslationSession);
                            } else if (type.equals("Static")) {
                                Gson gson = new Gson();
                                // This is pretty pointless
                                // If Spotify doesn't have lyrics, you can't open this page
                                // And it's very likely that if a song has static lyrics, Spotify won't have the
                                // lryics
                                // I redact my statement, there have been a few times that I've seen static
                                // lyrics
                                // And hey, guess what? It actually works!
                                // I wrote this code and never cared enough to go find a song to test it on

                                StaticSyncedLyrics providerLyrics = gson.fromJson(content, StaticSyncedLyrics.class);

                                ProviderLyrics providerLyricsThing = new ProviderLyrics();
                                providerLyricsThing.staticLyrics = providerLyrics;

                                TransformedLyrics transformedLyrics = LyricUtilities
                                        .transformLyrics(providerLyricsThing, activity);
                                renderStaticLyrics(activity, transformedLyrics, lyricsContainer);
                                beginTranslations(activity, transformedLyrics, lyricsContainer,
                                        pageTranslationSession);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        if (call.isCanceled() || !isOverlaySessionActive(activity, track, pageTranslationSession)) return;
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        Runnable runnable = () -> Toast.makeText(activity, "No lyrics found for this song", Toast.LENGTH_LONG).show();

                        mainHandler.post(runnable);
                    }
                });
            } catch (Exception ex) {
                XposedBridge.log(ex);
            } finally {
                executorService.shutdown();
            }
        });
    }

    private void updateOverlayBackground(Activity activity, SpotifyTrack track, Bitmap albumArt, boolean animated) {
        if (backgroundContainer == null) return;
        if (!animated || albumArt == null) {
            backgroundContainer.removeAllViews();
            animatedBackground = null;
            backgroundScrim = null;
            backgroundContainer.setBackgroundColor(parseTrackColor(track.color));
            return;
        }
        backgroundContainer.setBackgroundColor(Color.BLACK);
        if (animatedBackground == null) {
            backgroundContainer.removeAllViews();
            animatedBackground = new AnimatedBackgroundView(activity, albumArt, backgroundContainer);
            animatedBackground.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            backgroundContainer.addView(animatedBackground);
        } else {
            animatedBackground.updateImage(albumArt);
        }
        if (backgroundScrim == null || backgroundScrim.getParent() != backgroundContainer) {
            backgroundScrim = new View(activity);
            backgroundScrim.setBackgroundColor(Color.BLACK);
            backgroundScrim.setAlpha(0.12f);
            backgroundContainer.addView(backgroundScrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            backgroundScrim.bringToFront();
        }
    }

    private int parseTrackColor(String color) {
        try {
            return Color.parseColor(color != null && color.startsWith("#") ? color : "#" + color);
        } catch (Throwable ignored) {
            return Color.BLACK;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void renderSyllableLyrics(Activity activity, TransformedLyrics transformedLyrics,
                                      LinearLayout lyricsContainer,
                                      SpotifyTrack track, String writtenBy) {
        List<View> lines = new ArrayList<>();
        vocalGroups = new HashMap<>();
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        boolean newScrollingSystem = prefs.getBoolean("experiment_scroll", true);
        currentLineSpacingMode = prefs.getString("line_spacing", "default");

        int lineSpacing = getConfiguredLineSpacingDp(currentLineSpacingMode);
        int fontSize = getConfiguredFontSizeSp(currentLineSpacingMode);

        SyllableSyncedLyrics lyrics = transformedLyrics.lyrics.syllableLyrics;

        int i = 0;
        for (var vocalGroup : lyrics.content) {
            if (vocalGroup instanceof Interlude) {
                Interlude interlude = (Interlude) vocalGroup;
                RelativeLayout topGroup = new RelativeLayout(activity);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);

                if (interlude.time.startTime == 0) {
                    RelativeLayout.MarginLayoutParams params = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(40, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);
                } else {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(20, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);

                    if (i != lyrics.content.size() - 1 && isOppositeAlignedLyric(lyrics.content.get(i + 1))) {
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
            } else if (vocalGroup instanceof SyllableVocalSet) {
                SyllableVocalSet set = (SyllableVocalSet) vocalGroup;

                RelativeLayout evenMoreTopGroup = new RelativeLayout(activity);
                evenMoreTopGroup.setClipToPadding(false);
                evenMoreTopGroup.setClipChildren(false);

                LinearLayout topGroup = new LinearLayout(activity);
                topGroup.setOrientation(LinearLayout.VERTICAL);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);
                RelativeLayout.LayoutParams parms = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                parms.setMargins(dpToPx(25, activity), dpToPx(lineSpacing, activity), dpToPx(35, activity), 0);

                topGroup.setLayoutParams(parms);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                vocalGroupContainer.setLayoutParams(params);
                vocalGroupContainer.setPadding(dpToPx(6, activity), dpToPx(4, activity), dpToPx(6, activity),
                        dpToPx(4, activity));

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

                SyllableVocals sv = new SyllableVocals(vocalGroupContainer, set.lead.syllables, false, false,
                        set.oppositeAligned, activity, fontSize);
                sv.activityChanged.addListener(info -> {
                    View lineView = (View) info.view.getParent().getParent();
                    View scrollView = (View) lyricsContainer.getParent();

                    currentActiveLineView = lineView;
                    onActiveLineChanged(info.view, lyricsContainer);

                    if (newScrollingSystem) {
                        experimentalScrollToNewLine(lineView, lyricsContainer, info.immediate);
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
                    backgroundVocalGroupContainer.setJustifyContent(
                            set.oppositeAligned ? JustifyContent.FLEX_END : JustifyContent.FLEX_START);
                    topGroup.addView(backgroundVocalGroupContainer);
                    backgroundVocalGroupContainer.setPadding(dpToPx(6, activity), 0, dpToPx(6, activity), 0);

                    for (var backgroundVocal : set.background) {
                        startTime = Math.min(startTime, backgroundVocal.startTime);
                        vocals.add(new SyllableVocals(backgroundVocalGroupContainer, backgroundVocal.syllables, true,
                                false, set.oppositeAligned, activity, fontSize));
                    }
                }

                FlexboxLayout translationContainer = createTranslationContainer(activity, set.oppositeAligned);
                topGroup.addView(translationContainer);

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

                View.OnTouchListener seekTouchListener = (v, event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX[0] = event.getX();
                            downY[0] = event.getY();
                            isDragging[0] = false;

                            ObjectAnimator startAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha",
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
                            ObjectAnimator endAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha",
                                            highlightBackground.getAlpha(), 0)
                                    .setDuration(400);
                            endAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            endAnimation.start();

                            if (event.getActionMasked() == MotionEvent.ACTION_UP && !isDragging[0]) {
                                resumeAutoFollowAfterTap(lyricsContainer);
                                v.performClick();
                            } else {
                                isUserInteracting = false;
                            }
                            break;
                    }

                    return true;
                };
                vocalGroupContainer.setOnTouchListener(seekTouchListener);
                translationContainer.setOnTouchListener(seekTouchListener);

                View.OnClickListener seekClickListener = (v) -> {
                    resumeAutoFollowAfterTap(lyricsContainer);

                    try {
                        Object seekArg = ctor.newInstance((long) (finalStartTime * 1000));

                        if (seekInstance != null) {
                            Method method = bridge.findMethod(
                                            FindMethod.create()
                                                    .searchInClass(Collections
                                                            .singletonList(bridge.getClassData(seekInstance.getClass())))
                                                    .matcher(MethodMatcher.create()
                                                            .paramTypes(ctor.getDeclaringClass().getSuperclass())))
                                    .get(0).getMethodInstance(lpparm.classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            XposedHelpers.callMethod(block, "blockingGet");
                        } else {
                            XposedBridge.log("[SpotifyPlus] p.mmm is null :(");
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                };
                vocalGroupContainer.setOnClickListener(seekClickListener);
                translationContainer.setOnClickListener(seekClickListener);

                CopyOnWriteArrayList<SyncableVocals> syncedVocals = new CopyOnWriteArrayList<>(vocals);
                vocalGroups.put(vocalGroupContainer, syncedVocals);
                translationBindings.add(TranslationBinding.forSyllables(
                        LyricsTranslationMapper.reconstructLine(set.lead.syllables), topGroup,
                        vocalGroupContainer, translationContainer, syncedVocals, sv,
                        set.lead.syllables, set.oppositeAligned, fontSize));
            }

            i++;
        }

        lines.forEach(lyricsContainer::addView);
        registerLineRoots(lines);

        if (!writtenBy.isBlank()) {
            TextView writtenByTextView = new TextView(activity.getApplicationContext());
            writtenByTextView.setText("Written by: " + writtenBy);
            writtenByTextView.setTextSize(16f);
            writtenByTextView.setTypeface(References.beautifulFont.get());
            writtenByTextView.setTextColor(Color.parseColor("#f5f5f5"));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(dpToPx(30, activity), dpToPx(20, activity), dpToPx(30, activity), 0);
            writtenByTextView.setLayoutParams(p);

            lyricsContainer.addView(writtenByTextView);
            scrollFollowers.add(writtenByTextView);
        }

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                dpToPx(180, activity));
        spacer.setLayoutParams(spacerParams);
        lyricsContainer.addView(spacer);

        lyricsContainer.post(() -> {
            computeLogicalLayout(lyricsContainer);

            update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);
            updateProgress(track.position, System.currentTimeMillis(), vocalGroups, (View) lyricsContainer.getParent());
            applyHeaderFadeToLines();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void renderLineLyrics(Activity activity, TransformedLyrics transformedLyrics,
                                  LinearLayout lyricsContainer,
                                  SpotifyTrack track, String writtenBy) {
        List<View> lines = new ArrayList<>();
        vocalGroups = new HashMap<>();
        SharedPreferences prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        boolean newScrollingSystem = prefs.getBoolean("experiment_scroll", true);
        currentLineSpacingMode = prefs.getString("line_spacing", "default");

        LineSyncedLyrics lyrics = transformedLyrics.lyrics.lineLyrics;
        int lineSpacing = getConfiguredLineSpacingDp(currentLineSpacingMode);
        int fontSize = getConfiguredFontSizeSp(currentLineSpacingMode);

        int i = 0;
        for (var vocalGroup : lyrics.content) {
            if (vocalGroup instanceof Interlude) {
                Interlude interlude = (Interlude) vocalGroup;

                RelativeLayout topGroup = new RelativeLayout(activity);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);

                if (interlude.time.startTime == 0) {
                    RelativeLayout.MarginLayoutParams params = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(40, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);
                } else {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(30, activity), dpToPx(20, activity), 0, 0);
                    vocalGroupContainer.setLayoutParams(params);

                    if (i != lyrics.content.size() - 1 && isOppositeAlignedLyric(lyrics.content.get(i + 1))) {
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        params.setMargins(0, dpToPx(20, activity), dpToPx(30, activity), 0);
                    }
                }

                List<SyncableVocals> visual = new ArrayList<>();
                visual.add(new InterludeVisual(vocalGroupContainer, interlude, activity));
                vocalGroups.put(vocalGroupContainer, visual);

                topGroup.addView(vocalGroupContainer);
                lines.add(topGroup);
            } else if (vocalGroup instanceof LineVocal) {
                LineVocal vocal = (LineVocal) vocalGroup;

                RelativeLayout topGroup = new RelativeLayout(activity);
                topGroup.setClipToPadding(false);
                topGroup.setClipChildren(false);

                FlexboxLayout vocalGroupContainer = new FlexboxLayout(activity.getApplicationContext());
                vocalGroupContainer.setFlexWrap(FlexWrap.WRAP);
                vocalGroupContainer.setClipToPadding(false);
                vocalGroupContainer.setClipChildren(false);
                LinearLayout lineStack = new LinearLayout(activity);
                lineStack.setOrientation(LinearLayout.VERTICAL);
                lineStack.setClipToPadding(false);
                lineStack.setClipChildren(false);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(dpToPx(25, activity), dpToPx(lineSpacing, activity), dpToPx(35, activity), 0);

                if (vocal.oppositeAligned) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                    params.setMargins(dpToPx(35, activity), dpToPx(lineSpacing, activity), dpToPx(25, activity), 0);
                    vocalGroupContainer.setJustifyContent(JustifyContent.FLEX_END);
                }

                lineStack.setLayoutParams(params);
                vocalGroupContainer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                vocalGroupContainer.setPadding(dpToPx(6, activity), dpToPx(4, activity), dpToPx(6, activity), dpToPx(4, activity));
                lineStack.addView(vocalGroupContainer);
                FlexboxLayout translationContainer = createTranslationContainer(activity, vocal.oppositeAligned);
                lineStack.addView(translationContainer);
                topGroup.addView(lineStack);

                LineVocals lv = new LineVocals(vocalGroupContainer, vocal, false, activity, fontSize, false);
                lv.activityChanged.addListener(info -> {
                    View lineView = topGroup;
                    View scrollView = (View) lyricsContainer.getParent();

                    currentActiveLineView = lineView;
                    onActiveLineChanged(info.view, lyricsContainer);

                    if (newScrollingSystem) {
                        experimentalScrollToNewLine(lineView, lyricsContainer, info.immediate);
                    } else {
                        scrollToNewLine(lineView, (ScrollView) scrollView, info.immediate);
                    }
                });

                CopyOnWriteArrayList<SyncableVocals> syncedVocals = new CopyOnWriteArrayList<>();
                syncedVocals.add(lv);
                vocalGroups.put(vocalGroupContainer, syncedVocals);
                translationBindings.add(TranslationBinding.forLine(vocal.text, lineStack,
                        vocalGroupContainer, translationContainer, syncedVocals, vocal, lv, fontSize));

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

                View.OnTouchListener seekTouchListener = (v, event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX[0] = event.getX();
                            downY[0] = event.getY();
                            isDragging[0] = false;

                            ObjectAnimator startAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha",
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
                            ObjectAnimator endAnimation = ObjectAnimator.ofInt(highlightBackground, "alpha",
                                            highlightBackground.getAlpha(), 0)
                                    .setDuration(400);
                            endAnimation.setInterpolator(new DecelerateInterpolator(2.0f));
                            endAnimation.start();

                            if (event.getActionMasked() == MotionEvent.ACTION_UP && !isDragging[0]) {
                                resumeAutoFollowAfterTap(lyricsContainer);
                                v.performClick();
                            } else {
                                isUserInteracting = false;
                            }
                            break;
                    }

                    return true;
                };
                vocalGroupContainer.setOnTouchListener(seekTouchListener);
                translationContainer.setOnTouchListener(seekTouchListener);

                View.OnClickListener seekClickListener = (v) -> {
                    resumeAutoFollowAfterTap(lyricsContainer);

                    try {
                        Object seekArg = ctor.newInstance((long) (finalStartTime * 1000));

                        if (seekInstance != null) {
                            Method method = bridge.findMethod(
                                            FindMethod.create()
                                                    .searchInClass(Collections
                                                            .singletonList(bridge.getClassData(seekInstance.getClass())))
                                                    .matcher(MethodMatcher.create()
                                                            .paramTypes(ctor.getDeclaringClass().getSuperclass())))
                                    .get(0).getMethodInstance(lpparm.classLoader);

                            Object block = method.invoke(seekInstance, seekArg);
                            XposedHelpers.callMethod(block, "blockingGet");
                        } else {
                            XposedBridge.log("[SpotifyPlus] p.mmm is null :(");
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                };
                vocalGroupContainer.setOnClickListener(seekClickListener);
                translationContainer.setOnClickListener(seekClickListener);

                lines.add(topGroup);
            }
            i++;
        }

        lines.forEach(lyricsContainer::addView);
        registerLineRoots(lines);

        if (!writtenBy.isBlank()) {
            TextView writtenByTextView = new TextView(activity.getApplicationContext());
            writtenByTextView.setText("Written by: " + writtenBy);
            writtenByTextView.setTextSize(16f);
            writtenByTextView.setTypeface(References.beautifulFont.get());
            writtenByTextView.setTextColor(Color.parseColor("#f5f5f5"));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(dpToPx(30, activity), dpToPx(20, activity), dpToPx(30, activity), 0);
            writtenByTextView.setLayoutParams(p);

            lyricsContainer.addView(writtenByTextView);
            scrollFollowers.add(writtenByTextView);
        }

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                dpToPx(180, activity));
        spacer.setLayoutParams(spacerParams);
        lyricsContainer.addView(spacer);

        lyricsContainer.post(() -> {
            computeLogicalLayout(lyricsContainer);

            update(vocalGroups, track.position / 1000d, 1.0d / 60d, true);
            updateProgress(track.position, System.currentTimeMillis(), vocalGroups, (View) lyricsContainer.getParent());
            applyHeaderFadeToLines();
        });
    }

    private void update(Map<FlexboxLayout, List<SyncableVocals>> vocalGroups, double timestamp, double deltaTime,
                        boolean skipped) {
        try {
            for (var vocalGroup : vocalGroups.values()) {
                for (var vocal : vocalGroup) {
                    vocal.animate(timestamp, deltaTime, skipped);
                }
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private long lastUpdatedAt = 0;
    private double lastTimestamp = 0;

    private void updateProgress(long initialPositionS, double startedSyncAtS, Map<FlexboxLayout, List<SyncableVocals>> vocalGroups, View scrollView) {
        mainLoop = new Thread(() -> {
            try {
                int[] syncTimings = {50, 100, 150, 750};
                int syncIndex = 0;
                long nextSyncAt = syncTimings[0];
                long initialPosition = initialPositionS;
                double startedSyncAt = startedSyncAtS;
                lastUpdatedAt = System.currentTimeMillis();
                float refreshRate = overlayActivity == null ? 60f : overlayActivity.getWindowManager().getDefaultDisplay().getRefreshRate();
                long frameIntervalNanos = Math.max(1L, Math.round(1_000_000_000d / Math.max(30f, refreshRate)));
                long nextFrameAtNanos = System.nanoTime();

                while (!stop) {
                    long updatedAt = System.currentTimeMillis();

                    if (isPlaying) {
                        if (updatedAt > startedSyncAt + nextSyncAt) {
                            // Get the current position from Spotify
                            long position = References.getCurrentPlaybackPosition(bridge, lpparm);
                            if (position != -1) {
                                initialPosition = position;
                                startedSyncAt = updatedAt;

                                syncIndex++;

                                if (syncIndex < syncTimings.length) {
                                    nextSyncAt = syncTimings[syncIndex];
                                } else nextSyncAt = 500;
                            }
                        }

                        double syncedTimestamp = (initialPosition + (updatedAt - startedSyncAt)) / 1000d;
                        double deltaTime = (updatedAt - lastUpdatedAt) / 1000d;

                        update(vocalGroups, syncedTimestamp, deltaTime, Math.abs(syncedTimestamp - lastTimestamp) > 0.075d);
                        lastTimestamp = syncedTimestamp;
                    }

                    lastUpdatedAt = updatedAt;
                    nextFrameAtNanos += frameIntervalNanos;
                    long remainingNanos = nextFrameAtNanos - System.nanoTime();
                    if(remainingNanos > 0) LockSupport.parkNanos(remainingNanos);
                    else nextFrameAtNanos = System.nanoTime();
                    if(Thread.interrupted()) break;
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        });

        mainLoop.start();
    }

    private void registerLineRoots(List<View> roots) {
        lineRoots.clear();
        lineIndex.clear();

        for (int i = 0; i < roots.size(); i++) {
            View v = roots.get(i);
            lineRoots.add(v);
            lineIndex.put(v, i);
        }

        activeLineIndex = -1;
        lastAppliedActiveIndex = Integer.MIN_VALUE;
        lastBlurAllowed = true;
    }

    private void onActiveLineChanged(View anyViewInsideLine, LinearLayout lyricsContainer) {
        View root = findLineRoot(anyViewInsideLine, lyricsContainer);
        if (root == null)
            return;

        Integer idx = lineIndex.get(root);
        if (idx == null)
            idx = lineRoots.indexOf(root);
        if (idx == null || idx < 0)
            return;

        activeLineIndex = idx;

        lyricsContainer.post(() -> {
            applyCurrentLineTranslations();
            applyLineFocusEffects();
        });
    }

    private View findLineRoot(View v, LinearLayout lyricsContainer) {
        View cur = v;
        while (cur != null) {
            ViewParent p = cur.getParent();
            if (p == lyricsContainer)
                return cur;
            if (!(p instanceof View))
                return null;
            cur = (View) p;
        }
        return null;
    }

    private void applyLineFocusEffects() {
        boolean blurAllowed = isFollowingPlayback && !isUserInteracting;

        // if user is scrolling or we aren't following playback, clear everything
        if (!blurAllowed) {
            if (lastBlurAllowed) {
                clearAllLineBlur();
                lastBlurAllowed = false;
            }
            return;
        }

        lastBlurAllowed = true;

        if (activeLineIndex < 0) {
            clearAllLineBlur();
            return;
        }

        // no need to redo if same active line and blur is allowed
        if (activeLineIndex == lastAppliedActiveIndex)
            return;
        lastAppliedActiveIndex = activeLineIndex;

        for (int i = 0; i < lineRoots.size(); i++) {
            View line = lineRoots.get(i);

            int dist = Math.abs(i - activeLineIndex);
            int bucket = Math.min(dist, BLUR_MAX_DISTANCE);

            float radiusDp = BLUR_LEVELS_DP[bucket];
            float radiusPx = dpToPxF(radiusDp, line);

            setLineBlur(line, radiusPx);
        }
    }

    private void clearAllLineBlur() {
        for (View line : lineRoots) {
            setLineBlur(line, 0f);
        }
    }

    private float dpToPxF(float dp, View v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                v.getResources().getDisplayMetrics());
    }

    private void setLineBlur(View line, float radiusPx) {
        // No blur requested → clear effect
        if (radiusPx <= 0.01f) {
            RenderEffectCompat.clear(line);
            // optional: drop hardware layer if you want
            // line.setLayerType(View.LAYER_TYPE_NONE, null);
            return;
        }

        // optional: force hardware layer for performance stability
        // line.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        RenderEffectCompat.blur(line, radiusPx);
    }

    private static final class RenderEffectCompat {
        private static boolean checked;
        private static boolean available;

        private static Class<?> cRenderEffect;
        private static java.lang.reflect.Method mCreateBlurEffect;
        private static java.lang.reflect.Method mSetRenderEffect;

        // cache by integer px so we aren’t allocating a new effect constantly
        private static final Map<Integer, Object> blurCache = new HashMap<>();

        private static void ensure() {
            if (checked)
                return;
            checked = true;

            try {
                cRenderEffect = Class.forName("android.graphics.RenderEffect");
                mCreateBlurEffect = cRenderEffect.getMethod(
                        "createBlurEffect",
                        float.class, float.class, android.graphics.Shader.TileMode.class);
                mSetRenderEffect = View.class.getMethod("setRenderEffect", cRenderEffect);
                available = true;
            } catch (Throwable t) {
                available = false;
            }
        }

        static void blur(View v, float radiusPx) {
            ensure();
            if (!available)
                return;

            try {
                int key = Math.max(1, Math.round(radiusPx));
                Object effect = blurCache.get(key);
                if (effect == null) {
                    effect = mCreateBlurEffect.invoke(null, (float) key, (float) key,
                            android.graphics.Shader.TileMode.CLAMP);
                    blurCache.put(key, effect);
                }
                mSetRenderEffect.invoke(v, effect);
            } catch (Throwable ignored) {
            }
        }

        static void clear(View v) {
            ensure();
            if (!available)
                return;

            try {
                mSetRenderEffect.invoke(v, new Object[]{null});
            } catch (Throwable ignored) {
            }
        }
    }

    private void experimentalScrollToNewLine(View activeLine, LinearLayout lyricsContainer, boolean immediate) {
        if (isUserInteracting || !isFollowingPlayback)
            return;

        View finalLine = activeLine;
        while (finalLine.getParent() != lyricsContainer && finalLine.getParent() != null) {
            finalLine = (View) finalLine.getParent();
        }

        int finalActiveIndex = lineRoots.indexOf(finalLine);
        if(finalActiveIndex == -1) return;
        final View finalLineForCalc = finalLine;

        lyricsContainer.post(() -> {
            try {
                if (lyricsContainer.getParent() instanceof View) {
                    viewportHeight = ((View) lyricsContainer.getParent()).getHeight();
                }
                if (viewportHeight <= 0)
                    return;

                int activeLineTop = finalLineForCalc.getTop();
                int activeLineHeight = finalLineForCalc.getHeight();

                float scrollPositionRatio = overlayActivity != null && overlayActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 0.48f : SCROLL_POSITION_RATIO;
                int screenTargetY = (int) (viewportHeight * scrollPositionRatio) - (activeLineHeight / 2);

                double newGlobalOffset = screenTargetY - activeLineTop;
                targetScrollOffset = newGlobalOffset;

                limitScrollBounds(lyricsContainer);
                lyricsContainer.setTranslationY(0f);
                clearHeaderFade();
                clearAllLineBlur();
                lastAppliedActiveIndex = Integer.MIN_VALUE;
                long longestDelay = 0L;
                float overscan = (float)viewportHeight * 0.35f;
                for(int i = 0; i < lineRoots.size(); i++) {
                    View line = lineRoots.get(i);
                    float targetTranslation = (float)targetScrollOffset + getFocusedPairOffsetPx(line);
                    float currentTop = line.getTop() + line.getTranslationY();
                    float targetTop = line.getTop() + targetTranslation;
                    boolean nearViewport = currentTop + line.getHeight() >= -overscan && currentTop <= viewportHeight + overscan || targetTop + line.getHeight() >= -overscan && targetTop <= viewportHeight + overscan;
                    line.animate().cancel();
                    if(immediate || !nearViewport) {
                        line.setTranslationY(targetTranslation);
                        continue;
                    }
                    int distance = Math.abs(i - finalActiveIndex);
                    long delay = i == finalActiveIndex ? 0L : i < finalActiveIndex ? Math.round(distance * LINE_ANIMATION_DELAY_MS * 0.3d) : distance * LINE_ANIMATION_DELAY_MS;
                    longestDelay = Math.max(longestDelay, delay);
                    line.animate().translationY(targetTranslation).setStartDelay(delay).setDuration(LINE_ANIMATION_DURATION_MS).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                }
                for(View follower : scrollFollowers) {
                    float targetTranslation = (float)targetScrollOffset;
                    float currentTop = follower.getTop() + follower.getTranslationY();
                    float targetTop = follower.getTop() + targetTranslation;
                    boolean nearViewport = currentTop + follower.getHeight() >= -overscan && currentTop <= viewportHeight + overscan || targetTop + follower.getHeight() >= -overscan && targetTop <= viewportHeight + overscan;
                    follower.animate().cancel();
                    if(immediate || !nearViewport) {
                        follower.setTranslationY(targetTranslation);
                        continue;
                    }
                    long delay = Math.max(0, lineRoots.size() - finalActiveIndex) * LINE_ANIMATION_DELAY_MS;
                    longestDelay = Math.max(longestDelay, delay);
                    follower.animate().translationY(targetTranslation).setStartDelay(delay).setDuration(LINE_ANIMATION_DURATION_MS).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                }
                lyricsContainer.removeCallbacks(finishLineScrollEffects);
                if(immediate) finishLineScrollEffects.run();
                else lyricsContainer.postDelayed(finishLineScrollEffects, LINE_ANIMATION_DURATION_MS + longestDelay);
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        });
    }

    private void resumeAutoFollowAfterTap(LinearLayout lyricsContainer) {
        isUserInteracting = false;
        isFollowingPlayback = true;

        if (inertiaAnimator != null) {
            inertiaAnimator.cancel();
            inertiaAnimator = null;
        }

        lyricsContainer.setTranslationY(0f);
        for(View line : lineRoots) {
            line.animate().cancel();
            line.setTranslationY((float)targetScrollOffset + getFocusedPairOffsetPx(line));
        }
        for(View follower : scrollFollowers) {
            follower.animate().cancel();
            follower.setTranslationY((float)targetScrollOffset);
        }

        lyricsContainer.post(() -> {
            applyCurrentLineTranslations();
            applyLineFocusEffects();
            applyHeaderFadeToLines();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean handleTouchSurfaceEvent(View touchSurface, LinearLayout contentContainer, MotionEvent event) {
        rightContainer.setAlpha(1f);

        boolean result = gestureDetector.onTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isUserInteracting = false;
            limitScrollBounds(contentContainer);
            touchSurface.post(finishLineScrollEffects);

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
    }

    private void computeLogicalLayout(LinearLayout container) {
        logicalLineTops.clear();
        lineBaseOffsets.clear();

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
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();

            y += lp.topMargin;
            logicalLineTops.put(child, y);
            y += child.getMeasuredHeight() + lp.bottomMargin;
        }

        contentHeight = y + container.getPaddingBottom();

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Integer logicalTop = logicalLineTops.get(child);
            if (logicalTop == null)
                continue;

            int actualTop = child.getTop();
            lineBaseOffsets.put(child, (double) (logicalTop - actualTop));
        }

        View parent = (View) container.getParent();
        if (parent != null) {
            viewportHeight = parent.getHeight();
        }
    }

    private int getConfiguredLineSpacingDp(String spacingMode) {
        switch (spacingMode) {
            case "compact":
                return 32;
            case "spacious":
                return 42;
            case "more":
                return 46;
            case "max":
                return 46;
            default:
                return 36;
        }
    }

    private int getConfiguredFontSizeSp(String spacingMode) {
        boolean landscape = overlayActivity != null && overlayActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        switch(spacingMode) {
            case "compact": return 28;
            case "spacious": return landscape ? 31 : 36;
            case "more":
            case "max": return landscape ? 32 : 38;
            default: return landscape ? 30 : 34;
        }
    }

    private boolean isOppositeAlignedLyric(Object lyric) {
        return lyric instanceof SyllableVocalSet ? ((SyllableVocalSet)lyric).oppositeAligned : lyric instanceof LineVocal && ((LineVocal)lyric).oppositeAligned;
    }

    private boolean isMaxPairSpacingEnabled() {
        return currentLineSpacingMode.equals("max");
    }

    private float getFocusedPairIsolationAmountPx(View anyLine) {
        float vh = (float) viewportHeight;

        if (vh <= 0f) {
            ViewParent parent = anyLine.getParent();
            if (parent instanceof View) {
                vh = ((View) parent).getHeight();
            }
        }

        float min = dpToPxF(300f, anyLine);
        float max = dpToPxF(320f, anyLine);

        if (vh <= 0f)
            return min;

        float preferred = vh * 0.35f;
        return Math.max(min, Math.min(max, preferred));
    }

    private float getFocusedPairOffsetPx(View line) {
        if (!isMaxPairSpacingEnabled())
            return 0f;
        if (activeLineIndex < 0)
            return 0f;

        Integer idxObj = lineIndex.get(line);
        if (idxObj == null) {
            int idx = lineRoots.indexOf(line);
            if (idx < 0)
                return 0f;
            idxObj = idx;
        }

        int idx = idxObj;
        int nextIndex = Math.min(activeLineIndex + 1, lineRoots.size() - 1);

        float isolation = getFocusedPairIsolationAmountPx(line);

        if (idx < activeLineIndex) {
            return -isolation;
        }

        if (idx > nextIndex) {
            return isolation;
        }

        return 0f;
    }

    private void applyCurrentLineTranslations() {
        for (View line : lineRoots) {
            float translation = (float)targetScrollOffset + getFocusedPairOffsetPx(line);
            if(Math.abs(line.getTranslationY() - translation) > 0.1f) line.setTranslationY(translation);
        }
        for(View follower : scrollFollowers) if(Math.abs(follower.getTranslationY() - (float)targetScrollOffset) > 0.1f) follower.setTranslationY((float)targetScrollOffset);

        applyHeaderFadeToLines();
    }

    private void applyImmediateScrollOffset(LinearLayout contentContainer, double offset) {
        targetScrollOffset = offset;
        limitScrollBounds(contentContainer);
        contentContainer.setTranslationY(0f);
        for(View line : lineRoots) line.setTranslationY((float)targetScrollOffset + getFocusedPairOffsetPx(line));
        for(View follower : scrollFollowers) follower.setTranslationY((float)targetScrollOffset);
    }

    private void applyHeaderFadeToLines() {
        if (headerFadeAnchor == null || lineRoots.isEmpty())
            return;
        if (!headerFadeAnchor.isAttachedToWindow())
            return;

        float fadeDistancePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                HEADER_FADE_DISTANCE_DP,
                headerFadeAnchor.getResources().getDisplayMetrics());

        int[] headerLoc = new int[2];
        headerFadeAnchor.getLocationInWindow(headerLoc);
        float headerBottomInWindow = headerLoc[1] + headerFadeAnchor.getHeight();
        ViewParent lineParent = lineRoots.get(0).getParent();
        if(!(lineParent instanceof View)) return;
        int[] containerLoc = new int[2];
        ((View)lineParent).getLocationInWindow(containerLoc);

        for (View line : lineRoots) {
            if (line == null || !line.isAttachedToWindow())
                continue;

            float lineTopInWindow = containerLoc[1] + line.getTop() + line.getTranslationY();
            float lineBottomInWindow = lineTopInWindow + line.getHeight();
            float lineCenterInWindow = (lineTopInWindow + lineBottomInWindow) * 0.5f;

            float distanceBelowHeader = lineCenterInWindow - headerBottomInWindow;

            float alpha;
            if (distanceBelowHeader <= 0f) {
                alpha = HEADER_FADE_MIN_ALPHA;
            } else if (distanceBelowHeader >= fadeDistancePx) {
                alpha = 1f;
            } else {
                float t = distanceBelowHeader / fadeDistancePx;
                t = t * t * (3f - 2f * t);
                alpha = HEADER_FADE_MIN_ALPHA + ((1f - HEADER_FADE_MIN_ALPHA) * t);
            }

            if(Math.abs(line.getAlpha() - alpha) > 0.005f) line.setAlpha(alpha);
        }
    }

    private void clearHeaderFade() {
        for (View line : lineRoots) {
            if (line != null) {
                line.setAlpha(1f);
            }
        }
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

            if (immediate || (!scrollView.isPressed() && lineTopInSv >= scrollY
                    && lineBottom <= scrollY + scrollViewHeight)) {
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
            activeLine.animate().scaleX(1.008f).scaleY(1.008f).setDuration(400)
                    .setInterpolator(new OvershootInterpolator());
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
        } catch (IOException e) {
            XposedBridge.log(e);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TrackAnalysis getTrackAnalysis(String id) {
        OkHttpClient client = new OkHttpClient();
        Gson gson = new Gson();

        Request request = new Request.Builder().url("https://api.reccobeats.com/v1/audio-features?ids=" + id).get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    Toast.makeText(References.currentActivity, "Failed to get track analysis", Toast.LENGTH_LONG)
                            .show();
                });

                return TrackAnalysis.defaultTrack;
            }

            String body = response.body().string();
            return gson.fromJson(body, TrackAnalysis.class);
        } catch (IOException e) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                Toast.makeText(References.currentActivity, "Failed to get track analysis", Toast.LENGTH_LONG).show();
            });

            return TrackAnalysis.defaultTrack;
        }
    }

    private Drawable createTranslationIcon(Activity context) {
        int size = dpToPx(24, context);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#E6FFFFFF"));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setTextSize(size * 0.55f);
        canvas.drawText("A", size * 0.34f, size * 0.62f, paint);
        paint.setTextSize(size * 0.38f);
        canvas.drawText("文", size * 0.72f, size * 0.78f, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private enum TranslationKind {
        SYLLABLE,
        LINE,
        STATIC
    }

    private static final class TranslationBinding {
        final TranslationKind kind;
        final String sourceText;
        final ViewGroup lineStack;
        final View originalView;
        final FlexboxLayout translationContainer;
        final CopyOnWriteArrayList<SyncableVocals> animationGroup;
        final List<SyllableMetadata> sourceSyllables;
        final LineVocal lineVocal;
        final TextView staticOriginal;
        final TextView staticTranslation;
        final boolean oppositeAligned;
        final int fontSize;
        final SyllableVocals originalSyllableVocals;
        final LineVocals originalLineVocals;
        SyllableVocals translatedSyllableVocals;
        LineVocals translatedLineVocals;
        boolean hasTranslation;

        private TranslationBinding(TranslationKind kind, String sourceText, ViewGroup lineStack,
                View originalView, FlexboxLayout translationContainer,
                CopyOnWriteArrayList<SyncableVocals> animationGroup,
                List<SyllableMetadata> sourceSyllables, LineVocal lineVocal,
                TextView staticOriginal, TextView staticTranslation,
                boolean oppositeAligned, int fontSize,
                SyllableVocals originalSyllableVocals, LineVocals originalLineVocals) {
            this.kind = kind;
            this.sourceText = sourceText == null ? "" : sourceText.trim();
            this.lineStack = lineStack;
            this.originalView = originalView;
            this.translationContainer = translationContainer;
            this.animationGroup = animationGroup;
            this.sourceSyllables = sourceSyllables;
            this.lineVocal = lineVocal;
            this.staticOriginal = staticOriginal;
            this.staticTranslation = staticTranslation;
            this.oppositeAligned = oppositeAligned;
            this.fontSize = fontSize;
            this.originalSyllableVocals = originalSyllableVocals;
            this.originalLineVocals = originalLineVocals;
        }

        View getTranslationView() {
            return kind == TranslationKind.STATIC ? staticTranslation : translationContainer;
        }

        static TranslationBinding forSyllables(String sourceText, ViewGroup lineStack,
                FlexboxLayout originalContainer, FlexboxLayout translationContainer,
                CopyOnWriteArrayList<SyncableVocals> animationGroup,
                SyllableVocals originalVocals, List<SyllableMetadata> sourceSyllables,
                boolean oppositeAligned, int fontSize) {
            return new TranslationBinding(TranslationKind.SYLLABLE, sourceText, lineStack,
                    originalContainer, translationContainer, animationGroup, sourceSyllables,
                    null, null, null, oppositeAligned, fontSize, originalVocals, null);
        }

        static TranslationBinding forLine(String sourceText, ViewGroup lineStack,
                FlexboxLayout originalContainer, FlexboxLayout translationContainer,
                CopyOnWriteArrayList<SyncableVocals> animationGroup, LineVocal lineVocal,
                LineVocals originalVocals, int fontSize) {
            return new TranslationBinding(TranslationKind.LINE, sourceText, lineStack,
                    originalContainer, translationContainer, animationGroup, null, lineVocal,
                    null, null, lineVocal.oppositeAligned, fontSize, null, originalVocals);
        }

        static TranslationBinding forStatic(String sourceText, ViewGroup lineStack,
                TextView staticOriginal, TextView staticTranslation) {
            return new TranslationBinding(TranslationKind.STATIC, sourceText, lineStack,
                    staticOriginal, null, null, null, null, staticOriginal,
                    staticTranslation, false, 26, null, null);
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
                activity.getResources().getDisplayMetrics());
    }

    private static class TopFadeLayout extends FrameLayout {
        private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private LinearGradient fadeShader;
        private LinearGradient bottomFadeShader;
        private int fadeHeightPx;
        private int bottomClipInset;
        private OnTouchListener gestureTouchListener;

        public TopFadeLayout(Context context, int fadeHeightPx) {
            super(context);
            this.fadeHeightPx = fadeHeightPx;
            setWillNotDraw(false);

            fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            bottomFadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

            solidPaint.setColor(0xFFFFFFFF);
            solidPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        void setGestureTouchListener(OnTouchListener gestureTouchListener) {
            this.gestureTouchListener = gestureTouchListener;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            boolean gestureHandled = gestureTouchListener != null && gestureTouchListener.onTouch(this, event);
            return super.dispatchTouchEvent(event) || gestureHandled;
        }

        public void setFadeHeightPx(int fadeHeightPx) {
            this.fadeHeightPx = fadeHeightPx;
            rebuildShader();
            invalidate();
        }

        public void setBottomClipInset(int bottomClipInset) {
            int boundedInset = Math.max(0, Math.min(bottomClipInset, getHeight()));
            if(this.bottomClipInset == boundedInset) return;
            this.bottomClipInset = boundedInset;
            rebuildShader();
            invalidate();
        }

        private void rebuildShader() {
            if (getWidth() <= 0 || getHeight() <= 0 || fadeHeightPx <= 0) {
                fadeShader = null;
                bottomFadeShader = null;
                fadePaint.setShader(null);
                bottomFadePaint.setShader(null);
                return;
            }

            fadeShader = new LinearGradient(
                    0f, 0f,
                    0f, fadeHeightPx,
                    0x00FFFFFF,
                    0xFFFFFFFF,
                    Shader.TileMode.CLAMP);
            fadePaint.setShader(fadeShader);
            int contentBottom = Math.max(0, getHeight() - bottomClipInset);
            int bottomFadeTop = Math.max(0, contentBottom - fadeHeightPx);
            bottomFadeShader = new LinearGradient(0f, bottomFadeTop, 0f, contentBottom, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            bottomFadePaint.setShader(bottomFadeShader);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            rebuildShader();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int contentBottom = Math.max(0, getHeight() - bottomClipInset);
            int save = canvas.saveLayer(0, 0, getWidth(), contentBottom, null);
            canvas.clipRect(0, 0, getWidth(), contentBottom);

            super.dispatchDraw(canvas);

            if (fadeHeightPx > 0) {
                if (fadeShader != null) {
                    canvas.drawRect(0, 0, getWidth(), fadeHeightPx, fadePaint);
                }

                canvas.drawRect(0, fadeHeightPx, getWidth(), contentBottom, solidPaint);
            }
            if(bottomClipInset > 0 && bottomFadeShader != null) canvas.drawRect(0, Math.max(0, contentBottom - fadeHeightPx), getWidth(), contentBottom, bottomFadePaint);

            canvas.restoreToCount(save);
        }
    }

    private static class InteractionFrameLayout extends FrameLayout {
        private final Runnable interaction;
        private View header;
        private boolean lyricsGesture;
        private boolean moved;
        private float downRawY;

        InteractionFrameLayout(Context context, Runnable interaction) {
            super(context);
            this.interaction = interaction;
        }

        void setHeader(View header) {
            this.header = header;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if(action == MotionEvent.ACTION_DOWN) {
                lyricsGesture = header == null || event.getY() >= header.getBottom();
                downRawY = event.getRawY();
                moved = false;
                if(lyricsGesture) getParent().requestDisallowInterceptTouchEvent(true);
            } else if(action == MotionEvent.ACTION_MOVE && lyricsGesture) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float distance = event.getRawY() - downRawY;
                int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if(Math.abs(distance) > touchSlop) moved = true;
                if(distance > touchSlop) interaction.run();
            }
            boolean handled = super.dispatchTouchEvent(event);
            if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if(lyricsGesture) getParent().requestDisallowInterceptTouchEvent(false);
                if(action == MotionEvent.ACTION_UP && lyricsGesture && !moved) interaction.run();
                lyricsGesture = false;
            }
            return handled;
        }
    }
}

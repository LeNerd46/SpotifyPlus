package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.R;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

public final class NowPlayingViewHook extends SpotifyHook {
    public static final String PREFERENCE = "experiment_now_playing_view";

    private static volatile boolean enabled;

    public NowPlayingViewHook(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        enabled = preferences.getBoolean(PREFERENCE, false);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        Activity activity = References.currentActivity;
        if(activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.getWindow().getDecorView().post(() -> {
            if(activity.isFinishing() || activity.isDestroyed()) return;
            RemoveCreateButtonHook.resetSettingsOverlayState();
            activity.recreate();
        });
    }

    static boolean isEnabled() {
        return enabled;
    }

    @Override
    protected void hook() {
        new NowPlayingLandscapeHook().init(lpparm, bridge);
        new NowPlayingSwipeHook().init(lpparm, bridge);
        new NowPlayingCardsHook().init(lpparm, bridge);
        new NowPlayingControlsHook().init(lpparm, bridge);
        XposedBridge.log("[SpotifyPlus][NowPlayingView] Dynamic experimental redesign hooks installed");
    }
}

final class NowPlayingLandscapeHook extends SpotifyHook {
    private static final String NOW_PLAYING_ACTIVITY = "com.spotify.nowplaying.musicinstallation.NowPlayingActivity";
    private static final String PEEK_SCROLL_VIEW = "com.spotify.nowplaying.scroll.view.PeekScrollView";
    private final WeakHashMap<View, Activity> attachedPlayers = new WeakHashMap<>();
    private final WeakHashMap<Activity, Boolean> launchedPlayers = new WeakHashMap<>();

    @Override
    protected void hook() {
        try {
            Class<?> nowPlayingActivity = lpparm.classLoader.loadClass(NOW_PLAYING_ACTIVITY);
            Class<?> peekScrollView = lpparm.classLoader.loadClass(PEEK_SCROLL_VIEW);
            XposedHelpers.findAndHookMethod(nowPlayingActivity, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!NowPlayingViewHook.isEnabled()) return;
                    Activity activity = (Activity)param.thisObject;
                    makeWindowTransparent(activity);
                    requestOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
                }
            });
            XposedHelpers.findAndHookMethod(nowPlayingActivity, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(NowPlayingViewHook.isEnabled() && Boolean.TRUE.equals(param.args[0])) makeWindowTransparent((Activity)param.thisObject);
                }
            });
            XposedBridge.hookAllConstructors(peekScrollView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!NowPlayingViewHook.isEnabled() || !(param.thisObject instanceof View)) return;
                    View player = (View)param.thisObject;
                    player.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View view) {
                            if(!NowPlayingViewHook.isEnabled()) return;
                            Activity activity = activity(view.getContext());
                            if(activity == null) activity = References.currentActivity;
                            if(activity == null) return;
                            attachedPlayers.put(view, activity);
                            Activity hostActivity = activity;
                            if(!NOW_PLAYING_ACTIVITY.equals(hostActivity.getClass().getName())) view.post(() -> openDedicatedPlayer(hostActivity));
                        }

                        @Override
                        public void onViewDetachedFromWindow(View view) {
                            Activity activity = attachedPlayers.remove(view);
                            if(activity != null) activity.getWindow().getDecorView().postDelayed(() -> clearLaunchGuard(activity), 300L);
                        }
                    });
                }
            });
            XposedBridge.hookAllMethods(Activity.class, "setRequestedOrientation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if(NowPlayingViewHook.isEnabled() && param.thisObject != null && NOW_PLAYING_ACTIVITY.equals(param.thisObject.getClass().getName())) param.args[0] = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
                }
            });
            XposedBridge.log("[SpotifyPlus][NowPlayingLandscape] Now Playing rotation enabled");
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingLandscape] Could not enable Now Playing rotation");
            XposedBridge.log(throwable);
        }
    }

    private void requestOrientation(Activity activity, int orientation) {
        activity.setRequestedOrientation(orientation);
    }

    private void makeWindowTransparent(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void openDedicatedPlayer(Activity activity) {
        if(!NowPlayingViewHook.isEnabled() || activity.isFinishing() || Boolean.TRUE.equals(launchedPlayers.get(activity))) return;
        launchedPlayers.put(activity, Boolean.TRUE);
        Intent intent = new Intent();
        intent.setClassName(activity, NOW_PLAYING_ACTIVITY);
        activity.onBackPressed();
        activity.startActivity(intent);
    }

    private void clearLaunchGuard(Activity activity) {
        for(Activity attachedActivity : attachedPlayers.values()) if(attachedActivity == activity) return;
        launchedPlayers.remove(activity);
    }

    private Activity activity(Context context) {
        Context current = context;
        while(current instanceof ContextWrapper) {
            if(current instanceof Activity) return (Activity)current;
            Context next = ((ContextWrapper)current).getBaseContext();
            if(next == current) break;
            current = next;
        }
        return null;
    }
}

final class NowPlayingSwipeHook extends SpotifyHook {
    private static final String NOW_PLAYING_BAR_ID = "com.spotify.music:id/now_playing_bar_layout";
    private static final String NOW_PLAYING_CONTAINER_ID = "com.spotify.music:id/now_playing_container";
    private volatile WeakReference<Activity> gestureActivity = new WeakReference<>(null);
    private volatile WeakReference<View> miniPlayer = new WeakReference<>(null);
    private volatile WeakReference<View> nowPlayingSheet = new WeakReference<>(null);
    private volatile boolean tracking;
    private volatile boolean dragging;
    private volatile boolean rejected;
    private volatile boolean openingByGesture;
    private boolean pageApiAvailable;
    private volatile boolean pageOpened;
    private volatile boolean findingSheet;
    private volatile Boolean pendingCommit;
    private volatile float downRawX;
    private volatile float downRawY;
    private volatile float currentRawY;
    private volatile int touchSlop;
    private volatile VelocityTracker velocityTracker;

    @Override
    protected void hook() {
        try {
            var pageConfigs = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("enable_page_api_npv", "android-nowplaying-musicinstallation").addMethod(MethodMatcher.create().returnType(boolean.class).paramCount(0))));
            if (!pageConfigs.isEmpty()) {
                Class<?> pageApiConfig = pageConfigs.single().getInstance(lpparm.classLoader);
                // The configuration gained a second boolean for load reporting. Follow
                // the flag read by the actual page launcher instead of reflection order.
                java.util.Map<String, org.luckypray.dexkit.result.MethodData> flagReads = new java.util.LinkedHashMap<>();
                for (var launcher : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingStrings("now_playing_view_container")))) {
                    for (var call : launcher.getInvokes()) if (call.getClassName().equals(pageApiConfig.getName())
                            && call.getParamCount() == 0 && "boolean".equals(call.getReturnTypeName())) flagReads.put(call.getDescriptor(), call);
                }
                if (flagReads.size() != 1) throw new IllegalStateException("Could not uniquely resolve the Now Playing page flag: " + flagReads);
                Method pageApiEnabled = flagReads.values().iterator().next().getMethodInstance(lpparm.classLoader);
                XposedBridge.hookMethod(pageApiEnabled, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if(NowPlayingViewHook.isEnabled()) param.setResult(true);
                    }
                });
                pageApiAvailable = true;
            }
            hookLayoutInflation();
            hookActivityTouches();
            XposedBridge.log("[SpotifyPlus][NowPlayingSwipe] Installed " + (pageApiAvailable ? "page" : "legacy activity") + " gesture");
        } catch (Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingSwipe] Could not initialize the swipe-up gesture");
            XposedBridge.log(throwable);
        }
    }

    private void hookLayoutInflation() {
        XposedBridge.hookAllMethods(LayoutInflater.class, "inflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if(!NowPlayingViewHook.isEnabled() || param.args.length == 0 || !(param.args[0] instanceof Integer) || !(param.getResult() instanceof View)) return;
                View view = (View) param.getResult();
                String layoutName;
                try {layoutName = view.getResources().getResourceName((Integer) param.args[0]);} catch (Throwable ignored) {return;}
                if(layoutName.endsWith(":layout/now_playing_bar")) roundMiniPlayer(view);
                if(!openingByGesture) return;
                if(!layoutName.endsWith(":layout/now_playing_container_bottom_sheet")) return;
                attachGestureSheet(view);
            }
        });
    }

    private void roundMiniPlayer(View bar) {
        applyMiniPlayerWidth(bar);
        bar.setClipToOutline(true);
        bar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
        bar.post(() -> {
            applyMiniPlayerWidth(bar);
            bar.invalidateOutline();
            ImageView artwork = findMiniArtwork(bar, bar);
            if(artwork == null) return;
            artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
            artwork.setClipToOutline(true);
            artwork.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            artwork.invalidateOutline();
        });
    }

    private void applyMiniPlayerWidth(View bar) {
        if(!(bar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) bar.getLayoutParams();
        int horizontalMargin = dp(bar, 20);
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.setMarginStart(horizontalMargin);
        params.setMarginEnd(horizontalMargin);
        bar.setLayoutParams(params);
    }

    private ImageView findMiniArtwork(View view, View root) {
        ImageView best = null;
        long bestArea = 0;
        if(view instanceof ImageView && view.getWidth() >= dp(root, 36) && view.getHeight() >= dp(root, 36)) {
            float ratio = view.getWidth() / (float) view.getHeight();
            if(ratio > 0.85f && ratio < 1.15f) {
                best = (ImageView) view;
                bestArea = (long) view.getWidth() * view.getHeight();
            }
        }
        if(!(view instanceof ViewGroup)) return best;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            ImageView candidate = findMiniArtwork(group.getChildAt(index), root);
            if(candidate == null) continue;
            long area = (long) candidate.getWidth() * candidate.getHeight();
            if(area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private void hookActivityTouches() throws Throwable {
        Method dispatchTouchEvent = Activity.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        XposedBridge.hookMethod(dispatchTouchEvent, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if(!NowPlayingViewHook.isEnabled()) {
                    resetGesture(true);
                    return;
                }
                Activity activity = (Activity) param.thisObject;
                if(!"com.spotify.music.SpotifyMainActivity".equals(activity.getClass().getName()) || !(param.args[0] instanceof MotionEvent)) return;
                MotionEvent event = (MotionEvent) param.args[0];
                if(handleTouch(dispatchTouchEvent, activity, event)) param.setResult(true);
            }
        });
    }

    private boolean handleTouch(Method dispatchTouchEvent, Activity activity, MotionEvent event) throws Throwable {
        if(!NowPlayingViewHook.isEnabled()) {
            resetGesture(true);
            return false;
        }
        int action = event.getActionMasked();
        if(action == MotionEvent.ACTION_DOWN) {
            resetGesture(false);
            View bar = findViewByResourceName(activity.getWindow().getDecorView(), NOW_PLAYING_BAR_ID);
            if(bar == null || !bar.isShown() || !containsRawPoint(bar, event.getRawX(), event.getRawY())) return false;
            tracking = true;
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            currentRawY = downRawY;
            touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);
            gestureActivity = new WeakReference<>(activity);
            miniPlayer = new WeakReference<>(bar);
            return false;
        }
        if(!tracking) return false;
        if(velocityTracker != null) velocityTracker.addMovement(event);
        if(action == MotionEvent.ACTION_POINTER_DOWN) rejected = true;
        if(action == MotionEvent.ACTION_MOVE) {
            currentRawY = event.getRawY();
            float dx = event.getRawX() - downRawX;
            float dy = currentRawY - downRawY;
            if(!dragging && !rejected && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) rejected = true;
            if(!dragging && !rejected && -dy > touchSlop && -dy > Math.abs(dx) * 1.15f) {
                dragging = true;
                openingByGesture = pageApiAvailable;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                XposedBridge.invokeOriginalMethod(dispatchTouchEvent, activity, new Object[]{cancel});
                cancel.recycle();
                View bar = miniPlayer.get();
                if(pageApiAvailable && bar != null) bar.post(() -> openNowPlaying(bar));
            }
            if(dragging) {
                updateSheetPosition(activity);
                return true;
            }
            return false;
        }
        if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if(!dragging) {
                resetGesture(false);
                return false;
            }
            boolean commit = action == MotionEvent.ACTION_UP && shouldCommit(activity);
            if (!pageApiAvailable) {
                // 9.1.28 predates the page API. A committed swipe opens its native activity.
                View bar = miniPlayer.get();
                resetGesture(false);
                if (commit && bar != null) findClickTarget(bar).performClick();
                return true;
            }
            finishGesture(activity, commit);
            return true;
        }
        return dragging;
    }

    private void openNowPlaying(View bar) {
        if(!NowPlayingViewHook.isEnabled() || !openingByGesture) return;
        View clickTarget = findClickTarget(bar);
        if(clickTarget == null || !clickTarget.performClick()) {
            XposedBridge.log("[SpotifyPlus][NowPlayingSwipe] The mini-player click target was not found");
            finishGesture(gestureActivity.get(), false);
            return;
        }
        pageOpened = true;
        Activity activity = gestureActivity.get();
        if(activity != null) findGestureSheet(activity, 0);
    }

    private View findClickTarget(View bar) {
        View current = bar;
        while(current != null) {
            if(current.hasOnClickListeners()) return current;
            if(!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return bar;
    }

    private void findGestureSheet(Activity activity, int attempt) {
        if(!NowPlayingViewHook.isEnabled() || !openingByGesture || activity == null) return;
        if(attempt == 0 && findingSheet) return;
        if(attempt == 0) findingSheet = true;
        View sheet = findNowPlayingSheet(activity.getWindow().getDecorView(), miniPlayer.get());
        if(sheet != null) {
            findingSheet = false;
            attachGestureSheet(sheet);
            return;
        }
        if(attempt < 12) activity.getWindow().getDecorView().postDelayed(() -> findGestureSheet(activity, attempt + 1), 16);
        else {
            findingSheet = false;
            openingByGesture = false;
            if(Boolean.FALSE.equals(pendingCommit) && pageOpened && !activity.isFinishing()) activity.onBackPressed();
            pendingCommit = null;
            XposedBridge.log("[SpotifyPlus][NowPlayingSwipe] Timed out waiting for the Now Playing sheet");
        }
    }

    private void attachGestureSheet(View sheet) {
        if(!NowPlayingViewHook.isEnabled() || !openingByGesture) return;
        findingSheet = false;
        nowPlayingSheet = new WeakReference<>(sheet);
        Activity activity = gestureActivity.get();
        if(activity != null) sheet.setTranslationY(sheetTravel(activity));
        sheet.post(() -> {
            Activity currentActivity = gestureActivity.get();
            if(!openingByGesture || currentActivity == null) return;
            if(pendingCommit != null) settleSheet(currentActivity, pendingCommit);
            else updateSheetPosition(currentActivity);
        });
    }

    private void updateSheetPosition(Activity activity) {
        View sheet = nowPlayingSheet.get();
        if(sheet == null) {
            findGestureSheet(activity, 0);
            return;
        }
        float distance = Math.max(0f, downRawY - currentRawY);
        sheet.animate().cancel();
        sheet.setTranslationY(Math.max(0f, sheetTravel(activity) - distance));
    }

    private boolean shouldCommit(Activity activity) {
        float distance = Math.max(0f, downRawY - currentRawY);
        float velocityY = 0f;
        if(velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            velocityY = velocityTracker.getYVelocity();
        }
        return distance > sheetTravel(activity) * 0.12f || velocityY < -1200f;
    }

    private void finishGesture(Activity activity, boolean commit) {
        View sheet = nowPlayingSheet.get();
        if(sheet == null) {
            tracking = false;
            dragging = false;
            recycleVelocityTracker();
            if(!commit && !pageOpened) {
                resetGesture(true);
                return;
            }
            pendingCommit = commit;
            if(activity != null) findGestureSheet(activity, 0);
            return;
        }
        settleSheet(activity, commit);
    }

    private void settleSheet(Activity activity, boolean commit) {
        openingByGesture = false;
        pendingCommit = null;
        View sheet = nowPlayingSheet.get();
        if(sheet == null) return;
        float target = commit ? 0f : sheetTravel(activity);
        sheet.animate().translationY(target).setDuration(commit ? 220 : 180).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
            if(!commit && activity != null && !activity.isFinishing()) activity.onBackPressed();
            resetGesture(true);
        }).start();
        recycleVelocityTracker();
        tracking = false;
        dragging = false;
    }

    private float sheetTravel(Activity activity) {
        if(activity == null) return 1f;
        View decor = activity.getWindow().getDecorView();
        return Math.max(1, decor.getHeight());
    }

    private View findNowPlayingSheet(View root, View bar) {
        if(root == null) return null;
        if(NOW_PLAYING_CONTAINER_ID.equals(resourceName(root)) && root != bar && !isAncestor(root, bar) && root.isShown() && root.getHeight() > root.getResources().getDisplayMetrics().heightPixels * 0.75f) return root;
        if(root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for(int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = findNowPlayingSheet(group.getChildAt(i), bar);
                if(found != null) return found;
            }
        }
        return null;
    }

    private View findViewByResourceName(View root, String name) {
        if(root == null) return null;
        if(name.equals(resourceName(root))) return root;
        if(root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for(int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = findViewByResourceName(group.getChildAt(i), name);
                if(found != null) return found;
            }
        }
        return null;
    }

    private String resourceName(View view) {
        if(view.getId() == View.NO_ID) return "";
        try {return view.getResources().getResourceName(view.getId());} catch (Throwable ignored) {return "";}
    }

    private boolean containsRawPoint(View view, float rawX, float rawY) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0] && rawX < location[0] + view.getWidth() && rawY >= location[1] && rawY < location[1] + view.getHeight();
    }

    private boolean isAncestor(View ancestor, View child) {
        if(ancestor == null || child == null) return false;
        View current = child;
        while(current != null) {
            if(current == ancestor) return true;
            if(!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private void resetGesture(boolean clearSheet) {
        tracking = false;
        dragging = false;
        rejected = false;
        openingByGesture = false;
        pageOpened = false;
        findingSheet = false;
        pendingCommit = null;
        recycleVelocityTracker();
        if(clearSheet) {
            nowPlayingSheet = new WeakReference<>(null);
            miniPlayer = new WeakReference<>(null);
            gestureActivity = new WeakReference<>(null);
        }
    }

    private void recycleVelocityTracker() {
        if(velocityTracker != null) velocityTracker.recycle();
        velocityTracker = null;
    }
}

final class NowPlayingCardsHook extends SpotifyHook {
    private static final String PEEK_SCROLL_VIEW = "com.spotify.nowplaying.scroll.view.PeekScrollView";

    @Override
    protected void hook() {
        try {
            Class<?> peekScrollView = lpparm.classLoader.loadClass(PEEK_SCROLL_VIEW);
            XposedBridge.hookAllConstructors(peekScrollView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!NowPlayingViewHook.isEnabled() || !(param.thisObject instanceof View)) return;
                    hideSupplementalCards((View) param.thisObject);
                }
            });
            XposedBridge.log("[SpotifyPlus][NowPlayingCards] Supplemental Now Playing cards disabled");
        } catch (Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingCards] Could not disable supplemental Now Playing cards");
            XposedBridge.log(throwable);
        }
    }

    private void hideSupplementalCards(View root) {
        int containerId = root.getResources().getIdentifier("touch_blocking_container", "id", "com.spotify.music");
        View container = containerId == 0 ? null : root.findViewById(containerId);
        if(container == null) return;
        container.setVisibility(View.GONE);
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
}

final class NowPlayingControlsHook extends SpotifyHook {
    private static final String PEEK_SCROLL_VIEW = "com.spotify.nowplaying.scroll.view.PeekScrollView";
    private static final int WATCH_TAG = 0x53504C57;
    private static final int TRANSPORT_TAG = 0x53504C54;
    private static final int ACTIONS_TAG = 0x53504C41;
    private final Map<View, LyricsState> states = new WeakHashMap<>();
    private final ExecutorService artworkLoader = Executors.newSingleThreadExecutor();
    private volatile Bitmap cachedArtwork;
    private volatile String cachedArtworkUri;
    private volatile Drawable cachedArtworkSource;

    @Override
    protected void hook() {
        try {
            Class<?> peekScrollView = lpparm.classLoader.loadClass(PEEK_SCROLL_VIEW);
            XposedBridge.hookAllConstructors(peekScrollView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!NowPlayingViewHook.isEnabled() || !(param.thisObject instanceof View)) return;
                    watchNowPlayingLayout((View) param.thisObject);
                }
            });
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Custom Now Playing control layout enabled");
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Could not enable the custom control layout");
            XposedBridge.log(throwable);
        }
    }

    private void watchNowPlayingLayout(View root) {
        if(root.getTag(WATCH_TAG) != null) return;
        root.setTag(WATCH_TAG, Boolean.TRUE);
        Handler handler = new Handler(Looper.getMainLooper());
        View.OnLayoutChangeListener listener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> applyLayout(root);
        root.addOnLayoutChangeListener(listener);
        for(int attempt = 0; attempt < 20; attempt++) handler.postDelayed(() -> applyLayout(root), attempt * 100L);
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                if(!root.isAttachedToWindow()) return;
                applyLayout(root);
                root.postDelayed(this, 1000L);
            }
        };
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                view.removeCallbacks(refresh);
                view.post(refresh);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.removeCallbacks(refresh);
            }
        });
        root.postDelayed(refresh, 1000L);
    }

    private void applyLayout(View root) {
        if(!NowPlayingViewHook.isEnabled()) return;
        try {
            arrangeTransportControls(root);
            arrangeBottomActions(root);
            LyricsState state = states.get(root);
            arrangeLandscape(root, state == null ? state(root) : state);
            if(state != null && state.open) suppressStickyHeader(root, state);
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Layout pass failed");
            XposedBridge.log(throwable);
        }
    }

    private void arrangeTransportControls(View root) {
        ViewGroup container = findGroup(root, "playback_controls_container");
        if(container == null || container.getTag(TRANSPORT_TAG) != null) return;
        View playPause = find(root, "nowplaying_elements_playpause_button");
        View previous = findByDescription(root, string(root, "np_content_desc_prev"));
        View next = findByDescription(root, string(root, "np_content_desc_next"));
        if(playPause == null || previous == null || next == null || previous.getParent() != container || playPause.getParent() != container || next.getParent() != container) return;
        container.setTag(TRANSPORT_TAG, Boolean.TRUE);
        container.removeAllViews();
        container.setPadding(dp(root, 12), dp(root, 6), dp(root, 12), dp(root, 6));
        container.setBackground(capsule(root, 0x52242424));
        ViewGroup.MarginLayoutParams containerParams = margins(container.getLayoutParams());
        containerParams.height = dp(root, 78);
        containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        containerParams.setMargins(dp(root, 40), dp(root, 8), dp(root, 40), dp(root, 8));
        container.setLayoutParams(containerParams);
        LinearLayout row = new LinearLayout(root.getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        container.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addCell(row, previous, dp(root, 44));
        addCell(row, playPause, dp(root, 60));
        addCell(row, next, dp(root, 44));
    }

    private void arrangeBottomActions(View root) {
        ViewGroup trackInfo = findGroup(root, "track_info_feedback_container");
        View negativeFeedback = trackInfo == null ? null : findByDescription(trackInfo, string(root, "np_content_desc_ban"));
        if(negativeFeedback != null) negativeFeedback.setVisibility(View.GONE);
        ViewGroup footer = findGroup(root, "revised_template_overlay_footer");
        ViewGroup accessory = findGroup(root, "accessory_row");
        if(footer == null || accessory == null || footer.getTag(ACTIONS_TAG) != null) return;
        View share = findByDescription(accessory, string(root, "np_content_desc_share"));
        View queueIcon = find(root, "queue_button");
        View queue = queueIcon == null ? null : directChildOf(queueIcon, accessory);
        share = share == null ? null : directChildOf(share, accessory);
        if(share == null || queue == null) return;
        detach(share);
        detach(queue);
        LyricsState state = state(root);
        footer.setTag(ACTIONS_TAG, Boolean.TRUE);
        footer.removeAllViews();
        footer.setPadding(0, 0, 0, 0);
        footer.setBackground(null);
        ViewGroup.MarginLayoutParams footerParams = margins(footer.getLayoutParams());
        footerParams.height = dp(root, 68);
        footerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        footerParams.setMargins(0, dp(root, 12), 0, dp(root, 8));
        footer.setLayoutParams(footerParams);
        FrameLayout holder = new FrameLayout(root.getContext());
        footer.addView(holder, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(root.getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(root, 8), dp(root, 4), dp(root, 8), dp(root, 4));
        row.setBackground(capsule(root, 0x52242424));
        holder.addView(row, new FrameLayout.LayoutParams(dp(root, 200), dp(root, 60), Gravity.CENTER));
        ImageView lyrics = new ImageView(root.getContext());
        lyrics.setImageDrawable(lyricsIcon(root));
        lyrics.setContentDescription(References.getString(R.string.lyrics_button));
        lyrics.setPadding(dp(root, 12), dp(root, 12), dp(root, 12), dp(root, 12));
        lyrics.setOnClickListener(view -> toggleLyrics(root));
        state.lyricsButton = lyrics;
        state.transport = findGroup(root, "playback_controls_container");
        state.footer = footer;
        state.actionRow = row;
        addCell(row, lyrics, dp(root, 48));
        addCell(row, share, dp(root, 48));
        addCell(row, queue, dp(root, 48));
    }

    private void arrangeLandscape(View root, LyricsState state) {
        if(root.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            restorePortraitLayout(state);
            captureArtwork(root, state);
            return;
        }
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        if(overlay == null || root.getWidth() == 0 || root.getHeight() == 0) return;
        loadCurrentArtwork(root, state);
        if(state.landscapePanel == null) {
            FrameLayout panel = new FrameLayout(root.getContext());
            panel.setBackground(panel(root));
            overlay.addView(panel, 0);
            state.landscapePanel = panel;
        }
        if(state.landscapeCover == null) {
            ImageView cover = new ImageView(root.getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            cover.setElevation(dp(root, 12));
            cover.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(root, 12));
                }
            });
            overlay.addView(cover, 0);
            state.landscapeCover = cover;
        }
        if(state.landscapeMetadata == null) {
            LinearLayout metadata = new LinearLayout(root.getContext());
            metadata.setOrientation(LinearLayout.VERTICAL);
            metadata.setGravity(Gravity.CENTER);
            TextView title = new TextView(root.getContext());
            title.setTextColor(Color.WHITE);
            title.setTextSize(18f);
            title.setGravity(Gravity.CENTER);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView artist = new TextView(root.getContext());
            artist.setTextColor(0xFFB3B3B3);
            artist.setTextSize(14f);
            artist.setGravity(Gravity.CENTER);
            artist.setSingleLine(true);
            artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            metadata.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            metadata.addView(artist, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            overlay.addView(metadata);
            state.landscapeMetadata = metadata;
            state.landscapeTitle = title;
            state.landscapeArtist = artist;
        }
        if(cachedArtwork != null && state.landscapeCover.getDrawable() == null) state.landscapeCover.setImageBitmap(cachedArtwork);
        int coverSize = Math.min(dp(root, 252), root.getHeight() - dp(root, 76));
        ViewGroup.LayoutParams coverParams = state.landscapeCover.getLayoutParams();
        coverParams.width = coverSize;
        coverParams.height = coverSize;
        state.landscapeCover.setLayoutParams(coverParams);
        state.landscapeCover.setX(dp(root, 30));
        float coverTop = Math.max(dp(root, 34), (root.getHeight() - coverSize - dp(root, 58)) / 2f);
        state.landscapeCover.setY(coverTop);
        state.landscapeCover.setVisibility(state.open ? View.INVISIBLE : View.VISIBLE);
        ViewGroup.LayoutParams metadataParams = state.landscapeMetadata.getLayoutParams();
        metadataParams.width = coverSize;
        metadataParams.height = dp(root, 54);
        state.landscapeMetadata.setLayoutParams(metadataParams);
        state.landscapeMetadata.setX(dp(root, 30));
        state.landscapeMetadata.setY(coverTop + coverSize + dp(root, 8));
        state.landscapeMetadata.setVisibility(state.open ? View.INVISIBLE : View.VISIBLE);
        int rightStart = Math.max(coverSize + dp(root, 72), Math.round(root.getWidth() * 0.42f));
        int rightWidth = root.getWidth() - rightStart - dp(root, 28);
        View nativeTitle = find(root, "track_info_view_title");
        View nativeArtist = find(root, "track_info_view_subtitle");
        syncMetadata(state.landscapeTitle, nativeTitle);
        syncMetadata(state.landscapeArtist, nativeArtist);
        hideInLandscape(state, nativeTitle);
        hideInLandscape(state, nativeArtist);
        ViewGroup.LayoutParams panelParams = state.landscapePanel.getLayoutParams();
        panelParams.width = rightWidth + dp(root, 24);
        panelParams.height = Math.max(dp(root, 248), root.getHeight() - dp(root, 126));
        state.landscapePanel.setLayoutParams(panelParams);
        state.landscapePanel.setX(rightStart - dp(root, 12));
        state.landscapePanel.setY(dp(root, 92));
        state.landscapePanel.setVisibility(state.open ? View.INVISIBLE : View.VISIBLE);
        moveIntoPane(root, find(root, "lyrics_element"), state, rightStart, dp(root, 108), rightWidth - dp(root, 64), dp(root, 50));
        moveIntoPane(root, find(root, "track_info_feedback_container"), state, rightStart + rightWidth - dp(root, 56), dp(root, 104), dp(root, 56), dp(root, 56));
        moveIntoPane(root, find(root, "track_seekbar"), state, rightStart, dp(root, 158), rightWidth, dp(root, 48));
        int controlsWidth = Math.min(rightWidth, dp(root, 430));
        int controlsLeft = rightStart + Math.max(0, (rightWidth - controlsWidth) / 2);
        moveIntoPane(root, state.transport, state, controlsLeft, dp(root, 210), controlsWidth, dp(root, 70));
        moveIntoPane(root, state.footer, state, controlsLeft, dp(root, 282), controlsWidth, dp(root, 58));
        if(state.transport != null) state.transport.setBackground(null);
        if(state.actionRow != null) state.actionRow.setBackground(null);
    }

    private void moveIntoPane(View root, View view, LyricsState state, int desiredLeft, int desiredTop, int width, int height) {
        if(view == null || view.getWidth() == 0) return;
        if(!state.originalWidths.containsKey(view)) state.originalWidths.put(view, view.getLayoutParams().width);
        if(!state.originalHeights.containsKey(view)) state.originalHeights.put(view, view.getLayoutParams().height);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        if(height >= 0) params.height = height;
        view.setLayoutParams(params);
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        view.getLocationInWindow(viewLocation);
        float baseLeft = viewLocation[0] - rootLocation[0] - view.getTranslationX();
        float baseTop = viewLocation[1] - rootLocation[1] - view.getTranslationY();
        view.setTranslationX(desiredLeft - baseLeft);
        if(desiredTop >= 0) view.setTranslationY(desiredTop - baseTop);
    }

    private void restorePortraitLayout(LyricsState state) {
        if(state == null) return;
        if(state.landscapeCover != null) state.landscapeCover.setVisibility(View.GONE);
        if(state.landscapeMetadata != null) state.landscapeMetadata.setVisibility(View.GONE);
        if(state.landscapePanel != null) state.landscapePanel.setVisibility(View.GONE);
        if(state.transport != null) state.transport.setBackground(capsule(state.transport, 0x52242424));
        if(state.actionRow != null) state.actionRow.setBackground(capsule(state.actionRow, 0x52242424));
        for(Map.Entry<View, Integer> entry : state.originalVisibilities.entrySet()) entry.getKey().setVisibility(entry.getValue());
        state.originalVisibilities.clear();
        for(Map.Entry<View, Integer> entry : state.originalWidths.entrySet()) {
            View view = entry.getKey();
            if(view.getLayoutParams() != null) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = entry.getValue();
                Integer height = state.originalHeights.get(view);
                if(height != null) params.height = height;
                view.setLayoutParams(params);
            }
            view.setTranslationX(0f);
            view.setTranslationY(0f);
        }
        state.originalWidths.clear();
        state.originalHeights.clear();
    }

    private void hideInLandscape(LyricsState state, View view) {
        if(view == null) return;
        if(!state.originalVisibilities.containsKey(view)) state.originalVisibilities.put(view, view.getVisibility());
        view.setVisibility(View.INVISIBLE);
    }

    private void syncMetadata(TextView target, View source) {
        TextView sourceText = findTextView(source);
        if(target != null && sourceText != null) target.setText(sourceText.getText());
    }

    private TextView findTextView(View view) {
        if(view instanceof TextView) return (TextView)view;
        if(!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup)view;
        for(int index = 0; index < group.getChildCount(); index++) {
            TextView text = findTextView(group.getChildAt(index));
            if(text != null) return text;
        }
        return null;
    }

    private void captureArtwork(View root, LyricsState state) {
        View visibleArtwork = findArtwork(find(root, "track_carousel"), root);
        ImageView artwork = visibleArtwork instanceof ImageView ? (ImageView)visibleArtwork : cachedArtwork == null ? findArtworkImage(find(root, "track_carousel")) : null;
        if(artwork == null || artwork.getDrawable() == null) return;
        Drawable source = artwork.getDrawable();
        if(source == cachedArtworkSource) return;
        Bitmap snapshot = drawableBitmap(source, artwork.getWidth(), artwork.getHeight());
        if(snapshot == null) return;
        cachedArtworkSource = source;
        cachedArtwork = snapshot;
        if(state.landscapeCover != null) state.landscapeCover.setImageBitmap(snapshot);
    }

    private void loadCurrentArtwork(View root, LyricsState state) {
        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if(track == null || track.uri == null || track.imageId == null || track.imageId.isEmpty() || track.uri.equals(state.landscapeArtworkUri) || track.uri.equals(cachedArtworkUri)) return;
        state.landscapeArtworkUri = track.uri;
        String requestedUri = track.uri;
        String artworkUrl = track.imageId.startsWith("http") ? track.imageId : "https://i.scdn.co/image/" + track.imageId;
        artworkLoader.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL(artworkUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try(InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if(bitmap == null) return;
                    root.post(() -> {
                        if(!requestedUri.equals(state.landscapeArtworkUri) || state.landscapeCover == null) return;
                        cachedArtwork = bitmap;
                        cachedArtworkUri = requestedUri;
                        state.landscapeCover.setImageBitmap(bitmap);
                    });
                }
            } catch(Throwable throwable) {
                XposedBridge.log("[SpotifyPlus][NowPlayingControls] Could not load landscape artwork");
                XposedBridge.log(throwable);
            } finally {
                if(connection != null) connection.disconnect();
            }
        });
    }

    private Bitmap drawableBitmap(Drawable drawable, int viewWidth, int viewHeight) {
        if(drawable instanceof BitmapDrawable && ((BitmapDrawable)drawable).getBitmap() != null) return ((BitmapDrawable)drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, false);
        int width = viewWidth > 0 ? viewWidth : drawable.getIntrinsicWidth();
        int height = viewHeight > 0 ? viewHeight : drawable.getIntrinsicHeight();
        if(width <= 0 || height <= 0) return null;
        float scale = Math.min(1f, 768f / Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect originalBounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(canvas);
        drawable.setBounds(originalBounds);
        return bitmap;
    }

    private ImageView findArtworkImage(View view) {
        if(view == null) return null;
        ImageView best = view instanceof ImageView && ((ImageView)view).getDrawable() != null ? (ImageView)view : null;
        long bestArea = best == null ? 0 : (long)Math.max(best.getWidth(), best.getDrawable().getIntrinsicWidth()) * Math.max(best.getHeight(), best.getDrawable().getIntrinsicHeight());
        if(!(view instanceof ViewGroup)) return best;
        ViewGroup group = (ViewGroup)view;
        for(int index = 0; index < group.getChildCount(); index++) {
            ImageView candidate = findArtworkImage(group.getChildAt(index));
            if(candidate == null) continue;
            long area = (long)Math.max(candidate.getWidth(), candidate.getDrawable().getIntrinsicWidth()) * Math.max(candidate.getHeight(), candidate.getDrawable().getIntrinsicHeight());
            if(area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private LyricsState state(View root) {
        LyricsState existing = states.get(root);
        if(existing != null) return existing;
        LyricsState created = new LyricsState();
        created.hideControls = () -> setControlVisibility(created, false, true);
        states.put(root, created);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if(!created.open) return;
                created.open = false;
                created.handler.removeCallbacks(created.hideControls);
                if(created.artwork != null) created.artwork.setAlpha(1f);
                BeautifulLyricsHook.removeEmbedded(false);
            }
        });
        return created;
    }

    private void toggleLyrics(View root) {
        LyricsState state = state(root);
        if(state.transitioning) return;
        if(state.open) {
            closeLyrics(root, true);
            return;
        }
        Activity activity = References.currentActivity;
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        if(activity == null || overlay == null) return;
        boolean opened = BeautifulLyricsHook.showEmbedded(activity, overlay, () -> closeLyrics(root, true), () -> showControls(root));
        if(!opened) return;
        state.open = true;
        hideForLyrics(state, find(root, "player_overlay_header"));
        hideForLyrics(state, find(root, "lyrics_element"));
        hideForLyrics(state, find(root, "buttons_scroll_bar"));
        hideForLyrics(state, find(root, "track_info_feedback_container"));
        hideForLyrics(state, find(root, "track_seekbar"));
        suppressStickyHeader(root, state);
        if(root.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) setControlVisibility(state, false, false);
        else showControls(root);
        animateArtworkIntoLyrics(root, state);
    }

    private void closeLyrics(View root, boolean animated) {
        if (BeautifulLyricsHook.confirmCancelLyricsSync()) return;
        LyricsState state = state(root);
        if(!state.open || state.transitioning) return;
        state.open = false;
        state.handler.removeCallbacks(state.hideControls);
        setControlVisibility(state, true, false);
        restoreNativeViews(state);
        Runnable finish = () -> BeautifulLyricsHook.removeEmbedded(false);
        if(!animated || !animateArtworkOutOfLyrics(root, state, finish)) finish.run();
    }

    private void restoreNativeViews(LyricsState state) {
        for(Map.Entry<View, Integer> entry : state.hiddenViews.entrySet()) {
            View view = entry.getKey();
            view.animate().cancel();
            view.setVisibility(entry.getValue());
            if(entry.getValue() == View.VISIBLE) {
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(220).start();
            } else view.setAlpha(1f);
        }
        state.hiddenViews.clear();
    }

    private void animateArtworkIntoLyrics(View root, LyricsState state) {
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        View artwork = findArtwork(find(root, "track_carousel"), root);
        ImageView cover = BeautifulLyricsHook.getEmbeddedCover();
        if(overlay == null || artwork == null || cover == null || artwork.getWidth() == 0 || artwork.getHeight() == 0) {
            return;
        }
        state.transitioning = true;
        state.artwork = artwork;
        state.embeddedCover = cover;
        cover.setAlpha(0f);
        overlay.post(() -> {
            if(!state.open || !artwork.isAttachedToWindow() || !cover.isAttachedToWindow()) {
                state.transitioning = false;
                cover.setAlpha(1f);
                return;
            }
            ImageView snapshot = artworkSnapshot(artwork);
            if(snapshot == null) {
                state.transitioning = false;
                cover.setAlpha(1f);
                return;
            }
            int[] overlayLocation = new int[2];
            int[] artworkLocation = new int[2];
            int[] coverLocation = new int[2];
            overlay.getLocationInWindow(overlayLocation);
            artwork.getLocationInWindow(artworkLocation);
            cover.getLocationInWindow(coverLocation);
            overlay.addView(snapshot, new ViewGroup.LayoutParams(artwork.getWidth(), artwork.getHeight()));
            snapshot.setPivotX(0f);
            snapshot.setPivotY(0f);
            snapshot.setX(artworkLocation[0] - overlayLocation[0]);
            snapshot.setY(artworkLocation[1] - overlayLocation[1]);
            snapshot.setElevation(dp(root, 32));
            artwork.setAlpha(0f);
            float targetScaleX = cover.getWidth() / (float) artwork.getWidth();
            float targetScaleY = cover.getHeight() / (float) artwork.getHeight();
            snapshot.animate().x(coverLocation[0] - overlayLocation[0]).y(coverLocation[1] - overlayLocation[1]).scaleX(targetScaleX).scaleY(targetScaleY).setDuration(460).setInterpolator(new DecelerateInterpolator()).withLayer().withEndAction(() -> {
                detach(snapshot);
                cover.setAlpha(1f);
                state.transitioning = false;
            }).start();
        });
    }

    private boolean animateArtworkOutOfLyrics(View root, LyricsState state, Runnable finish) {
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        View artwork = state.artwork;
        ImageView cover = state.embeddedCover;
        if(overlay == null || artwork == null || cover == null || artwork.getWidth() == 0 || artwork.getHeight() == 0 || !cover.isAttachedToWindow()) return false;
        ImageView snapshot = artworkSnapshot(cover);
        if(snapshot == null) return false;
        state.transitioning = true;
        int[] overlayLocation = new int[2];
        int[] artworkLocation = new int[2];
        int[] coverLocation = new int[2];
        overlay.getLocationInWindow(overlayLocation);
        artwork.getLocationInWindow(artworkLocation);
        cover.getLocationInWindow(coverLocation);
        overlay.addView(snapshot, new ViewGroup.LayoutParams(artwork.getWidth(), artwork.getHeight()));
        snapshot.setPivotX(0f);
        snapshot.setPivotY(0f);
        snapshot.setX(coverLocation[0] - overlayLocation[0]);
        snapshot.setY(coverLocation[1] - overlayLocation[1]);
        snapshot.setScaleX(cover.getWidth() / (float) artwork.getWidth());
        snapshot.setScaleY(cover.getHeight() / (float) artwork.getHeight());
        snapshot.setElevation(dp(root, 32));
        cover.setAlpha(0f);
        snapshot.animate().x(artworkLocation[0] - overlayLocation[0]).y(artworkLocation[1] - overlayLocation[1]).scaleX(1f).scaleY(1f).setDuration(460).setInterpolator(new DecelerateInterpolator()).withLayer().withEndAction(() -> {
            detach(snapshot);
            artwork.setAlpha(1f);
            state.artwork = null;
            state.embeddedCover = null;
            state.transitioning = false;
            finish.run();
        }).start();
        return true;
    }

    private ImageView artworkSnapshot(View artwork) {
        try {
            float bitmapScale = Math.min(1f, 512f / Math.max(artwork.getWidth(), artwork.getHeight()));
            int bitmapWidth = Math.max(1, Math.round(artwork.getWidth() * bitmapScale));
            int bitmapHeight = Math.max(1, Math.round(artwork.getHeight() * bitmapScale));
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(bitmapWidth / (float) artwork.getWidth(), bitmapHeight / (float) artwork.getHeight());
            artwork.draw(canvas);
            ImageView snapshot = new ImageView(artwork.getContext());
            snapshot.setImageBitmap(bitmap);
            snapshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return snapshot;
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Could not capture the Now Playing artwork");
            XposedBridge.log(throwable);
            return null;
        }
    }

    private View findArtwork(View view, View root) {
        if(view == null) return null;
        View best = null;
        long bestArea = 0;
        if(view instanceof ImageView && view.getVisibility() == View.VISIBLE && view.getWidth() >= dp(root, 160) && view.getHeight() >= dp(root, 160)) {
            float ratio = view.getWidth() / (float) view.getHeight();
            if(ratio > 0.8f && ratio < 1.2f) {
                best = view;
                bestArea = visibleArea(view);
            }
        }
        if(!(view instanceof ViewGroup)) return best;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            View candidate = findArtwork(group.getChildAt(index), root);
            if(candidate == null) continue;
            long area = visibleArea(candidate);
            if(area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private long visibleArea(View view) {
        Rect visibleBounds = new Rect();
        return view.isShown() && view.getGlobalVisibleRect(visibleBounds) ? (long) visibleBounds.width() * visibleBounds.height() : 0;
    }

    private void hideForLyrics(LyricsState state, View view) {
        if(view == null || state.hiddenViews.containsKey(view)) return;
        state.hiddenViews.put(view, view.getVisibility());
        if(view.getVisibility() != View.VISIBLE) return;
        view.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if(state.open) view.setVisibility(View.INVISIBLE);
        }).start();
    }

    private void suppressStickyHeader(View root, LyricsState state) {
        View hierarchyRoot = root;
        while(hierarchyRoot.getParent() instanceof View) hierarchyRoot = (View) hierarchyRoot.getParent();
        View stickyHeader = find(hierarchyRoot, "revised_template_sticky_header");
        if(stickyHeader == null) return;
        if(!state.hiddenViews.containsKey(stickyHeader)) state.hiddenViews.put(stickyHeader, stickyHeader.getVisibility());
        stickyHeader.animate().cancel();
        stickyHeader.setAlpha(0f);
        stickyHeader.setVisibility(View.INVISIBLE);
    }

    private void showControls(View root) {
        LyricsState state = state(root);
        if(!state.open) return;
        if (BeautifulLyricsHook.isSyncingLyrics()) { setControlVisibility(state, false, false); return; }
        if(root.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setControlVisibility(state, false, false);
            return;
        }
        state.handler.removeCallbacks(state.hideControls);
        setControlVisibility(state, true, false);
        state.handler.postDelayed(state.hideControls, 2800L);
    }

    private void setControlVisibility(LyricsState state, boolean visible, boolean animate) {
        for(View control : new View[]{state.transport, state.footer}) {
            if(control == null) continue;
            control.animate().cancel();
            if(visible) {
                control.setVisibility(View.VISIBLE);
                if(animate) {
                    control.animate().alpha(1f).setDuration(180).start();
                } else control.setAlpha(1f);
            } else {
                if(!animate) {
                    control.setAlpha(0f);
                    control.setVisibility(View.INVISIBLE);
                    continue;
                }
                control.animate().alpha(0f).setDuration(320).withEndAction(() -> {
                    if(state.open) control.setVisibility(View.INVISIBLE);
                }).start();
                state.handler.postDelayed(() -> {
                    if(state.open && control.getAlpha() < 0.2f) control.setVisibility(View.INVISIBLE);
                }, 380L);
            }
        }
        if(visible) BeautifulLyricsHook.setEmbeddedControlOcclusion(state.transport, true);
        else if(animate) state.handler.postDelayed(() -> {
            if(state.open) BeautifulLyricsHook.setEmbeddedControlOcclusion(null, false);
        }, 340L);
        else BeautifulLyricsHook.setEmbeddedControlOcclusion(null, false);
    }

    private Drawable lyricsIcon(View root) {
        int size = dp(root, 24);
        float scale = size / 24f;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(dp(root, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(3f * scale, 6f * scale, 15f * scale, 6f * scale, paint);
        canvas.drawLine(3f * scale, 11f * scale, 12f * scale, 11f * scale, paint);
        canvas.drawLine(3f * scale, 16f * scale, 10f * scale, 16f * scale, paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(17f * scale, 5f * scale, 17f * scale, 16f * scale, paint);
        canvas.drawLine(17f * scale, 5f * scale, 22f * scale, 4f * scale, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(14.5f * scale, 17f * scale, 2.5f * scale, paint);
        return new BitmapDrawable(root.getResources(), bitmap);
    }

    private void addCell(LinearLayout row, View button, int size) {
        FrameLayout cell = new FrameLayout(row.getContext());
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        cell.addView(button, params);
        button.setVisibility(View.VISIBLE);
    }

    private GradientDrawable capsule(View root, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(root, 100));
        drawable.setStroke(dp(root, 1), Color.argb(48, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable panel(View root) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x34161616);
        drawable.setCornerRadius(dp(root, 28));
        drawable.setStroke(dp(root, 1), Color.argb(28, 255, 255, 255));
        return drawable;
    }

    private ViewGroup.MarginLayoutParams margins(ViewGroup.LayoutParams params) {
        if(params instanceof ViewGroup.MarginLayoutParams) return (ViewGroup.MarginLayoutParams) params;
        if(params != null) return new ViewGroup.MarginLayoutParams(params);
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.spotify.music");
        return id == 0 ? null : root.findViewById(id);
    }

    private ViewGroup findGroup(View root, String name) {
        View view = find(root, name);
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    private String string(View root, String name) {
        int id = root.getResources().getIdentifier(name, "string", "com.spotify.music");
        return id == 0 ? null : root.getResources().getString(id);
    }

    private View findByDescription(View view, String description) {
        if(description == null) return null;
        CharSequence current = view.getContentDescription();
        if(current != null && description.contentEquals(current)) return view;
        if(!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            View match = findByDescription(group.getChildAt(index), description);
            if(match != null) return match;
        }
        return null;
    }

    private View findByClassName(View view, String simpleName) {
        if(view.getClass().getSimpleName().equals(simpleName)) return view;
        if(!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            View match = findByClassName(group.getChildAt(index), simpleName);
            if(match != null) return match;
        }
        return null;
    }

    private View directChildOf(View view, ViewGroup ancestor) {
        View current = view;
        while(current != null && current.getParent() != ancestor) {
            ViewParent parent = current.getParent();
            if(!(parent instanceof View)) return null;
            current = (View) parent;
        }
        return current != null && current.getParent() == ancestor ? current : null;
    }

    private void detach(View view) {
        if(view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    private int dp(View root, int value) {
        return Math.round(value * root.getResources().getDisplayMetrics().density);
    }

    private static class LyricsState {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Map<View, Integer> hiddenViews = new LinkedHashMap<>();
        View transport;
        View footer;
        LinearLayout actionRow;
        View lyricsButton;
        View artwork;
        ImageView embeddedCover;
        ImageView landscapeCover;
        FrameLayout landscapePanel;
        LinearLayout landscapeMetadata;
        TextView landscapeTitle;
        TextView landscapeArtist;
        String landscapeArtworkUri;
        final Map<View, Integer> originalWidths = new WeakHashMap<>();
        final Map<View, Integer> originalHeights = new WeakHashMap<>();
        final Map<View, Integer> originalVisibilities = new WeakHashMap<>();
        boolean open;
        boolean transitioning;
        Runnable hideControls;
    }
}

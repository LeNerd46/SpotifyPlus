package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;

public class NowPlayingSwipeHook extends SpotifyHook {
    private static final String NOW_PLAYING_BAR_ID = "com.spotify.music:id/now_playing_bar_layout";
    private static final String NOW_PLAYING_CONTAINER_ID = "com.spotify.music:id/now_playing_container";
    private volatile WeakReference<Activity> gestureActivity = new WeakReference<>(null);
    private volatile WeakReference<View> miniPlayer = new WeakReference<>(null);
    private volatile WeakReference<View> nowPlayingSheet = new WeakReference<>(null);
    private volatile boolean tracking;
    private volatile boolean dragging;
    private volatile boolean rejected;
    private volatile boolean openingByGesture;
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
            Class<?> pageApiConfig = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("enable_page_api_npv", "android-nowplaying-musicinstallation").addMethod(MethodMatcher.create().returnType(boolean.class).paramCount(0)))).single().getInstance(lpparm.classLoader);
            Method pageApiEnabled = null;
            for(Method method : pageApiConfig.getDeclaredMethods()) if(method.getReturnType() == boolean.class && method.getParameterCount() == 0) pageApiEnabled = method;
            if(pageApiEnabled == null) throw new IllegalStateException("The Now Playing page API flag accessor was not found");
            XposedBridge.hookMethod(pageApiEnabled, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });
            hookLayoutInflation();
            hookActivityTouches();
            XposedBridge.log("[SpotifyPlus][NowPlayingSwipe] Enabled the in-process Now Playing page");
        } catch (Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingSwipe] Could not initialize the swipe-up gesture");
            XposedBridge.log(throwable);
        }
    }

    private void hookLayoutInflation() {
        XposedBridge.hookAllMethods(LayoutInflater.class, "inflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if(param.args.length == 0 || !(param.args[0] instanceof Integer) || !(param.getResult() instanceof View)) return;
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
                Activity activity = (Activity) param.thisObject;
                if(!"com.spotify.music.SpotifyMainActivity".equals(activity.getClass().getName()) || !(param.args[0] instanceof MotionEvent)) return;
                MotionEvent event = (MotionEvent) param.args[0];
                if(handleTouch(dispatchTouchEvent, activity, event)) param.setResult(true);
            }
        });
    }

    private boolean handleTouch(Method dispatchTouchEvent, Activity activity, MotionEvent event) throws Throwable {
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
                openingByGesture = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                XposedBridge.invokeOriginalMethod(dispatchTouchEvent, activity, new Object[]{cancel});
                cancel.recycle();
                View bar = miniPlayer.get();
                if(bar != null) bar.post(() -> openNowPlaying(bar));
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
            finishGesture(activity, commit);
            return true;
        }
        return dragging;
    }

    private void openNowPlaying(View bar) {
        if(!openingByGesture) return;
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
        if(!openingByGesture || activity == null) return;
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
        if(!openingByGesture) return;
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

package com.lenerd.spotifyplus.module.scripting;

import android.app.Activity;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the UI V8 isolate's Android-main-thread entry points.
 *
 * Native worklet registration is queued from the Node thread, but V8 is entered
 * only by this class on the main looper. View updates are delivered as parallel
 * typed arrays so animation frames never cross JNI as JSON and never call Node.
 */
public final class WorkletRuntimeManager {
    private static final String TAG = "SpotifyPlus:Worklets";

    public static final int VALUE_TYPE_NULL = 0;
    public static final int VALUE_TYPE_NUMBER = 1;
    public static final int VALUE_TYPE_BOOLEAN = 2;
    public static final int VALUE_TYPE_STRING = 3;
    public static final int VALUE_TYPE_OBJECT = 4;
    public static final int VALUE_TYPE_ARRAY = 5;

    @FunctionalInterface
    public interface UpdateSink {
        void applyUpdates(
            long frameTimeNanos,
            String[] surfaceIds,
            int[] nodeIds,
            String[] properties,
            int[] valueTypes,
            double[] numberValues,
            String[] stringValues
        );
    }

    public interface HostFunctionSink {
        String measure(String surfaceId, int nodeId);

        String getRelativeCoords(
            String surfaceId,
            int nodeId,
            double absoluteX,
            double absoluteY
        );

        boolean scrollTo(
            String surfaceId,
            int nodeId,
            double x,
            double y,
            boolean animated
        );

        boolean dispatchCommand(
            String surfaceId,
            int nodeId,
            String command,
            String argsJson
        );
    }

    /** Optional host hook for renderer-owned sources such as scroll offsets. */
    public interface SourceLifecycleSink {
        void activate(String sourceId, String configJson);

        void deactivate(String sourceId, String configJson);
    }

    private static final WorkletRuntimeManager INSTANCE = new WorkletRuntimeManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Choreographer.FrameCallback frameCallback = this::doFrame;

    private Choreographer choreographer;
    private boolean framePosted;
    private boolean delayedFrame;
    private volatile UpdateSink updateSink;
    private volatile HostFunctionSink hostFunctionSink;
    private volatile SourceLifecycleSink sourceLifecycleSink;

    // All fields below are confined to the Android main looper.
    private Context applicationContext;
    private WeakReference<Activity> activityReference = new WeakReference<>(null);
    private final Map<String, Map<String, Integer>> activeSourceConfigs = new HashMap<>();
    private final Map<String, SensorSubscription> sensorSubscriptions = new HashMap<>();
    private SensorManager sensorManager;
    private ContentObserver reducedMotionObserver;
    private View keyboardRoot;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private WindowInsetsAnimation.Callback keyboardAnimationCallback;
    private boolean keyboardAnimationRunning;
    private int keyboardHeight = -1;
    private int keyboardState = -1;

    private WorkletRuntimeManager() {}

    public static WorkletRuntimeManager getInstance() {
        return INSTANCE;
    }

    public void setUpdateSink(UpdateSink sink) {
        updateSink = sink;
    }

    public void setHostFunctionSink(HostFunctionSink sink) {
        hostFunctionSink = sink;
    }

    public void setSourceLifecycleSink(SourceLifecycleSink sink) {
        sourceLifecycleSink = sink;
    }

    /** Supplies the current Spotify activity without retaining it after replacement. */
    public void attachContext(Context context) {
        if (context == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Context appContext = context.getApplicationContext();
            if (appContext != null) {
                float scale = Settings.Global.getFloat(
                    appContext.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                );
                nativePublishSourceValue("reducedMotion", scale == 0f ? "true" : "false");
            }
            mainHandler.post(() -> attachContext(context));
            return;
        }

        Activity nextActivity = context instanceof Activity ? (Activity) context : null;
        Activity previousActivity = activityReference.get();
        applicationContext = context.getApplicationContext();
        activityReference = new WeakReference<>(nextActivity);

        if (previousActivity != nextActivity) {
            stopKeyboardSource();
            refreshKeyboardSource();
        }

        ensureReducedMotionObserver();
        publishReducedMotion();
        refreshAllSensors();
    }

    public void activateSource(String sourceId, String configJson) {
        if (sourceId == null || sourceId.isEmpty()) return;
        final String normalizedConfig = normalizeConfig(configJson);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> activateSource(sourceId, normalizedConfig));
            return;
        }

        Map<String, Integer> configs = activeSourceConfigs.computeIfAbsent(
            sourceId,
            ignored -> new HashMap<>()
        );
        configs.put(normalizedConfig, configs.getOrDefault(normalizedConfig, 0) + 1);
        refreshSource(sourceId);

        SourceLifecycleSink sink = sourceLifecycleSink;
        if (sink != null && sourceId.startsWith("scrollOffset:")) {
            sink.activate(sourceId, normalizedConfig);
        }
    }

    public void deactivateSource(String sourceId, String configJson) {
        if (sourceId == null || sourceId.isEmpty()) return;
        final String normalizedConfig = normalizeConfig(configJson);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> deactivateSource(sourceId, normalizedConfig));
            return;
        }

        Map<String, Integer> configs = activeSourceConfigs.get(sourceId);
        if (configs != null) {
            int count = configs.getOrDefault(normalizedConfig, 0);
            if (count <= 1) {
                configs.remove(normalizedConfig);
            } else {
                configs.put(normalizedConfig, count - 1);
            }
            if (configs.isEmpty()) activeSourceConfigs.remove(sourceId);
        }
        refreshSource(sourceId);

        SourceLifecycleSink sink = sourceLifecycleSink;
        if (sink != null && sourceId.startsWith("scrollOffset:")) {
            sink.deactivate(sourceId, normalizedConfig);
        }
    }

    public void publishSourceValue(String sourceId, String valueJson) {
        if (sourceId == null || sourceId.isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be empty");
        }

        if (valueJson == null || valueJson.isEmpty()) {
            throw new IllegalArgumentException("valueJson must not be empty");
        }

        nativePublishSourceValue(sourceId, valueJson);
    }

    public void requestFrame() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::requestFrame);
            return;
        }

        ensureChoreographer();
        if (framePosted && !delayedFrame) return;

        if (framePosted) {
            choreographer.removeFrameCallback(frameCallback);
        }

        framePosted = true;
        delayedFrame = false;
        choreographer.postFrameCallback(frameCallback);
    }

    /**
     * Executes an already-registered worklet synchronously during a UI event.
     * argsJson must encode an array. The returned string is JSON, or null when
     * execution failed; errors can be drained through the Node addon.
     */
    public String executeNow(
        String scriptId,
        long generation,
        String workletId,
        String argsJson
    ) {
        assertMainThread();
        return nativeExecuteNow(
            scriptId,
            generation,
            workletId,
            argsJson == null ? "[]" : argsJson,
            System.nanoTime()
        );
    }

    public void shutdown() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::shutdown);
            return;
        }

        if (choreographer != null && framePosted) {
            choreographer.removeFrameCallback(frameCallback);
        }

        framePosted = false;
        delayedFrame = false;
        stopAllSensors();
        stopKeyboardSource();
        stopReducedMotionObserver();
        activeSourceConfigs.clear();
        nativeShutdown();
    }

    void dispatchUpdates(
        long frameTimeNanos,
        String[] surfaceIds,
        int[] nodeIds,
        String[] properties,
        int[] valueTypes,
        double[] numberValues,
        String[] stringValues
    ) {
        assertMainThread();

        UpdateSink sink = updateSink;
        if (sink == null || surfaceIds.length == 0) return;

        int count = surfaceIds.length;
        if (nodeIds.length != count
            || properties.length != count
            || valueTypes.length != count
            || numberValues.length != count
            || stringValues.length != count) {
            throw new IllegalArgumentException("Mismatched worklet update arrays");
        }

        sink.applyUpdates(
            frameTimeNanos,
            surfaceIds,
            nodeIds,
            properties,
            valueTypes,
            numberValues,
            stringValues
        );
    }

    String measure(String surfaceId, int nodeId) {
        assertMainThread();
        HostFunctionSink sink = hostFunctionSink;
        return sink == null ? null : sink.measure(surfaceId, nodeId);
    }

    String getRelativeCoords(
        String surfaceId,
        int nodeId,
        double absoluteX,
        double absoluteY
    ) {
        assertMainThread();
        HostFunctionSink sink = hostFunctionSink;
        return sink == null
            ? null
            : sink.getRelativeCoords(surfaceId, nodeId, absoluteX, absoluteY);
    }

    boolean scrollTo(
        String surfaceId,
        int nodeId,
        double x,
        double y,
        boolean animated
    ) {
        assertMainThread();
        HostFunctionSink sink = hostFunctionSink;
        return sink != null && sink.scrollTo(surfaceId, nodeId, x, y, animated);
    }

    boolean dispatchCommand(
        String surfaceId,
        int nodeId,
        String command,
        String argsJson
    ) {
        assertMainThread();
        HostFunctionSink sink = hostFunctionSink;
        return sink != null && sink.dispatchCommand(surfaceId, nodeId, command, argsJson);
    }

    private static String normalizeConfig(String configJson) {
        return configJson == null || configJson.isEmpty() ? "{}" : configJson;
    }

    private boolean isSourceActive(String sourceId) {
        Map<String, Integer> configs = activeSourceConfigs.get(sourceId);
        return configs != null && !configs.isEmpty();
    }

    private void refreshSource(String sourceId) {
        if (sourceId.startsWith("sensor:")) {
            refreshSensor(sourceId);
        } else if ("keyboardHeight".equals(sourceId) || "keyboardState".equals(sourceId)) {
            refreshKeyboardSource();
        } else if ("reducedMotion".equals(sourceId)) {
            ensureReducedMotionObserver();
            publishReducedMotion();
        } else if ("playbackClock".equals(sourceId) || "frame".equals(sourceId)) {
            if (isSourceActive(sourceId)) requestFrame();
        }
    }

    private void ensureReducedMotionObserver() {
        if (applicationContext == null || reducedMotionObserver != null) return;

        reducedMotionObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                publishReducedMotion();
            }
        };
        applicationContext.getContentResolver().registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            reducedMotionObserver
        );
    }

    private void stopReducedMotionObserver() {
        if (applicationContext != null && reducedMotionObserver != null) {
            applicationContext.getContentResolver().unregisterContentObserver(reducedMotionObserver);
        }
        reducedMotionObserver = null;
    }

    private void publishReducedMotion() {
        if (applicationContext == null) return;
        float scale = Settings.Global.getFloat(
            applicationContext.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        );
        publishSourceValue("reducedMotion", scale == 0f ? "true" : "false");
    }

    private void refreshAllSensors() {
        for (String sourceId : activeSourceConfigs.keySet()) {
            if (sourceId.startsWith("sensor:")) refreshSensor(sourceId);
        }
    }

    private void refreshSensor(String sourceId) {
        if (!isSourceActive(sourceId) || applicationContext == null) {
            stopSensor(sourceId);
            return;
        }

        if (sensorManager == null) {
            sensorManager = (SensorManager) applicationContext.getSystemService(Context.SENSOR_SERVICE);
        }
        if (sensorManager == null) return;

        int animatedType = parseSensorType(sourceId);
        int androidType = androidSensorType(animatedType);
        if (androidType == -1) {
            Log.w(TAG, "Unknown animated sensor source: " + sourceId);
            stopSensor(sourceId);
            return;
        }

        Sensor sensor = sensorManager.getDefaultSensor(androidType);
        if (sensor == null) {
            Log.w(TAG, "Android sensor is unavailable: " + sourceId);
            stopSensor(sourceId);
            return;
        }

        int intervalUs = 20_000;
        boolean adjustToOrientation = false;
        Map<String, Integer> configs = activeSourceConfigs.get(sourceId);
        if (configs != null) {
            for (String configJson : configs.keySet()) {
                try {
                    JSONObject config = new JSONObject(configJson);
                    Object interval = config.opt("interval");
                    if (interval instanceof Number) {
                        int requestedUs = Math.max(1_000, (int) Math.round(((Number) interval).doubleValue() * 1_000d));
                        intervalUs = Math.min(intervalUs, requestedUs);
                    }
                    adjustToOrientation |= config.optBoolean("adjustToInterfaceOrientation", false);
                } catch (Exception exception) {
                    Log.w(TAG, "Invalid sensor source config: " + configJson, exception);
                }
            }
        }

        SensorSubscription previous = sensorSubscriptions.get(sourceId);
        if (previous != null && previous.matches(sensor, intervalUs, adjustToOrientation)) return;
        if (previous != null) sensorManager.unregisterListener(previous);

        SensorSubscription subscription = new SensorSubscription(
            sourceId,
            animatedType,
            sensor,
            intervalUs,
            adjustToOrientation
        );
        if (sensorManager.registerListener(subscription, sensor, intervalUs, mainHandler)) {
            sensorSubscriptions.put(sourceId, subscription);
        } else {
            Log.w(TAG, "Failed to register Android sensor: " + sourceId);
            sensorSubscriptions.remove(sourceId);
        }
    }

    private void stopSensor(String sourceId) {
        SensorSubscription subscription = sensorSubscriptions.remove(sourceId);
        if (sensorManager != null && subscription != null) {
            sensorManager.unregisterListener(subscription);
        }
    }

    private void stopAllSensors() {
        if (sensorManager != null) {
            for (SensorSubscription subscription : sensorSubscriptions.values()) {
                sensorManager.unregisterListener(subscription);
            }
        }
        sensorSubscriptions.clear();
        sensorManager = null;
    }

    private static int parseSensorType(String sourceId) {
        try {
            return Integer.parseInt(sourceId.substring(sourceId.indexOf(':') + 1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int androidSensorType(int animatedType) {
        switch (animatedType) {
            case 1:
                return Sensor.TYPE_ACCELEROMETER;
            case 2:
                return Sensor.TYPE_GYROSCOPE;
            case 3:
                return Sensor.TYPE_GRAVITY;
            case 4:
                return Sensor.TYPE_MAGNETIC_FIELD;
            case 5:
                return Sensor.TYPE_ROTATION_VECTOR;
            case 6:
                return Sensor.TYPE_LINEAR_ACCELERATION;
            default:
                return -1;
        }
    }

    private void refreshKeyboardSource() {
        boolean active = isSourceActive("keyboardHeight") || isSourceActive("keyboardState");
        Activity activity = activityReference.get();
        if (!active || activity == null || activity.getWindow() == null) {
            stopKeyboardSource();
            return;
        }

        View root = activity.getWindow().getDecorView();
        if (keyboardRoot == root && keyboardLayoutListener != null) return;
        stopKeyboardSource();

        keyboardRoot = root;
        keyboardLayoutListener = this::publishKeyboardState;
        root.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            installKeyboardAnimationCallback(root);
        }
        root.post(this::publishKeyboardState);
    }

    private void installKeyboardAnimationCallback(View root) {
        keyboardAnimationCallback = new WindowInsetsAnimation.Callback(
            WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
            @Override
            public void onPrepare(WindowInsetsAnimation animation) {
                if (!isImeAnimation(animation)) return;
                keyboardAnimationRunning = true;

                WindowInsets insets = root.getRootWindowInsets();
                boolean visible = insets != null && insets.isVisible(WindowInsets.Type.ime());
                int height = insets == null
                    ? Math.max(0, keyboardHeight)
                    : Math.max(0, insets.getInsets(WindowInsets.Type.ime()).bottom);
                publishKeyboardValues(height, visible ? 3 : 1, false);
            }

            @Override
            public WindowInsetsAnimation.Bounds onStart(
                WindowInsetsAnimation animation,
                WindowInsetsAnimation.Bounds bounds
            ) {
                if (isImeAnimation(animation)) keyboardAnimationRunning = true;
                return bounds;
            }

            @Override
            public WindowInsets onProgress(
                WindowInsets insets,
                List<WindowInsetsAnimation> runningAnimations
            ) {
                boolean imeRunning = false;
                for (WindowInsetsAnimation animation : runningAnimations) {
                    if (isImeAnimation(animation)) {
                        imeRunning = true;
                        break;
                    }
                }
                if (!imeRunning) return insets;

                keyboardAnimationRunning = true;
                int height = Math.max(0, insets.getInsets(WindowInsets.Type.ime()).bottom);
                int state;
                if (height > Math.max(0, keyboardHeight)) {
                    state = 1;
                } else if (height < Math.max(0, keyboardHeight)) {
                    state = 3;
                } else if (keyboardState == 1 || keyboardState == 3) {
                    state = keyboardState;
                } else {
                    state = insets.isVisible(WindowInsets.Type.ime()) ? 1 : 3;
                }
                publishKeyboardValues(height, state, false);
                return insets;
            }

            @Override
            public void onEnd(WindowInsetsAnimation animation) {
                if (!isImeAnimation(animation)) return;
                keyboardAnimationRunning = false;

                WindowInsets insets = root.getRootWindowInsets();
                boolean visible = insets != null && insets.isVisible(WindowInsets.Type.ime());
                int height = visible && insets != null
                    ? Math.max(0, insets.getInsets(WindowInsets.Type.ime()).bottom)
                    : 0;
                publishKeyboardValues(height, visible ? 2 : 4, false);
            }
        };
        root.setWindowInsetsAnimationCallback(keyboardAnimationCallback);
    }

    private static boolean isImeAnimation(WindowInsetsAnimation animation) {
        return (animation.getTypeMask() & WindowInsets.Type.ime()) != 0;
    }

    private void stopKeyboardSource() {
        if (keyboardRoot != null && keyboardLayoutListener != null) {
            ViewTreeObserver observer = keyboardRoot.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(keyboardLayoutListener);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            keyboardRoot != null &&
            keyboardAnimationCallback != null)
        {
            keyboardRoot.setWindowInsetsAnimationCallback(null);
        }
        keyboardRoot = null;
        keyboardLayoutListener = null;
        keyboardAnimationCallback = null;
        keyboardAnimationRunning = false;
        keyboardHeight = -1;
        keyboardState = -1;
    }

    private void publishKeyboardState() {
        View root = keyboardRoot;
        if (root == null || keyboardAnimationRunning) return;

        int height = 0;
        boolean visible = false;
        WindowInsets windowInsets = root.getRootWindowInsets();
        if (windowInsets != null) {
            Insets imeInsets = windowInsets.getInsets(WindowInsets.Type.ime());
            visible = windowInsets.isVisible(WindowInsets.Type.ime());
            height = visible ? Math.max(0, imeInsets.bottom) : 0;
        } else {
            Rect visibleFrame = new Rect();
            root.getWindowVisibleDisplayFrame(visibleFrame);
            height = Math.max(0, root.getRootView().getHeight() - visibleFrame.bottom);
            visible = height > root.getRootView().getHeight() * 0.15f;
            if (!visible) height = 0;
        }

        int previousHeight = keyboardHeight;
        int state;
        if (visible && height > Math.max(0, previousHeight)) {
            state = 1;
        } else if (!visible && previousHeight > 0) {
            state = 3;
        } else {
            state = visible ? 2 : 4;
        }
        publishKeyboardValues(height, state, state == 1 || state == 3);
    }

    private void publishKeyboardValues(int height, int state, boolean settleAfterFrame) {
        View root = keyboardRoot;
        if (root == null) return;

        if (height != keyboardHeight) {
            keyboardHeight = height;
            publishSourceValue("keyboardHeight", Integer.toString(height));
        }
        if (state != keyboardState) {
            keyboardState = state;
            publishSourceValue("keyboardState", Integer.toString(state));
        }

        if (settleAfterFrame) {
            int transitionHeight = height;
            int settledState = state == 1 ? 2 : 4;
            root.postOnAnimation(() -> {
                if (keyboardRoot != root ||
                    keyboardAnimationRunning ||
                    keyboardHeight != transitionHeight)
                {
                    return;
                }
                if (keyboardState != settledState) {
                    keyboardState = settledState;
                    publishSourceValue("keyboardState", Integer.toString(settledState));
                }
            });
        }
    }

    private int currentInterfaceOrientation() {
        Activity activity = activityReference.get();
        if (activity == null || activity.getDisplay() == null) return 0;
        switch (activity.getDisplay().getRotation()) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private final class SensorSubscription implements SensorEventListener {
        private final String sourceId;
        private final int animatedType;
        private final Sensor sensor;
        private final int intervalUs;
        private final boolean adjustToOrientation;

        SensorSubscription(
            String sourceId,
            int animatedType,
            Sensor sensor,
            int intervalUs,
            boolean adjustToOrientation
        ) {
            this.sourceId = sourceId;
            this.animatedType = animatedType;
            this.sensor = sensor;
            this.intervalUs = intervalUs;
            this.adjustToOrientation = adjustToOrientation;
        }

        boolean matches(Sensor nextSensor, int nextIntervalUs, boolean nextAdjustToOrientation) {
            return sensor == nextSensor
                && intervalUs == nextIntervalUs
                && adjustToOrientation == nextAdjustToOrientation;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length < 3) return;
            int orientation = currentInterfaceOrientation();
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            try {
                JSONObject value = new JSONObject();
                value.put("x", x);
                value.put("y", y);
                value.put("z", z);
                value.put("interfaceOrientation", orientation);

                if (animatedType == 5) {
                    float[] quaternion = new float[4];
                    float[] rotationMatrix = new float[9];
                    float[] angles = new float[3];
                    SensorManager.getQuaternionFromVector(quaternion, event.values);
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                    SensorManager.getOrientation(rotationMatrix, angles);
                    value.put("qw", quaternion[0]);
                    value.put("qx", quaternion[1]);
                    value.put("qy", quaternion[2]);
                    value.put("qz", quaternion[3]);
                    value.put("yaw", angles[0]);
                    value.put("pitch", angles[1]);
                    value.put("roll", angles[2]);
                }

                publishSourceValue(sourceId, value.toString());
            } catch (Exception exception) {
                Log.e(TAG, "Failed to serialize Android sensor value", exception);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    private void doFrame(long frameTimeNanos) {
        assertMainThread();
        framePosted = false;
        delayedFrame = false;

        long nextDelayMillis = nativeDoFrame(frameTimeNanos);
        if (nextDelayMillis < 0) return;

        ensureChoreographer();

        // Native tasks can request another frame while nativeDoFrame is running.
        // Keep that already-posted immediate callback instead of posting twice.
        if (framePosted && !delayedFrame) return;

        if (framePosted) choreographer.removeFrameCallback(frameCallback);
        framePosted = true;
        delayedFrame = nextDelayMillis > 0;

        if (delayedFrame) {
            choreographer.postFrameCallbackDelayed(frameCallback, nextDelayMillis);
        } else {
            choreographer.postFrameCallback(frameCallback);
        }
    }

    private void ensureChoreographer() {
        assertMainThread();
        if (choreographer == null) choreographer = Choreographer.getInstance();
    }

    private static void assertMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Worklet UI runtime entered off the Android main thread");
        }
    }

    private static native long nativeDoFrame(long frameTimeNanos);

    private static native String nativeExecuteNow(
        String scriptId,
        long generation,
        String workletId,
        String argsJson,
        long frameTimeNanos
    );

    private static native void nativeShutdown();

    private static native void nativePublishSourceValue(String sourceId, String valueJson);
}

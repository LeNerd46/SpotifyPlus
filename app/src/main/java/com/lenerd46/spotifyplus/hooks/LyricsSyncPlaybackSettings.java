package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.player.SpotifyCompletableAwaiter;

import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import de.robv.android.xposed.*;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;

public final class LyricsSyncPlaybackSettings extends SpotifyHook {
    private static volatile Object service;
    private static Method read, write;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final String KEY = "audio.crossfade_v2";
    private static final String JOURNAL = "sync_crossfade_restore";

    @Override protected void hook() {
        try {
            Class<?> type = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .usingStrings("Failed to get preference with key %s"))).single().getInstance(lpparm.classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType().getName().equals("io.reactivex.rxjava3.core.Single")
                        && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class})) read = method;
                if (method.getReturnType().getName().equals("io.reactivex.rxjava3.core.Completable")
                        && method.getParameterCount() == 2 && method.getParameterTypes()[1] == String.class) write = method;
            }

            if (read != null) XposedBridge.hookMethod(read, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) { service = param.thisObject; }
            });
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType().getName().equals("io.reactivex.rxjava3.core.Observable")
                        && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class}))
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) { service = param.thisObject; }
                    });
            }
            XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    service = param.thisObject;
                    Context app = android.app.AndroidAppHelper.currentApplication();
                    if (app != null) worker.execute(() -> recover(app));
                }
            });
        } catch (Throwable error) { XposedBridge.log(error); }
    }

    private static Object awaitSingle(Object value) {
        Object timed = XposedHelpers.callMethod(value, "timeout", new Class<?>[]{long.class, TimeUnit.class}, 5L, TimeUnit.SECONDS);
        return XposedHelpers.callMethod(timed, "blockingGet");
    }

    private static void set(Object target, boolean enabled) throws Exception {
        Class<?> valueType = write.getParameterTypes()[0];
        Object value = XposedHelpers.newInstance(valueType);
        XposedHelpers.setIntField(value, "valueCase_", 2);
        XposedHelpers.setObjectField(value, "value_", enabled);
        Object operation = write.invoke(target, value, KEY);
        SpotifyCompletableAwaiter.await(operation, 5L, TimeUnit.SECONDS);
    }

    private static boolean active;
    private static void recover(Context context) {
        if (active || service == null || write == null) return;
        SharedPreferences prefs = context.getSharedPreferences("SpotifyPlus", 0);
        if (!prefs.contains(JOURNAL)) return;
        try {
            set(service, prefs.getBoolean(JOURNAL, false));
            prefs.edit().remove(JOURNAL).commit();
        } catch (Throwable error) { XposedBridge.log(error); }
    }

    public static final class Lease implements AutoCloseable {
        public volatile boolean ready;
        public volatile String error;
        private volatile boolean closed;
        private final Context context;
        private Lease(Context context) { this.context = context.getApplicationContext(); }
        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            worker.execute(() -> { active = false; recover(context); });
        }
    }

    public static Lease suspend(Context context) {
        Lease lease = new Lease(context);
        worker.execute(() -> {
            if (lease.closed) return;
            try {
                if (service == null || read == null || write == null)
                    throw new IllegalStateException(References.getString(R.string.ui_spotify_crossfade_controls_are_not_ready_reopen_spotify_and));
                recover(context);
                SharedPreferences prefs = context.getSharedPreferences("SpotifyPlus", 0);
                if (prefs.contains(JOURNAL)) throw new IllegalStateException(References.getString(R.string.ui_could_not_restore_the_previous_crossfade_setting_try_again));
                Object original = awaitSingle(read.invoke(service, KEY));
                if (XposedHelpers.getIntField(original, "valueCase_") != 2) throw new IllegalStateException(References.getString(R.string.ui_spotify_returned_an_unknown_crossfade_setting));
                boolean enabled = (Boolean) XposedHelpers.getObjectField(original, "value_");
                if (!prefs.edit().putBoolean(JOURNAL, enabled).commit()) throw new IllegalStateException(References.getString(R.string.ui_could_not_back_up_crossfade_settings));
                active = true;
                set(service, false);
                lease.ready = true;
            } catch (Throwable error) {
                XposedBridge.log(error);
                lease.error = References.getString(R.string.ui_could_not_temporarily_disable_crossfade_leave_syncing_and_try);
                active = false;
                recover(context);
            }
        });
        return lease;
    }
}

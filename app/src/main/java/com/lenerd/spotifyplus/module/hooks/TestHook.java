package com.lenerd.spotifyplus.module.hooks;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@XposedHooker
public class TestHook extends SpotifyHook {
    private static final ThreadLocal<Long> traceUntil = ThreadLocal.withInitial(() -> 0L);
    private static final Set<String> hookedMethods = new HashSet<>();

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        Class<?> q5lClass = findClass("p.q5l");
        hook(q5lClass.getDeclaredMethod("onClick", View.class), TestHook.class);

        Class<?> l4oClass = findClass("p.l4o");
        hook(l4oClass.getDeclaredMethod("invoke", Object.class), TestHook.class);

        Class<?> zm9Class = findClass("p.zm9");
        for (Field field : zm9Class.getDeclaredFields()) field.setAccessible(true);
        for (Method method : zm9Class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            hook(method, TestHook.class);
            Log.d("SpotifyPlus", "Hooked p.zm9 method: " + describeMethod(method));
        }
    }

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        TestHook hook = getHook(TestHook.class);
        if (hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        TestHook hook = getHook(TestHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();
        if (!(member instanceof Method method)) return;

        String owner = method.getDeclaringClass().getName();

        if (owner.equals("p.q5l") && method.getName().equals("onClick")) {
            View view = callback.getArgs() != null && callback.getArgs().length > 0 && callback.getArgs()[0] instanceof View
                    ? (View) callback.getArgs()[0] : null;

            String desc = view != null && view.getContentDescription() != null
                    ? view.getContentDescription().toString()
                    : null;

            if (!looksRelevant(desc)) return;

            traceUntil.set(SystemClock.uptimeMillis() + 1000L);

            Log.d("SpotifyPlus", "=== q5l.onClick START ===");
            Log.d("SpotifyPlus", "contentDescription=" + desc);
            Log.d("SpotifyPlus", "viewClass=" + (view != null ? view.getClass().getName() : "null"));
            return;
        }

        if (!isTracing()) return;

        if (owner.equals("p.l4o") && method.getName().equals("invoke")) {
            Object thisObject = callback.getThisObject();
            Object arg = callback.getArgs() != null && callback.getArgs().length > 0 ? callback.getArgs()[0] : null;

            Log.d("SpotifyPlus", "=== p.l4o.invoke ===");
            Log.d("SpotifyPlus", "this=" + safeToString(thisObject));
            Log.d("SpotifyPlus", "args=" + formatArgs(callback.getArgs()));

            dumpFields("l4o", thisObject);

            if (arg != null) {
                dumpFields("l4o arg " + arg.getClass().getName(), arg);
                hookCapturedFieldMethods(thisObject);
                hookCapturedFieldMethods(arg);
            }

            return;
        }

        if (owner.equals("p.zm9")) {
            Log.d("SpotifyPlus", "=== p.zm9 call ===");
            Log.d("SpotifyPlus", "method=" + describeMethod(method));
            Log.d("SpotifyPlus", "this=" + safeToString(callback.getThisObject()));
            Log.d("SpotifyPlus", "args=" + formatArgs(callback.getArgs()));
            return;
        }

        Log.d("SpotifyPlus", "=== traced call ===");
        Log.d("SpotifyPlus", "method=" + describeMethod(method));
        Log.d("SpotifyPlus", "this=" + safeToString(callback.getThisObject()));
        Log.d("SpotifyPlus", "args=" + formatArgs(callback.getArgs()));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        Member member = callback.getMember();
        if (!(member instanceof Method method)) return;
        if (!isTracing()) return;

        String owner = method.getDeclaringClass().getName();

        if (owner.equals("p.l4o") || owner.equals("p.zm9")) {
            Log.d("SpotifyPlus", "return=" + safeToString(callback.getResult()));
        }

        if (owner.equals("p.q5l") && method.getName().equals("onClick")) {
            Log.d("SpotifyPlus", "=== q5l.onClick END ===");
        }
    }

    private void hookCapturedFieldMethods(Object instance) {
        if (instance == null) return;

        try {
            for (Field field : instance.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (Modifier.isStatic(field.getModifiers())) continue;

                Object value = field.get(instance);
                if (value == null) continue;

                String className = value.getClass().getName();
                if (className.startsWith("java.") || className.startsWith("kotlin.") || className.equals("java.lang.Integer"))
                    continue;

                Log.d("SpotifyPlus", "Inspecting captured field " + field.getName() + " -> " + className);

                for (Method method : value.getClass().getDeclaredMethods()) {
                    if (method.isSynthetic() || method.isBridge()) continue;

                    String key = value.getClass().getName() + "#" + method.getName() + "#" + method.getParameterCount();
                    if (!hookedMethods.add(key)) continue;

                    try {
                        hook(method, TestHook.class);
                        Log.d("SpotifyPlus", "Hooked captured method: " + describeMethod(method));
                    } catch (Throwable e) {
                        Log.d("SpotifyPlus", "Failed to hook " + describeMethod(method) + ": " + e);
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("SpotifyPlus", "Failed to inspect captured fields", e);
        }
    }

    private void dumpFields(String label, Object instance) {
        if (instance == null) return;

        try {
            Log.d("SpotifyPlus", "--- " + label + " fields ---");
            Class<?> cls = instance.getClass();

            while (cls != null) {
                for (Field field : cls.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object value = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(instance);
                        Log.d("SpotifyPlus", cls.getName() + "." + field.getName() + " = " + safeToString(value));
                    } catch (Throwable e) {
                        Log.d("SpotifyPlus", cls.getName() + "." + field.getName() + " = <error: " + e.getClass().getSimpleName() + ">");
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable e) {
            Log.e("SpotifyPlus", "Failed dumping fields for " + label, e);
        }
    }

    private boolean looksRelevant(String desc) {
        if (desc == null) return false;
        String value = desc.toLowerCase();
        return value.contains("shuffle")
                || value.contains("shuffling")
                || value.contains("repeat")
                || value.contains("repeating")
                || value.contains("listening mode")
                || value.contains("loop");
    }

    private boolean isTracing() {
        return SystemClock.uptimeMillis() <= traceUntil.get();
    }

    private static String describeMethod(Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(Modifier.toString(method.getModifiers()));
        if (builder.length() > 0) builder.append(" ");
        builder.append(method.getReturnType().getName()).append(" ");
        builder.append(method.getDeclaringClass().getName()).append(".").append(method.getName()).append("(");

        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append(params[i].getName());
        }

        builder.append(")");
        return builder.toString();
    }

    private static String formatArgs(Object[] args) {
        if (args == null) return "null";
        try {
            String[] parts = new String[args.length];
            for (int i = 0; i < args.length; i++) parts[i] = safeToString(args[i]);
            return Arrays.toString(parts);
        } catch (Throwable e) {
            return "<failed to format args: " + e.getClass().getSimpleName() + ">";
        }
    }

    private static String safeToString(Object value) {
        if (value == null) return "null";
        try {
            return value + " (" + value.getClass().getName() + ")";
        } catch (Throwable e) {
            return "<toString failed: " + e.getClass().getSimpleName() + ">";
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        return null;
    }
}
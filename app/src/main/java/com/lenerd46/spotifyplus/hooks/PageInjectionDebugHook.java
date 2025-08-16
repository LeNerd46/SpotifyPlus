package com.lenerd46.spotifyplus.hooks;

import android.content.Intent;
import android.net.Uri;

import java.lang.reflect.*;
import java.util.*;

import android.os.Bundle;
import de.robv.android.xposed.*;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;
import org.luckypray.dexkit.result.MethodDataList;


public class PageInjectionDebugHook extends SpotifyHook {
    private static final String TAG = "[SpotifyPlus][Probe]";
    private static final String PREFS = "SpotifyPlus";
    private static final String PREF_VERBOSE = "uri_trace_verbose"; // default false

    // Closest-to-intent classes from your stack
    private static final String[] CANDIDATES = new String[] {
            "com.spotify.music.SpotifyMainActivity",
            "p.x7b", "p.lsi0", "p.qr20", "p.eu20",
            "p.lvn", "p.czl0", "p.if30"
    };

    @Override protected void hook() {
        try {
            hookOnNewIntent();
            for (String cn : CANDIDATES) hookClassUriStringMethods(cn);
            for (String cn : CANDIDATES) hookClassMapFactories(cn);
            hookGlobalMapFactoriesWithSpotifyStrings();
            hookId30AndEq20();
            hookCzl0K0();
        } catch (Throwable t) {
            XposedBridge.log(TAG + " init fail: " + t);
        }
    }

    private boolean verbose() {
        return true;
    }

    private static boolean isSpotify(Object v) {
        if (v instanceof Uri) {
            Uri u = (Uri) v; String s = u != null ? u.toString() : null;
            return s != null && (s.startsWith("spotify:") || s.startsWith("spotify://"));
        } else if (v instanceof String) {
            String s = (String) v;
            return s != null && (s.startsWith("spotify:") || s.startsWith("spotify://"));
        }
        return false;
    }

    /* 1) Activity onNewIntent ------------------------------------------------ */
    private void hookOnNewIntent() {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.spotify.music.SpotifyMainActivity",
                    lpparm.classLoader,
                    "onNewIntent", Intent.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            Intent i = (Intent) p.args[0];
                            Uri u = i != null ? i.getData() : null;
                            if (u != null && isSpotify(u)) {
                                XposedBridge.log(TAG + " SpotifyMainActivity.onNewIntent " + u);
                                if (verbose()) dumpIntent(i);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + " onNewIntent hook failed: " + t);
        }
    }

    private void dumpIntent(Intent i) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("  action=").append(i.getAction())
                    .append(" cat=").append(i.getCategories())
                    .append(" flags=0x").append(Integer.toHexString(i.getFlags())).append('\n');
            sb.append("  cmp=").append(i.getComponent()).append(" data=").append(i.getData()).append('\n');
            Bundle extras = i.getExtras();
            if (extras != null) {
                for (String k : extras.keySet()) {
                    Object v = extras.get(k);
                    sb.append("  extra ").append(k).append(" = ").append(typ(v)).append('\n');
                }
            }
            XposedBridge.log(TAG + " Intent dump:\n" + sb);
        } catch (Throwable ignored) {}
    }

    private static String typ(Object o) {
        if (o == null) return "null";
        return o.getClass().getName() + " " + String.valueOf(o);
    }

    /* 2) Hook all methods in a class that take Uri/String ------------------- */
    private void hookClassUriStringMethods(String className) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, lpparm.classLoader);
            if (cls == null) return;

            for (Method m : cls.getDeclaredMethods()) {
                boolean touchesUri = false, touchesString = false;
                for (Class<?> p : m.getParameterTypes()) {
                    if (p == Uri.class) touchesUri = true;
                    if (p == String.class) touchesString = true;
                }
                if (!touchesUri && !touchesString) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        // Only log when args contain spotify deep link (unless verbose)
                        if (!verbose()) {
                            boolean hit = false;
                            for (Object a : p.args) if (isSpotify(a)) { hit = true; break; }
                            if (!hit) return;
                        }
                        XposedBridge.log(TAG + " " + sig(m) + " args=");
                        for (Object a : p.args) {
                            if (a instanceof Uri || a instanceof String) {
                                XposedBridge.log("   → " + typ(a));
                            } else if (verbose()) {
                                XposedBridge.log("   · " + typ(a));
                            }
                        }
                    }

                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (r instanceof Map) {
                            Map<?,?> map = (Map<?,?>) r;
                            XposedBridge.log(TAG + " " + sig(m) + " returned Map size=" + map.size());
                            // dump string keys
                            int shown = 0;
                            for (Object k : map.keySet()) {
                                if (k instanceof String) {
                                    XposedBridge.log("   key: " + k);
                                    if (++shown >= 50 && !verbose()) break;
                                }
                            }
                        } else if (verbose()) {
                            XposedBridge.log(TAG + " " + sig(m) + " return=" + typ(r));
                        }
                    }
                });
            }
            XposedBridge.log(TAG + " hooked Uri/String methods in " + className);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookClassUriStringMethods(" + className + ") failed: " + t);
        }
    }

    /* 3) Hook Map-returning methods in target classes ----------------------- */
    private void hookClassMapFactories(String className) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, lpparm.classLoader);
            if (cls == null) return;

            for (Method m : cls.getDeclaredMethods()) {
                if (Map.class.isAssignableFrom(m.getReturnType()) && m.getParameterTypes().length <= 2) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Object r = p.getResult();
                            if (!(r instanceof Map)) return;
                            Map<?,?> map = (Map<?,?>) r;
                            XposedBridge.log(TAG + " MapFactory " + sig(m) + " size=" + map.size());
                            int seen = 0;
                            for (Object k : map.keySet()) {
                                if (k instanceof String && (((String) k).startsWith("spotify:") || ((String) k).contains("home") || ((String) k).contains("settings"))) {
                                    XposedBridge.log("   · key=" + k);
                                }
                                if (++seen >= 100 && !verbose()) break;
                            }
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookClassMapFactories(" + className + ") failed: " + t);
        }
    }

    /* 4) Global DexKit search: methods returning Map + use "spotify:" ------- */
    private void hookGlobalMapFactoriesWithSpotifyStrings() {
        try {
            MethodDataList md = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .returnType(Map.class)
                                    .usingStrings("spotify:", "spotify://")
                    )
            );
            int hooked = 0;
            for (var mdata : md) {
                try {
                    Method m = mdata.getMethodInstance(lpparm.classLoader);
                    if (!m.getDeclaringClass().getName().startsWith("com.spotify.")
                            && !m.getDeclaringClass().getName().startsWith("p.")) continue;

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Object r = p.getResult();
                            if (!(r instanceof Map)) return;
                            Map<?,?> map = (Map<?,?>) r;
                            XposedBridge.log(TAG + " GLOBAL MapFactory " + sig(m) + " size=" + map.size());
                            int seen = 0;
                            for (Object k : map.keySet()) {
                                if (k instanceof String) XposedBridge.log("   key=" + k);
                                if (++seen >= 100 && !verbose()) break;
                            }
                        }
                    });
                    hooked++;
                } catch (Throwable ignored) {}
            }
            XposedBridge.log(TAG + " hooked " + hooked + " global Map factories");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " global Map factory search failed: " + t);
        }
    }

    private static String sig(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getDeclaringClass().getName()).append("#").append(m.getName()).append("(");
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(p[i].getSimpleName());
        }
        sb.append(") : ").append(m.getReturnType().getSimpleName());
        return sb.toString();
    }

    private void hookId30AndEq20() {
        try {
            // eq20(String) ctor & m()
            Class<?> eq20 = XposedHelpers.findClassIfExists("p.eq20", lpparm.classLoader);
            if (eq20 != null) {
                // ctor(String)
                for (Constructor<?> c : eq20.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length == 1 && p[0] == String.class) {
                        XposedBridge.hookMethod(c, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                XposedBridge.log("[SpotifyPlus][Probe] eq20.<init> uri=" + p.args[0]);
                            }
                        });
                    }
                }
                // m(): hd30
                for (Method m : eq20.getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 0) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                Object hd30 = p.getResult();
                                if (hd30 != null) {
                                    XposedBridge.log("[SpotifyPlus][Probe] eq20.m() -> " +
                                            hd30.getClass().getName() + " @" + System.identityHashCode(hd30));
                                }
                            }
                        });
                    }
                }
            }

            // id30.a(hd30): Intent
            Class<?> id30 = XposedHelpers.findClassIfExists("p.id30", lpparm.classLoader);
            if (id30 != null) {
                for (Method m : id30.getDeclaredMethods()) {
                    if (m.getName().equals("a")
                            && m.getParameterTypes().length == 1
                            && Intent.class.isAssignableFrom(m.getReturnType())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                Object hd30 = p.args[0];
                                XposedBridge.log("[SpotifyPlus][Probe] id30.a(hd30) arg=" +
                                        (hd30 == null ? "null" : hd30.getClass().getName()));
                            }
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                Intent out = (Intent) p.getResult();
                                if (out != null) {
                                    XposedBridge.log("[SpotifyPlus][Probe] id30.a -> Intent{ action=" +
                                            out.getAction() + " data=" + out.getData() + " flags=0x" +
                                            Integer.toHexString(out.getFlags()) + " }");
                                    Bundle ex = out.getExtras();
                                    if (ex != null) {
                                        for (String k : ex.keySet()) {
                                            Object v = ex.get(k);
                                            XposedBridge.log("   extra " + k + " = " +
                                                    (v == null ? "null" : v.getClass().getName()+" "+v));
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus][Probe] hookId30AndEq20 failed: " + t);
        }
    }

    private final Set<String> intentHookedClasses = new HashSet<>();

    private void hookCzl0K0() {
        try {
            Class<?> czl0 = XposedHelpers.findClassIfExists("p.czl0", lpparm.classLoader);
            if (czl0 == null) return;
            // Find K0(Intent)
            for (Method m : czl0.getDeclaredMethods()) {
                if (m.getName().equals("K0")) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 1 && ps[0] == Intent.class) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                Intent in = (Intent) p.args[0];
                                XposedBridge.log("[SpotifyPlus][Probe] czl0.K0 <- " + in.getAction() +
                                        " " + in.getData());
                            }
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                Object self = p.thisObject;
                                try {
                                    Field cField = czl0.getDeclaredField("c");
                                    Field dField = czl0.getDeclaredField("d");
                                    Field eField = czl0.getDeclaredField("e");
                                    cField.setAccessible(true);
                                    dField.setAccessible(true);
                                    eField.setAccessible(true);

                                    List<?> list = (List<?>) cField.get(self);
                                    Set<?> set = (Set<?>) dField.get(self);

                                    XposedBridge.log("[SpotifyPlus][Probe] czl0 subscribers: list=" +
                                            (list == null ? 0 : list.size()) + ", set=" +
                                            (set == null ? 0 : set.size()));

                                    if (list != null) for (Object h : list) logAndHookIntentMethods(h);
                                    if (set != null) for (Object h : set) logAndHookIntentMethods(h);
                                } catch (Throwable t) {
                                    XposedBridge.log("[SpotifyPlus][Probe] czl0 introspect failed: " + t);
                                }
                            }
                        });
                        XposedBridge.log("[SpotifyPlus][Probe] hooked czl0.K0(Intent)");
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus][Probe] hookCzl0K0 failed: " + t);
        }
    }

    private void logAndHookIntentMethods(Object handler) {
        if (handler == null) return;
        Class<?> cls = handler.getClass();
        String name = cls.getName();
        XposedBridge.log("   · handler " + name);
        if (intentHookedClasses.add(name)) {
            // Hook every method that takes an Intent
            for (Method hm : cls.getDeclaredMethods()) {
                for (Class<?> pt : hm.getParameterTypes()) {
                    if (pt == Intent.class) {
                        try {
                            XposedBridge.hookMethod(hm, new XC_MethodHook() {
                                @Override protected void beforeHookedMethod(MethodHookParam p) {
                                    Intent in = null;
                                    for (Object a : p.args) if (a instanceof Intent) { in = (Intent) a; break; }
                                    if (in != null) {
                                        XposedBridge.log("[SpotifyPlus][Probe] " + sig(hm) +
                                                " <= " + in.getAction() + " " + in.getData());
                                    }
                                }
                            });
                            XposedBridge.log("[SpotifyPlus][Probe] hooked " + sig(hm));
                        } catch (Throwable ignore) {}
                        break;
                    }
                }
            }
        }
    }

}

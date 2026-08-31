package com.lenerd.spotifyplus.module.hooks;

import android.util.Log;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@XposedHooker
public class PlayerHook extends SpotifyHook {
    // Seek
    private Object seekInstance = null;
    private Constructor<?> ctor;
    private Constructor<?> seekConstructor;
    private List<Constructor<?>> seekConstructors;

    // Play/Pause
    private static volatile Object controller;
    private static volatile String featureName;

    private static Constructor<?> c2e0Ctor;
    private static Constructor<?> z1e0Ctor;
    private static Method x2e0aMethod;
    private static Method ignoreElementMethod;
    private static Method completableSubscribeMethod;
    private Class<?> iid0Class;
    private Method invokeMethod;
    private static volatile boolean isPlaying = false;

    // Skip
    private Constructor<?> l6r0Ctor;
    private Field l6r0AField;
    private Field jxqAField;
    private Method rufCallSingleMethod;
    private Method subscribeMethod;

    private volatile Object cachedL6r0;
    private volatile Object cachedJxq;
    private volatile Object cachedRuf;

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        SpotifyNativeBridge.registerHandler("player", this);

        // Seek
        Class<?> requestClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).fieldCount(1).addField(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(long.class)).methods(MethodsMatcher.create()
                .count(6)
                .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).paramCount(13))
                .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(void.class).paramCount(12))
                .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).addParamType(Object.class))
        ))).get(0).getInstance(classLoader);

        ctor = requestClass.getConstructor(long.class);
        ctor.setAccessible(true);

        Class<?> hzc = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify.player.esperanto.proto.ContextPlayer", "SetOptions"))).get(0).getInstance(classLoader);
        Class<?> seek = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).methodCount(3).fields(FieldsMatcher.create()
                .count(3)
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(hzc))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(boolean.class))
        ))).get(0).getInstance(classLoader);

        seekConstructors = Arrays.stream(seek.getConstructors()).collect(Collectors.toList());
        seekConstructors.forEach(this::hook);
        //        seekConstructor = seek.getConstructors()[3];
//        hook(seekConstructor);

        // Play/Pause
        Class<?> hid0Class = findClass("p.hid0");
        invokeMethod = hid0Class.getDeclaredMethod("invoke", Object.class, Object.class, Object.class, Object.class);
        hook(invokeMethod);

        Class<?> c2e0Class = findClass("p.c2e0");
        Class<?> z1e0Class = findClass("p.z1e0");
        Class<?> x2e0Class = findClass("p.x2e0");
        Class<?> singleClass = findClass("io.reactivex.rxjava3.core.Single");

        c2e0Ctor = c2e0Class.getDeclaredConstructor(String.class, boolean.class);
        z1e0Ctor = z1e0Class.getDeclaredConstructor(String.class, boolean.class);
        x2e0aMethod = x2e0Class.getDeclaredMethod("a", findClass("p.m2e0"));
        ignoreElementMethod = singleClass.getDeclaredMethod("ignoreElement");

        Class<?> completableClass = findClass("io.reactivex.rxjava3.core.Completable");

        ignoreElementMethod = singleClass.getDeclaredMethod("ignoreElement");
        completableSubscribeMethod = completableClass.getDeclaredMethod("subscribe");

        iid0Class = findClass("p.iid0");
        Class<?> j4oClass = findClass("p.j4o");
        Method bindMethod = iid0Class.getDeclaredMethod("a", Object.class, j4oClass);
        hook(bindMethod);

        // Skip
        Class<?> l6r0Class = findClass("p.l6r0");
        Class<?> jxqClass = findClass("p.jxq");
        Class<?> rufClass = findClass("p.ruf");

        for (Constructor<?> ctor : l6r0Class.getDeclaredConstructors()) {
            l6r0Ctor = ctor;
            break;
        }

        if (l6r0Ctor == null) throw new NoSuchMethodException("Could not find p.l6r0 constructor");

        l6r0AField = l6r0Class.getDeclaredField("a");
        l6r0AField.setAccessible(true);

        jxqAField = jxqClass.getDeclaredField("a");
        jxqAField.setAccessible(true);

        for (Method method : rufClass.getMethods()) {
            if (!method.getName().equals("callSingle")) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3 && params[0] == String.class && params[1] == String.class) {
                rufCallSingleMethod = method;
                rufCallSingleMethod.setAccessible(true);
                break;
            }
        }

        if (rufCallSingleMethod == null)
            throw new NoSuchMethodException("Could not find ruf.callSingle(String, String, Object)");

        hook(l6r0Ctor);
    }

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        PlayerHook hook = getHook(PlayerHook.class);
        if (hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        if (member.getDeclaringClass() == iid0Class && member.getName().equals("a")) {
            Object[] args = callback.getArgs();
            if (args.length < 1 || args[0] == null) return;

            try {
                Object bid0 = args[0];
                isPlaying = bid0.getClass().getDeclaredField("b").getBoolean(bid0);
            } catch (Exception e) {
                logError(e);
            }
        } else if (member.getName().equals("invoke")) {
            Object thisObject = callback.getThisObject();

            try {
                Object jpf = thisObject.getClass().getDeclaredField("b").get(thisObject);

                Object controller = jpf.getClass().getDeclaredField("e").get(jpf);
                Object featureObj = jpf.getClass().getDeclaredField("f").get(jpf);

                PlayerHook.controller = controller;
                featureName = (String) featureObj.getClass().getDeclaredMethod("getName").invoke(featureObj);
            } catch (Exception e) {
                logError(e);
            }
        }
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        PlayerHook hook = getHook(PlayerHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        if(member == seekConstructor) {
            log("Seek constructor was called!");
            seekInstance = callback.getThisObject();
        }
        if (member == l6r0Ctor) {
            // Skip
            Object thisObject = callback.getThisObject();
            if (thisObject == null) return;

            cachedL6r0 = thisObject;

            try {
                Object jxq = l6r0AField.get(thisObject);
                if (jxq != null) {
                    cachedJxq = jxq;

                    Object ruf = jxqAField.get(jxq);
                    if (ruf != null) {
                        cachedRuf = ruf;
                    } else {
                        Log.e("SpotifyPlus", "jxq.a was null");
                    }
                } else {
                    Log.e("SpotifyPlus", "l6r0.a was null");
                }
            } catch (Throwable t) {
                Log.e("SpotifyPlus", "Failed reading l6r0 -> jxq -> ruf", t);
            }
        }

    }

    @Override
    public Object handle(String command, Object[] args) {
        try {
            switch (command) {
                case "seek" -> {
                    long position = (long) args[0];
                    Object seekArg = null;
                    seekArg = ctor.newInstance(position * 1000);

//                    try {
//                        double percentage = Double.parseDouble(position);
//                        if (percentage > 1) throw new NumberFormatException("Percentage cannot exceed 1");
//
//                        SpotifyTrack track = Utils.getTrack(classLoader);
//
//                        seekArg = ctor.newInstance(track.duration * percentage);
//                    } catch (NumberFormatException ignored) {
//                        seekArg = ctor.newInstance(Long.parseLong(position) * 1000);
//                    } catch (Exception ignored) {
//                    }

                    if (seekInstance == null) {
                        logError("Seek instance was null!");
                        return null;
                    }

                    Method method = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(seekInstance.getClass()))).matcher(MethodMatcher.create().paramTypes(ctor.getDeclaringClass().getSuperclass()))).get(0).getMethodInstance(classLoader);
                    Object block = method.invoke(seekInstance, seekArg);
                    if (block == null) {
                        logError("Failed to get block object");
                        return null;
                    }

                    block.getClass().getMethod("blockingGet").invoke(block);
                }
                case "getProgress" -> {
                    long position = Utils.getCurrentPlaybackPosition();
//                    ScriptManager.send(id, "response", "player.getProgress", new JSONObject().put("position", position));
                }
                case "getDuration" -> {
                    SpotifyTrack track = Utils.getTrack(classLoader);
//                    ScriptManager.send(id, "response", "player.getDuration", new JSONObject().put("duration", track.duration));
                }
                case "togglePlay" -> {
//                    if (json.has("play")) {
//                        boolean play = json.getBoolean("play");
//                        togglePlay(play);
//                    } else {
//                        togglePlay();
//                    }

                    togglePlay();
                }
                case "skipNext" -> skipToNext();
                case "skipPrevious" -> skipToPrevious();
            }

            return null;
        } catch (Exception e) {
            logError(e);
            return null;
        }
    }

    // Play/Pause
    public static void togglePlay() {
        if (controller == null || featureName == null) return;

        try {
            Object command = isPlaying ? z1e0Ctor.newInstance(featureName, false) : c2e0Ctor.newInstance(featureName, false);
            Object single = x2e0aMethod.invoke(controller, command);
            if (single == null) return;

            Object completable = ignoreElementMethod.invoke(single);
            if (completable == null) return;

            completableSubscribeMethod.invoke(completable);
        } catch (Exception e) {
            logError(e);
        }
    }

    private static void togglePlay(boolean play) {
        if (controller == null || featureName == null) return;

        try {
            Object command = play ? c2e0Ctor.newInstance(featureName, false) : z1e0Ctor.newInstance(featureName, false);
            Object single = x2e0aMethod.invoke(controller, command);
            if (single == null) return;

            Object completable = ignoreElementMethod.invoke(single);
            if (completable == null) return;

            completableSubscribeMethod.invoke(completable);
        } catch (Exception e) {
            logError(e);
        }
    }

    // Skip
    private void skipToNext() {
        try {
            if (cachedRuf == null && cachedJxq != null) {
                Object ruf = jxqAField.get(cachedJxq);
                if (ruf != null) {
                    cachedRuf = ruf;
                }
            }

            if (cachedRuf == null) {
                Log.e("SpotifyPlus", "No cached ruf yet");
                return;
            }

            Object request = buildSkipNextRequest();
            if (request == null) {
                Log.e("SpotifyPlus", "Failed to build EsSkipNextRequest");
                return;
            }

            Object result = rufCallSingleMethod.invoke(cachedRuf, "spotify.player.esperanto.proto.ContextPlayer", "SkipNext", request);

            if (result != null) subscribeResult(result);
        } catch (Throwable t) {
            Log.e("SpotifyPlus", "Failed to dispatch SkipNext", t);
        }
    }

    private void skipToPrevious() {
        try {
            if (cachedRuf == null && cachedJxq != null) {
                Object ruf = jxqAField.get(cachedJxq);
                if (ruf != null) {
                    cachedRuf = ruf;
                }
            }

            if (cachedRuf == null) {
                Log.e("SpotifyPlus", "No cached ruf yet");
                return;
            }

            Object request = buildSkipPrevRequest();
            if (request == null) {
                Log.e("SpotifyPlus", "Failed to build EsSkipPrevRequest");
                return;
            }

            Object result = rufCallSingleMethod.invoke(cachedRuf, "spotify.player.esperanto.proto.ContextPlayer", "SkipPrev", request);

            if (result != null) subscribeResult(result);
        } catch (Throwable t) {
            Log.e("SpotifyPlus", "Failed to dispatch SkipPrev", t);
        }
    }

    private Object buildSkipNextRequest() {
        try {
            Class<?> loggingParamsClass = classLoader.loadClass("com.spotify.player.model.command.options.LoggingParams");
            Method loggingBuilderMethod = loggingParamsClass.getDeclaredMethod("builder");
            Object loggingBuilder = loggingBuilderMethod.invoke(null);
            Method loggingBuildMethod = loggingBuilder.getClass().getMethod("build");
            Object loggingParams = loggingBuildMethod.invoke(loggingBuilder);

            Class<?> si01Class = classLoader.loadClass("p.si01");
            Method si01m = si01Class.getDeclaredMethod("m", loggingParamsClass);
            si01m.setAccessible(true);
            Object esLoggingParams = si01m.invoke(null, loggingParams);

            Class<?> requestClass = classLoader.loadClass("com.spotify.player.esperanto.proto.EsSkipNext$SkipNextRequest");
            Method wMethod = requestClass.getDeclaredMethod("w");
            Object builder = wMethod.invoke(null);

            Method vMethod = null;
            for (Method method : builder.getClass().getMethods()) {
                if (method.getName().equals("v") && method.getParameterCount() == 1) {
                    vMethod = method;
                    break;
                }
            }

            if (vMethod == null) throw new NoSuchMethodException("Could not find EsSkipNextRequest.Builder.v(...)");

            vMethod.invoke(builder, esLoggingParams);

            Method buildMethod = builder.getClass().getMethod("build");
            return buildMethod.invoke(builder);
        } catch (Throwable t) {
            Log.e("SpotifyPlus", "Failed building EsSkipNextRequest", t);
            return null;
        }
    }

    private Object buildSkipPrevRequest() {
        try {
            Class<?> loggingParamsClass = classLoader.loadClass("com.spotify.player.model.command.options.LoggingParams");
            Method loggingBuilderMethod = loggingParamsClass.getDeclaredMethod("builder");
            Object loggingBuilder = loggingBuilderMethod.invoke(null);
            Method loggingBuildMethod = loggingBuilder.getClass().getMethod("build");
            Object loggingParams = loggingBuildMethod.invoke(loggingBuilder);

            Class<?> si01Class = classLoader.loadClass("p.si01");
            Method si01m = si01Class.getDeclaredMethod("m", loggingParamsClass);
            si01m.setAccessible(true);
            Object esLoggingParams = si01m.invoke(null, loggingParams);

            Class<?> requestClass = classLoader.loadClass("com.spotify.player.esperanto.proto.EsSkipPrev$SkipPrevRequest");
            Method wMethod = requestClass.getDeclaredMethod("w");
            Object builder = wMethod.invoke(null);

            Method allowSeekingMethod = null;
            Method loggingMethod = null;

            for (Method method : builder.getClass().getMethods()) {
                if (method.getParameterCount() != 1) continue;

                Class<?> param = method.getParameterTypes()[0];

                if (param == boolean.class || param == Boolean.class) {
                    allowSeekingMethod = method;
                } else if (param.isInstance(esLoggingParams)) {
                    loggingMethod = method;
                }
            }

            if (loggingMethod == null)
                throw new NoSuchMethodException("Could not find EsSkipPrevRequest.Builder logging setter");
            if (allowSeekingMethod == null)
                throw new NoSuchMethodException("Could not find EsSkipPrevRequest.Builder allowSeeking setter");

            allowSeekingMethod.invoke(builder, false);
            loggingMethod.invoke(builder, esLoggingParams);

            Method buildMethod = builder.getClass().getMethod("build");
            return buildMethod.invoke(builder);
        } catch (Throwable t) {
            Log.e("SpotifyPlus", "Failed building EsSkipPrevRequest", t);
            return null;
        }
    }

    private void subscribeResult(Object result) {
        try {
            if (subscribeMethod == null) {
                for (Method method : result.getClass().getMethods()) {
                    if (method.getName().equals("subscribe") && method.getParameterCount() == 0) {
                        subscribeMethod = method;
                        subscribeMethod.setAccessible(true);
                        break;
                    }
                }
            }

            if (subscribeMethod == null) {
                Log.e("SpotifyPlus", "Could not find no-arg subscribe() on " + result.getClass().getName());
                return;
            }

            subscribeMethod.invoke(result);
        } catch (Throwable t) {
            Log.e("SpotifyPlus", "Failed subscribing to skip result", t);
        }
    }
}

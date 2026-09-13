package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.UsingType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;

public class PrivateSessionHook extends SpotifyHook {
    private static final long REFRESH_DELAY = 5L * 60L * 60L * 1000L + 45L * 60L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private Object privateSessionKey;
    private Object settingsRepository;
    private Runnable refreshPrivateSession;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    public PrivateSessionHook(Context context) {
        prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
    }

    @Override
    protected void hook() {
        try {
            var settingKeyClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).fieldCount(4, 64).addField(FieldMatcher.create().modifiers(Modifier.STATIC)).addMethod(MethodMatcher.create().name("<clinit>").usingStrings("offline_mode", "play_explicit_content", "private_session", "download_over_3g"))));
            if (settingKeyClasses.size() != 1) throw new IllegalStateException("[PrivateSessionHook/DexKit] Expected one static settings-key registry but found " + settingKeyClasses);
            Class<?> settingKeysClass = settingKeyClasses.get(0).getInstance(lpparm.classLoader);

            privateSessionKey = findSettingKey(settingKeysClass, "private_session");
            // 9.1.82 moved the RPC strings into a shared ClientBase implementation.
            // Identify the settings repository by its router and typed mutation API.
            var setters = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType("io.reactivex.rxjava3.core.Completable")
                    .paramTypes((String) null, Object.class.getName())
                    .declaredClass(ClassMatcher.create().addFieldForType("com.spotify.cosmos.rxrouter.RxRouter"))));
            java.util.List<Method> settingsMethods = new java.util.ArrayList<>();
            for (var setter : setters) {
                Method method = setter.getMethodInstance(lpparm.classLoader);
                if (!Modifier.isStatic(method.getModifiers()) && method.getParameterTypes()[0].isInstance(privateSessionKey)) settingsMethods.add(method);
            }
            if (settingsMethods.size() != 1) throw new IllegalStateException("Expected one settings mutation API, found " + settingsMethods);
            Method settingsMethod = settingsMethods.get(0);
            Class<?> settingsClass = settingsMethod.getDeclaringClass();

            Class<?> settingsStateClass = XposedHelpers.findClass("com.spotify.settings.esperanto.proto.SettingsOuterClass$SettingsState", lpparm.classLoader);
            Method checkPrivateSessionMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(settingsStateClass))).matcher(MethodMatcher.create()
                    .returnType(boolean.class)
                    .paramCount(0)
                    .usingFields(Collections.singletonList(UsingFieldMatcher.create().field(FieldMatcher.create().declaredClass(settingsStateClass).name("privateSession_").type(boolean.class)).usingType(UsingType.Read)))
            )).single().getMethodInstance(lpparm.classLoader);

            Method onIncognitoModeDisabledByTimerMethod = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .name("onIncognitoModeDisabledByTimer")
                    .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                    .returnType(void.class)
                    .paramCount(0)
            )).single().getMethodInstance(lpparm.classLoader);

            refreshPrivateSession = () -> {
                handler.removeCallbacks(refreshPrivateSession);

                if (!prefs.getBoolean("private_session", false) || settingsRepository == null || privateSessionKey == null) {
                    return;
                }

                try {
                    Object update = settingsMethod.invoke(settingsRepository, privateSessionKey, Boolean.TRUE);
                    XposedHelpers.callMethod(update, "subscribe");

                    handler.removeCallbacks(refreshPrivateSession);
                    handler.postDelayed(refreshPrivateSession, REFRESH_DELAY);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                    handler.removeCallbacks(refreshPrivateSession);
                    handler.postDelayed(refreshPrivateSession, 60_000L);
                }
            };

            preferenceListener = (sharedPreferences, key) -> {
                if (!"private_session".equals(key)) return;

                handler.removeCallbacks(refreshPrivateSession);
                if (sharedPreferences.getBoolean(key, false)) {
                    handler.post(refreshPrivateSession);
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener);

            XposedBridge.hookAllConstructors(settingsClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    settingsRepository = param.thisObject;
                    handler.post(refreshPrivateSession);
                }
            });

            XposedBridge.hookMethod(settingsMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("private_session", false) || param.args[0] != privateSessionKey || !Boolean.FALSE.equals(param.args[1])) {
                        return;
                    }

                    param.args[1] = Boolean.TRUE;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("private_session", false) || param.args[0] != privateSessionKey || !Boolean.TRUE.equals(param.args[1])) {
                        return;
                    }

                    handler.removeCallbacks(refreshPrivateSession);
                    handler.postDelayed(refreshPrivateSession, REFRESH_DELAY);
                }
            });

            XposedBridge.hookMethod(checkPrivateSessionMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("private_session", false)) return;

                    if (Boolean.FALSE.equals(param.getResult())) {
                        handler.removeCallbacks(refreshPrivateSession);
                        handler.postDelayed(refreshPrivateSession, 750L);
                    }
                    param.setResult(Boolean.TRUE);
                }
            });

            XposedBridge.hookMethod(onIncognitoModeDisabledByTimerMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("private_session", false)) return;

                    handler.removeCallbacks(refreshPrivateSession);
                    handler.postDelayed(refreshPrivateSession, 250L);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private Object findSettingKey(Class<?> settingKeysClass, String keyName) throws Throwable {
        for (java.lang.reflect.Field staticField : settingKeysClass.getDeclaredFields()) {
            if (!Modifier.isStatic(staticField.getModifiers())) continue;
            staticField.setAccessible(true);
            Object candidate = staticField.get(null);
            if (candidate == null) continue;
            for (java.lang.reflect.Field field : candidate.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
                field.setAccessible(true);
                if (keyName.equals(field.get(candidate))) return candidate;
            }
        }
        throw new IllegalStateException("Could not identify the " + keyName + " setting key");
    }
}

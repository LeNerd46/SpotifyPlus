package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.SettingsSync;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.SpotifyPlusSettings;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.ScriptSideDrawerItem;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.module.scripting.ExtensionAssetRegistry;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.MatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.query.matchers.ParametersMatcher;
import org.luckypray.dexkit.result.ClassDataList;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@XposedHooker
public class SideDrawerHook extends SpotifyHook {
    private static final int SETTINGS_OVERLAY_ID = 0x53504c53;
    private static final int DETAILED_SETTINGS_OVERLAY_ID = 0x53504c54;
    private static int idToUse = 8001;
    private static int resourceIdToUse = 2131957898;
    private static SharedPreferences prefs;
    private static boolean isNewSideDrawer = false;
    private static final AtomicReference<Object> sideDrawerBackDispatcher = new AtomicReference<>();
    private static final AtomicReference<Object> sideDrawerBackCallback = new AtomicReference<>();

    private static ClassDataList fwd0Classes;
    private static ClassDataList dwd0Classes;
    private static ClassDataList propertiesClasses;
    private static ClassDataList onClickClasses;
    private static Class<?> whateverThisInterfaceDoes;
    private static Class<?> iconInterface;
    private static Class<?> wwk;
    private static Class<?> bti0Class;
    private static Class<?> buttonClass;
    private static Class<?> sideDrawerItemClass;
    private static Class<?> propertiesClass;
    private static Class<?> onClickClass;
    private static Class<?> qbpInterface;
    private static Class<?> zpj0Interface;
    private static Class<?> cbpInterface;
    private static Field sideDrawerArrayField;

    private static Constructor<?> navigationBarConstructor;
    private static Method routeIntentMethod;
    private static Method routeRewriteMethod;
    private static Method mainOnCreateMethod;
    private static Method mainOnNewIntentMethod;
    private static Method invokeSuspendMethod;
    private static Method onClickMethod;
    private static Object targetOnClick;
    private static Runnable onClickRunnable;
    private static Method resourceMethod;

    private static final List<ScriptSideDrawerItem> scriptItems = new ArrayList<>();
    private static final Map<Pair<String, String>, Runnable> clickHandlers = Collections.synchronizedMap(new WeakHashMap<>());

    //    private static final ConcurrentHashMap<Pair<Integer, String>, List<SettingItem.SettingSection>> scriptSettings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Pair<Integer, String>, Runnable> scriptSideButtons = new ConcurrentHashMap<>();
    private static final AtomicBoolean overlayShown = new AtomicBoolean(false);
    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        var constructorClassList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItemSet(item1=")));
        var parameterClassList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItem(icon=").methodCount(4).fieldCount(5, 6)));
        if (!constructorClassList.isEmpty() && !parameterClassList.isEmpty()) {
            Class<?> constructorClass = constructorClassList.get(0).getInstance(classLoader);
            Class<?> parameterClass = parameterClassList.get(0).getInstance(classLoader);
            navigationBarConstructor = constructorClass.getDeclaredConstructor(parameterClass, parameterClass, parameterClass, parameterClass, parameterClass);
            hook(navigationBarConstructor);
        } else {
            log("[SpotifyPlus] Constructor class not found");
        }

//        SpotifyTitleOverride.install();

        Class<?> id30 = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("FeatureIdentifier.InternalReferrer.Persistable", "extra_animation_in"))).get(0).getInstance(classLoader);
        log("id30: " + id30.getName());
        routeIntentMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(bridge.getClassData(id30))).matcher(MethodMatcher.create().returnType(Intent.class))).get(0).getMethodInstance(classLoader);
        hook(routeIntentMethod);

        var ysi0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify:concept:").fieldCount(1).modifiers(Modifier.PUBLIC | Modifier.FINAL)));
        Class<?> ysi0 = ysi0Classes.get(0).getInstance(classLoader);
        log("ysi0: " + ysi0.getName());
        bti0Class = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("user:([^:]+)"))).get(0).getInstance(classLoader);
        log("bti0: " + bti0Class.getName());
        var classData = bridge.getClassData(ysi0);

        // Bro it can't find the method if we look for it the normal way (with bridge.FindMethod())
        for (var methodData : classData.getMethods()) {
            try {
                if (methodData.getName().equals("<init>")) continue;
                if (!methodData.getUsingStrings().isEmpty()) continue;
                if (!methodData.getReturnType().getName().equals(bti0Class.getName())) continue;
                if (methodData.getParamTypeNames().size() != 1 && methodData.getParamTypeNames().get(0).equals("String"))
                    continue;

                Method method = methodData.getMethodInstance(classLoader);
                routeRewriteMethod = method;
                hook(routeRewriteMethod);
                break;
            } catch (Throwable t) {
                logError(t);
            }
        }

        Class<?> main = findClass("com.spotify.music.SpotifyMainActivity");
        mainOnCreateMethod = main.getDeclaredMethod("onCreate", Bundle.class);
        mainOnNewIntentMethod = main.getDeclaredMethod("onNewIntent", Intent.class);
        hook(mainOnCreateMethod);
        hook(mainOnNewIntentMethod);

        var modifyDataListClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).methodCount(3).fields(FieldsMatcher.create()
                .count(4)
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(int.class))
                .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class))
        )));
        var methodsThing = bridge.findMethod(FindMethod.create().searchInClass(modifyDataListClass).matcher(MethodMatcher.create().returnType(Object.class).modifiers(Modifier.PUBLIC | Modifier.FINAL).paramCount(1).paramTypes(Object.class)));
        invokeSuspendMethod = methodsThing.get(methodsThing.toArray().length - 1).getMethodInstance(classLoader);
        Class<?> correctClass = invokeSuspendMethod.getDeclaringClass();
        sideDrawerArrayField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(correctClass))).matcher(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class))).get(0).getFieldInstance(classLoader);
        hook(invokeSuspendMethod);

        var whateverInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("quick_add_to_playlist_item")));
        var iconInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("getState(Lcom/spotify/alignedcuration/firstsave/page/contents/DefaultSaveDestinationElement$Props;)Lkotlinx/coroutines/flow/Flow;")));
        var wwkList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Encore.Vector.CopyAlt16")));
        dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("SideDrawerListItem(element=")));
        if (dwd0Classes.isEmpty())
            dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("SideDrawerListItem(content=")));
        if (dwd0Classes.isEmpty())
            dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL).superClass(ClassMatcher.create()).methods(MethodsMatcher.create()
                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).params(ParametersMatcher.create().add(Object.class)).name("equals"))
                    .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0))
                    .add(MethodMatcher.create().name("<init>").paramCount(1))
                    .add(MethodMatcher.create().name("<init>").paramCount(1))
                    .add(MethodMatcher.create().name("<init>").paramCount(7))
            ).fieldCount(1)));

        fwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL).fields(FieldsMatcher.create().count(2).add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))).usingStrings("ListItem(id=")));
        if (fwd0Classes.isEmpty())
            fwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL).superClass(ClassMatcher.create()).methods(MethodsMatcher.create().count(3)
                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).params(ParametersMatcher.create().add(Object.class)).name("equals"))
                    .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0).usingNumbers(31))
                    .add(MethodMatcher.create().name("<init>").paramCount(2))
            ).fields(FieldsMatcher.create().count(2)
                    .add(FieldMatcher.create().type(int.class))
                    .add(FieldMatcher.create().type(dwd0Classes.get(0).getInstance(classLoader)))
            )));

        propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Props(icon=", ", title=", ", titleRes=", ", uriToNavigate=", ", isNew=", ", instrumentation=", ", hasNotification=")));
        if (propertiesClasses.isEmpty())
            propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Navigation(icon=", "title=null", "uriToNavigate=", "isNew=", "instrumentation=")));
        if (propertiesClasses.isEmpty())
            propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(1).methods(MethodsMatcher.create()
                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).params(ParametersMatcher.create().add(Object.class)).name("equals"))
                    .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0).usingNumbers(961, 1231, 1237))
            ).fields(FieldsMatcher.create().count(6)
                    .add(FieldMatcher.create().type(Integer.class).modifiers(Modifier.PUBLIC | Modifier.FINAL))
                    .add(FieldMatcher.create().type(String.class).modifiers(Modifier.PUBLIC | Modifier.FINAL))
                    .add(FieldMatcher.create().type(boolean.class).modifiers(Modifier.PUBLIC | Modifier.FINAL))
            )));

        onClickClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Instrumentation(node=", ", onClick=", ", onImpression=").fieldCount(3)));
        if (onClickClasses.isEmpty()) {
            Class<?> interfaceToUse = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("tracks_section", "footer_section", "location").fieldCount(3).methodCount(2))).get(0).getInstance(classLoader).getInterfaces()[0];
            onClickClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL).superClass(ClassMatcher.create()).methods(MethodsMatcher.create().count(3)
                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).params(ParametersMatcher.create().add(Object.class)).name("equals"))
                    .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0).usingNumbers(31, 0))
                    .add(MethodMatcher.create().name("<init>").paramCount(3))
            ).fields(FieldsMatcher.create().count(3)
                    .add(FieldMatcher.create().type(Object.class))
                    .add(FieldMatcher.create().type(interfaceToUse))
            )));
        }

        var qbpInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.FINAL, MatchType.Equals).interfaceCount(1).fields(FieldsMatcher.create().add(FieldMatcher.create().type(int.class)).count(2)).methods(MethodsMatcher.create()
                .count(4)
                .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).name("invoke").paramTypes(Object.class, Object.class))
                .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).name("invokeSuspend").paramTypes(Object.class))
        )));
        var zpj0InterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("premium_row")));
        var cbpInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("video_surface_view_seek_frame_tag")));

        if (whateverInterfaceList.isEmpty() || iconInterfaceList.isEmpty() || wwkList.isEmpty() || fwd0Classes.isEmpty() || dwd0Classes.isEmpty() || propertiesClasses.isEmpty() || onClickClasses.isEmpty() || qbpInterfaceList.isEmpty() || zpj0InterfaceList.isEmpty() || cbpInterfaceList.isEmpty()) {
            log("[SpotifyPlus] whatever interface: " + whateverInterfaceList.size());
            log("[SpotifyPlus] icon interface: " + iconInterfaceList.size());
            log("[SpotifyPlus] wwk: " + wwkList.size());
            log("[SpotifyPlus] fwd0: " + fwd0Classes.size());
            log("[SpotifyPlus] dwd0: " + dwd0Classes.size());
            log("[SpotifyPlus] props: " + propertiesClasses.size());
            log("[SpotifyPlus] onClick: " + onClickClasses.size());
            log("[SpotifyPlus] qbp interface: " + qbpInterfaceList.size());
            log("[SpotifyPlus] zpj0 interface: " + zpj0InterfaceList.size());
            log("[SpotifyPlus] cbpInterface interface: " + cbpInterfaceList.size());
            log("[SpotifyPlus] No classes found");
            return;
        }

        whateverThisInterfaceDoes = whateverInterfaceList.get(0).getInstance(classLoader).getInterfaces()[0];
        iconInterface = iconInterfaceList.get(0).getInstance(classLoader).getInterfaces()[0];
        wwk = wwkList.get(0).getInstance(classLoader).getSuperclass();
        buttonClass = fwd0Classes.get(0).getInstance(classLoader);
        sideDrawerItemClass = dwd0Classes.get(0).getInstance(classLoader);
        propertiesClass = propertiesClasses.get(0).getInstance(classLoader);
        onClickClass = onClickClasses.get(0).getInstance(classLoader);
        qbpInterface = qbpInterfaceList.get(0).getInstance(classLoader).getInterfaces()[0];
        zpj0Interface = zpj0InterfaceList.get(0).getInstance(classLoader).getInterfaces()[0];
        cbpInterface = cbpInterfaceList.get(0).getInstance(classLoader).getMethod("getOnScrubEnd").getReturnType();

        var resourceClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ad.skippable_ad_delay")));
        resourceMethod = bridge.findMethod(FindMethod.create().searchInClass(resourceClass).matcher(MethodMatcher.create().returnType(String.class).paramCount(2))).get(0).getMethodInstance(classLoader);
        hook(resourceMethod);

        SpotifyNativeBridge.registerHandler("side", this);
    }

    @BeforeInvocation
    public static void beforeHook(XposedInterface.BeforeHookCallback callback) {
        SideDrawerHook hook = getHook(SideDrawerHook.class);
        if (hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        try {
            if (member == resourceMethod) {
                int id = (int) callback.getArgs()[1];

                if (id == 2131957897) {
                    callback.returnAndSkip(Utils.getString(currentActivity.get(), R.string.settings_label));
                    return;
                }

                var thing = scriptItems.stream().filter(x -> x.resourceId == id).findFirst();
                if (thing.isEmpty()) return;

                ScriptSideDrawerItem item = thing.get();
                callback.returnAndSkip(item.title);
            }

            if (member == mainOnCreateMethod) {
                if (callback.getThisObject() instanceof Activity activity) {
                    currentActivity = new WeakReference<>(activity);
                    prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
                }
                return;
            }

            if (member == navigationBarConstructor) {
                if (SpotifyPlusSettings.removeCreateButton) {
                    for (int i = 0; i < Math.min(5, callback.getArgs().length); i++) {
                        Object item = callback.getArgs()[i];
                        if (item == null) continue;
                        String content = item.toString().toLowerCase();
                        if (content.contains("create") || content.contains("premium")) {
                            log("[SpotifyPlus] Removing navbar item: " + content);
                            callback.getArgs()[i] = null;
                        }
                    }
                }
                return;
            }

            if (member == routeRewriteMethod) {
                String s = (String) callback.getArgs()[0];
                if (s == null) return;

                Uri uri = Uri.parse(s);
                if (s.contains("spotifyplus")) {
                    log(s);
                }
                if (!uri.isHierarchical()) return;
                String spotifyPlus = uri.getQueryParameter("spotifyplus");

                if (spotifyPlus != null && spotifyPlus.equals("side")) {
                    try {
                        log(s);

                        if (uri.getHost().equals("side")) {
                            JSONObject json = new JSONObject();
                            json.put("id", uri.getQueryParameter("id"));
                            json.put("scriptId", uri.getQueryParameter("scriptId"));

                            ScriptManager.send("", "event", "side.press", json);

                            callback.returnAndSkip(newInstance(bti0Class, "spotify:home"));
                            return;
                        }
                    } catch (Exception e) {
                        logError(e);
                        return;
                    }

                    if (uri.getHost().equals("settings")) {
                        showSettingsOverlay();
                        callback.returnAndSkip(newInstance(bti0Class, "spotify:settings"));
                        return;
                    }
                }
                return;
            }

            if (member == invokeSuspendMethod) {
                try {
                    ReactManager.registerSurfaceSilent("sideDrawer", (ViewGroup) currentActivity.get().getWindow().getDecorView());
                } catch(Exception ignored) {}

                Object[] originalItemsWithNull = (Object[]) sideDrawerArrayField.get(callback.getThisObject());
                if (originalItemsWithNull == null) return;
                Object[] originalItems = Arrays.stream(originalItemsWithNull).filter(Objects::nonNull).toArray(Object[]::new);
                if (originalItems.length < 4 || originalItems[0].getClass() != buttonClass) return;

                isNewSideDrawer = originalItems.length >= 6 && originalItems.length != 12;
                Object newArray = Array.newInstance(buttonClass, originalItems.length + 2 + scriptItems.size());
                for (int i = 0; i < originalItems.length; i++) Array.set(newArray, i, originalItems[i]);

                Object template = originalItems[isNewSideDrawer ? originalItems.length - 2 : originalItems.length - 1];
                Object templateLightning = originalItems[isNewSideDrawer ? 2 : 1];
                Array.set(newArray, originalItems.length, createSideDrawerButton("Spotify Plus Settings", template, 2131957897, "spotifyplus:settings", null));

                int index = originalItems.length + 2;
                for (var item : scriptItems) {
                    Array.set(newArray, index, createSideDrawerButton(item.title, templateLightning, item.resourceId, "spotifyplus:side?scriptId=" + Uri.encode(item.scriptId) + "&id=" + Uri.encode(item.id), item));
                    index++;
                }

                sideDrawerArrayField.set(callback.getThisObject(), newArray);
            }

            if (member.getName().equals("invoke")) {
//                if (callback.getThisObject() != targetOnClick) return;
//                if (!overlayShown.compareAndSet(false, true)) return;

                Runnable runnable = clickHandlers.get(callback.getThisObject());
                if (runnable == null) return;

                try {
                    runnable.run();
                } catch (Exception e) {
                    logError(e);
                }
            }
        } catch (Throwable t) {
            logError(t);
        }
    }

    @AfterInvocation
    public static void afterHook(XposedInterface.AfterHookCallback callback) {
        SideDrawerHook hook = getHook(SideDrawerHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        try {
            if (member == routeIntentMethod) {
                Object nav = callback.getArgs()[0];
                String raw = (String) getFieldValue(nav, "a");
                if (raw != null && raw.startsWith("spotifyplus:")) {
                    Intent intent = (Intent) callback.getResult();
                    String path = raw.substring("spotifyplus:".length());
                    if (path.startsWith("side")) {
                        Map<String, String> params = new HashMap<>();

                        for (String part : path.split("\\?")[1].split("&")) {
                            int equals = part.indexOf("=");

                            if (equals >= 0) {
                                String key = Uri.decode(part.substring(0, equals));
                                String value = Uri.decode(part.substring(equals + 1));

                                params.put(key, value);
                                log(key + " | " + value);
                            } else {
                                params.put(Uri.decode(part), "");
                            }
                        }

                        String scriptId = params.get("scriptId");
                        String id = params.get("id");

                        try {
                            JSONObject json = new JSONObject();
                            json.put("id", id);
                            json.put("scriptId", scriptId);

                            SpotifyNativeBridge.sendEvent("side.press", json.toString());
//                            ScriptManager.send("", "event", "side.press", json);

                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                Activity activity = currentActivity.get();
                                clearSideDrawerBackCallback();
                                final android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
                                final android.window.OnBackInvokedCallback backCallback = new android.window.OnBackInvokedCallback() {
                                    @Override
                                    public void onBackInvoked() {
                                        log("Back pressed!");

                                        try {
                                            JSONObject payload = new JSONObject();
                                            payload.put("scriptId", scriptId);
                                            payload.put("surfaceId", "sideDrawer");
                                            SpotifyNativeBridge.sendEvent("android.backPressed", payload.toString());
                                        } catch (Exception e) {
                                            logError(e);
                                        }
                                    }
                                };

                                sideDrawerBackDispatcher.set(dispatcher);
                                sideDrawerBackCallback.set(backCallback);
                                dispatcher.registerOnBackInvokedCallback(1000001, backCallback);
                            }
                        } catch (Exception e) {
                            logError(e);
                        }

                        Intent newIntent = new Intent();
                        newIntent.setData(Uri.parse("spotify:null"));
                        newIntent.setClassName("com.spotify.music", "com.spotify.music.SpotifyMainActivity");

                        callback.setResult(newIntent);
                    }


//                    intent.setData(Uri.parse("spotify:settings"));
//                    intent.putExtra("is_internal_navigation", true);
//                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//                    intent.putExtra("spx", "spotifyplus:" + path);
//                    intent.putExtra("spx_src", raw);
//                    Context appCtx = (Context) getFieldValue(callback.getThisObject(), "b");
//                    String activityClass = (String) getFieldValue(callback.getThisObject(), "a");
//                    intent.setClassName(appCtx, activityClass);
//                    callback.setResult(intent);
//                    log("[SpotifyPlus][id30.a] rewrote to spotify:settings with extras");
                }
                return;
            }

            if (member == mainOnNewIntentMethod) {
                Activity activity = (Activity) callback.getThisObject();
                currentActivity = new WeakReference<>(activity);
                Intent receivedIntent = (Intent) callback.getArgs()[0];
                if (receivedIntent != null && receivedIntent.getStringExtra("spx") != null && receivedIntent.getStringExtra("spx").startsWith("spotifyplus:"))
                    return;

                activity.runOnUiThread(() -> {
                    try {
                        View root = activity.getWindow().getDecorView().findViewById(SETTINGS_OVERLAY_ID);
                        View detailed = activity.getWindow().getDecorView().findViewById(DETAILED_SETTINGS_OVERLAY_ID);
                        if (detailed != null && root != null) {
                            ((ViewGroup) root.getParent()).removeView(detailed);
                            ((ViewGroup) root.getParent()).removeView(root);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:settings"));
                            intent.putExtra("spx", "spotifyplus");
                            intent.setClassName("com.spotify.music", "com.spotify.music.SpotifyMainActivity");
                            intent.putExtra("is_internal_navigation", true);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            activity.startActivity(intent);
                        } else if (root != null) {
//                            ((ViewGroup) root.getParent()).removeView(root);
                            overlayShown.set(false);
                        }
                    } catch (Throwable t) {
                        logError(t);
                    }
                });
            }
        } catch (Throwable t) {
            logError(t);
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        if (command.equals("register")) {
            try {
                String itemId = (String) args[0];
                String scriptId = (String) args[1];
                String title = (String) args[2];
                String iconAssetId = args.length > 3 && args[3] instanceof String ? (String) args[3] : null;

                ScriptSideDrawerItem item = new ScriptSideDrawerItem(itemId, scriptId, title, iconAssetId);
                if (scriptItems.contains(item)) return null;
                log("Registering " + itemId + " | " + scriptId);

                item.resourceId = resourceIdToUse;
                resourceIdToUse++;

                scriptItems.add(item);
            } catch (Exception ignored) {
            }
        } else if (command.equals("unregisterScript")) {
            try {
                String scriptId = (String) args[0];
                scriptItems.removeIf(item -> item.scriptId.equals(scriptId));
            } catch (Exception ignored) {
            }
        } else if (command.equals("surfaceClosed")) {
            if (args.length > 0 && "sideDrawer".equals(args[0])) {
                clearSideDrawerBackCallback();
            }
        }

        return null;
    }

    private static void clearSideDrawerBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;

        Object dispatcherValue = sideDrawerBackDispatcher.getAndSet(null);
        Object callbackValue = sideDrawerBackCallback.getAndSet(null);
        if (!(dispatcherValue instanceof android.window.OnBackInvokedDispatcher)
            || !(callbackValue instanceof android.window.OnBackInvokedCallback)) return;

        Activity activity = currentActivity.get();
        if (activity == null) return;

        android.window.OnBackInvokedDispatcher dispatcher =
            (android.window.OnBackInvokedDispatcher) dispatcherValue;
        android.window.OnBackInvokedCallback callback =
            (android.window.OnBackInvokedCallback) callbackValue;
        activity.runOnUiThread(() -> dispatcher.unregisterOnBackInvokedCallback(callback));
    }

    private Object createSideDrawerButton(String title, Object template, int resId, String uri, ScriptSideDrawerItem item) {
        try {
            var dwd0List = bridge.findField(FindField.create().searchInClass(fwd0Classes).matcher(FieldMatcher.create().type(sideDrawerItemClass)));
            var fieldList = bridge.findField(FindField.create().searchInClass(dwd0Classes).matcher(FieldMatcher.create().type(Object.class)));
            if (fieldList.isEmpty()) fieldList = bridge.findField(FindField.create().searchInClass(dwd0Classes));
            var bwd0List = bridge.findField(FindField.create().searchInClass(propertiesClasses).matcher(FieldMatcher.create().type(onClickClass)));
            var nodeList = bridge.findField(FindField.create().searchInClass(onClickClasses).matcher(FieldMatcher.create().type(whateverThisInterfaceDoes)));
            var impressionList = bridge.findField(FindField.create().searchInClass(onClickClasses).matcher(FieldMatcher.create().type(cbpInterface)));
            if (impressionList.isEmpty())
                impressionList = bridge.findField(FindField.create().searchInClass(onClickClasses).matcher(FieldMatcher.create().type(Object.class)));
            var iconList = bridge.findField(FindField.create().searchInClass(dwd0Classes).matcher(FieldMatcher.create().type(iconInterface)));
            if (iconList.isEmpty())
                iconList = bridge.findField(FindField.create().searchInClass(propertiesClasses).matcher(FieldMatcher.create().name("a")));
            var whateverList = bridge.findField(FindField.create().searchInClass(propertiesClasses).matcher(FieldMatcher.create().type(wwk)));

            if (dwd0List.isEmpty() || fieldList.isEmpty() || bwd0List.isEmpty() || nodeList.isEmpty() || impressionList.isEmpty() || iconList.isEmpty() || whateverList.isEmpty()) {
                log("[SpotifyPlus] dwd0: " + dwd0List.size());
                log("[SpotifyPlus] field: " + fieldList.size());
                log("[SpotifyPlus] bwd0: " + bwd0List.size());
                log("[SpotifyPlus] node: " + nodeList.size());
                log("[SpotifyPlus] impression: " + impressionList.size());
                log("[SpotifyPlus] icon: " + iconList.size());
                log("[SpotifyPlus] whatever: " + whateverList.size());
                log("[SpotifyPlus] No classes found");
                return null;
            }

            Object originalDwd0 = dwd0List.get(0).getFieldInstance(classLoader).get(template);
            Field field = fieldList.get(0).getFieldInstance(classLoader);
            Object originalProps = field.get(originalDwd0);
            Object originalBwd0 = bwd0List.get(0).getFieldInstance(classLoader).get(originalProps);
            Object originalNode = nodeList.get(0).getFieldInstance(classLoader).get(originalBwd0);
            Object originalImpression = impressionList.get(0).getFieldInstance(classLoader).get(originalBwd0);
            Object originalIcon;
            try {
                originalIcon = iconList.get(0).getFieldInstance(classLoader).get(originalDwd0);
            } catch (Throwable t) {
                originalIcon = originalProps.getClass().getFields()[0].get(originalProps);
            }
            Object drawerIcon = originalIcon;
            if (item != null && item.iconAssetId != null) {
                if (item.icon == null) item.icon = createDrawerIcon(originalIcon, item.iconAssetId);
                if (item.icon != null) drawerIcon = item.icon;
            }
            Object weirdField = whateverList.get(0).getFieldInstance(classLoader).get(originalProps);

            Object originalOnClick = null;
            if (isNewSideDrawer) {
                Class<?> vjwCls = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Could not retrieve pinned shortcuts"))).get(0).getInstance(classLoader).getSuperclass();
                for (Field f : originalBwd0.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(originalBwd0);
                    if (v != null && vjwCls.isAssignableFrom(v.getClass())) {
                        originalOnClick = v;
                        break;
                    }
                }

                if (originalOnClick != null) {
//                    clickHandlers.put(originalOnClick, onClick);

                    for (Method method : originalOnClick.getClass().getDeclaredMethods()) {
                        if (!method.getName().equals("invoke")) continue;
                        hook(method);
                    }
                }
            }

            Object newOnClick = java.lang.reflect.Proxy.newProxyInstance(classLoader, new Class[]{qbpInterface}, (proxy, method, args) -> null);

            Constructor<?> bwd0Ctor = onClickClass.getConstructor(zpj0Interface, qbpInterface, cbpInterface);
            Constructor<?> propsCtor = propertiesClass.getConstructors()[0];
            int mask = 1 | 2 | 4 | 16;

            Object newInstrumentation;
            try {
                newInstrumentation = bwd0Ctor.newInstance(originalNode, isNewSideDrawer ? originalOnClick : newOnClick, originalImpression);
            } catch (Throwable t) {
                logError(t);
                return null;
            }

//            SpotifyTitleOverride.overrideSpotifyStringById(resId, title);
            Object newProps;
            try {
                newProps = propsCtor.newInstance(drawerIcon, resId, uri, false, newInstrumentation, false, mask);
            } catch (Throwable t) {
                newProps = propsCtor.newInstance(drawerIcon, resId, uri, false, newInstrumentation, originalProps.getClass().getFields()[5].get(originalProps));
            }

            Object newDwd0 = !isNewSideDrawer ? newInstance(sideDrawerItemClass, originalIcon, newProps) : newInstance(sideDrawerItemClass, newProps);
            return newInstance(buttonClass, idToUse++, newDwd0);
        } catch (Throwable t) {
            logError(t);
            return null;
        }
    }

    private Object createDrawerIcon(Object templateIcon, String assetId) {
        try {
            File asset = ExtensionAssetRegistry.resolveFile(assetId);
            if (asset == null) return null;

            Bitmap bitmap = BitmapFactory.decodeFile(asset.getAbsolutePath());
            if (bitmap == null) return null;

            Field normalField = Arrays.stream(templateIcon.getClass().getSuperclass().getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .findFirst()
                    .orElseThrow();
            normalField.setAccessible(true);
            Object templateSizePair = normalField.get(templateIcon);
            Field[] sizeFields = Arrays.stream(templateSizePair.getClass().getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .toArray(Field[]::new);
            for (Field field : sizeFields) field.setAccessible(true);

            Object templateVector = sizeFields[0].get(templateSizePair);
            Object vector16 = createImageVector(templateVector, bitmap, 16);
            Object vector24 = createImageVector(templateVector, bitmap, 24);
            bitmap.recycle();

            Constructor<?> sizePairConstructor = templateSizePair.getClass().getDeclaredConstructor(templateVector.getClass(), templateVector.getClass());
            sizePairConstructor.setAccessible(true);
            Object sizePair = sizePairConstructor.newInstance(vector16, vector24);

            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method putObject = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
            Object icon = allocateInstance.invoke(unsafe, templateIcon.getClass());
            for (Field field : templateIcon.getClass().getSuperclass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != templateSizePair.getClass()) continue;
                field.setAccessible(true);
                long offset = (long) objectFieldOffset.invoke(unsafe, field);
                putObject.invoke(unsafe, icon, offset, sizePair);
            }
            return icon;
        } catch (Throwable t) {
            logError("Failed to create side drawer icon for " + assetId);
            logError(t);
            return null;
        }
    }

    private Object createImageVector(Object templateVector, Bitmap source, int size) throws Exception {
        Bitmap bitmap = Bitmap.createScaledBitmap(source, size, size, true);
        boolean hasTransparency = false;
        for (int y = 0; y < size && !hasTransparency; y++) {
            for (int x = 0; x < size; x++) {
                if ((bitmap.getPixel(x, y) >>> 24) < 64) {
                    hasTransparency = true;
                    break;
                }
            }
        }

        int background = bitmap.getPixel(0, 0);
        String packageName = templateVector.getClass().getPackage().getName();
        Class<?> moveClass = findClass(packageName + ".tdc0");
        Class<?> lineClass = findClass(packageName + ".sdc0");
        Class<?> closeClass = findClass(packageName + ".pdc0");
        Constructor<?> moveConstructor = moveClass.getDeclaredConstructor(float.class, float.class);
        Constructor<?> lineConstructor = lineClass.getDeclaredConstructor(float.class, float.class);
        moveConstructor.setAccessible(true);
        lineConstructor.setAccessible(true);
        Field closeField = Arrays.stream(closeClass.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == closeClass)
                .findFirst()
                .orElseThrow();
        closeField.setAccessible(true);
        Object close = closeField.get(null);

        ArrayList<Object> commands = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            int x = 0;
            while (x < size) {
                while (x < size && !isIconPixel(bitmap.getPixel(x, y), background, hasTransparency)) x++;
                if (x >= size) break;
                int start = x;
                while (x < size && isIconPixel(bitmap.getPixel(x, y), background, hasTransparency)) x++;

                commands.add(moveConstructor.newInstance((float) start, (float) y));
                commands.add(lineConstructor.newInstance((float) x, (float) y));
                commands.add(lineConstructor.newInstance((float) x, (float) (y + 1)));
                commands.add(lineConstructor.newInstance((float) start, (float) (y + 1)));
                commands.add(close);
            }
        }
        if (bitmap != source) bitmap.recycle();

        Field rootField = Arrays.stream(templateVector.getClass().getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && Iterable.class.isAssignableFrom(field.getType()))
                .findFirst()
                .orElseThrow();
        rootField.setAccessible(true);
        Object templateRoot = rootField.get(templateVector);
        Field childrenField = Arrays.stream(templateRoot.getClass().getDeclaredFields())
                .filter(field -> ArrayList.class.isAssignableFrom(field.getType()))
                .findFirst()
                .orElseThrow();
        childrenField.setAccessible(true);
        ArrayList<?> templateChildren = (ArrayList<?>) childrenField.get(templateRoot);
        Object templatePath = templateChildren.get(0);

        Constructor<?> pathConstructor = Arrays.stream(templatePath.getClass().getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 14)
                .findFirst()
                .orElseThrow();
        pathConstructor.setAccessible(true);
        Class<?> brushClass = pathConstructor.getParameterTypes()[3];
        Field brushField = Arrays.stream(templatePath.getClass().getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && field.getType() == brushClass)
                .findFirst()
                .orElseThrow();
        brushField.setAccessible(true);
        Object brush = brushField.get(templatePath);
        Object path = pathConstructor.newInstance("", commands, 0, brush, 1.0f, null, 1.0f, 1.0f, 0, 0, 1.0f, 0.0f, 1.0f, 0.0f);

        Constructor<?> rootConstructor = Arrays.stream(templateRoot.getClass().getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 10)
                .findFirst()
                .orElseThrow();
        rootConstructor.setAccessible(true);
        Object root = rootConstructor.newInstance("", 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, Collections.emptyList(), new ArrayList<>(Collections.singletonList(path)));

        Constructor<?> vectorConstructor = Arrays.stream(templateVector.getClass().getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 9)
                .findFirst()
                .orElseThrow();
        vectorConstructor.setAccessible(true);
        return vectorConstructor.newInstance("SpotifyPlus.AssetIcon" + size, (float) size, (float) size, (float) size, (float) size, root, 0L, 0, false);
    }

    private boolean isIconPixel(int color, int background, boolean hasTransparency) {
        int alpha = color >>> 24;
        if (alpha < 64) return false;
        if (hasTransparency) return true;

        int red = Math.abs(((color >> 16) & 0xff) - ((background >> 16) & 0xff));
        int green = Math.abs(((color >> 8) & 0xff) - ((background >> 8) & 0xff));
        int blue = Math.abs((color & 0xff) - (background & 0xff));
        return red + green + blue >= 72;
    }

    private void showSettingsOverlay() {
        try {
            Activity activity = currentActivity.get();
            if (activity == null || activity.isFinishing()) return;
            activity.runOnUiThread(() -> {
//                if (!overlayShown.compareAndSet(false, true)) return;

                ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                AtomicReference<View> currentDetailedSettingsPage = new AtomicReference<>();
                AtomicReference<View> lastfmPopup = new AtomicReference<>();

                View settingsPage = Utils.inflate(activity, R.layout.settings_page, root);
                if (settingsPage == null) {
                    logError("Settings page was null");
                    overlayShown.set(false);
                    return;
                }

                settingsPage.setId(SETTINGS_OVERLAY_ID);
                root.addView(settingsPage);

                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    final android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
                    final android.window.OnBackInvokedCallback callback = new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            View detailedPage = currentDetailedSettingsPage.get();
                            boolean homePage = detailedPage == null;
                            if (homePage) {
                                dispatcher.unregisterOnBackInvokedCallback(this);
                                ViewParent parent = settingsPage.getParent();
                                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(settingsPage);
                                overlayShown.set(false);
                            } else {
                                if (lastfmPopup.get() != null) {
                                    root.removeView(lastfmPopup.get());
                                    lastfmPopup.set(null);
                                    return;
                                }
                                ViewParent parent = settingsPage.getParent();
                                if (parent instanceof ViewGroup) animatePageOut((ViewGroup) parent, () -> {
                                    ((ViewGroup) parent).removeView(detailedPage);
                                    currentDetailedSettingsPage.set(null);
                                });
                            }
                        }
                    };
                    dispatcher.registerOnBackInvokedCallback(1000001, callback);
                }

                MaterialToolbar toolbar = settingsPage.findViewById(R.id.toolbar);
                toolbar.setNavigationOnClickListener(v -> {
                    ViewParent parent = settingsPage.getParent();
                    if (parent instanceof ViewGroup) animatePageOut((ViewGroup) parent, () -> {
                        ((ViewGroup) parent).removeView(settingsPage);
                        overlayShown.set(false);
                    });
                });

                View generalSettings = settingsPage.findViewById(R.id.settings_general);
                View lyricsSettings = settingsPage.findViewById(R.id.settings_lyrics);
//                View experimentalSettings = settingsPage.findViewById(R.id.settings_experimental);
                View aboutSettings = settingsPage.findViewById(R.id.settings_about);

                generalSettings.setOnClickListener(v -> {
                    View view = Utils.inflate(activity, R.layout.general_settings_page, root);
                    if (view == null) return;
                    view.setId(DETAILED_SETTINGS_OVERLAY_ID);
                    root.addView(view);
                    animatePageIn(view);
                    currentDetailedSettingsPage.set(view);

                    MaterialToolbar detailedToolbar = view.findViewById(R.id.general_toolbar);
                    detailedToolbar.setNavigationOnClickListener(w -> {
                        ViewParent parent = settingsPage.getParent();
                        if (parent instanceof ViewGroup)
                            animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                    });

                    MaterialSwitch update = view.findViewById(R.id.switch_check_update);
                    MaterialSwitch create = view.findViewById(R.id.switch_remove_create);
                    MaterialButton lastfm = view.findViewById(R.id.btn_set_lastfm);
                    LinearLayout group = view.findViewById(R.id.current_lastfm_username_group);
                    TextView textView = view.findViewById(R.id.current_lastfm_username_text);
                    MaterialRadioButton home = view.findViewById(R.id.rb_home);
                    MaterialRadioButton search = view.findViewById(R.id.rb_search);
                    MaterialRadioButton explore = view.findViewById(R.id.rb_explore);
                    MaterialRadioButton library = view.findViewById(R.id.rb_library);
                    MaterialSwitch animatedAlbumArt = view.findViewById(R.id.switch_animated_art);
                    MaterialSwitch blockAds = view.findViewById(R.id.switch_block_ads);
                    MaterialSwitch blockTelemetry = view.findViewById(R.id.switch_block_telemetry);

                    update.setOnCheckedChangeListener((check, value) -> {
                        setPref("general_check_update", value);
                        SpotifyPlusSettings.checkForUpdates = value;
                    });

                    create.setOnCheckedChangeListener((check, value) -> {
                        setPref("remove_create", value);
                        SpotifyPlusSettings.removeCreateButton = value;
                    });

                    lastfm.setOnClickListener(button -> {
                        View lastfmThing = Utils.inflate(activity, R.layout.lastfm_username_view, root);
                        if (lastfmThing == null) return;
                        root.addView(lastfmThing);
                        lastfmPopup.set(lastfmThing);

                        FrameLayout background = lastfmThing.findViewById(R.id.lastfm_popup_root);
                        TextInputEditText input = lastfmThing.findViewById(R.id.input_lastfm_username);
                        MaterialButton confirmButton = lastfmThing.findViewById(R.id.btn_submit_lastfm);
                        MaterialButton clearButton = lastfmThing.findViewById(R.id.btn_clear_lastfm);
                        MaterialButton closeButton = lastfmThing.findViewById(R.id.btn_cancel_lastfm);

                        String current = SpotifyPlusSettings.lastfmUsername;
                        if (!"null".equals(current)) input.setText(current);

                        background.setOnClickListener(layout -> {
                            lastfmPopup.set(null);
                            root.removeView(lastfmThing);
                        });

                        confirmButton.setOnClickListener(confirm -> {
                            if (input.getText() == null || input.getText().toString().isEmpty()) return;
                            setPref("last_fm_username", input.getText().toString());
                            SpotifyPlusSettings.lastfmUsername = input.getText().toString();
                            group.setVisibility(LinearLayout.VISIBLE);
                            textView.setText(Utils.getString(activity, R.string.lastfm_info, input.getText()));
                            root.removeView(lastfmThing);
                            lastfmPopup.set(null);
                        });

                        clearButton.setOnClickListener(clear -> {
                            setPref("last_fm_username", "null");
                            SpotifyPlusSettings.lastfmUsername = "null";
                            group.setVisibility(LinearLayout.INVISIBLE);
                            textView.setText("Currently set to ");
                            root.removeView(lastfmThing);
                            lastfmPopup.set(null);
                        });

                        closeButton.setOnClickListener(close -> {
                            root.removeView(lastfmThing);
                            lastfmPopup.set(null);
                        });
                    });

                    home.setOnClickListener(c -> {
                        setPref("startup_page", "HOME");
                        SpotifyPlusSettings.startupPage = SpotifyPlusSettings.StartupPage.HOME;

                        home.setChecked(true);
                        search.setChecked(false);
                        explore.setChecked(false);
                        library.setChecked(false);
                    });

                    search.setOnClickListener(c -> {
                        setPref("startup_page", "SEARCH");
                        SpotifyPlusSettings.startupPage = SpotifyPlusSettings.StartupPage.SEARCH;

                        home.setChecked(false);
                        search.setChecked(true);
                        explore.setChecked(false);
                        library.setChecked(false);
                    });

                    explore.setOnClickListener(c -> {
                        setPref("startup_page", "EXPLORE");
                        SpotifyPlusSettings.startupPage = SpotifyPlusSettings.StartupPage.EXPLORE;

                        home.setChecked(false);
                        search.setChecked(false);
                        explore.setChecked(true);
                        library.setChecked(false);
                    });

                    library.setOnClickListener(c -> {
                        setPref("startup_page", "LIBRARY");
                        SpotifyPlusSettings.startupPage = SpotifyPlusSettings.StartupPage.LIBRARY;

                        home.setChecked(false);
                        search.setChecked(false);
                        explore.setChecked(false);
                        library.setChecked(true);
                    });

                    animatedAlbumArt.setOnCheckedChangeListener((button, value) -> {
                        setPref("animated_art", value);
                        SpotifyPlusSettings.animatedAlbumArtworkEnabled = value;
                    });

                    blockAds.setOnCheckedChangeListener((button, value) -> {
                        setPref("block_ads", value);
                        SpotifyPlusSettings.blockAds = value;
                    });

                    blockTelemetry.setOnCheckedChangeListener((button, value) -> {
                        setPref("block_telemetry", value);
                        SpotifyPlusSettings.blockTelemetry = value;
                    });

                    update.setChecked(SpotifyPlusSettings.checkForUpdates);
                    animatedAlbumArt.setChecked(SpotifyPlusSettings.animatedAlbumArtworkEnabled);
                    blockAds.setChecked(SpotifyPlusSettings.blockAds);
                    blockTelemetry.setChecked(SpotifyPlusSettings.blockTelemetry);

                    group.setVisibility("null".equals(SpotifyPlusSettings.lastfmUsername) ? LinearLayout.INVISIBLE : LinearLayout.VISIBLE);
                    textView.setText("null".equals(SpotifyPlusSettings.lastfmUsername) ? "" : "Currently set to " + SpotifyPlusSettings.lastfmUsername);

                    create.setChecked(SpotifyPlusSettings.removeCreateButton);

                    SpotifyPlusSettings.StartupPage page = SpotifyPlusSettings.startupPage;
                    home.setChecked(page == SpotifyPlusSettings.StartupPage.HOME);
                    search.setChecked(page == SpotifyPlusSettings.StartupPage.SEARCH);
                    explore.setChecked(page == SpotifyPlusSettings.StartupPage.EXPLORE);
                    library.setChecked(page == SpotifyPlusSettings.StartupPage.LIBRARY);
                });

                lyricsSettings.setOnClickListener(v -> {
                    View view = Utils.inflate(activity, R.layout.lyrics_settings_page, root);
                    if (view == null) return;
                    view.setId(DETAILED_SETTINGS_OVERLAY_ID);
                    root.addView(view);
                    animatePageIn(view);
                    currentDetailedSettingsPage.set(view);

                    MaterialToolbar detailedToolbar = view.findViewById(R.id.lyrics_toolbar);
                    detailedToolbar.setNavigationOnClickListener(w -> {
                        ViewParent parent = settingsPage.getParent();
                        if (parent instanceof ViewGroup)
                            animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                    });

                    MaterialRadioButton visualBeautiful = view.findViewById(R.id.rb_beautiful_lyrics_anim);
                    MaterialRadioButton visualApple = view.findViewById(R.id.rb_apple_music_anim);
                    MaterialRadioButton fontSpotify = view.findViewById(R.id.font_spotify);
                    MaterialRadioButton fontBeautifulLyrics = view.findViewById(R.id.font_beautiful_lyrics);
                    MaterialRadioButton fontApple = view.findViewById(R.id.font_apple_music);
                    MaterialRadioButton interludeBeautiful = view.findViewById(R.id.rb_beautiful_lyrics_interlude);
                    MaterialRadioButton interludeSpicy = view.findViewById(R.id.rb_spicy_lyrics_interlude);
                    MaterialRadioButton interludeSpotifyPlus = view.findViewById(R.id.rb_spotify_plus_interlude);
                    MaterialRadioButton interludeApple = view.findViewById(R.id.rb_apple_music_interlude);
                    Slider slider = view.findViewById(R.id.line_spacing_slider);
                    TextView valueLabel = view.findViewById(R.id.line_spacing_value_label);
                    MaterialSwitch background = view.findViewById(R.id.switch_enable_background);
                    MaterialSwitch lineGradient = view.findViewById(R.id.switch_enable_line_gradient);
                    MaterialRadioButton high = view.findViewById(R.id.rb_background_high);
                    MaterialRadioButton mid = view.findViewById(R.id.rb_background_mid);
                    MaterialRadioButton low = view.findViewById(R.id.rb_background_low);
                    MaterialRadioButton superLow = view.findViewById(R.id.rb_background_superlow);

                    visualBeautiful.setOnClickListener(c -> {
                        setPref("lyric_animation_style", "DEFAULT");
                        SpotifyPlusSettings.animationStyle = SpotifyPlusSettings.AnimationStyle.DEFAULT;

                        visualBeautiful.setChecked(true);
                        visualApple.setChecked(false);
                    });

                    visualApple.setOnClickListener(c -> {
                        setPref("lyric_animation_style", "APPLE");
                        SpotifyPlusSettings.animationStyle = SpotifyPlusSettings.AnimationStyle.APPLE;

                        visualBeautiful.setChecked(false);
                        visualApple.setChecked(true);
                    });

                    fontSpotify.setOnClickListener(c -> {
                        setPref("lyrics_font", "SPOTIFY");
                        SpotifyPlusSettings.userSelectedFont = SpotifyPlusSettings.LyricsFont.SPOTIFY;
                        SpotifyPlusSettings.activeFont = "spotifymix-medium.ttf";

                        fontSpotify.setChecked(true);
                        fontBeautifulLyrics.setChecked(false);
                        fontApple.setChecked(false);
                    });

                    fontBeautifulLyrics.setOnClickListener(c -> {
                        setPref("lyrics_font", "DEFAULT");
                        SpotifyPlusSettings.userSelectedFont = SpotifyPlusSettings.LyricsFont.DEFAULT;
                        SpotifyPlusSettings.activeFont = "lyrics_medium.ttf";

                        fontSpotify.setChecked(false);
                        fontBeautifulLyrics.setChecked(true);
                        fontApple.setChecked(false);
                    });

                    fontApple.setOnClickListener(c -> {
                        setPref("lyrics_font", "APPLE");
                        SpotifyPlusSettings.userSelectedFont = SpotifyPlusSettings.LyricsFont.APPLE;
                        SpotifyPlusSettings.activeFont = "sf-pro-display-bold.ttf";

                        fontSpotify.setChecked(false);
                        fontBeautifulLyrics.setChecked(false);
                        fontApple.setChecked(true);
                    });

                    interludeBeautiful.setOnClickListener(c -> {
                        setPref("lyric_interlude_duration", "BEAUTIFUL_LYRICS");
                        SpotifyPlusSettings.interludeDuration = SpotifyPlusSettings.InterludeDuration.BEAUTIFUL_LYRICS;

                        interludeBeautiful.setChecked(true);
                        interludeSpicy.setChecked(false);
                        interludeSpotifyPlus.setChecked(false);
                        interludeApple.setChecked(false);
                    });

                    interludeSpicy.setOnClickListener(c -> {
                        setPref("lyric_interlude_duration", "SPICY");
                        SpotifyPlusSettings.interludeDuration = SpotifyPlusSettings.InterludeDuration.SPICY;

                        interludeBeautiful.setChecked(false);
                        interludeSpicy.setChecked(true);
                        interludeSpotifyPlus.setChecked(false);
                        interludeApple.setChecked(false);
                    });

                    interludeSpotifyPlus.setOnClickListener(c -> {
                        setPref("lyric_interlude_duration", "SPOTIFY_PLUS");
                        SpotifyPlusSettings.interludeDuration = SpotifyPlusSettings.InterludeDuration.SPOTIFY_PLUS;

                        interludeBeautiful.setChecked(false);
                        interludeSpicy.setChecked(false);
                        interludeSpotifyPlus.setChecked(true);
                        interludeApple.setChecked(false);
                    });

                    interludeApple.setOnClickListener(c -> {
                        setPref("lyric_interlude_duration", "APPLE");
                        SpotifyPlusSettings.interludeDuration = SpotifyPlusSettings.InterludeDuration.APPLE;

                        interludeBeautiful.setChecked(false);
                        interludeSpicy.setChecked(false);
                        interludeSpotifyPlus.setChecked(false);
                        interludeApple.setChecked(true);
                    });

                    slider.setThumbRadius(dpToPx(8, activity));
                    slider.setHaloRadius(0);
                    slider.addOnChangeListener((s, value, fromUser) -> {
                        String text;
                        switch (Math.round(value)) {
                            case 0:
                                text = "Compact";
                                setPref("line_spacing", "COMPACT");
                                SpotifyPlusSettings.lineSpacing = SpotifyPlusSettings.LineSpacing.COMPACT;
                                break;
                            case 1:
                                text = "Default";
                                setPref("line_spacing", "DEFAULT");
                                SpotifyPlusSettings.lineSpacing = SpotifyPlusSettings.LineSpacing.DEFAULT;
                                break;
                            case 2:
                                text = "Spacious";
                                setPref("line_spacing", "SPACIOUS");
                                SpotifyPlusSettings.lineSpacing = SpotifyPlusSettings.LineSpacing.SPACIOUS;
                                break;
                            case 3:
                                text = "More Spacious";
                                setPref("line_spacing", "MORE");
                                SpotifyPlusSettings.lineSpacing = SpotifyPlusSettings.LineSpacing.MORE;
                                break;
                            case 4:
                                text = "Max";
                                setPref("line_spacing", "MAX");
                                SpotifyPlusSettings.lineSpacing = SpotifyPlusSettings.LineSpacing.MAX;
                                break;
                            default:
                                text = "";
                                break;
                        }
                        valueLabel.setText(text);
                        slider.post(() -> {
                            float fraction = (value - slider.getValueFrom()) / (slider.getValueTo() - slider.getValueFrom());
                            int sliderWidth = slider.getWidth();
                            int thumbX = (int) (fraction * sliderWidth);
                            valueLabel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                            int labelWidth = valueLabel.getMeasuredWidth();
                            float x = thumbX - (labelWidth / 2f);
                            x = Math.max(0, Math.min(x, sliderWidth - labelWidth));
                            valueLabel.setX(x);
                            valueLabel.setY(dpToPx(-12, activity));
                        });
                    });
                    slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                        @Override
                        public void onStartTrackingTouch(Slider slider) {
                            valueLabel.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onStopTrackingTouch(Slider slider) {
                            valueLabel.setVisibility(View.GONE);
                        }
                    });

                    SpotifyPlusSettings.LineSpacing sliderValueThing = SpotifyPlusSettings.lineSpacing;
                    switch (sliderValueThing) {
                        case COMPACT:
                            slider.setValue(0);
                            break;
                        case SPACIOUS:
                            slider.setValue(2);
                            break;
                        case MORE:
                            slider.setValue(3);
                            break;
                        case MAX:
                            slider.setValue(4);
                            break;
                        default:
                            slider.setValue(1);
                            break;
                    }

                    high.setOnClickListener(c -> {
                        setPref("lyric_background_quality", "HIGH");
                        SpotifyPlusSettings.backgroundQuality = SpotifyPlusSettings.BackgroundQuality.HIGH;

                        high.setChecked(true);
                        mid.setChecked(false);
                        low.setChecked(false);
                        superLow.setChecked(false);
                    });

                    mid.setOnClickListener(c -> {
                        setPref("lyric_background_quality", "MID");
                        SpotifyPlusSettings.backgroundQuality = SpotifyPlusSettings.BackgroundQuality.MID;

                        high.setChecked(false);
                        mid.setChecked(true);
                        low.setChecked(false);
                        superLow.setChecked(false);
                    });

                    low.setOnClickListener(c -> {
                        setPref("lyric_background_quality", "LOW");
                        SpotifyPlusSettings.backgroundQuality = SpotifyPlusSettings.BackgroundQuality.LOW;

                        high.setChecked(false);
                        mid.setChecked(false);
                        low.setChecked(true);
                        superLow.setChecked(false);
                    });

                    superLow.setOnClickListener(c -> {
                        setPref("lyric_background_quality", "SUPER_LOW");
                        SpotifyPlusSettings.backgroundQuality = SpotifyPlusSettings.BackgroundQuality.SUPER_LOW;

                        high.setChecked(false);
                        mid.setChecked(false);
                        low.setChecked(false);
                        superLow.setChecked(true);
                    });

                    background.setOnCheckedChangeListener((button, value) -> {
                        setPref("lyric_enable_background", value);
                        SpotifyPlusSettings.enabledBackground = value;
                    });

                    lineGradient.setOnCheckedChangeListener((button, value) -> {
                        setPref("lyric_enable_line_gradient", value);
                        SpotifyPlusSettings.lineGradient = value;
                    });

                    SpotifyPlusSettings.AnimationStyle style = SpotifyPlusSettings.animationStyle;
                    visualBeautiful.setChecked(style == SpotifyPlusSettings.AnimationStyle.DEFAULT);
                    visualApple.setChecked(style == SpotifyPlusSettings.AnimationStyle.APPLE);

                    SpotifyPlusSettings.LyricsFont font = SpotifyPlusSettings.userSelectedFont;
                    fontSpotify.setChecked(font == SpotifyPlusSettings.LyricsFont.SPOTIFY);
                    fontBeautifulLyrics.setChecked(font == SpotifyPlusSettings.LyricsFont.DEFAULT);
                    fontApple.setChecked(font == SpotifyPlusSettings.LyricsFont.APPLE);

                    SpotifyPlusSettings.InterludeDuration interludeDuration = SpotifyPlusSettings.interludeDuration;
                    interludeBeautiful.setChecked(interludeDuration == SpotifyPlusSettings.InterludeDuration.BEAUTIFUL_LYRICS);
                    interludeSpicy.setChecked(interludeDuration == SpotifyPlusSettings.InterludeDuration.SPICY);
                    interludeSpotifyPlus.setChecked(interludeDuration == SpotifyPlusSettings.InterludeDuration.SPOTIFY_PLUS);
                    interludeApple.setChecked(interludeDuration == SpotifyPlusSettings.InterludeDuration.APPLE);

                    background.setChecked(SpotifyPlusSettings.enabledBackground);
                    lineGradient.setChecked(SpotifyPlusSettings.lineGradient);

                    SpotifyPlusSettings.BackgroundQuality quality = SpotifyPlusSettings.backgroundQuality;
                    high.setChecked(quality == SpotifyPlusSettings.BackgroundQuality.HIGH);
                    mid.setChecked(quality == SpotifyPlusSettings.BackgroundQuality.MID);
                    low.setChecked(quality == SpotifyPlusSettings.BackgroundQuality.LOW);
                    superLow.setChecked(quality == SpotifyPlusSettings.BackgroundQuality.SUPER_LOW);
                });

//                experimentalSettings.setOnClickListener(v -> {
//                    View view = Utils.inflate(activity, R.layout.experimental_settings_page, root);
//                    if (view == null) return;
//                    view.setId(DETAILED_SETTINGS_OVERLAY_ID);
//                    root.addView(view);
//                    animatePageIn(view);
//                    currentDetailedSettingsPage.set(view);
//
//                    MaterialToolbar detailedToolbar = view.findViewById(R.id.experimental_toolbar);
//                    detailedToolbar.setNavigationOnClickListener(w -> {
//                        ViewParent parent = settingsPage.getParent();
//                        if (parent instanceof ViewGroup)
//                            animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
//                    });
//
//                    MaterialSwitch scrollingAnimation = view.findViewById(R.id.switch_new_scroller);
//                    MaterialSwitch newBackground = view.findViewById(R.id.switch_animated_art);
//                    scrollingAnimation.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("experiment_scroll", value).apply());
//                    newBackground.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("experiment_animated_art", value).apply());
//                    scrollingAnimation.setChecked(prefs.getBoolean("experiment_scroll", false));
//                    newBackground.setChecked(prefs.getBoolean("experiment_animated_art", true));
//                });

                aboutSettings.setOnClickListener(v -> {
                    View view = Utils.inflate(activity, R.layout.about_settings_page, root);
                    if (view == null) return;
                    view.setId(DETAILED_SETTINGS_OVERLAY_ID);
                    root.addView(view);
                    animatePageIn(view);
                    currentDetailedSettingsPage.set(view);

                    TextView versionText = view.findViewById(R.id.version_text);
                    versionText.setText(BuildConfig.VERSION_NAME);

                    MaterialToolbar detailedToolbar = view.findViewById(R.id.about_toolbar);
                    detailedToolbar.setNavigationOnClickListener(w -> {
                        ViewParent parent = settingsPage.getParent();
                        if (parent instanceof ViewGroup)
                            animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                    });

                    View github = view.findViewById(R.id.open_github);
                    github.setOnClickListener(button -> activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeNerd46/SpotifyPlus"))));

                    View telegram = view.findViewById(R.id.open_telegram);
                    telegram.setOnClickListener(button -> activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/spotifypluscool"))));

//                    TextView text = view.findViewById(R.id.translate_text);
//                    MaterialButton button = view.findViewById(R.id.translate_button);
//                    button.setOnClickListener(button1 -> {
//                        try {
//                            String originalText = text.getText().toString();
//                            text.setText("Translating...");
//                        } catch (Throwable t) {
//                            logError(t);
//                        }
//                    });
                });
            });
        } catch (Throwable t) {
            overlayShown.set(false);
            logError(t);
        }
    }

    private void animatePageIn(View page) {
        page.setAlpha(0.0f);
        page.animate().alpha(1.0f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void animatePageOut(View page, Runnable onComplete) {
        page.animate().alpha(1.0f).setDuration(150).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(onComplete).start();
    }

//    public void registerSettingSection(String title, int id, SettingItem.SettingSection section) {
//        var key = scriptSettings.keySet().stream().filter(entry -> entry.first.equals(id)).findFirst().orElse(null);
//        if (key == null) scriptSettings.put(Pair.create(id, title), new ArrayList<>(List.of(section)));
//        else {
//            var sections = scriptSettings.get(key);
//            sections.add(section);
//            scriptSettings.put(key, sections);
//        }
//    }

    public void registerSideButton(String title, int id, Runnable onClick) {
        try {
            var key = scriptSideButtons.keySet().stream().filter(entry -> entry.first.equals(id)).findFirst().orElse(null);
            if (key == null) scriptSideButtons.put(Pair.create(id, title), onClick);
        } catch (Throwable t) {
            logError(t);
        }
    }

    private int dpToPx(int dp, Activity activity) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, activity.getResources().getDisplayMetrics());
    }

    private Object getFieldValue(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private Object newInstance(Class<?> cls, Object... args) throws Exception {
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length != args.length) continue;
            boolean ok = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null) continue;
                if (!wrap(types[i]).isAssignableFrom(args[i].getClass())) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        }
        throw new NoSuchMethodException("No constructor found for " + cls.getName());
    }

    private Class<?> wrap(Class<?> cls) {
        if (!cls.isPrimitive()) return cls;
        if (cls == int.class) return Integer.class;
        if (cls == long.class) return Long.class;
        if (cls == boolean.class) return Boolean.class;
        if (cls == float.class) return Float.class;
        if (cls == double.class) return Double.class;
        if (cls == byte.class) return Byte.class;
        if (cls == short.class) return Short.class;
        if (cls == char.class) return Character.class;
        return cls;
    }

    private void returnAndSkip(XposedInterface.BeforeHookCallback callback, Object result) throws Exception {
        try {
            Method method = callback.getClass().getMethod("returnAndSkip", Object.class);
            method.invoke(callback, result);
        } catch (NoSuchMethodException ignored) {
            Method method = callback.getClass().getMethod("setResult", Object.class);
            method.invoke(callback, result);
        }
    }

    private void setPref(String key, Object value) {
        Intent intent = new Intent("com.lenerd.spotifyplus.SET_PREF");
        intent.setPackage("com.lenerd.spotifyplus");
        intent.putExtra("key", key);

        Activity activity = currentActivity.get();
        if (activity == null) return;

        if (value instanceof Boolean) {
            SettingsSync.putBooleanLocal(activity, key, (Boolean) value);
            intent.putExtra("type", "boolean");
            intent.putExtra("value", (Boolean) value);
        } else if (value instanceof Integer) {
            intent.putExtra("type", "int");
            intent.putExtra("value", (Integer) value);
        } else if (value instanceof Long) {
            intent.putExtra("type", "long");
            intent.putExtra("value", (Long) value);
        } else if (value instanceof Float) {
            intent.putExtra("type", "float");
            intent.putExtra("value", (Float) value);
        } else if (value instanceof String) {
            SettingsSync.putStringLocal(activity, key, (String) value);
            intent.putExtra("type", "string");
            intent.putExtra("value", (String) value);
        }
    }
}

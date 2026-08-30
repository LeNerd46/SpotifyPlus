package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.*;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lenerd46.spotifyplus.*;
import com.lenerd46.spotifyplus.beautifullyrics.translation.LyricsTranslationService;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.MatchType;
import org.luckypray.dexkit.query.matchers.*;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RemoveCreateButtonHook extends SpotifyHook {
    private static final int SETTINGS_OVERLAY_ID = 0x53504c53;

    private static final int DETAILED_SETTINGS_OVERLAY_ID = 0x53504c54;
    private static final int MARKETPLACE_OVERLAY_ID = 0x53504c55;
    private int idToUse = 8001;
    private int resourceIdToUse = 2131957895;
    private SharedPreferences prefs;
    private final Context context;
    private boolean isNewSideDrawer = false;

    private ClassDataList fwd0Classes;
    private ClassDataList dwd0Classes;
    private ClassDataList propertiesClasses;
    private ClassDataList onClickClasses;
    private Class<?> whateverThisInterfaceDoes;
    private Class<?> iconInterface;
    private Class<?> wwk;
    private final static ConcurrentHashMap<Pair<Integer, String>, List<SettingItem.SettingSection>> scriptSettings = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<Pair<Integer, String>, Runnable> scriptSideButtons = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean overlayShown = new java.util.concurrent.atomic.AtomicBoolean(false);

    public RemoveCreateButtonHook(final Context context) {
        this.context = context;
    }

    @Override
    protected void hook() {
        try {
            if (prefs == null) {
                prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
            }

            // var clazz =
            // bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("tracks_section",
            // "footer_section",
            // "location").fieldCount(3).methodCount(2))).get(0).getInstance(lpparm.classLoader).getInterfaces()[0];
            //
            // var testThing =
            // bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC
            // |
            // Modifier.FINAL).superClass(ClassMatcher.create()).methods(MethodsMatcher.create().count(3)
            // .add(MethodMatcher.create().modifiers(Modifier.PUBLIC |
            // Modifier.FINAL).returnType(boolean.class).params(ParametersMatcher.create().add(Object.class)).name("equals"))
            // .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0).usingNumbers(31,
            // 0))
            // .add(MethodMatcher.create().name("<init>").paramCount(3))
            // ).fields(FieldsMatcher.create().count(3)
            // .add(FieldMatcher.create().type(Object.class))
            // .add(FieldMatcher.create().type(clazz))
            // )));
            //
            // XposedBridge.log("[SpotifyPlus] Test Thing Count: " +
            // testThing.toArray().length);
            // testThing.forEach(x -> XposedBridge.log("[SpotifyPlus] " + x.getName()));

            var constructorClassList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItemSet(item1=")));
            var parameterClassList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItem(icon=").methodCount(4).fieldCount(5, 6)));
            if (constructorClassList.isEmpty() || parameterClassList.isEmpty()) {
                XposedBridge.log("[SpotifyPlus] Constructor class not found");
            } else {
                var constructorClass = constructorClassList.get(0).getInstance(lpparm.classLoader);
                var parameterClass = parameterClassList.get(0).getInstance(lpparm.classLoader);

                XposedHelpers.findAndHookConstructor(constructorClass, parameterClass, parameterClass, parameterClass,
                        parameterClass, parameterClass, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (prefs.getBoolean("remove_create", false)) {
                                    for (int i = 0; i < 5; i++) {
                                        var item = param.args[i];

                                        if (item == null) {
                                            continue;
                                        }

                                        String content = item.toString().toLowerCase();

                                        if (content.contains("create") || content.contains("premium")) {
                                            XposedBridge.log("[SpotifyPlus] Removing navbar item: " + content);
                                            param.args[i] = null;
                                        }
                                    }
                                }
                            }
                        });
            }

            SpotifyTitleOverride.install();

            // var list =
            // bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify:artist:",
            // "Failed requirement.", "spotify:concept:", "spotify:list:",
            // "podcast-chapters", "spotify:show:")));
            // Class<?> clazz = list.get(0).getInstance(lpparm.classLoader);

            Class<?> main = XposedHelpers.findClass("com.spotify.music.SpotifyMainActivity", lpparm.classLoader);
            XposedBridge.hookAllMethods(main, "onNewIntent", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.app.Activity act = (android.app.Activity) param.thisObject;
                    Intent it = (Intent) param.args[0];
                    if (it != null && it.getStringExtra("spx") != null
                            && it.getStringExtra("spx").startsWith("spotifyplus:")) {
                        act.runOnUiThread(() -> {
                        });
                    } else {
                        act.runOnUiThread(() -> {
                            android.view.View v = act.getWindow().getDecorView().findViewById(SETTINGS_OVERLAY_ID);
                            android.view.View detailed = act.getWindow().getDecorView()
                                    .findViewById(DETAILED_SETTINGS_OVERLAY_ID);

                            if (detailed != null) {
                                ((android.view.ViewGroup) v.getParent()).removeView(detailed);
                                ((android.view.ViewGroup) v.getParent()).removeView(v);

                                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:settings"));
                                i.putExtra("spx", "spotifyplus");
                                i.setClassName("com.spotify.music", "com.spotify.music.SpotifyMainActivity");
                                i.putExtra("is_internal_navigation", true);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                act.startActivity(i);
                            } else if (v != null) {
                                ((android.view.ViewGroup) v.getParent()).removeView(v);
                                overlayShown.set(false);
                            }
                        });
                    }
                }
            });

            var modifyDataListClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).methodCount(3)
                    .fields(FieldsMatcher.create()
                            .count(4)
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(int.class))
                            .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class)))));

            // var things =
            // bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC
            // |
            // Modifier.FINAL).interfaceCount(1).methodCount(3).fields(FieldsMatcher.create()
            // .count(4)
            // .add(FieldMatcher.create().modifiers(Modifier.PUBLIC |
            // Modifier.FINAL).type(int.class))
            // .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(int.class))
            // .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class))
            // )));

            // var things =
            // bridge.findMethod(FindMethod.create().searchInClass(modifyDataListClass).matcher(MethodMatcher.create().returnType(Object.class).modifiers(Modifier.PUBLIC
            // | Modifier.FINAL).paramCount(1).paramTypes(Object.class)));
            // XposedBridge.log("[SpotifyPlus] Test Thing Count: " +
            // things.toArray().length);
            // things.forEach(x -> XposedBridge.log("[SpotifyPlus] " +
            // x.getDeclaredClassName()));

            var methodsThing = bridge.findMethod(FindMethod.create().searchInClass(modifyDataListClass)
                    .matcher(MethodMatcher.create().returnType(Object.class).modifiers(Modifier.PUBLIC | Modifier.FINAL)
                            .paramCount(1).paramTypes(Object.class)));
            List<Method> invokeSuspendMethods = new ArrayList<>();
            for (var methodData : methodsThing) {
                Method method = methodData.getMethodInstance(lpparm.classLoader);
                if (!invokeSuspendMethods.contains(method)) invokeSuspendMethods.add(method);
            }
            XposedBridge.log("[SpotifyPlus] Side-drawer array mutation candidates: " + invokeSuspendMethods.stream().map(method -> method.getDeclaringClass().getName() + "#" + method.getName()).collect(java.util.stream.Collectors.joining(", ")));

            var whateverInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("quick_add_to_playlist_item")));
            var iconInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("getState(Lcom/spotify/alignedcuration/firstsave/page/contents/DefaultSaveDestinationElement$Props;)Lkotlinx/coroutines/flow/Flow;")));
            var wwkList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Encore.Vector.CopyAlt16")));
            dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("SideDrawerListItem(element=")));
            if (dwd0Classes.isEmpty())
                dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("SideDrawerListItem(content=")));
            if (dwd0Classes.isEmpty()) {
                // They removed all of the toString() methods in later versions??? This makes it
                // extremely hard to track down
                dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0)
                        .modifiers(Modifier.PUBLIC | Modifier.FINAL).superClass(ClassMatcher.create())
                        .methods(MethodsMatcher.create()
                                .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                        .returnType(boolean.class).params(ParametersMatcher.create().add(Object.class))
                                        .name("equals"))
                                .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0))
                                .add(MethodMatcher.create().name("<init>").paramCount(1))
                                .add(MethodMatcher.create().name("<init>").paramCount(1))
                                .add(MethodMatcher.create().name("<init>").paramCount(7)))
                        .fieldCount(1)));
            }

            fwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL)
                    .fields(FieldsMatcher.create().count(2).add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))).usingStrings("ListItem(id=")));
            if (fwd0Classes.isEmpty() && dwd0Classes.size() == 1) {
                // They removed all of the toString() methods in later versions??? This makes it
                // extremely hard to track down
                fwd0Classes = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                .superClass(ClassMatcher.create()).methods(MethodsMatcher.create().count(3)
                                        .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                                .returnType(boolean.class)
                                                .params(ParametersMatcher.create().add(Object.class)).name("equals"))
                                        .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0)
                                                .usingNumbers(31))
                                        .add(MethodMatcher.create().name("<init>").paramCount(2)))
                                .fields(FieldsMatcher.create().count(2)
                                        .add(FieldMatcher.create().type(int.class))
                                        .add(FieldMatcher.create()
                                                .type(dwd0Classes.get(0).getInstance(lpparm.classLoader))))));
            }

            propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Props(icon=", ", title=", ", titleRes=", ", uriToNavigate=", ", isNew=", ", instrumentation=", ", hasNotification=")));
            if (propertiesClasses.isEmpty())
                propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Navigation(icon=", "title=null", "uriToNavigate=", "isNew=", "instrumentation=")));
            if (propertiesClasses.isEmpty()) {
                // They removed all of the toString() methods in later versions??? This makes it
                // extremely hard to track down
                propertiesClasses = bridge
                        .findClass(FindClass.create().matcher(ClassMatcher
                                .create().interfaceCount(1).methods(MethodsMatcher.create()
                                        .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                                .returnType(boolean.class)
                                                .params(ParametersMatcher.create().add(Object.class)).name("equals"))
                                        .add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(
                                                0).usingNumbers(961, 1231, 1237)))
                                .fields(FieldsMatcher.create().count(6)
                                        .add(FieldMatcher.create().type(Integer.class)
                                                .modifiers(Modifier.PUBLIC | Modifier.FINAL))
                                        .add(FieldMatcher.create().type(String.class)
                                                .modifiers(Modifier.PUBLIC | Modifier.FINAL))
                                        .add(FieldMatcher.create().type(boolean.class)
                                                .modifiers(Modifier.PUBLIC | Modifier.FINAL)))));
            }
            if (propertiesClasses.isEmpty() && dwd0Classes.size() == 1 && dwd0Classes.get(0).getFieldCount() == 1) {
                ClassData drawerContentInterface = dwd0Classes.get(0).getFields().get(0).getType();
                propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().addInterface(drawerContentInterface.getName()).fieldCount(6).addFieldForType(Integer.class).addFieldForType(String.class).addFieldForType(boolean.class).addMethod(MethodMatcher.create().name("<init>").paramCount(6))));
            }

            onClickClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Instrumentation(node=", ", onClick=", ", onImpression=").fieldCount(3)));
            if (onClickClasses.isEmpty()) {
                // They removed all of the toString() methods in later versions??? This makes it
                // extremely hard to track down
                var instrumentationNodeClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("tracks_section", "footer_section", "location").fieldCount(3).methodCount(2)));
                if (instrumentationNodeClasses.size() == 1 && instrumentationNodeClasses.get(0).getInstance(lpparm.classLoader).getInterfaces().length > 0) {
                    Class<?> interfaceToUse = instrumentationNodeClasses.get(0).getInstance(lpparm.classLoader).getInterfaces()[0];
                    onClickClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL).superClass(ClassMatcher.create()).methods(MethodsMatcher.create().count(3).add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).params(ParametersMatcher.create().add(Object.class)).name("equals")).add(MethodMatcher.create().name("hashCode").returnType(int.class).paramCount(0).usingNumbers(31, 0)).add(MethodMatcher.create().name("<init>").paramCount(3))).fields(FieldsMatcher.create().count(3).add(FieldMatcher.create().type(Object.class)).add(FieldMatcher.create().type(interfaceToUse)))));
                }
            }
            if (onClickClasses.isEmpty() && propertiesClasses.size() == 1) {
                List<ClassData> instrumentationFieldTypes = propertiesClasses.get(0).getFields().stream().map(fieldData -> fieldData.getType()).filter(type -> type.getFieldCount() == 3).filter(type -> type.getMethods().stream().anyMatch(methodData -> methodData.isConstructor() && methodData.getParamCount() == 3)).filter(type -> type.getMethods().stream().anyMatch(methodData -> methodData.getName().equals("equals") && methodData.getParamCount() == 1)).filter(type -> type.getMethods().stream().anyMatch(methodData -> methodData.getName().equals("hashCode") && methodData.getParamCount() == 0)).distinct().collect(java.util.stream.Collectors.toList());
                if (instrumentationFieldTypes.size() == 1) onClickClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().className(instrumentationFieldTypes.get(0).getName())));
                else XposedBridge.log("[SpotifyPlus] Could not derive side-drawer instrumentation from props fields; candidates: " + instrumentationFieldTypes.stream().map(ClassData::getName).collect(java.util.stream.Collectors.joining(", ")));
            }

            var qbpInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .modifiers(Modifier.FINAL, MatchType.Equals).interfaceCount(1)
                    .fields(FieldsMatcher.create().add(FieldMatcher.create().type(int.class)).count(2))
                    .methods(MethodsMatcher.create()
                            .count(4)
                            .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                    .returnType(Object.class).name("invoke").paramTypes(Object.class, Object.class))
                            .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL)
                                    .returnType(Object.class).name("invokeSuspend").paramTypes(Object.class)))));

            var zpj0InterfaceList = bridge
                    .findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("premium_row")));
            var cbpInterfaceList = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("video_surface_view_seek_frame_tag")));

            if (invokeSuspendMethods.isEmpty()) {
                XposedBridge.log("[SpotifyPlus] whatever interface: " + whateverInterfaceList.size());
                XposedBridge.log("[SpotifyPlus] icon interface: " + iconInterfaceList.size());
                XposedBridge.log("[SpotifyPlus] wwk: " + wwkList.size());
                XposedBridge.log("[SpotifyPlus] fwd0: " + fwd0Classes.size());
                XposedBridge.log("[SpotifyPlus] dwd0: " + dwd0Classes.size());
                XposedBridge.log("[SpotifyPlus] props: " + propertiesClasses.size());
                XposedBridge.log("[SpotifyPlus] onClick: " + onClickClasses.size());
                XposedBridge.log("[SpotifyPlus] qbp interface: " + qbpInterfaceList.size());
                XposedBridge.log("[SpotifyPlus] zpj0 interface: " + zpj0InterfaceList.size());
                XposedBridge.log("[SpotifyPlus] cbpInterface interface: " + cbpInterfaceList.size());

                XposedBridge.log("[SpotifyPlus] Could not identify a side-drawer array mutation coroutine.");
                return;
            } else {
                XposedBridge.log("[SpotifyPlus] Side-drawer runtime cloning enabled; legacy fingerprints: wrapper=" + fwd0Classes.size() + ", content=" + dwd0Classes.size() + ", props=" + propertiesClasses.size() + ", instrumentation=" + onClickClasses.size());
            }

            // Class<?> cbpInterface =
            // .get(0).getInstance(lpparm.classLoader).getInterfaces()[0];

            // for(var interlace : modifyDataListClass) {
            // XposedBridge.log("[SpotifyPlus] Found Class: " + interlace);
            // }

            for (Method invokeSuspend : invokeSuspendMethods) XposedBridge.hookMethod(invokeSuspend, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Field a =
                    // bridge.findField(FindField.create().searchInClass(modifyDataListClass).matcher(FieldMatcher.create().modifiers(Modifier.PUBLIC
                    // |
                    // Modifier.FINAL).type(int.class))).get(0).getFieldInstance(lpparm.classLoader);
                    Field d = bridge
                            .findField(FindField.create()
                                    .searchInClass(Collections.singletonList(bridge.getClassData(param.thisObject.getClass())))
                                    .matcher(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class)))
                            .get(0).getFieldInstance(lpparm.classLoader);

                    // int number = a.getInt(param.thisObject);
                    // if(number != 20) return;

                    Object[] originalItemsWithNull = (Object[]) d.get(param.thisObject);
                    if (originalItemsWithNull == null)
                        return;
                    Object[] originalItems = Arrays.stream(originalItemsWithNull).filter(Objects::nonNull)
                            .toArray(Object[]::new);

                    // This should work in theory. Spotify seems to keep changing the amount of
                    // buttons, sooo
                    if (originalItems.length < 4) return;
                    if (Arrays.stream(originalItems).anyMatch(item -> containsDrawerDestination(item, 4, new IdentityHashMap<>(), "spotify:null"))) return;
                    Class<?> runtimeButtonClass = originalItems[0].getClass();
                    if (Arrays.stream(originalItems).anyMatch(item -> !runtimeButtonClass.isInstance(item))) return;
                    int settingsItemIndex = findSettingsItemIndex(originalItems);
                    if (settingsItemIndex < 0) return;
                    int customItemIndex = settingsItemIndex + 1;
                    Object tempalte = originalItems[settingsItemIndex];
                    Object runtimeSideDrawerItem = findDirectChildContainingSettings(tempalte);
                    Object runtimeProperties = findDirectChildContainingSettings(runtimeSideDrawerItem);
                    if (runtimeSideDrawerItem == null || runtimeProperties == null) {
                        XposedBridge.log("[SpotifyPlus] Could not identify the runtime side-drawer wrapper and Settings properties.");
                        return;
                    }
                    Object newArray = Array.newInstance(runtimeButtonClass, originalItems.length + 1 + scriptSideButtons.size());

                    for (int i = 0; i < originalItems.length; i++) {
                        Array.set(newArray, i < customItemIndex ? i : i + 1, originalItems[i]);
                    }

                    Object tempalteLightning = tempalte;

                    Object settingsButton = createSideDrawerButton("Spotify Plus Settings", tempalte, 2131957897, () -> {
                        try {
                            XModuleResources modResources = References.modResources;
                            Activity activity = References.currentActivity;
                            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                            AtomicReference<View> currentDetailedSettingsPage = new AtomicReference<>();

                            int themeOverlay = R.style.Theme_SpotifyPlus;
                            Context themedCtx = new ModuleContextWrapper(activity.getApplicationContext(), themeOverlay, modResources, ModuleContextWrapper.class.getClassLoader());
                            LayoutInflater inflater = LayoutInflater.from(activity.getApplicationContext()).cloneInContext(themedCtx);
                            View settingsPage = inflater.inflate(R.layout.settings_page, root, false);
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
                                            if (parent instanceof ViewGroup) {
                                                ((ViewGroup) parent).removeView(settingsPage);
                                            }
                                            overlayShown.set(false);

                                            // try {
                                            // activity.getWindow().getDecorView().post(() -> {
                                            // dispatcher.registerOnBackInvokedCallback(1000001, this);
                                            // });
                                            // } catch(Throwable t) { }
                                        } else {
                                            ViewParent parent = settingsPage.getParent();
                                            if (parent instanceof ViewGroup) {
                                                animatePageOut((ViewGroup) parent, () -> {
                                                    ((ViewGroup) parent).removeView(detailedPage);
                                                    currentDetailedSettingsPage.set(null);
                                                });
                                            }
                                        }
                                    }
                                };

                                try {
                                    dispatcher.registerOnBackInvokedCallback(
                                            OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
                                } catch (Exception e) {
                                    XposedBridge.log(e);
                                }
                            }

                            MaterialToolbar toolbar = settingsPage.findViewById(R.id.toolbar);
                            toolbar.setNavigationOnClickListener(v -> {
                                ViewParent parent = settingsPage.getParent();
                                if (parent instanceof ViewGroup) {
                                    animatePageOut((ViewGroup) parent, () -> {
                                        ((ViewGroup) parent).removeView(settingsPage);
                                        overlayShown.set(false);
                                    });
                                }
                            });

                            View generalSettings = settingsPage.findViewById(R.id.settings_general);
                            View lyricsSettings = settingsPage.findViewById(R.id.settings_lyrics);
                            View themeSettings = settingsPage.findViewById(R.id.settings_theme);
                            View experimentalSettings = settingsPage.findViewById(R.id.settings_experimental);
                            // View scriptingSettings = settingsPage.findViewById(R.id.settings_scripting);
                            View aboutSettings = settingsPage.findViewById(R.id.settings_about);

                            generalSettings.setOnClickListener(v -> {
                                View view = inflater.inflate(R.layout.general_settings_page, root, false);
                                root.addView(view);
                                animatePageIn(view);
                                currentDetailedSettingsPage.set(view);

                                MaterialToolbar detailedToolbar = view.findViewById(R.id.general_toolbar);
                                detailedToolbar.setNavigationOnClickListener(w -> {
                                    ViewParent parent = settingsPage.getParent();
                                    if (parent instanceof ViewGroup) {
                                        animatePageOut((ViewGroup) parent, () -> {
                                            ((ViewGroup) parent).removeView(view);
                                        });
                                    }
                                });

                                MaterialSwitch update = view.findViewById(R.id.switch_check_update);
                                MaterialSwitch create = view.findViewById(R.id.switch_remove_create);

                                update.setOnCheckedChangeListener((check, value) -> {
                                    prefs.edit().putBoolean("general_check_updates", value).apply();
                                });

                                MaterialButton lastfm = view.findViewById(R.id.btn_set_lastfm);
                                LinearLayout group = view.findViewById(R.id.current_lastfm_username_group);
                                TextView textView = view.findViewById(R.id.current_lastfm_username_text);

                                lastfm.setOnClickListener(button -> {
                                    try {
                                        int themeOverlayLast = R.style.Theme_SpotifyPlus;
                                        Context themedCtxLast = new ModuleContextWrapper(activity.getApplicationContext(), themeOverlayLast, modResources, ModuleContextWrapper.class.getClassLoader());
                                        LayoutInflater inflaterLast = LayoutInflater.from(activity.getApplicationContext()).cloneInContext(themedCtxLast);

                                        View lastfmThing = inflaterLast.inflate(modResources.getIdentifier("lastfm_username_view", "layout", "com.lenerd46.spotifyplus"), null, false);
                                        SpotifyBottomSheet sheet = new SpotifyBottomSheet(lpparm.classLoader, activity);
                                        sheet.create(lastfmThing);

                                        TextInputEditText input = lastfmThing.findViewById(modResources.getIdentifier("input_lastfm_username", "id", "com.lenerd46.spotifyplus"));
                                        MaterialButton confirmButton = lastfmThing.findViewById(modResources.getIdentifier("btn_submit_lastfm", "id", "com.lenerd46.spotifyplus"));
                                        MaterialButton clearButton = lastfmThing.findViewById(modResources.getIdentifier("btn_clear_lastfm", "id", "com.lenerd46.spotifyplus"));
                                        MaterialButton closeButton = lastfmThing.findViewById(modResources.getIdentifier("btn_cancel_lastfm", "id", "com.lenerd46.spotifyplus"));

                                        if (!prefs.getString("last_fm_username", "null").equals("null")) {
                                            input.setText(prefs.getString("last_fm_username", "null"));
                                        }

                                        confirmButton.setOnClickListener(confirm -> {
                                            if (input.getText().toString().isEmpty())
                                                return;

                                            prefs.edit().putString("last_fm_username", input.getText().toString()).apply();

                                            group.setVisibility(LinearLayout.VISIBLE);
                                            textView.setText("Currently set to " + input.getText().toString());
                                            XposedHelpers.callMethod(sheet, "dismiss");
                                        });

                                        clearButton.setOnClickListener(clear -> {
                                            prefs.edit().putString("last_fm_username", "null").apply();

                                            group.setVisibility(LinearLayout.INVISIBLE);
                                            textView.setText("Currently set to ");
                                            sheet.dismiss();
                                        });

                                        closeButton.setOnClickListener(close -> {
                                            sheet.dismiss();
                                        });
                                    } catch (Throwable t) {
                                        XposedBridge.log(t);
                                    }
                                });

                                MaterialSwitch blockAds = view.findViewById(R.id.switch_block_ads);
                                blockAds.setOnCheckedChangeListener((check, value) -> prefs.edit().putBoolean("block_ads", value).apply());

                                blockAds.setChecked(prefs.getBoolean("block_ads", false));

                                MaterialSwitch privateSession = view.findViewById(R.id.switch_private_session);
                                privateSession.setOnCheckedChangeListener((check, value) -> prefs.edit().putBoolean("private_session", value).apply());

                                privateSession.setChecked(prefs.getBoolean("private_session", false));
                                create.setOnCheckedChangeListener((check, value) -> prefs.edit().putBoolean("remove_create", value).apply());

                                MaterialButton manageSleepTimers = view.findViewById(R.id.btn_manage_timers);

                                manageSleepTimers.setOnClickListener(managerView -> {
                                    try {
                                        int themeOverlayLast = R.style.Theme_SpotifyPlus;
                                        Context themedCtxLast = new ModuleContextWrapper(activity.getApplicationContext(), themeOverlayLast, modResources, ModuleContextWrapper.class.getClassLoader());
                                        LayoutInflater inflaterLast = LayoutInflater.from(activity.getApplicationContext()).cloneInContext(themedCtxLast);

                                        View timerViews = inflaterLast.inflate(modResources.getIdentifier("manage_sleep_timers_view", "layout", "com.lenerd46.spotifyplus"), null, false);
                                        SpotifyBottomSheet sheet = new SpotifyBottomSheet(lpparm.classLoader, activity);
                                        sheet.create(timerViews);

                                        MaterialSwitch autoReorderSwitch = timerViews.findViewById(modResources.getIdentifier("switch_sleep_timer_auto_reorder", "id", "com.lenerd46.spotifyplus"));
                                        TextView hintView = timerViews.findViewById(modResources.getIdentifier("sleep_timer_presets_hint", "id", "com.lenerd46.spotifyplus"));
                                        RecyclerView recycler = timerViews.findViewById(modResources.getIdentifier("recycler_sleep_timer_presets", "id", "com.lenerd46.spotifyplus"));
                                        TextView emptyView = timerViews.findViewById(modResources.getIdentifier("sleep_timer_presets_empty", "id", "com.lenerd46.spotifyplus"));
                                        View saveButton = timerViews.findViewById(modResources.getIdentifier("btn_save_sleep_timer_presets", "id", "com.lenerd46.spotifyplus"));
                                        View cancelButton = timerViews.findViewById(modResources.getIdentifier("btn_cancel_sleep_timer_presets", "id", "com.lenerd46.spotifyplus"));

                                        ArrayList<SleepTimerHook.SleepTimerInfo> presets = loadSleepTimerPresets(prefs);

                                        boolean[] autoReorder = {prefs.getBoolean("custom_sleep_timers_auto_reorder", true)};

                                        if (autoReorder[0]) {
                                            sortSleepTimerPresets(presets);
                                        }

                                        autoReorderSwitch.setChecked(autoReorder[0]);
                                        hintView.setText(autoReorder[0] ? "Manual reordering is disabled while auto reorder is enabled." : "Hold and drag a preset to reorder it.");

                                        SleepTimerPresetAdapter adapter = new SleepTimerPresetAdapter(themedCtxLast, modResources, inflaterLast, presets, () -> {
                                            boolean empty = presets.isEmpty();
                                            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                                            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                                        });

                                        recycler.setLayoutManager(new LinearLayoutManager(themedCtxLast));
                                        recycler.setAdapter(adapter);

                                        autoReorderSwitch.setOnCheckedChangeListener((button, checked) -> {
                                            autoReorder[0] = checked;

                                            if (checked) {
                                                sortSleepTimerPresets(presets);
                                                adapter.notifyDataSetChanged();
                                            }

                                            hintView.setText(checked ? "Manual reordering is disabled while auto reorder is enabled." : "Hold and drag a preset to reorder it.");
                                        });

                                        boolean empty = presets.isEmpty();
                                        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                                        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

                                        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                                            @Override
                                            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                                                if (autoReorder[0])
                                                    return false;

                                                int fromPos = from.getBindingAdapterPosition();
                                                int toPos = to.getBindingAdapterPosition();

                                                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION)
                                                    return false;

                                                Collections.swap(presets, fromPos, toPos);
                                                adapter.notifyItemMoved(fromPos, toPos);
                                                return true;
                                            }

                                            @Override
                                            public void onSwiped(
                                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                                    int direction) {
                                            }

                                            @Override
                                            public boolean isLongPressDragEnabled() {
                                                return !autoReorder[0];
                                            }
                                        });

                                        helper.attachToRecyclerView(recycler);

                                        saveButton.setOnClickListener(save -> {
                                            if (autoReorder[0]) {
                                                sortSleepTimerPresets(presets);
                                            }

                                            prefs.edit().putBoolean("custom_sleep_timers_auto_reorder", autoReorder[0]).apply();
                                            saveSleepTimerPresets(prefs, presets);
                                            sheet.dismiss();
                                        });

                                        cancelButton.setOnClickListener(cancel -> sheet.dismiss());

                                        timerViews.setOnClickListener(timer -> sheet.dismiss());
                                    } catch (Throwable t) {
                                        XposedBridge.log(t);
                                    }
                                });

                                MaterialRadioButton home = view.findViewById(R.id.rb_home);
                                MaterialRadioButton search = view.findViewById(R.id.rb_search);
                                MaterialRadioButton explore = view.findViewById(R.id.rb_explore);
                                MaterialRadioButton library = view.findViewById(R.id.rb_library);

                                home.setOnClickListener(c -> {
                                    prefs.edit().putString("startup_page", "HOME").apply();

                                    home.setChecked(true);
                                    search.setChecked(false);
                                    explore.setChecked(false);
                                    library.setChecked(false);
                                });

                                search.setOnClickListener(c -> {
                                    prefs.edit().putString("startup_page", "SEARCH").apply();

                                    home.setChecked(false);
                                    search.setChecked(true);
                                    explore.setChecked(false);
                                    library.setChecked(false);
                                });

                                explore.setOnClickListener(c -> {
                                    prefs.edit().putString("startup_page", "EXPLORE").apply();

                                    home.setChecked(false);
                                    search.setChecked(false);
                                    explore.setChecked(true);
                                    library.setChecked(false);
                                });

                                library.setOnClickListener(c -> {
                                    prefs.edit().putString("startup_page", "LIBRARY").apply();

                                    home.setChecked(false);
                                    search.setChecked(false);
                                    explore.setChecked(false);
                                    library.setChecked(true);
                                });

                                update.setChecked(prefs.getBoolean("general_check_updates", true));
                                group.setVisibility(prefs.getString("last_fm_username", "null").equals("null") ? LinearLayout.INVISIBLE : LinearLayout.VISIBLE);
                                textView.setText(prefs.getString("last_fm_username", "null").equals("null") ? "" : "Currently set to " + prefs.getString("last_fm_username", "null"));
                                create.setChecked(prefs.getBoolean("remove_create", false));

                                String page = prefs.getString("startup_page", "HOME");
                                home.setChecked(page.equals("HOME"));
                                search.setChecked(page.equals("SEARCH"));
                                explore.setChecked(page.equals("EXPLORE"));
                                library.setChecked(page.equals("LIBRARY"));
                            });

                            lyricsSettings.setOnClickListener(v -> {
                                View view = inflater.inflate(R.layout.beautiful_lyrics_settings_page, root, false);
                                root.addView(view);
                                animatePageIn(view);
                                currentDetailedSettingsPage.set(view);

                                MaterialToolbar detailedToolbar = view.findViewById(R.id.lyrics_toolbar);
                                detailedToolbar.setNavigationOnClickListener(w -> {
                                    ViewParent parent = settingsPage.getParent();
                                    if (parent instanceof ViewGroup) {
                                        animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                                    }
                                });

                                MaterialRadioButton visualBeautiful = view.findViewById(R.id.rb_beautiful_lyrics_anim);
                                MaterialRadioButton visualApple = view.findViewById(R.id.rb_apple_music_anim);

                                visualBeautiful.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_animation_style", "Beautiful Lyrics").apply();

                                    visualBeautiful.setChecked(true);
                                    visualApple.setChecked(false);
                                });

                                visualApple.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_animation_style", "Apple Music").apply();

                                    visualBeautiful.setChecked(false);
                                    visualApple.setChecked(true);
                                });

                                MaterialRadioButton fontSpotify = view.findViewById(R.id.font_spotify);
                                MaterialRadioButton fontBeautifulLyrics = view.findViewById(R.id.font_beautiful_lyrics);
                                MaterialRadioButton fontApple = view.findViewById(R.id.font_apple_music);

                                fontSpotify.setOnClickListener(c -> {
                                    try {
                                        References.beautifulFont = new WeakReference<>(Typeface.createFromAsset(modResources.getAssets(), "fonts/spotifymix-medium.ttf"));
                                        prefs.edit().putString("lyrics_font", "spotify").apply();

                                        fontSpotify.setChecked(true);
                                        fontBeautifulLyrics.setChecked(false);
                                        fontApple.setChecked(false);
                                    } catch (Exception e) {
                                        Toast.makeText(activity, "Failed to change font", Toast.LENGTH_SHORT).show();
                                    }
                                });

                                fontBeautifulLyrics.setOnClickListener(c -> {
                                    try {
                                        References.beautifulFont = new WeakReference<>(Typeface.createFromAsset(modResources.getAssets(), "fonts/lyrics_medium.ttf"));
                                        prefs.edit().putString("lyrics_font", "default").apply();

                                        fontSpotify.setChecked(false);
                                        fontBeautifulLyrics.setChecked(true);
                                        fontApple.setChecked(false);
                                    } catch (Exception e) {
                                        Toast.makeText(activity, "Failed to change font", Toast.LENGTH_SHORT).show();
                                    }
                                });

                                fontApple.setOnClickListener(c -> {
                                    try {
                                        References.beautifulFont = new WeakReference<>(Typeface.createFromAsset(modResources.getAssets(), "fonts/sf-pro-display-bold.ttf"));
                                        prefs.edit().putString("lyrics_font", "apple").apply();

                                        fontSpotify.setChecked(false);
                                        fontBeautifulLyrics.setChecked(false);
                                        fontApple.setChecked(true);
                                    } catch (Exception e) {
                                        Toast.makeText(activity, "Failed to change font", Toast.LENGTH_SHORT).show();
                                    }
                                });

                                MaterialRadioButton interludeBeautiful = view.findViewById(R.id.rb_beautiful_lyrics_interlude);
                                MaterialRadioButton interludeSpicy = view.findViewById(R.id.rb_spicy_lyrics_interlude);
                                MaterialRadioButton interludeSpotifyPlus = view.findViewById(R.id.rb_spotify_plus_interlude);
                                MaterialRadioButton interludeApple = view.findViewById(R.id.rb_apple_music_interlude);

                                interludeBeautiful.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Beautiful Lyrics").apply();

                                    interludeBeautiful.setChecked(true);
                                    interludeSpicy.setChecked(false);
                                    interludeSpotifyPlus.setChecked(false);
                                    interludeApple.setChecked(false);
                                });

                                interludeSpicy.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Spicy Lyrics")
                                            .apply();

                                    interludeBeautiful.setChecked(false);
                                    interludeSpicy.setChecked(true);
                                    interludeSpotifyPlus.setChecked(false);
                                    interludeApple.setChecked(false);
                                });

                                interludeSpotifyPlus.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Spotify Plus")
                                            .apply();

                                    interludeBeautiful.setChecked(false);
                                    interludeSpicy.setChecked(false);
                                    interludeSpotifyPlus.setChecked(true);
                                    interludeApple.setChecked(false);
                                });

                                interludeApple.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Apple Music")
                                            .apply();

                                    interludeBeautiful.setChecked(false);
                                    interludeSpicy.setChecked(false);
                                    interludeSpotifyPlus.setChecked(false);
                                    interludeApple.setChecked(true);
                                });

                                Slider slider = view.findViewById(R.id.line_spacing_slider);
                                TextView valueLabel = view.findViewById(R.id.line_spacing_value_label);
                                FrameLayout sliderContainer = view.findViewById(R.id.line_spacing_slider_container);

                                slider.setThumbRadius(dpToPx(8));
                                slider.setHaloRadius(0);

                                slider.addOnChangeListener((s, value, fromUser) -> {
                                    String text;
                                    switch (Math.round(value)) {
                                        case 0:
                                            text = "Compact";
                                            prefs.edit().putString("line_spacing", "compact").apply();
                                            break;
                                        case 1:
                                            text = "Default";
                                            prefs.edit().putString("line_spacing", "default").apply();
                                            break;
                                        case 2:
                                            text = "Spacious";
                                            prefs.edit().putString("line_spacing", "spacious").apply();
                                            break;
                                        case 3:
                                            text = "More Spacious";
                                            prefs.edit().putString("line_spacing", "more").apply();
                                            break;
                                        case 4:
                                            text = "Max";
                                            prefs.edit().putString("line_spacing", "max").apply();
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
                                        valueLabel.setY(dpToPx(-12));
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

                                String sliderValueThing = prefs.getString("line_spacing", "default");
                                switch (sliderValueThing) {
                                    case "compact":
                                        slider.setValue(0);
                                    case "default":
                                        slider.setValue(1);
                                    case "spacious":
                                        slider.setValue(2);
                                    case "more":
                                        slider.setValue(3);
                                    case "max":
                                        slider.setValue(4);
                                    default:
                                        slider.setValue(1);
                                }

                                MaterialSwitch background = view.findViewById(R.id.switch_enable_background);
                                MaterialSwitch lineGradient = view.findViewById(R.id.switch_enable_line_gradient);

                                MaterialRadioButton high = view.findViewById(R.id.rb_background_high);
                                MaterialRadioButton mid = view.findViewById(R.id.rb_background_mid);
                                MaterialRadioButton low = view.findViewById(R.id.rb_background_low);
                                MaterialRadioButton superLow = view.findViewById(R.id.rb_background_superlow);

                                high.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_background_quality", "high").apply();

                                    high.setChecked(true);
                                    mid.setChecked(false);
                                    low.setChecked(false);
                                    superLow.setChecked(false);
                                });

                                mid.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_background_quality", "mid").apply();

                                    high.setChecked(false);
                                    mid.setChecked(true);
                                    low.setChecked(false);
                                    superLow.setChecked(false);
                                });

                                low.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_background_quality", "low").apply();

                                    high.setChecked(false);
                                    mid.setChecked(false);
                                    low.setChecked(true);
                                    superLow.setChecked(false);
                                });

                                superLow.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_background_quality", "superLow").apply();

                                    high.setChecked(false);
                                    mid.setChecked(false);
                                    low.setChecked(false);
                                    superLow.setChecked(true);
                                });

                                MaterialSwitch swapTranslations = view.findViewById(R.id.switch_swap_translations);
                                MaterialSwitch hideOriginal = view.findViewById(R.id.switch_hide_original);
                                TextInputLayout translationLanguageLayout = view.findViewById(R.id.translation_language_layout);
                                TextInputEditText translationLanguage = view.findViewById(R.id.input_translation_language);

                                background.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("lyric_enable_background", value).apply());
                                lineGradient.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("lyric_enable_line_gradient", value).apply());
                                swapTranslations.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("lyrics_swap_translations", value).apply());
                                hideOriginal.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("lyrics_hide_original", value).apply());

                                String style = prefs.getString("lyric_animation_style", "Beautiful Lyrics");
                                visualBeautiful.setChecked(style.equals("Beautiful Lyrics"));
                                visualApple.setChecked(style.equals("Apple Music"));

                                String font = prefs.getString("lyrics_font", "default");
                                fontSpotify.setChecked(font.equals("spotify"));
                                fontBeautifulLyrics.setChecked(font.equals("default"));
                                fontApple.setChecked(font.equals("apple"));

                                String interludeDuration = prefs.getString("lyric_interlude_duration", "Spotify Plus");
                                interludeBeautiful.setChecked(interludeDuration.equals("Beautiful Lyrics"));
                                interludeSpicy.setChecked(interludeDuration.equals("Spicy Lyrics"));
                                interludeSpotifyPlus.setChecked(interludeDuration.equals("Spotify Plus"));
                                interludeApple.setChecked(interludeDuration.equals("Apple Music"));

                                background.setChecked(prefs.getBoolean("lyric_enable_background", true));
                                lineGradient.setChecked(prefs.getBoolean("lyric_enable_line_gradient", true));

                                String quality = prefs.getString("lyric_background_quality", "high");
                                high.setChecked(quality.equals("high"));
                                mid.setChecked(quality.equals("mid"));
                                low.setChecked(quality.equals("low"));
                                superLow.setChecked(quality.equals("superLow"));

                                swapTranslations.setChecked(prefs.getBoolean("lyrics_swap_translations", false));
                                hideOriginal.setChecked(prefs.getBoolean("lyrics_hide_original", false));
                                String configuredTranslationLanguage = LyricsTranslationService.normalizeTargetLanguage(prefs.getString("lyrics_translation_language", LyricsTranslationService.DEFAULT_TARGET_LANGUAGE));
                                translationLanguage.setText(configuredTranslationLanguage.isEmpty() ? LyricsTranslationService.DEFAULT_TARGET_LANGUAGE : configuredTranslationLanguage);
                                translationLanguage.addTextChangedListener(new android.text.TextWatcher() {
                                    @Override
                                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                                    @Override
                                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                                    @Override
                                    public void afterTextChanged(android.text.Editable editable) {
                                        String text = editable.toString();
                                        String language = LyricsTranslationService.normalizeTargetLanguage(text);
                                        translationLanguageLayout.setError(!text.isBlank() && text.length() >= 2 && language.isEmpty() ? "Enter a valid ISO language code" : null);
                                        if (!language.isEmpty()) prefs.edit().putString("lyrics_translation_language", language).apply();
                                    }
                                });
                            });

                            experimentalSettings.setOnClickListener(v -> {
                                View view = inflater.inflate(R.layout.experimental_settings_page, root, false);
                                root.addView(view);
                                animatePageIn(view);
                                currentDetailedSettingsPage.set(view);

                                MaterialToolbar detailedToolbar = view.findViewById(R.id.experimental_toolbar);
                                detailedToolbar.setNavigationOnClickListener(w -> {
                                    ViewParent parent = settingsPage.getParent();
                                    if (parent instanceof ViewGroup) {
                                        animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                                    }
                                });

                                MaterialSwitch scrollingAnimation = view.findViewById(R.id.switch_new_scroller);
                                scrollingAnimation.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("experiment_scroll", value).apply());
                                scrollingAnimation.setChecked(prefs.getBoolean("experiment_scroll", true));

                                MaterialSwitch newBackground = view.findViewById(R.id.switch_animated_art);
                                newBackground.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("experiment_animated_art", value).apply());
                                newBackground.setChecked(prefs.getBoolean("experiment_animated_art", true));
                            });

                            themeSettings.setOnClickListener(v -> {
                                View view = inflater.inflate(R.layout.theme_settings_page, root, false);
                                root.addView(view);
                                animatePageIn(view);
                                currentDetailedSettingsPage.set(view);

                                MaterialToolbar detailedToolbar = view.findViewById(R.id.theme_toolbar);
                                detailedToolbar.setNavigationOnClickListener(w -> {
                                    ViewParent parent = settingsPage.getParent();
                                    if (parent instanceof ViewGroup) {
                                        animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                                    }
                                });

                                MaterialSwitch themeEnabled = view.findViewById(R.id.switch_theme_enabled);
                                themeEnabled.setChecked(prefs.getBoolean("theme_enabled", false));
                                themeEnabled.setOnCheckedChangeListener((button, value) -> {
                                    prefs.edit().putBoolean("theme_enabled", value).apply();
                                    ThemeHook.setThemeEnabled(value, lpparm.classLoader);
                                });

                                MaterialSwitch autoTheme = view.findViewById(R.id.switch_auto_theme);
                                autoTheme.setChecked(prefs.getBoolean("auto_theme", false));
                                autoTheme.setOnCheckedChangeListener((button, value) -> prefs.edit().putBoolean("auto_theme", value).apply());

                                bindRadioButtons(view, prefs, "generated_theme_mode", "neutral", new int[]{R.id.rb_generated_light, R.id.rb_generated_neutral, R.id.rb_generated_dark}, new String[]{"light", "neutral", "dark"});
                                bindRadioButtons(view, prefs, "auto_theme_mode", "neutral", new int[]{R.id.rb_auto_light, R.id.rb_auto_neutral, R.id.rb_auto_dark}, new String[]{"light", "neutral", "dark"});

                                bindColorRow(activity, view, prefs, R.id.row_generated_theme_color, "theme_base", 0xFF0077B6);
                                view.findViewById(R.id.btn_generate_theme).setOnClickListener(z -> {
                                    int color = prefs.getInt("theme_base", 0xFF0077B6);
                                    String mode = prefs.getString("generated_theme_mode", "neutral");
                                    ThemeHook.generateTheme(color, mode, lpparm.classLoader);
                                    saveCurrentTheme(prefs);
                                });

                                bindColorRow(activity, view, prefs, R.id.row_color_background, "theme_background", ThemeHook.BACKGROUND);
                                bindColorRow(activity, view, prefs, R.id.row_color_background_highlight, "theme_background_highlight", ThemeHook.BACKGROUND_HIGHLIGHT);
                                bindColorRow(activity, view, prefs, R.id.row_color_background_press, "theme_background_press", ThemeHook.BACKGROUND_PRESS);

                                bindColorRow(activity, view, prefs, R.id.row_color_surface, "theme_surface", ThemeHook.SURFACE);
                                bindColorRow(activity, view, prefs, R.id.row_color_surface_highlight, "theme_surface_highlight", ThemeHook.SURFACE_HIGHLIGHT);
                                bindColorRow(activity, view, prefs, R.id.row_color_surface_press, "theme_surface_press", ThemeHook.SURFACE_PRESS);

                                bindColorRow(activity, view, prefs, R.id.row_color_tinted, "theme_tinted", ThemeHook.TINTED);
                                bindColorRow(activity, view, prefs, R.id.row_color_tinted_highlight, "theme_tinted_highlight", ThemeHook.TINTED_HIGHLIGHT);
                                bindColorRow(activity, view, prefs, R.id.row_color_tinted_press, "theme_tinted_press", ThemeHook.TINTED_PRESS);

                                bindColorRow(activity, view, prefs, R.id.row_color_text, "theme_text", ThemeHook.TEXT);
                                bindColorRow(activity, view, prefs, R.id.row_color_text_subdued, "theme_text_subdued", ThemeHook.TEXT_SUBDUED);

                                bindColorRow(activity, view, prefs, R.id.row_color_accent, "theme_accent", ThemeHook.ACCENT);
                                bindColorRow(activity, view, prefs, R.id.row_color_accent_highlight, "theme_accent_highlight", ThemeHook.ACCENT_HIGHLIGHT);
                                bindColorRow(activity, view, prefs, R.id.row_color_accent_press, "theme_accent_press", ThemeHook.ACCENT_PRESS);

                                bindColorRow(activity, view, prefs, R.id.row_color_announcement, "theme_announcement", ThemeHook.ANNOUNCEMENT);
                                bindColorRow(activity, view, prefs, R.id.row_color_decorative, "theme_decorative", ThemeHook.DECORATIVE);
                                bindColorRow(activity, view, prefs, R.id.row_color_decorative_subdued, "theme_decorative_subdued", ThemeHook.DECORATIVE_SUBDUED);

                                bindColorRow(activity, view, prefs, R.id.row_color_negative, "theme_negative", ThemeHook.NEGATIVE);
                                bindColorRow(activity, view, prefs, R.id.row_color_warning, "theme_warning", ThemeHook.WARNING);
                                bindColorRow(activity, view, prefs, R.id.row_color_positive, "theme_positive", ThemeHook.POSITIVE);

                                bindColorRow(activity, view, prefs, R.id.row_color_scrim, "theme_scrim", ThemeHook.SCRIM);
                                bindColorRow(activity, view, prefs, R.id.row_color_on_accent, "theme_on_accent", ThemeHook.ON_ACCENT);
                                view.findViewById(R.id.btn_apply_custom_theme).setOnClickListener(x -> ThemeHook.applyCustomTheme(prefs, lpparm.classLoader));
                            });

                            aboutSettings.setOnClickListener(v -> {
                                View view = inflater.inflate(R.layout.about_settings_page, root, false);
                                root.addView(view);
                                animatePageIn(view);
                                currentDetailedSettingsPage.set(view);

                                MaterialToolbar detailedToolbar = view.findViewById(R.id.about_toolbar);
                                detailedToolbar.setNavigationOnClickListener(w -> {
                                    ViewParent parent = settingsPage.getParent();
                                    if (parent instanceof ViewGroup) {
                                        animatePageOut((ViewGroup) parent, () -> ((ViewGroup) parent).removeView(view));
                                    }
                                });

                                View github = view.findViewById(R.id.open_github);

                                github.setOnClickListener(button -> {
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/LeNerd46/SpotifyPlus"));
                                    activity.startActivity(browserIntent);
                                });

                                View telegram = view.findViewById(R.id.open_telegram);

                                telegram.setOnClickListener(button -> {
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://t.me/spotifypluscool"));
                                    activity.startActivity(browserIntent);
                                });

                                // TextView text = view.findViewById(R.id.translate_text);
                                // MaterialButton button = view.findViewById(R.id.translate_button);
                                //
                                // button.setOnClickListener(button1 -> {
                                // try {
                                // final String originalText = text.getText().toString();
                                // text.setText("Translating...");
                                //
                                //
                                // } catch (Exception e) {
                                // XposedBridge.log("[SpotifyPlus] " + e);
                                // }
                                // });
                            });
                        } catch (Exception e) {
                            XposedBridge
                                    .log("[SpotifyPlus] Could not inflate layout: " + e.getMessage());
                            XposedBridge.log(e);
                        }
                    });
                    if (settingsButton == null) {
                        XposedBridge.log("[SpotifyPlus] Failed to clone the Settings and privacy side-drawer row.");
                        return;
                    }
                    Array.set(newArray, customItemIndex, settingsButton);

                    // Array.set(newArray, originalItems.length + 1,
                    // createSideDrawerButton("Marketplace", tempalteLightning, buttonClass,
                    // sideDrawerItem, propertiesClass, onClickClass, qbpInterface, zpj0Interface,
                    // cbpInterface, 2131957896, () -> XposedBridge.log("[SpotifyPlus] Hello!")));

                    int index = originalItems.length + 1;

                    for (var item : scriptSideButtons.keySet()) {
                        Runnable run = scriptSideButtons.get(item);
                        Array.set(newArray, index, createSideDrawerButton(item.second, tempalteLightning, resourceIdToUse, run));
                        resourceIdToUse--;
                        index++;
                    }

                    XposedHelpers.setObjectField(param.thisObject, d.getName(), newArray);
                    XposedBridge.log("[SpotifyPlus] Injected Spotify Plus Settings after side-drawer item " + settingsItemIndex + " using " + runtimeButtonClass.getName() + " -> " + runtimeSideDrawerItem.getClass().getName() + " -> " + runtimeProperties.getClass().getName());
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
            XposedBridge.log("[SpotifyPlus] Could not find class: " + e.getMessage());
        }
    }

    private int findSettingsItemIndex(Object[] items) {
        for (int i = 0; i < items.length; i++) {
            if (containsSettingsDestination(items[i], 4, new IdentityHashMap<>())) return i;
        }
        return -1;
    }

    private boolean containsSettingsDestination(Object value, int remainingDepth, IdentityHashMap<Object, Boolean> visited) {
        return containsDrawerDestination(value, remainingDepth, visited, "spotify:settings", "spotify:preferences", "spotify:config");
    }

    private boolean containsDrawerDestination(Object value, int remainingDepth, IdentityHashMap<Object, Boolean> visited, String... destinations) {
        if (value instanceof String && Arrays.asList(destinations).contains(value)) return true;
        if (value == null || remainingDepth == 0 || visited.put(value, Boolean.TRUE) != null) return false;
        Class<?> valueClass = value.getClass();
        if (valueClass.isPrimitive() || valueClass.isEnum() || valueClass.isArray() || valueClass.getName().startsWith("java.") || valueClass.getName().startsWith("android.") || valueClass.getName().startsWith("kotlin.")) return false;
        for (Class<?> type = valueClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    if (containsDrawerDestination(field.get(value), remainingDepth - 1, visited, destinations)) return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private Object findDirectChildContainingSettings(Object owner) {
        if (owner == null) return null;
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (containsSettingsDestination(value, 3, new IdentityHashMap<>())) return value;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private Object createSideDrawerButton(String title, Object template, int resId, Runnable onClick) {
        try {
            Object originalContent = findDirectChildContainingSettings(template);
            Object originalProps = findDirectChildContainingSettings(originalContent);
            if (originalContent == null || originalProps == null) throw new IllegalStateException("[RemoveCreateButtonHook] Could not resolve the live Settings row content and props.");
            List<Field> propsFields = getInstanceFields(originalProps.getClass());
            int instrumentationIndex = findInstrumentationIndex(originalProps, propsFields);
            Object originalInstrumentation = readField(propsFields.get(instrumentationIndex), originalProps);
            List<Field> instrumentationFields = getInstanceFields(originalInstrumentation.getClass());
            Object[] instrumentationValues = readFieldValues(originalInstrumentation, instrumentationFields);
            int clickIndex = findClickIndex(instrumentationFields, instrumentationValues);
            Constructor<?> instrumentationConstructor = findCompatibleConstructor(originalInstrumentation.getClass(), instrumentationValues);
            instrumentationValues[clickIndex] = createClickCallback(instrumentationFields.get(clickIndex).getType(), instrumentationConstructor.getParameterTypes()[clickIndex], instrumentationValues[clickIndex], resId, onClick);
            Object newInstrumentation = instantiateLike(originalInstrumentation.getClass(), instrumentationValues);
            Object[] propsValues = readFieldValues(originalProps, propsFields);
            boolean replacedTitleResource = false;
            for (int i = 0; i < propsValues.length; i++) {
                if (i == instrumentationIndex) propsValues[i] = newInstrumentation;
                else if (propsValues[i] instanceof String && containsSettingsDestination(propsValues[i], 1, new IdentityHashMap<>())) propsValues[i] = "spotify:null";
                else if (propsValues[i] instanceof String && isSettingsTitle((String) propsValues[i])) propsValues[i] = title;
                else if (propsValues[i] instanceof Integer && isSettingsTitleResource((Integer) propsValues[i])) {
                    propsValues[i] = resId;
                    replacedTitleResource = true;
                }
            }
            if (!replacedTitleResource) {
                List<Integer> integerFields = new ArrayList<>();
                for (int i = 0; i < propsFields.size(); i++) if (propsFields.get(i).getType() == int.class || propsFields.get(i).getType() == Integer.class) integerFields.add(i);
                if (integerFields.size() == 1) propsValues[integerFields.get(0)] = resId;
            }
            SpotifyTitleOverride.overrideSpotifyStringById(resId, title);
            Object newProps = instantiateLike(originalProps.getClass(), propsValues);
            Object newContent = cloneReplacingIdentity(originalContent, originalProps, newProps, null);
            Object newButton = cloneReplacingIdentity(template, originalContent, newContent, idToUse++);
            XposedBridge.log("[SpotifyPlus] Injected " + title + " by cloning runtime classes " + template.getClass().getName() + " -> " + originalContent.getClass().getName() + " -> " + originalProps.getClass().getName() + " -> " + originalInstrumentation.getClass().getName());
            return newButton;
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
            return null;
        }
    }

    private List<Field> getInstanceFields(Class<?> type) {
        List<Field> fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).collect(java.util.stream.Collectors.toList());
        fields.forEach(field -> field.setAccessible(true));
        return fields;
    }

    private Object readField(Field field, Object owner) throws IllegalAccessException {
        field.setAccessible(true);
        return field.get(owner);
    }

    private Object[] readFieldValues(Object owner, List<Field> fields) throws IllegalAccessException {
        Object[] values = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) values[i] = readField(fields.get(i), owner);
        return values;
    }

    private int findInstrumentationIndex(Object props, List<Field> fields) throws IllegalAccessException {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Object value = readField(fields.get(i), props);
            if (value == null) continue;
            List<Field> childFields = getInstanceFields(value.getClass());
            if (childFields.size() < 2 || childFields.size() > 3) continue;
            Object[] childValues = readFieldValues(value, childFields);
            if (findClickIndexOrNegative(childFields, childValues) >= 0) candidates.add(i);
        }
        if (candidates.size() != 1) throw new IllegalStateException("[RemoveCreateButtonHook] Expected one live Settings instrumentation field in " + props.getClass().getName() + " but found " + candidates.size() + ": " + candidates);
        return candidates.get(0);
    }

    private int findClickIndex(List<Field> fields, Object[] values) {
        int index = findClickIndexOrNegative(fields, values);
        if (index < 0) throw new IllegalStateException("[RemoveCreateButtonHook] Could not identify the live Settings click callback.");
        return index;
    }

    private int findClickIndexOrNegative(List<Field> fields, Object[] values) {
        if (fields.size() > 1 && isInvokeCallback(fields.get(1).getType(), values[1])) return 1;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) if (isInvokeCallback(fields.get(i).getType(), values[i])) candidates.add(i);
        return candidates.size() == 1 ? candidates.get(0) : -1;
    }

    private boolean isInvokeCallback(Class<?> declaredType, Object value) {
        if (Arrays.stream(declaredType.getMethods()).anyMatch(method -> method.getName().equals("invoke"))) return true;
        return value != null && Arrays.stream(value.getClass().getMethods()).anyMatch(method -> method.getName().equals("invoke"));
    }

    private Object createClickCallback(Class<?> fieldType, Class<?> constructorType, Object originalClick, int resId, Runnable onClick) throws Exception {
        if (fieldType.isInterface() && constructorType.isInterface()) return Proxy.newProxyInstance(lpparm.classLoader, new Class[]{constructorType}, (proxy, method, args) -> {
            if (method.getName().equals("invoke")) runSideDrawerClick(resId, onClick);
            return defaultValue(method.getReturnType());
        });
        List<Field> clickFields = getInstanceFields(originalClick.getClass());
        Object clonedClick = instantiateCallbackLike(originalClick, readFieldValues(originalClick, clickFields));
        XposedBridge.hookAllMethods(clonedClick.getClass(), "invoke", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject != clonedClick) return;
                runSideDrawerClick(resId, onClick);
                param.setResult(defaultValue(((Method) param.method).getReturnType()));
            }
        });
        return clonedClick;
    }

    private Object instantiateCallbackLike(Object originalClick, Object[] values) throws Exception {
        try {
            return instantiateLike(originalClick.getClass(), values);
        } catch (IllegalStateException ignored) {
            int invokeArity = Arrays.stream(originalClick.getClass().getDeclaredMethods()).filter(method -> method.getName().equals("invoke") && !method.isBridge()).mapToInt(Method::getParameterCount).max().orElse(0);
            List<Constructor<?>> candidates = Arrays.stream(originalClick.getClass().getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == values.length + 1 && wrapPrimitive(constructor.getParameterTypes()[0]) == Integer.class && parametersAccept(Arrays.copyOfRange(constructor.getParameterTypes(), 1, constructor.getParameterCount()), values)).collect(java.util.stream.Collectors.toList());
            if (candidates.size() != 1) throw new IllegalStateException("[RemoveCreateButtonHook] Could not clone concrete click callback " + originalClick.getClass().getName() + " from its captured fields; constructors: " + Arrays.toString(originalClick.getClass().getDeclaredConstructors()));
            candidates.get(0).setAccessible(true);
            Object[] constructorValues = new Object[values.length + 1];
            constructorValues[0] = invokeArity;
            System.arraycopy(values, 0, constructorValues, 1, values.length);
            return candidates.get(0).newInstance(constructorValues);
        }
    }

    private void runSideDrawerClick(int resId, Runnable onClick) {
        if (resId == 2131957897 && !overlayShown.compareAndSet(false, true)) return;
        try {
            onClick.run();
        } catch (Throwable throwable) {
            if (resId == 2131957897) overlayShown.set(false);
            XposedBridge.log(throwable);
        }
    }

    private Constructor<?> findCompatibleConstructor(Class<?> type, Object[] values) {
        List<Constructor<?>> candidates = Arrays.stream(type.getDeclaredConstructors()).filter(constructor -> constructor.getParameterCount() == values.length).filter(constructor -> parametersAccept(constructor.getParameterTypes(), values)).collect(java.util.stream.Collectors.toList());
        if (candidates.size() != 1) throw new IllegalStateException("[RemoveCreateButtonHook] Expected one primary constructor in " + type.getName() + " for " + values.length + " live fields but found " + candidates.size() + ": " + Arrays.toString(type.getDeclaredConstructors()));
        candidates.get(0).setAccessible(true);
        return candidates.get(0);
    }

    private boolean parametersAccept(Class<?>[] parameterTypes, Object[] values) {
        for (int i = 0; i < parameterTypes.length; i++) if (values[i] == null ? parameterTypes[i].isPrimitive() : !wrapPrimitive(parameterTypes[i]).isInstance(values[i])) return false;
        return true;
    }

    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private Object instantiateLike(Class<?> type, Object[] values) throws Exception {
        return findCompatibleConstructor(type, values).newInstance(values);
    }

    private Object cloneReplacingIdentity(Object template, Object oldChild, Object newChild, Integer replacementId) throws Exception {
        List<Field> fields = getInstanceFields(template.getClass());
        Object[] values = readFieldValues(template, fields);
        boolean childReplaced = false;
        boolean hasIdField = fields.stream().anyMatch(field -> field.getType() == int.class || field.getType() == Integer.class) || Arrays.stream(values).anyMatch(value -> value instanceof Integer);
        boolean idReplaced = replacementId == null || !hasIdField;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == oldChild) {
                values[i] = newChild;
                childReplaced = true;
            } else if (!idReplaced && (fields.get(i).getType() == int.class || fields.get(i).getType() == Integer.class || values[i] instanceof Integer)) {
                values[i] = replacementId;
                idReplaced = true;
            }
        }
        if (!childReplaced || !idReplaced) throw new IllegalStateException("[RemoveCreateButtonHook] Could not clone " + template.getClass().getName() + ": childReplaced=" + childReplaced + ", idReplaced=" + idReplaced);
        return instantiateLike(template.getClass(), values);
    }

    private boolean isSettingsTitle(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("settings") || normalized.contains("privacy");
    }

    private boolean isSettingsTitleResource(int resourceId) {
        try {
            String entryName = context.getResources().getResourceEntryName(resourceId).toLowerCase(Locale.ROOT);
            if (entryName.contains("settings") || entryName.contains("privacy")) return true;
            return isSettingsTitle(context.getString(resourceId));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) return null;
        if (returnType == boolean.class) return false;
        if (returnType == char.class) return '\0';
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0.0f;
        return 0.0d;
    }

    private void animatePageIn(View page) {
        page.setAlpha(0.0f);

        page.animate()
                .alpha(1.0f)
                .setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void animatePageOut(View page, Runnable onComplete) {
        page.animate()
                .alpha(1.0f)
                .setDuration(150)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(onComplete)
                .start();
    }

    public static void registerSettingSection(String title, int id, SettingItem.SettingSection section) {
        var key = scriptSettings.keySet().stream().filter(entry -> entry.first.equals(id)).findFirst().orElse(null);

        if (key == null) {
            scriptSettings.put(Pair.create(id, title), new ArrayList<>(Arrays.asList(section)));
        } else {
            var sections = scriptSettings.get(key);
            sections.add(section);
            scriptSettings.put(key, sections);
        }
    }

    public static void registerSideButton(String title, int id, Runnable onClick) {
        try {
            var key = scriptSideButtons.keySet().stream().filter(entry -> entry.first.equals(id)).findFirst()
                    .orElse(null);

            if (key == null) {
                scriptSideButtons.put(Pair.create(id, title), onClick);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private String getAboslutePath(DocumentFile file) {
        Uri uri = file.getUri();

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            File tempFile = new File(context.getCacheDir(), "test.apk");

            try (OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int len;

                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics());
    }

    private ArrayList<SleepTimerHook.SleepTimerInfo> loadSleepTimerPresets(SharedPreferences prefs) {
        ArrayList<SleepTimerHook.SleepTimerInfo> presets = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(prefs.getString("custom_sleep_timers",
                    "[{\"value\":5,\"unit\":false},{\"value\":10,\"unit\":false},{\"value\":15,\"unit\":false},{\"value\":30,\"unit\":false},{\"value\":45,\"unit\":false},{\"value\":1,\"unit\":true}]"));

            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                presets.add(new SleepTimerHook.SleepTimerInfo(object.getInt("value"), object.getBoolean("unit")));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        return presets;
    }

    private void saveSleepTimerPresets(SharedPreferences prefs, ArrayList<SleepTimerHook.SleepTimerInfo> presets) {
        try {
            JSONArray array = new JSONArray();

            for (SleepTimerHook.SleepTimerInfo preset : presets) {
                JSONObject object = new JSONObject();
                object.put("value", preset.value);
                object.put("unit", preset.unit);
                array.put(object);
            }

            prefs.edit().putString("custom_sleep_timers", array.toString()).apply();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void resetSettingsOverlayState() {overlayShown.set(false);}

    private static class SleepTimerPresetAdapter extends RecyclerView.Adapter<SleepTimerPresetAdapter.Holder> {
        private final Context context;
        private final Resources modResources;
        private final LayoutInflater inflater;
        private final ArrayList<SleepTimerHook.SleepTimerInfo> presets;
        private final Runnable onChanged;

        SleepTimerPresetAdapter(Context context, Resources modResources, LayoutInflater inflater,
                                ArrayList<SleepTimerHook.SleepTimerInfo> presets, Runnable onChanged) {
            this.context = context;
            this.modResources = modResources;
            this.inflater = inflater;
            this.presets = presets;
            this.onChanged = onChanged;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(
                    modResources.getIdentifier("item_custom_sleep_timer", "layout", "com.lenerd46.spotifyplus"), parent,
                    false);
            return new Holder(view, modResources);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SleepTimerHook.SleepTimerInfo preset = presets.get(position);

            holder.title.setText(preset.getTitle());

            holder.deleteButton.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION)
                    return;

                presets.remove(pos);
                notifyItemRemoved(pos);
                onChanged.run();
            });
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            View deleteButton;

            Holder(@NonNull View itemView, Resources modResources) {
                super(itemView);

                title = itemView.findViewById(
                        modResources.getIdentifier("sleep_timer_preset_title", "id", "com.lenerd46.spotifyplus"));
                subtitle = itemView.findViewById(
                        modResources.getIdentifier("sleep_timer_preset_subtitle", "id", "com.lenerd46.spotifyplus"));
                deleteButton = itemView.findViewById(
                        modResources.getIdentifier("btn_delete_sleep_timer_preset", "id", "com.lenerd46.spotifyplus"));
            }
        }
    }

    private void sortSleepTimerPresets(ArrayList<SleepTimerHook.SleepTimerInfo> presets) {
        presets.sort(Comparator.comparingLong(this::getSleepTimerDurationMillis));
    }

    private long getSleepTimerDurationMillis(SleepTimerHook.SleepTimerInfo preset) {
        return preset.unit ? java.util.concurrent.TimeUnit.HOURS.toMillis(preset.value) : java.util.concurrent.TimeUnit.MINUTES.toMillis(preset.value);
    }

    private void bindRadioButtons(View root, SharedPreferences prefs, String prefKey, String defaultValue, int[] buttonIds, String[] values) {
        MaterialRadioButton[] buttons = new MaterialRadioButton[buttonIds.length];
        String selectedValue = prefs.getString(prefKey, defaultValue);
        for (int i = 0; i < buttonIds.length; i++) {
            buttons[i] = root.findViewById(buttonIds[i]);
            buttons[i].setChecked(values[i].equals(selectedValue));
        }
        for (int i = 0; i < buttons.length; i++) {
            int selectedIndex = i;
            buttons[i].setOnCheckedChangeListener((button, checked) -> {
                if (!checked) return;
                for (int j = 0; j < buttons.length; j++) if (j != selectedIndex) buttons[j].setChecked(false);
                prefs.edit().putString(prefKey, values[selectedIndex]).apply();
            });
        }
    }

    private void bindColorRow(Activity activity, View root, SharedPreferences prefs, int rowId, String prefKey, int defaultColor) {
        View row = root.findViewById(rowId);
        TextView value = (TextView) ((ViewGroup) row).getChildAt(1);
        MaterialCardView swatch = (MaterialCardView) ((ViewGroup) row).getChildAt(2);

        int color = prefs.getInt(prefKey, defaultColor);
        value.setText(String.format("#%08X", color));
        swatch.setCardBackgroundColor(color);

        row.setOnClickListener(v -> openColorPicker(activity, prefs.getInt(prefKey, defaultColor), selectedColor -> {
            prefs.edit().putInt(prefKey, selectedColor).apply();
            value.setText(String.format("#%08X", selectedColor));
            swatch.setCardBackgroundColor(selectedColor);
        }));
    }

    private void openColorPicker(Activity activity, int initialColor, java.util.function.IntConsumer onColorSelected) {
        try {
            XModuleResources modResources = References.modResources;
            Context themedContext = new ModuleContextWrapper(activity.getApplicationContext(), R.style.Theme_SpotifyPlus, modResources, ModuleContextWrapper.class.getClassLoader());
            LayoutInflater inflater = LayoutInflater.from(activity.getApplicationContext()).cloneInContext(themedContext);
            View picker = inflater.inflate(modResources.getIdentifier("color_picker_view", "layout", "com.lenerd46.spotifyplus"), null, false);
            SpotifyBottomSheet sheet = new SpotifyBottomSheet(lpparm.classLoader, activity);
            FrameLayout colorFieldContainer = picker.findViewById(modResources.getIdentifier("color_picker_field", "id", "com.lenerd46.spotifyplus"));
            FrameLayout hueContainer = picker.findViewById(modResources.getIdentifier("color_picker_hue", "id", "com.lenerd46.spotifyplus"));
            MaterialCardView preview = picker.findViewById(modResources.getIdentifier("color_picker_preview", "id", "com.lenerd46.spotifyplus"));
            TextInputLayout hexLayout = picker.findViewById(modResources.getIdentifier("color_picker_hex_layout", "id", "com.lenerd46.spotifyplus"));
            TextInputEditText hex = picker.findViewById(modResources.getIdentifier("input_color_picker_hex", "id", "com.lenerd46.spotifyplus"));
            MaterialButton cancel = picker.findViewById(modResources.getIdentifier("btn_cancel_color_picker", "id", "com.lenerd46.spotifyplus"));
            MaterialButton select = picker.findViewById(modResources.getIdentifier("btn_select_color_picker", "id", "com.lenerd46.spotifyplus"));
            ColorFieldView colorField = new ColorFieldView(themedContext);
            HueBarView hueBar = new HueBarView(themedContext);
            colorFieldContainer.addView(colorField, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            hueContainer.addView(hueBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            float[] hsv = new float[3];
            Color.colorToHSV(initialColor, hsv);
            int[] alpha = {Color.alpha(initialColor)};
            int[] selectedColor = {initialColor};
            boolean[] syncing = {false};
            colorField.setHue(hsv[0]);
            colorField.setSelection(hsv[1], hsv[2]);
            hueBar.setHue(hsv[0]);
            preview.setCardBackgroundColor(initialColor);
            hex.setText(String.format("#%08X", initialColor));

            Runnable updateColor = () -> {
                if (syncing[0]) return;
                selectedColor[0] = Color.HSVToColor(alpha[0], hsv);
                syncing[0] = true;
                preview.setCardBackgroundColor(selectedColor[0]);
                hex.setText(String.format("#%08X", selectedColor[0]));
                hex.setSelection(hex.length());
                hexLayout.setError(null);
                syncing[0] = false;
            };

            colorField.setOnColorChanged((saturation, value) -> {
                hsv[1] = saturation;
                hsv[2] = value;
                updateColor.run();
            });
            hueBar.setOnHueChanged(hue -> {
                hsv[0] = hue;
                colorField.setHue(hue);
                updateColor.run();
            });
            hex.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable editable) {
                    if (syncing[0]) return;
                    String text = editable.toString();
                    boolean valid = text.matches("#?(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");
                    hexLayout.setError(!valid && text.length() >= 7 ? "Enter #RRGGBB or #AARRGGBB" : null);
                    if (!valid) return;
                    selectedColor[0] = Color.parseColor(text.startsWith("#") ? text : "#" + text);
                    Color.colorToHSV(selectedColor[0], hsv);
                    alpha[0] = Color.alpha(selectedColor[0]);
                    syncing[0] = true;
                    preview.setCardBackgroundColor(selectedColor[0]);
                    colorField.setHue(hsv[0]);
                    colorField.setSelection(hsv[1], hsv[2]);
                    hueBar.setHue(hsv[0]);
                    hexLayout.setError(null);
                    syncing[0] = false;
                }
            });

            cancel.setOnClickListener(v -> sheet.dismiss());
            select.setOnClickListener(v -> {
                String text = hex.getText() == null ? "" : hex.getText().toString();
                if (!text.matches("#?(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")) {
                    hexLayout.setError("Enter #RRGGBB or #AARRGGBB");
                    return;
                }
                onColorSelected.accept(selectedColor[0]);
                sheet.dismiss();
            });
            sheet.create(picker);
            sheet.setDraggable(false);
        } catch (Throwable throwable) {
            XposedBridge.log("[SpotifyPlus] Could not open color picker");
            XposedBridge.log(throwable);
        }
    }

    private static class ColorFieldView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private float saturation;
        private float value = 1f;
        private java.util.function.BiConsumer<Float, Float> onColorChanged;

        ColorFieldView(Context context) {super(context);}

        void setHue(float hue) {
            this.hue = hue;
            invalidate();
        }

        void setSelection(float saturation, float value) {
            this.saturation = saturation;
            this.value = value;
            invalidate();
        }

        void setOnColorChanged(java.util.function.BiConsumer<Float, Float> onColorChanged) {this.onColorChanged = onColorChanged;}

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0, Color.WHITE, Color.HSVToColor(new float[]{hue, 1f, 1f}), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            float x = saturation * getWidth();
            float y = (1f - value) * getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x, y, dp(10), paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(10), paint);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN && event.getAction() != android.view.MotionEvent.ACTION_MOVE) return true;
            saturation = Math.max(0f, Math.min(1f, event.getX() / getWidth()));
            value = 1f - Math.max(0f, Math.min(1f, event.getY() / getHeight()));
            invalidate();
            if (onColorChanged != null) onColorChanged.accept(saturation, value);
            return true;
        }

        private float dp(float value) {return value * getResources().getDisplayMetrics().density;}
    }

    private static class HueBarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private java.util.function.Consumer<Float> onHueChanged;

        HueBarView(Context context) {super(context);}

        void setHue(float hue) {
            this.hue = hue;
            invalidate();
        }

        void setOnHueChanged(java.util.function.Consumer<Float> onHueChanged) {this.onHueChanged = onHueChanged;}

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0, colors, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            float x = hue / 360f * getWidth();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x, getHeight() / 2f, dp(10), paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, getHeight() / 2f, dp(10), paint);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN && event.getAction() != android.view.MotionEvent.ACTION_MOVE) return true;
            hue = Math.max(0f, Math.min(360f, event.getX() / getWidth() * 360f));
            invalidate();
            if (onHueChanged != null) onHueChanged.accept(hue);
            return true;
        }

        private float dp(float value) {return value * getResources().getDisplayMetrics().density;}
    }

    private void saveCurrentTheme(SharedPreferences prefs) {
        prefs.edit()
                .putBoolean("theme_palette_saved", true)
                .putInt("theme_background", ThemeHook.BACKGROUND)
                .putInt("theme_background_highlight", ThemeHook.BACKGROUND_HIGHLIGHT)
                .putInt("theme_background_press", ThemeHook.BACKGROUND_PRESS)
                .putInt("theme_surface", ThemeHook.SURFACE)
                .putInt("theme_surface_highlight", ThemeHook.SURFACE_HIGHLIGHT)
                .putInt("theme_surface_press", ThemeHook.SURFACE_PRESS)
                .putInt("theme_tinted", ThemeHook.TINTED)
                .putInt("theme_tinted_highlight", ThemeHook.TINTED_HIGHLIGHT)
                .putInt("theme_tinted_press", ThemeHook.TINTED_PRESS)
                .putInt("theme_text", ThemeHook.TEXT)
                .putInt("theme_text_subdued", ThemeHook.TEXT_SUBDUED)
                .putInt("theme_accent", ThemeHook.ACCENT)
                .putInt("theme_accent_highlight", ThemeHook.ACCENT_HIGHLIGHT)
                .putInt("theme_accent_press", ThemeHook.ACCENT_PRESS)
                .putInt("theme_announcement", ThemeHook.ANNOUNCEMENT)
                .putInt("theme_decorative", ThemeHook.DECORATIVE)
                .putInt("theme_decorative_subdued", ThemeHook.DECORATIVE_SUBDUED)
                .putInt("theme_negative", ThemeHook.NEGATIVE)
                .putInt("theme_warning", ThemeHook.WARNING)
                .putInt("theme_positive", ThemeHook.POSITIVE)
                .putInt("theme_scrim", ThemeHook.SCRIM)
                .putInt("theme_on_accent", ThemeHook.ON_ACCENT)
                .apply();
    }
}

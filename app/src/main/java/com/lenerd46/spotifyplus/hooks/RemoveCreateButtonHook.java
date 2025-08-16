package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.XModuleResources;
import android.net.Uri;
import android.util.Pair;
import android.util.TypedValue;
import android.view.*;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.lenerd46.spotifyplus.ModuleContextWrapper;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SettingItem;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.MatchType;
import org.luckypray.dexkit.query.matchers.*;
import org.luckypray.dexkit.result.ClassDataList;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RemoveCreateButtonHook extends SpotifyHook {
    private static final int SETTINGS_OVERLAY_ID = 0x53504c53;

    private static final int DETAILED_SETTINGS_OVERLAY_ID = 0x53504c54;
    private static final int MARKETPLACE_OVERLAY_ID = 0x53504c55;
    private int idToUse = 8001;
    private SharedPreferences prefs;
    private final Context context;

    private ClassDataList fwd0Classes;
    private ClassDataList dwd0Classes;
    private ClassDataList propertiesClasses;
    private ClassDataList onClickClasses;
    private Class<?> whateverThisInterfaceDoes;
    private Class<?> iconInterface;
    private Class<?> wwk;
    private final static ConcurrentHashMap<Pair<Integer, String>, List<SettingItem.SettingSection>> scriptSettings = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<Pair<Integer, String>, Runnable> scriptSideButtons = new ConcurrentHashMap<>();

    public RemoveCreateButtonHook(final Context context) {
        this.context = context;
    }

    @Override
    protected void hook() {
        try {
            if (prefs == null) {
                prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
            }

            var constructorClassList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItemSet(item1=")));
            var parameterClassList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItem(icon=").methodCount(4).fieldCount(5, 6)));
            if (constructorClassList.isEmpty() || parameterClassList.isEmpty()) {
                XposedBridge.log("[SpotifyPlus] Constructor class not found");
                return;
            }

            var constructorClass = constructorClassList.get(0).getInstance(lpparm.classLoader);
            var parameterClass = parameterClassList.get(0).getInstance(lpparm.classLoader);

            XposedHelpers.findAndHookConstructor(constructorClass, parameterClass, parameterClass, parameterClass, parameterClass, parameterClass, new XC_MethodHook() {
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

            var list = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("spotify:artist:", "Failed requirement.", "spotify:concept:", "spotify:list:", "podcast-chapters", "spotify:show:")));
            Class<?> clazz = list.get(0).getInstance(lpparm.classLoader);

            Class<?> id30 = XposedHelpers.findClass("p.id30", lpparm.classLoader);
            XposedBridge.hookAllMethods(id30, "a", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object nav = param.args[0]; // hd30
                    String raw = (String) XposedHelpers.getObjectField(nav, "a");
                    if (raw != null && raw.startsWith("spotifyplus:")) {
                        Intent intent = (Intent) param.getResult();
                        String path = raw.substring("spotifyplus:".length());

                        // Make the route canonical and *without* query
                        intent.setData(Uri.parse("spotify:settings"));

                        // Attach your flags/metadata as extras (survive through the pipeline)
                        intent.putExtra("is_internal_navigation", true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                        // Marker for your overlay logic
                        intent.putExtra("spx", "spotifyplus:" + path);
                        intent.putExtra("spx_src", raw);

                        // Keep Spotify's explicit component targeting
                        Context appCtx = (Context) XposedHelpers.getObjectField(param.thisObject, "b");
                        String activityClass = (String) XposedHelpers.getObjectField(param.thisObject, "a");
                        intent.setClassName(appCtx, activityClass);

                        param.setResult(intent);
                        XposedBridge.log("[SpotifyPlus][id30.a] rewrote to spotify:settings with extras");
                    }
                }
            });

            Class<?> ysi0 = XposedHelpers.findClass("p.ysi0", lpparm.classLoader);
            Class<?> bti0 = XposedHelpers.findClass("p.bti0", lpparm.classLoader);

            XposedBridge.hookAllMethods(ysi0, "g", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String s = (String) param.args[0];
                    if (s != null && s.startsWith("spotifyplus:")) {
                        XposedBridge.log("[SpotifyPlus] " + s);
                        // Rewrite to a *real* internal route so the router pushes Settings
                        String rewritten = "spotify:settings?spx=spotifyplus&src=" + Uri.encode(s);

                        // IMPORTANT: construct bti0 directly (constructor), not via ysi0.g()
                        Object bt = XposedHelpers.newInstance(bti0, rewritten);
                        param.setResult(bt);
                    }
                }
            });

            Class<?> main = XposedHelpers.findClass("com.spotify.music.SpotifyMainActivity", lpparm.classLoader);
            XposedBridge.hookAllMethods(main, "onNewIntent", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.app.Activity act = (android.app.Activity) param.thisObject;
                    Intent it = (Intent) param.args[0];
                    if (it != null && it.getStringExtra("spx").startsWith("spotifyplus:")) {
                        act.runOnUiThread(() -> {
                        });
                    } else {
                        // Any other navigation: remove overlay
                        act.runOnUiThread(() -> {
                            android.view.View v = act.getWindow().getDecorView().findViewById(SETTINGS_OVERLAY_ID);
                            android.view.View detailed = act.getWindow().getDecorView().findViewById(DETAILED_SETTINGS_OVERLAY_ID);

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
                            }
                        });
                    }
                }
            });

            var modifyDataListClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).interfaceCount(1).methodCount(3).fields(FieldsMatcher.create()
                    .count(4)
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(int.class))
                    .add(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class))
            )));

            Method invokeSuspend = bridge.findMethod(FindMethod.create().searchInClass(modifyDataListClass).matcher(MethodMatcher.create().returnType(Object.class).modifiers(Modifier.PUBLIC | Modifier.FINAL).paramCount(1).paramTypes(Object.class))).get(0).getMethodInstance(lpparm.classLoader);

            var whateverInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("quick_add_to_playlist_item")));
            var iconInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("getState(Lcom/spotify/alignedcuration/firstsave/page/contents/DefaultSaveDestinationElement$Props;)Lkotlinx/coroutines/flow/Flow;")));
            var wwkList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Encore.Vector.CopyAlt16")));
            fwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().interfaceCount(0).modifiers(Modifier.PUBLIC | Modifier.FINAL).fields(FieldsMatcher.create().count(2).add(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))).usingStrings("ListItem(id=")));
            dwd0Classes = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("SideDrawerListItem(element=")));
            propertiesClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Props(icon=", ", title=", ", titleRes=", ", uriToNavigate=", ", isNew=", ", instrumentation=", ", hasNotification=")));
            onClickClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Instrumentation(node=", ", onClick=", ", onImpression=").fieldCount(3)));

            var qbpInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().modifiers(Modifier.FINAL, MatchType.Equals).interfaceCount(1).fields(FieldsMatcher.create().add(FieldMatcher.create().type(int.class)).count(2)).methods(MethodsMatcher.create()
                    .count(4)
                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).name("invoke").paramTypes(Object.class, Object.class))
                    .add(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(Object.class).name("invokeSuspend").paramTypes(Object.class))
            )));

            var zpj0InterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("premium_row")));

            var cbpInterfaceList = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("video_surface_view_seek_frame_tag")));

            if (whateverInterfaceList.isEmpty() || iconInterfaceList.isEmpty() || wwkList.isEmpty() || fwd0Classes.isEmpty() || dwd0Classes.isEmpty() || propertiesClasses.isEmpty() || onClickClasses.isEmpty() || qbpInterfaceList.isEmpty() || zpj0InterfaceList.isEmpty() || cbpInterfaceList.isEmpty()) {
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

                XposedBridge.log("[SpotifyPlus] No classes found");
                return;
            }

            whateverThisInterfaceDoes = whateverInterfaceList.get(0).getInstance(lpparm.classLoader).getInterfaces()[0];
            iconInterface = iconInterfaceList.get(0).getInstance(lpparm.classLoader).getInterfaces()[0];
            wwk = wwkList.get(0).getInstance(lpparm.classLoader).getSuperclass();

            Class<?> buttonClass = fwd0Classes.get(0).getInstance(lpparm.classLoader); // p.fvd0
            Class<?> sideDrawerItem = dwd0Classes.get(0).getInstance(lpparm.classLoader); // p.dwd0
            Class<?> propertiesClass = propertiesClasses.get(0).getInstance(lpparm.classLoader); // p.cwd0
            Class<?> onClickClass = onClickClasses.get(0).getInstance(lpparm.classLoader); // p.bwd0

            Class<?> qbpInterface = qbpInterfaceList.get(0).getInstance(lpparm.classLoader).getInterfaces()[0];
            Class<?> zpj0Interface = zpj0InterfaceList.get(0).getInstance(lpparm.classLoader).getInterfaces()[0];

            Class<?> cbpInterface = cbpInterfaceList.get(0).getInstance(lpparm.classLoader).getMethod("getOnScrubEnd").getReturnType();

//            Class<?> cbpInterface = .get(0).getInstance(lpparm.classLoader).getInterfaces()[0];

//            for(var interlace : modifyDataListClass) {
//                XposedBridge.log("[SpotifyPlus] Found Class: " + interlace);
//            }

            XposedBridge.hookMethod(invokeSuspend, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    Field a = bridge.findField(FindField.create().searchInClass(modifyDataListClass).matcher(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(int.class))).get(0).getFieldInstance(lpparm.classLoader);
                    Field d = bridge.findField(FindField.create().searchInClass(modifyDataListClass).matcher(FieldMatcher.create().modifiers(Modifier.PUBLIC).type(Object[].class))).get(0).getFieldInstance(lpparm.classLoader);

//                    int number = a.getInt(param.thisObject);
//                    if(number != 20) return;

                    Object[] originalItemsWithNull = (Object[]) d.get(param.thisObject);
                    if (originalItemsWithNull == null) return;
                    Object[] originalItems = Arrays.stream(originalItemsWithNull).filter(Objects::nonNull).toArray(Object[]::new);
                    if (originalItems.length != 4 || originalItems[0].getClass() != buttonClass) return;

                    Object newArray = Array.newInstance(buttonClass, originalItems.length + 3 + scriptSideButtons.size());

                    for (int i = 0; i < originalItems.length; i++) {
                        Array.set(newArray, i, originalItems[i]);
                    }

                    Object tempalte = originalItems[originalItems.length - 1];
                    Object tempalteLightning = originalItems[1];

                    Array.set(newArray, originalItems.length, createSideDrawerButton("Spotify Plus Settings", tempalte, buttonClass, sideDrawerItem, propertiesClass, onClickClass, qbpInterface, zpj0Interface, cbpInterface, () -> {
                        try {
                            XModuleResources modResources = References.modResources;
                            Activity activity = References.currentActivity;
                            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                            AtomicReference<View> currentDetailedSettingsPage = new AtomicReference<>();

                            int themeOverlay = R.style.Theme_SpotifyPlus;
                            Context themedCtx = new ModuleContextWrapper(activity, themeOverlay, modResources, ModuleContextWrapper.class.getClassLoader());
                            LayoutInflater inflater = LayoutInflater.from(activity).cloneInContext(themedCtx);
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

                                            //                                            try {
//                                                activity.getWindow().getDecorView().post(() -> {
//                                                    dispatcher.registerOnBackInvokedCallback(1000001, this);
//                                                });
//                                            } catch(Throwable t) { }
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

                                dispatcher.registerOnBackInvokedCallback(1000001, callback);
                            }

                            MaterialToolbar toolbar = settingsPage.findViewById(R.id.toolbar);
                            toolbar.setNavigationOnClickListener(v -> {
                                ViewParent parent = settingsPage.getParent();
                                if (parent instanceof ViewGroup) {
                                    animatePageOut((ViewGroup) parent, () -> {
                                        ((ViewGroup) parent).removeView(settingsPage);
                                    });
                                }
                            });

                            View generalSettings = settingsPage.findViewById(R.id.settings_general);
                            View lyricsSettings = settingsPage.findViewById(R.id.settings_lyrics);
                            View experimentalSettings = settingsPage.findViewById(R.id.settings_experimental);
                            View scriptingSettings = settingsPage.findViewById(R.id.settings_scripting);
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

                                create.setOnCheckedChangeListener((check, value) -> {
                                    prefs.edit().putBoolean("remove_create", value).apply();
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
                                        animatePageOut((ViewGroup) parent, () -> {
                                            ((ViewGroup) parent).removeView(view);
                                        });
                                    }
                                });

                                MaterialRadioButton visualBeautiful = view.findViewById(R.id.rb_beautiful_lyrics_anim);
                                MaterialRadioButton visualApple = view.findViewById(R.id.rb_apple_music_anim);

                                MaterialRadioButton interludeBeautiful = view.findViewById(R.id.rb_beautiful_lyrics_interlude);
                                MaterialRadioButton interludeSpotifyPlus = view.findViewById(R.id.rb_spotify_plus_interlude);
                                MaterialRadioButton interludeApple = view.findViewById(R.id.rb_apple_music_interlude);

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

                                interludeBeautiful.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Beautiful Lyrics").apply();

                                    interludeBeautiful.setChecked(true);
                                    interludeSpotifyPlus.setChecked(false);
                                    interludeApple.setChecked(false);
                                });

                                interludeSpotifyPlus.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Spotify Plus").apply();

                                    interludeBeautiful.setChecked(false);
                                    interludeSpotifyPlus.setChecked(true);
                                    interludeApple.setChecked(false);
                                });

                                interludeApple.setOnClickListener(c -> {
                                    prefs.edit().putString("lyric_interlude_duration", "Apple Music").apply();

                                    interludeBeautiful.setChecked(false);
                                    interludeSpotifyPlus.setChecked(false);
                                    interludeApple.setChecked(true);
                                });

                                MaterialSwitch background = view.findViewById(R.id.switch_enable_background);
                                MaterialSwitch lineGradient = view.findViewById(R.id.switch_enable_line_gradient);

                                MaterialSwitch sendToken = view.findViewById(R.id.switch_send_token);
                                MaterialSwitch userLyrics = view.findViewById(R.id.switch_check_user_lyrics);

                                background.setOnCheckedChangeListener((button, value) -> {
                                    prefs.edit().putBoolean("lyric_enable_background", value).apply();
                                });

                                lineGradient.setOnCheckedChangeListener((button, value) -> {
                                    prefs.edit().putBoolean("lyric_enable_line_gradient", value).apply();
                                });

                                sendToken.setOnCheckedChangeListener((button, value) -> {
                                    prefs.edit().putBoolean("lyrics_send_token", value).apply();
                                });

                                userLyrics.setOnCheckedChangeListener((button, value) -> {
                                    prefs.edit().putBoolean("lyrics_check_custom", value).apply();
                                });

                                String style = prefs.getString("lyric_animation_style", "Beautiful Lyrics");
                                visualBeautiful.setChecked(style.equals("Beautiful Lyrics"));
                                visualApple.setChecked(style.equals("Apple Music"));

                                String interludeDuration = prefs.getString("lyric_interlude_duration", "Beautiful Lyrics");
                                interludeBeautiful.setChecked(interludeDuration.equals("Beautiful Lyrics"));
                                interludeSpotifyPlus.setChecked(interludeDuration.equals("Spotify Plus"));
                                interludeApple.setChecked(interludeDuration.equals("Apple Music"));

                                background.setChecked(prefs.getBoolean("lyric_enable_background", true));
                                lineGradient.setChecked(prefs.getBoolean("lyric_enable_line_gradient", true));

                                sendToken.setChecked(prefs.getBoolean("lyrics_send_token", true));
                                userLyrics.setChecked(prefs.getBoolean("lyrics_check_custom", false));
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
                                        animatePageOut((ViewGroup) parent, () -> {
                                            ((ViewGroup) parent).removeView(view);
                                        });
                                    }
                                });

                                MaterialSwitch scrollingAnimation = view.findViewById(R.id.switch_new_scroller);

                                scrollingAnimation.setOnCheckedChangeListener((button, value) -> {
                                    prefs.edit().putBoolean("experiment_scroll", value).apply();
                                });

                                scrollingAnimation.setChecked(prefs.getBoolean("experiment_scroll", false));
                            });

                            scriptingSettings.setOnClickListener(v -> {
                                View view = inflater.inflate(R.layout.scripting_settings_page, root, false);
                                root.addView(view);
                                animatePageIn(view);
                                currentDetailedSettingsPage.set(view);

                                MaterialToolbar detailedToolbar = view.findViewById(R.id.scripting_toolbar);
                                detailedToolbar.setNavigationOnClickListener(w -> {
                                    ViewParent parent = settingsPage.getParent();
                                    if (parent instanceof ViewGroup) {
                                        animatePageOut((ViewGroup) parent, () -> {
                                            ((ViewGroup) parent).removeView(view);
                                        });
                                    }
                                });

                                MaterialButton selectDirectory = view.findViewById(R.id.btn_select_directory);

                                selectDirectory.setOnClickListener(button -> {
                                    if (activity != null && !activity.isFinishing()) {
                                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                                        activity.startActivityForResult(intent, 9072022);
                                    }
                                });
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
                                        animatePageOut((ViewGroup) parent, () -> {
                                            ((ViewGroup) parent).removeView(view);
                                        });
                                    }
                                });

                                View github = view.findViewById(R.id.open_github);

                                github.setOnClickListener(button -> {
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeNerd46/SpotifyPlus"));
                                    activity.startActivity(browserIntent);
                                });
                            });
                        } catch (Exception e) {
                            XposedBridge.log("[SpotifyPlus] Could not inflate layout: " + e.getMessage());
                            XposedBridge.log(e);
                        }
                    }));

                    Array.set(newArray, originalItems.length + 1, createSideDrawerButton("Marketplace", tempalteLightning, buttonClass, sideDrawerItem, propertiesClass, onClickClass, qbpInterface, zpj0Interface, cbpInterface, () -> XposedBridge.log("[SpotifyPlus] Hello!")));

                    int index = originalItems.length + 2;

                    for (var item : scriptSideButtons.keySet()) {
                        Runnable run = scriptSideButtons.get(item);
                        Array.set(newArray, index, createSideDrawerButton(item.second, tempalteLightning, buttonClass, sideDrawerItem, propertiesClass, onClickClass, qbpInterface, zpj0Interface, cbpInterface, run));
                        index++;
                    }

                    XposedHelpers.setObjectField(param.thisObject, d.getName(), newArray);
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
            XposedBridge.log("[SpotifyPlus] Could not find class: " + e.getMessage());
        }
    }

    private Object createSideDrawerButton(String title, Object template, Class<?> fvd0, Class<?> dwd0, Class<?> cwd0, Class<?> bwd0, Class<?> qbp, Class<?> zpj0, Class<?> cbp, Runnable onClick) {
        try {
            // Don't do this every time we create a button! Just do it once!
            var dwd0List = bridge.findField(FindField.create().searchInClass(fwd0Classes).matcher(FieldMatcher.create().type(dwd0)));
            var fieldList = bridge.findField(FindField.create().searchInClass(dwd0Classes).matcher(FieldMatcher.create().type(Object.class)));
            var bwd0List = bridge.findField(FindField.create().searchInClass(propertiesClasses).matcher(FieldMatcher.create().type(bwd0)));
            var nodeList = bridge.findField(FindField.create().searchInClass(onClickClasses).matcher(FieldMatcher.create().type(whateverThisInterfaceDoes)));
            var impressionList = bridge.findField(FindField.create().searchInClass(onClickClasses).matcher(FieldMatcher.create().type(cbp)));
            var iconList = bridge.findField(FindField.create().searchInClass(dwd0Classes).matcher(FieldMatcher.create().type(iconInterface)));
            var whateverList = bridge.findField(FindField.create().searchInClass(propertiesClasses).matcher(FieldMatcher.create().type(wwk)));

            if (dwd0List.isEmpty() || fieldList.isEmpty() || bwd0List.isEmpty() || nodeList.isEmpty() || impressionList.isEmpty() || iconList.isEmpty() || whateverList.isEmpty()) {
                XposedBridge.log("[SpotifyPlus] dwd0: " + dwd0List.size());
                XposedBridge.log("[SpotifyPlus] field: " + fieldList.size());
                XposedBridge.log("[SpotifyPlus] bwd0: " + bwd0List.size());
                XposedBridge.log("[SpotifyPlus] node: " + nodeList.size());
                XposedBridge.log("[SpotifyPlus] impression: " + impressionList.size());
                XposedBridge.log("[SpotifyPlus] icon: " + iconList.size());
                XposedBridge.log("[SpotifyPlus] whatever: " + whateverList.size());

                XposedBridge.log("[SpotifyPlus] No classes found");
                return null;
            }

            Object originalDwd0 = dwd0List.get(0).getFieldInstance(lpparm.classLoader).get(template); // p.dwd0
            Field field = fieldList.get(0).getFieldInstance(lpparm.classLoader);
            Object originalProps = field.get(originalDwd0); // p.cwd0
            String propName = field.getName();
            Object originalBwd0 = bwd0List.get(0).getFieldInstance(lpparm.classLoader).get(originalProps); // p.bwd0;
            Object originalNode = nodeList.get(0).getFieldInstance(lpparm.classLoader).get(originalBwd0);
            Object originalImpression = impressionList.get(0).getFieldInstance(lpparm.classLoader).get(originalBwd0);
            Object originalIcon = iconList.get(0).getFieldInstance(lpparm.classLoader).get(originalDwd0);
            Object iDontEvenKnowWhatThisFieldDoes = whateverList.get(0).getFieldInstance(lpparm.classLoader).get(originalProps);

            Object newOnClick = Proxy.newProxyInstance(lpparm.classLoader, new Class[]{qbp}, (proxy, method, args) -> {
                onClick.run();

                return null;
            });

            Constructor<?> bwd0Ctor = bwd0.getConstructor(zpj0, qbp, cbp);
            Constructor<?> propsCtor = cwd0.getConstructors()[0];

            int mask = 0;
            mask |= 1;
            mask |= 2;
            mask |= 4;
            mask |= 16;

            Object newInstrumentation = bwd0Ctor.newInstance(originalNode, newOnClick, originalImpression);
            Object newProps = propsCtor.newInstance(iDontEvenKnowWhatThisFieldDoes, 2131957897, "spotify:home", false, newInstrumentation, false, mask);

            XposedHelpers.setObjectField(newProps, propName, title);
            Object newDwd0 = XposedHelpers.newInstance(dwd0, originalIcon, newProps);

            return XposedHelpers.newInstance(fvd0, idToUse++, newDwd0);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
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
            var key = scriptSideButtons.keySet().stream().filter(entry -> entry.first.equals(id)).findFirst().orElse(null);

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
                context.getResources().getDisplayMetrics()
        );
    }
}
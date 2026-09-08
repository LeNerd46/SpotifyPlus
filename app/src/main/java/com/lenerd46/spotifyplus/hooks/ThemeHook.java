package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ThemeHook extends SpotifyHook {

    private final SharedPreferences prefs;
    private final Context context;
    private static volatile boolean themeEnabled = true;
    private static volatile Long originalHomeFilterChipColor;
    private static volatile Field homeFilterChipColorField;
    private static volatile Method composeDisposeMethod;
    private static volatile Method composeCreateMethod;
    private volatile Method creativeWorkGetViewMethod;
    private volatile Method encorePaletteAccessor;
    private volatile Method encorePaletteProvider;
    private static final ThreadLocal<Boolean> imageHeaderBody = new ThreadLocal<>();
    private volatile Object modernHomeFilterChipUnselectedStyle;
    private volatile Field modernHomeFilterChipProviderField;
    private volatile boolean modernHomeFilterChipProviderReplaced;

    public static volatile int BACKGROUND = 0xFF121212;
    public static volatile int BACKGROUND_HIGHLIGHT = 0xFF1F1F1F;
    public static volatile int BACKGROUND_PRESS = 0xFF000000;

    public static volatile int SURFACE = 0xFF1F1F1F;
    public static volatile int SURFACE_HIGHLIGHT = 0xFF2A2A2A;
    public static volatile int SURFACE_PRESS = 0xFF191919;

    public static volatile int TINTED = 0x1AFFFFFF;
    public static volatile int TINTED_HIGHLIGHT = 0x24FFFFFF;
    public static volatile int TINTED_PRESS = 0x36FFFFFF;

    public static volatile int TEXT = 0xFFFFFFFF;
    public static volatile int TEXT_SUBDUED = 0xFFB3B3B3;

    public static volatile int ACCENT = 0xFF1ED760;
    public static volatile int ACCENT_HIGHLIGHT = 0xFF3BE477;
    public static volatile int ACCENT_PRESS = 0xFF1ABC54;

    public static volatile int ANNOUNCEMENT = 0xFF539DF5;
    public static volatile int DECORATIVE = 0xFFFFFFFF;
    public static volatile int DECORATIVE_SUBDUED = 0xFF292929;

    public static volatile int NEGATIVE = 0xFFED2C3F;
    public static volatile int WARNING = 0xFFFFA42B;
    public static volatile int POSITIVE = 0xFF1ED760;
    public static volatile int SCRIM = 0xFF000000;
    public static volatile int ON_ACCENT = Color.BLACK;

    private static final Map<Object, Object> encoreCache = new IdentityHashMap<>();
    private static final Map<Object, Object> material3Cache = new IdentityHashMap<>();
    private static final Map<Object, Boolean> composeHosts = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, Integer> activityPaletteGenerations = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile int paletteGeneration;

    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger artworkRequestGeneration = new AtomicInteger();
    private final LruCache<String, Integer> artworkColorCache = new LruCache<>(64);

    private volatile String lastArtworkUrl;
    private final Handler paletteHandler = new Handler(Looper.getMainLooper());

    private static WeakReference<Activity> visibleActivity = new WeakReference<>(null);

    public ThemeHook(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        if (prefs.getBoolean("theme_palette_saved", false)) loadCustomTheme(prefs);
        themeEnabled = prefs.getBoolean("theme_enabled", false);
        ThemeResourcesHook.setEnabled(themeEnabled);
        ThemeResourcesHook.applyPalette(BACKGROUND, SURFACE, TINTED, TEXT, TEXT_SUBDUED, ACCENT, ON_ACCENT);
    }

    public static void setThemeEnabled(boolean enabled, ClassLoader classLoader) {
        themeEnabled = enabled;
        synchronized (encoreCache) {encoreCache.clear();}
        synchronized (material3Cache) {material3Cache.clear();}
        ThemeResourcesHook.setEnabled(enabled);
        updateHomeFilterChipColor(classLoader);
        paletteGeneration++;
        refreshComposeHosts();
        recreateVisibleActivity();
    }

    public static void applyCustomTheme(SharedPreferences prefs, ClassLoader classLoader) {
        prefs.edit().putBoolean("theme_palette_saved", true).apply();
        loadCustomTheme(prefs);
        onPaletteChanged(classLoader);
        recreateVisibleActivity();
    }

    private static void loadCustomTheme(SharedPreferences prefs) {
        BACKGROUND = prefs.getInt("theme_background", 0xFF121212);
        BACKGROUND_HIGHLIGHT = prefs.getInt("theme_background_highlight", 0xFF1F1F1F);
        BACKGROUND_PRESS = prefs.getInt("theme_background_press", 0xFF000000);
        SURFACE = prefs.getInt("theme_surface", 0xFF1F1F1F);
        SURFACE_HIGHLIGHT = prefs.getInt("theme_surface_highlight", 0xFF2A2A2A);
        SURFACE_PRESS = prefs.getInt("theme_surface_press", 0xFF191919);
        TINTED = prefs.getInt("theme_tinted", 0x1AFFFFFF);
        TINTED_HIGHLIGHT = prefs.getInt("theme_tinted_highlight", 0x24FFFFFF);
        TINTED_PRESS = prefs.getInt("theme_tinted_press", 0x36FFFFFF);
        TEXT = prefs.getInt("theme_text", 0xFFFFFFFF);
        TEXT_SUBDUED = prefs.getInt("theme_text_subdued", 0xFFB3B3B3);
        ACCENT = prefs.getInt("theme_accent", 0xFF1ED760);
        ACCENT_HIGHLIGHT = prefs.getInt("theme_accent_highlight", 0xFF3BE477);
        ACCENT_PRESS = prefs.getInt("theme_accent_press", 0xFF1ABC54);
        ANNOUNCEMENT = prefs.getInt("theme_announcement", 0xFF539DF5);
        DECORATIVE = prefs.getInt("theme_decorative", 0xFFFFFFFF);
        DECORATIVE_SUBDUED = prefs.getInt("theme_decorative_subdued", 0xFF292929);
        NEGATIVE = prefs.getInt("theme_negative", 0xFFED2C3F);
        WARNING = prefs.getInt("theme_warning", 0xFFFFA42B);
        POSITIVE = prefs.getInt("theme_positive", 0xFF1ED760);
        SCRIM = prefs.getInt("theme_scrim", 0xFF000000);
        ON_ACCENT = prefs.getInt("theme_on_accent", 0xFF000000);
    }

    @Override
    protected void hook() {
        hookComposeHosts();
        hookEncorePalette();
        hookMaterial3Palette();

        hookSideDrawerBackground();
        hookHomeShortcutCards();
        patchHomeFilterChipColor();
        hookArtworkGradients();

        hookSpotifyConnectColor();
        hookCreativeWorkAlbumHeader();
        hookModernAlbumHeader();
        hookImageHeaderChrome();
        hookImageHeaderBody();
        hookTrackRowColors();
        hookBottomNavigationGradient();

        hookVisibleActivity();

        hookTrackArtworkColor();
    }

    private void hookEncorePalette() {
        try {
            Method paletteAccessor = findEncorePaletteAccessor();
            encorePaletteAccessor = paletteAccessor;
            XposedBridge.log("[SpotifyPlus] Encore palette accessor: " + paletteAccessor);
            XposedBridge.hookMethod(paletteAccessor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object original = param.getResult();
                    if (original == null) return;

                    try {
                        param.setResult(recolorEncorePalette(original));
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
            hookEncoreLayoutPalettes(paletteAccessor.getReturnType().getName());

        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookEncoreLayoutPalettes(String paletteClassName) {
        try {
            List<MethodData> layoutThemeMethods = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().returnType(void.class).paramCount(5).addUsingString("EncoreLayoutTheme: Invalid screen dimensions (", StringMatchType.Contains)));
            if (layoutThemeMethods.size() != 1) {
                if (!layoutThemeMethods.isEmpty()) XposedBridge.log("[SpotifyPlus] Could not uniquely identify the Encore layout-theme provider: " + layoutThemeMethods);
                return;
            }
            List<String> layoutThemeParameters = layoutThemeMethods.get(0).getParamTypeNames();
            if (layoutThemeParameters.size() < 3) return;
            String composerClassName = layoutThemeParameters.get(2);
            List<MethodData> paletteResolvers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(paletteClassName).paramTypes("int", composerClassName)));
            if (paletteResolvers.isEmpty()) return;
            if (paletteResolvers.size() != 1) throw new IllegalStateException("Expected one Encore layout palette resolver for " + paletteClassName + " and " + composerClassName + ", found " + paletteResolvers);
            MethodData paletteResolverData = paletteResolvers.get(0);
            Method paletteResolver = paletteResolverData.getMethodInstance(lpparm.classLoader);
            XposedBridge.log("[SpotifyPlus] Encore layout palette resolver: " + paletteResolver);
            XposedBridge.hookMethod(paletteResolver, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object original = param.getResult();
                    if (original == null) return;
                    try {
                        param.setResult(recolorEncorePalette(original));
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
            hookEncorePaletteProviders(paletteResolverData, paletteClassName, composerClassName);
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookEncorePaletteProviders(MethodData paletteResolver, String paletteClassName, String composerClassName) throws Throwable {
        ClassData providerClass = null;
        String contentClassName = null;
        for (MethodData caller : paletteResolver.getCallers()) {
            List<String> parameters = caller.getParamTypeNames();
            if (!"void".equals(caller.getReturnType().getName()) || parameters.size() != 4 || !"int".equals(parameters.get(0)) || !composerClassName.equals(parameters.get(2)) || !"int".equals(parameters.get(3))) continue;
            if (providerClass != null && !providerClass.getName().equals(caller.getDeclaredClassName())) throw new IllegalStateException("Encore layout palette resolver has multiple provider classes: " + paletteResolver.getCallers());
            providerClass = caller.getDeclaredClass();
            contentClassName = parameters.get(1);
        }
        if (providerClass == null) return;
        List<MethodData> paletteProviders = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(providerClass)).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(void.class).paramTypes(paletteClassName, contentClassName, composerClassName, "int")));
        if (paletteProviders.isEmpty()) throw new IllegalStateException("Could not find the Encore palette providers in " + providerClass.getName());
        for (MethodData providerData : paletteProviders) {
            Method provider = providerData.getMethodInstance(lpparm.classLoader);
            encorePaletteProvider = provider;
            XposedBridge.log("[SpotifyPlus] Encore palette provider: " + provider);
            XposedBridge.hookMethod(provider, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object original = param.args[0];
                    if (original == null) return;
                    try {
                        param.args[0] = recolorEncorePalette(original);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
        }
    }

    private Method findEncorePaletteAccessor() throws Throwable {
        Set<String> paletteClassNames = new HashSet<>();
        var paletteInitializers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<clinit>").usingNumbers(4278190080L, 4294967295L, 4280229663L, 4279374354L, 4280887593L)));
        for (MethodData initializer : paletteInitializers) {
            for (MethodData invoked : initializer.getInvokes()) {
                if (invoked.isConstructor() && invoked.getParamCount() == 4 && invoked.getDeclaredClass().getFieldCount() == 4) paletteClassNames.add(invoked.getClassName());
            }
        }
        if (paletteClassNames.size() != 1) throw new IllegalStateException("Expected one Encore palette type, found " + paletteClassNames);
        String paletteClassName = paletteClassNames.iterator().next();
        ClassData accessorClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().fieldCount(1).methodCount(5).addMethod(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(paletteClassName).paramCount(1)))).single();
        return bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(accessorClass)).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(paletteClassName).paramCount(1))).single().getMethodInstance(lpparm.classLoader);
    }

    private Object recolorEncorePalette(Object original) {
        if (!themeEnabled) return original;
        synchronized (encoreCache) {
            Object cached = encoreCache.get(original);
            if (cached != null) {
                return cached;
            }

            Object oldBackgrounds = getConstructorField(original, 0, 4);
            int originalBase = findEncoreBaseColor(oldBackgrounds);

            final Object replacement;

            switch (originalBase) {
                case 0xFF121212:
                    replacement = createLightEncorePalette(original, BACKGROUND, BACKGROUND_HIGHLIGHT, BACKGROUND_PRESS);
                    break;

                case 0xFF1F1F1F:
                    replacement = createLightEncorePalette(original, SURFACE, SURFACE_HIGHLIGHT, SURFACE_PRESS);
                    break;

                case 0x1AFFFFFF:
                    replacement = createLightEncorePalette(original, TINTED, TINTED_HIGHLIGHT, TINTED_PRESS);
                    break;

                case 0xFF1ED760:
                    replacement = createBlueAccentPalette(original);
                    break;

                default:
                    replacement = original;
                    break;
            }

            encoreCache.put(original, replacement);
            return replacement;
        }
    }

    private Object createLightEncorePalette(Object original, int mainBackground, int mainHighlight, int mainPress) {
        Object oldBackgrounds = getConstructorField(original, 0, 4);
        Object oldElevated = getConstructorField(oldBackgrounds, 0, 5);
        Object oldText = getConstructorField(original, 1, 4);
        Object oldEssential = getConstructorField(original, 2, 4);
        Object oldDecorative = getConstructorField(original, 3, 4);

        Object elevated = XposedHelpers.newInstance(oldElevated.getClass(), packColor(SURFACE), packColor(SURFACE_HIGHLIGHT), packColor(SURFACE_PRESS));

        Object tinted = XposedHelpers.newInstance(oldElevated.getClass(), packColor(TINTED), packColor(TINTED_HIGHLIGHT), packColor(TINTED_PRESS));

        Object backgrounds = XposedHelpers.newInstance(oldBackgrounds.getClass(), elevated, tinted, packColor(mainBackground), packColor(mainHighlight), packColor(mainPress));

        Object text = XposedHelpers.newInstance(oldText.getClass(), packColor(TEXT), packColor(TEXT_SUBDUED), packColor(ACCENT), packColor(NEGATIVE), packColor(WARNING), packColor(POSITIVE), packColor(ANNOUNCEMENT));

        Object essential = XposedHelpers.newInstance(oldEssential.getClass(), packColor(TEXT), packColor(0xFF5E7A91), packColor(ACCENT), packColor(0xFFC0364B), packColor(0xFFB67800), packColor(0xFF0A8F69), packColor(ANNOUNCEMENT));

        Object decorative = XposedHelpers.newInstance(oldDecorative.getClass(), packColor(DECORATIVE), packColor(DECORATIVE_SUBDUED));

        return XposedHelpers.newInstance(original.getClass(), backgrounds, text, essential, decorative);
    }

    private Object createBlueAccentPalette(Object original) {
        Object oldBackgrounds = getConstructorField(original, 0, 4);
        Object oldElevated = getConstructorField(oldBackgrounds, 0, 5);
        Object oldText = getConstructorField(original, 1, 4);
        Object oldEssential = getConstructorField(original, 2, 4);
        Object oldDecorative = getConstructorField(original, 3, 4);

        Object elevated = XposedHelpers.newInstance(oldElevated.getClass(), packColor(ACCENT), packColor(ACCENT_HIGHLIGHT), packColor(ACCENT_PRESS));
        Object tinted = XposedHelpers.newInstance(oldElevated.getClass(), packColor(ACCENT), packColor(ACCENT_HIGHLIGHT), packColor(ACCENT_PRESS));
        Object backgrounds = XposedHelpers.newInstance(oldBackgrounds.getClass(), elevated, tinted, packColor(ACCENT), packColor(ACCENT_HIGHLIGHT), packColor(ACCENT_PRESS));
        Object text = XposedHelpers.newInstance(oldText.getClass(), packColor(ON_ACCENT), packColor(0xFFD8F1FF), packColor(ON_ACCENT), packColor(0xFFFFE1E6), packColor(0xFFFFF0C2), packColor(0xFFD5FFED), packColor(0xFFDCEEFF));
        Object essential = XposedHelpers.newInstance(oldEssential.getClass(), packColor(ON_ACCENT), packColor(0xFFD8F1FF), packColor(ON_ACCENT), packColor(0xFFFFE1E6), packColor(0xFFFFF0C2), packColor(0xFFD5FFED), packColor(0xFFDCEEFF));
        Object decorative = XposedHelpers.newInstance(oldDecorative.getClass(), packColor(ON_ACCENT), packColor(0xFFB9E5FA));

        return XposedHelpers.newInstance(original.getClass(), backgrounds, text, essential, decorative);
    }

    private void hookMaterial3Palette() {
        try {
            ClassData colorSchemeData = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ColorScheme(primary=", "surfaceContainerLowest="))).single();
            Method materialThemeMethod = findMaterialThemeMethod(colorSchemeData);
            XposedBridge.log("[SpotifyPlus] Material theme method: " + materialThemeMethod);
            XposedBridge.hookMethod(materialThemeMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object original = param.args[0];
                    if (original == null) return;

                    try {
                        param.args[0] = recolorMaterial3Palette(original);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });

        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private Method findMaterialThemeMethod(ClassData colorSchemeData) throws Throwable {
        String colorSchemeClassName = colorSchemeData.getName();
        List<MethodData> candidates = new ArrayList<>();
        candidates.addAll(bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(void.class).paramTypes(colorSchemeClassName, null, null, null, null, "int"))));
        candidates.addAll(bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(void.class).paramTypes(colorSchemeClassName, null, null, null, null, null, "int"))));
        if (candidates.isEmpty()) throw new IllegalStateException("Could not find the Material theme method for " + colorSchemeClassName);
        MethodData selected = null;
        for (MethodData candidate : candidates) {
            List<String> parameterTypes = candidate.getParamTypeNames();
            if (parameterTypes.size() == 7 && !"int".equals(parameterTypes.get(5))) {
                if (selected != null) throw new IllegalStateException("Found multiple direct Material theme methods: " + candidates);
                selected = candidate;
            }
        }
        if (selected == null && candidates.size() == 1) selected = candidates.get(0);
        if (selected == null) throw new IllegalStateException("Could not uniquely identify the Material theme method: " + candidates);
        return selected.getMethodInstance(lpparm.classLoader);
    }

    private Object recolorMaterial3Palette(Object original) {
        if (!themeEnabled) return original;
        synchronized (material3Cache) {
            Object cached = material3Cache.get(original);
            if (cached != null) {
                return cached;
            }

            if (!hasPackedColor(original, 0xFF121212)) {
                material3Cache.put(original, original);
                return original;
            }

            int colorCount = 0;
            for (Constructor<?> constructor : original.getClass().getDeclaredConstructors()) {
                boolean allColors = constructor.getParameterCount() >= 36;
                for (Class<?> parameterType : constructor.getParameterTypes()) allColors &= parameterType == long.class;
                if (allColors) colorCount = Math.max(colorCount, constructor.getParameterCount());
            }
            Object[] colors;
            if (colorCount == 36) {
                colors = new Object[]{packColor(ACCENT), packColor(ON_ACCENT), packColor(0xFFCBEAFF), packColor(0xFF082F49), packColor(0xFF7DD3FC), packColor(0xFF476F85), packColor(ON_ACCENT), packColor(0xFFD6EFFC), packColor(0xFF163746), packColor(0xFF5E5A92), packColor(ON_ACCENT), packColor(0xFFE5DFFF), packColor(0xFF2B2857), packColor(BACKGROUND), packColor(TEXT), packColor(SURFACE), packColor(TEXT), packColor(0xFFDCECF5), packColor(0xFF3C5668), packColor(ACCENT), packColor(0xFF233A4A), packColor(0xFFE9F5FB), packColor(0xFFBA1A1A), packColor(ON_ACCENT), packColor(0xFFFFDAD6), packColor(0xFF410002), packColor(0xFF6F8795), packColor(0xFFBFCCD4), packColor(SCRIM), packColor(ON_ACCENT), packColor(0xFFCFDDE5), packColor(0xFFEDF7FC), packColor(0xFFE4F1F7), packColor(0xFFD9EAF2), packColor(0xFFF4FAFD), packColor(ON_ACCENT)};
            } else if (colorCount == 48) {
                colors = new Object[]{packColor(ACCENT), packColor(ON_ACCENT), packColor(0xFFCBEAFF), packColor(0xFF082F49), packColor(0xFF7DD3FC), packColor(0xFF476F85), packColor(ON_ACCENT), packColor(0xFFD6EFFC), packColor(0xFF163746), packColor(0xFF5E5A92), packColor(ON_ACCENT), packColor(0xFFE5DFFF), packColor(0xFF2B2857), packColor(BACKGROUND), packColor(TEXT), packColor(SURFACE), packColor(TEXT), packColor(0xFFDCECF5), packColor(0xFF3C5668), packColor(ACCENT), packColor(0xFF233A4A), packColor(0xFFE9F5FB), packColor(0xFFBA1A1A), packColor(ON_ACCENT), packColor(0xFFFFDAD6), packColor(0xFF410002), packColor(0xFF6F8795), packColor(0xFFBFCCD4), packColor(SCRIM), packColor(ON_ACCENT), packColor(0xFFCFDDE5), packColor(0xFFEDF7FC), packColor(0xFFE4F1F7), packColor(0xFFD9EAF2), packColor(0xFFF4FAFD), packColor(ON_ACCENT), packColor(ACCENT_HIGHLIGHT), packColor(ACCENT), packColor(ON_ACCENT), packColor(ON_ACCENT), packColor(0xFFD6EFFC), packColor(0xFF476F85), packColor(ON_ACCENT), packColor(0xFF163746), packColor(0xFFE5DFFF), packColor(0xFF5E5A92), packColor(ON_ACCENT), packColor(0xFF2B2857)};
            } else {
                XposedBridge.log("[SpotifyPlus] Unsupported Material ColorScheme size: " + colorCount);
                material3Cache.put(original, original);
                return original;
            }
            Object replacement = XposedHelpers.newInstance(original.getClass(), colors);

            material3Cache.put(original, replacement);
            return replacement;
        }
    }

    private void hookSideDrawerBackground() {
        try {
            Class<?> drawerClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("mainLayoutConfig", "drawerState", "No drawer view found with gravity "))).single().getInstance(lpparm.classLoader);
            XposedBridge.hookAllConstructors(drawerClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        ViewGroup drawerLayout = (ViewGroup) param.thisObject;
                        View outer = drawerLayout.getChildCount() > 0 ? drawerLayout.getChildAt(0) : null;
                        View drawer = outer instanceof ViewGroup && ((ViewGroup) outer).getChildCount() > 0 ? ((ViewGroup) outer).getChildAt(0) : null;
                        if (drawer != null) drawer.setBackgroundColor(SURFACE);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });

        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookHomeShortcutCards() {
        try {
            int shortcutLayoutId = resourceId("layout", "shortcut_card");
            int titleId = resourceId("id", "title");
            List<MethodData> shortcutConstructors = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>").paramCount(4).usingNumbers(shortcutLayoutId)));
            for (MethodData shortcutConstructorData : shortcutConstructors) {
                Constructor<?> shortcutConstructor = shortcutConstructorData.getConstructorInstance(lpparm.classLoader);
                XposedBridge.hookMethod(shortcutConstructor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!themeEnabled) return;
                        try {
                            View card = findRootViewWithId(param.thisObject, titleId, 2);
                            if (card == null) return;
                            card.setBackgroundTintList(ColorStateList.valueOf(TINTED));
                            TextView title = card.findViewById(titleId);
                            if (title != null) title.setTextColor(TEXT);
                        } catch (Throwable throwable) {
                            XposedBridge.log(throwable);
                        }
                    }
                });
            }

        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void patchHomeFilterChipColor() {
        try {
            ClassData filterChipClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("HomeFilterChip").fieldCount(4))).single();
            List<FieldData> longFields = new ArrayList<>();
            for (FieldData field : filterChipClass.getFields()) {
                if ("long".equals(field.getTypeName()) && Modifier.isStatic(field.getModifiers())) longFields.add(field);
            }
            if (longFields.size() != 2) throw new IllegalStateException("Expected two Home filter-chip color fields, found " + longFields);
            if (patchModernHomeFilterChipStyle(filterChipClass)) {
                hookModernHomeFilterChipRenderer(filterChipClass);
            } else {
                homeFilterChipColorField = longFields.get(1).getFieldInstance(lpparm.classLoader);
                homeFilterChipColorField.setAccessible(true);
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        updateHomeFilterChipColor(lpparm.classLoader);
    }

    private boolean patchModernHomeFilterChipStyle(ClassData filterChipClass) throws Throwable {
        for (FieldData fieldData : filterChipClass.getFields()) {
            if (!Modifier.isStatic(fieldData.getModifiers()) || "long".equals(fieldData.getTypeName())) continue;
            Field field = fieldData.getFieldInstance(lpparm.classLoader);
            field.setAccessible(true);
            if (field.getType().isPrimitive()) continue;
            Object style = field.get(null);
            if (style == null) continue;
            Field styleColorSet = findIntFieldWithValue(style, 1);
            if (styleColorSet == null) continue;
            Object paletteProvider = null;
            Field paletteProviderField = null;
            Field providerColorSet = null;
            for (Field objectField : getInstanceFields(style.getClass())) {
                if (objectField.getType().isPrimitive()) continue;
                Object value = objectField.get(style);
                if (value == null) continue;
                List<Field> valueFields = getInstanceFields(value.getClass());
                int intFieldCount = 0;
                int objectFieldCount = 0;
                Field colorSet = null;
                for (Field valueField : valueFields) {
                    if (valueField.getType() == int.class) {
                        intFieldCount++;
                        if (valueField.getInt(value) == 1) colorSet = valueField;
                    } else if (!valueField.getType().isPrimitive()) {
                        objectFieldCount++;
                    }
                }
                if (intFieldCount == 1 && objectFieldCount == 3 && colorSet != null) {
                    paletteProvider = value;
                    paletteProviderField = objectField;
                    providerColorSet = colorSet;
                    break;
                }
            }
            if (paletteProvider == null) continue;
            styleColorSet.setInt(style, 2);
            providerColorSet.setInt(paletteProvider, 2);
            modernHomeFilterChipUnselectedStyle = style;
            modernHomeFilterChipProviderField = paletteProviderField;
            return true;
        }
        return false;
    }

    private void hookModernHomeFilterChipRenderer(ClassData filterChipClass) throws Throwable {
        Method renderer = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(filterChipClass)).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(void.class).paramCount(12).usingStrings("HomeFilterChip"))).single().getMethodInstance(lpparm.classLoader);
        XposedBridge.hookMethod(renderer, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (modernHomeFilterChipProviderReplaced || Boolean.TRUE.equals(param.args[3])) return;
                try {
                    Constructor<?> tintedStyleConstructor = null;
                    for (Constructor<?> constructor : param.args[1].getClass().getDeclaredConstructors()) {
                        if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0] == int.class) tintedStyleConstructor = constructor;
                    }
                    if (tintedStyleConstructor == null) return;
                    tintedStyleConstructor.setAccessible(true);
                    Object tintedStyle = tintedStyleConstructor.newInstance(2);
                    Object tintedProvider = modernHomeFilterChipProviderField.get(tintedStyle);
                    modernHomeFilterChipProviderField.set(modernHomeFilterChipUnselectedStyle, tintedProvider);
                    modernHomeFilterChipProviderReplaced = true;
                    XposedBridge.log("[SpotifyPlus] Home filter chips now use the Encore tinted palette through " + tintedStyle.getClass().getName());
                } catch (Throwable throwable) {
                    XposedBridge.log(throwable);
                }
            }
        });
    }

    private static Field findIntFieldWithValue(Object object, int value) throws Throwable {
        for (Field field : getInstanceFields(object.getClass())) {
            if (field.getType() == int.class && field.getInt(object) == value) return field;
        }
        return null;
    }

    private static void updateHomeFilterChipColor(ClassLoader classLoader) {
        try {
            Field colorField = homeFilterChipColorField;
            if (colorField == null) return;
            if (originalHomeFilterChipColor == null) originalHomeFilterChipColor = colorField.getLong(null);
            XposedHelpers.setStaticLongField(colorField.getDeclaringClass(), colorField.getName(), themeEnabled ? packColor(TINTED) : originalHomeFilterChipColor);
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookArtworkGradients() {
        hookNowPlayingGradient();
        hookAlbumHeaderGradient();
    }

    private void hookNowPlayingGradient() {
        try {
            int startColorId = resourceId("color", "bg_gradient_start_color");
            int endColorId = resourceId("color", "bg_gradient_end_color");
            Constructor<?> gradientConstructor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>").paramTypes(Context.class, AttributeSet.class, int.class).usingNumbers(startColorId, endColorId).declaredClass(ClassMatcher.create().fieldCount(1).addFieldForType(GradientDrawable.class)))).single().getConstructorInstance(lpparm.classLoader);
            XposedBridge.hookMethod(gradientConstructor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        GradientDrawable gradient = (GradientDrawable) getInstanceFieldValue(param.thisObject, GradientDrawable.class);
                        if (gradient == null) return;
                        int[] colors = {Color.TRANSPARENT, Color.TRANSPARENT, BACKGROUND};

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            gradient.setColors(colors, new float[]{0.00f, 0.88f, 1.00f});
                        } else {
                            gradient.setColors(new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, BACKGROUND});
                        }
                    } catch (Throwable throwable) {
                    }
                }
            });
        } catch (Throwable throwable) {
        }
    }

    private void hookAlbumHeaderGradient() {
        hookLegacyAlbumHeaderGradient();
        hookComposeAlbumHeaderGradient();
    }

    private void hookLegacyAlbumHeaderGradient() {
        try {
            int gradientEndId = resourceId("color", "encore_header_gradient_end");
            MethodData gradientConstructorData = null;
            for (MethodData source : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingNumbers(gradientEndId)))) {
                for (MethodData invoked : source.getInvokes()) {
                    if (!invoked.isConstructor() || invoked.getParamCount() != 1 || !List.class.getName().equals(invoked.getParamTypeNames().get(0))) continue;
                    ClassData declaredClass = invoked.getDeclaredClass();
                    if (declaredClass.getFieldCount() != 2 || declaredClass.getInterfaceCount() != 1) continue;
                    boolean hasList = false;
                    boolean hasInteger = false;
                    for (FieldData field : declaredClass.getFields()) {
                        hasList |= List.class.getName().equals(field.getTypeName());
                        hasInteger |= Integer.class.getName().equals(field.getTypeName());
                    }
                    if (!hasList || !hasInteger) continue;
                    if (gradientConstructorData != null && !gradientConstructorData.equals(invoked)) throw new IllegalStateException("Found multiple legacy header-gradient constructors");
                    gradientConstructorData = invoked;
                }
            }
            if (gradientConstructorData == null) return;
            ClassData gradientClass = gradientConstructorData.getDeclaredClass();
            String gradientInterface = gradientClass.getInterfaces().get(0).getName();
            Constructor<?> gradientConstructor = gradientConstructorData.getConstructorInstance(lpparm.classLoader);
            Constructor<?> positionedGradientConstructor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>").paramTypes(float.class, List.class).declaredClass(ClassMatcher.create().fieldCount(3).addInterface(gradientInterface).addFieldForType(float.class).addFieldForType(List.class).addFieldForType(Integer.class)))).single().getConstructorInstance(lpparm.classLoader);
            XposedBridge.hookMethod(gradientConstructor, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        List<?> original = (List<?>) param.args[0];

                        if (original == null || original.size() != 2) {
                            return;
                        }

                        Object start = original.get(0);
                        Object darkEnd = original.get(1);
                        if (start == null || darkEnd == null || start.getClass() == darkEnd.getClass()) return;
                        Object themedEnd = XposedHelpers.newInstance(darkEnd.getClass(), BACKGROUND);

                        ArrayList<Object> flowingGradient = new ArrayList<>(2);

                        flowingGradient.add(start);
                        flowingGradient.add(themedEnd);

                        param.args[0] = flowingGradient;

                    } catch (Throwable throwable) {
                    }
                }
            });

            XposedBridge.hookMethod(positionedGradientConstructor, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        List<?> original = (List<?>) param.args[1];

                        if (original == null || original.size() != 3) {
                            return;
                        }

                        Object firstStop = original.get(0);
                        Object secondStop = original.get(1);
                        Object finalStop = original.get(2);

                        Object firstColor = getOnlyObjectFieldValue(firstStop);
                        Object secondColor = getOnlyObjectFieldValue(secondStop);
                        Object finalColor = getOnlyObjectFieldValue(finalStop);
                        if (firstColor == null || secondColor == null || finalColor == null || firstColor.getClass() != secondColor.getClass() || firstColor.getClass() == finalColor.getClass()) return;
                        float firstPosition = ((Number) getInstanceFieldValue(firstStop, float.class)).floatValue();
                        float secondPosition = ((Number) getInstanceFieldValue(secondStop, float.class)).floatValue();
                        float finalPosition = ((Number) getInstanceFieldValue(finalStop, float.class)).floatValue();

                        if (firstPosition != 0.0f || secondPosition != 0.1f || finalPosition != 1.0f) {
                            return;
                        }

                        Object themedEnd = XposedHelpers.newInstance(finalColor.getClass(), BACKGROUND);

                        ArrayList<Object> themedStops = new ArrayList<>(3);
                        themedStops.add(firstStop);
                        themedStops.add(secondStop);
                        themedStops.add(XposedHelpers.newInstance(finalStop.getClass(), 1.0f, themedEnd));

                        param.args[1] = themedStops;

                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookComposeAlbumHeaderGradient() {
        try {
            int gradientStartId = resourceId("color", "encore_header_gradient_start");
            int gradientEndId = resourceId("color", "encore_header_gradient_end");
            Set<String> factoryDescriptors = new HashSet<>();
            for (MethodData source : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingNumbers(gradientStartId, gradientEndId)))) {
                for (MethodData invoked : source.getInvokes()) {
                    List<String> params = invoked.getParamTypeNames();
                    if (params.size() != 4 || !List.class.getName().equals(params.get(0)) || !"float".equals(params.get(1)) || !"float".equals(params.get(2)) || !"int".equals(params.get(3)) || !factoryDescriptors.add(invoked.getDescriptor())) continue;
                    XposedBridge.hookMethod(invoked.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!themeEnabled || !(param.args[0] instanceof List)) return;
                            try {
                                List<?> original = (List<?>) param.args[0];
                                if (original.size() < 2) return;
                                Object start = original.get(0);
                                Object end = original.get(original.size() - 1);
                                Object startValue = getInstanceFieldValue(start, long.class);
                                Object endValue = getInstanceFieldValue(end, long.class);
                                if (!(startValue instanceof Long) || !(endValue instanceof Long)) return;
                                if (unpackColor((Long) startValue) != context.getColor(gradientStartId) || unpackColor((Long) endValue) != context.getColor(gradientEndId)) return;
                                ArrayList<Object> themed = new ArrayList<>(original);
                                themed.set(themed.size() - 1, XposedHelpers.newInstance(end.getClass(), packColor(BACKGROUND)));
                                param.args[0] = themed;
                            } catch (Throwable throwable) {
                                XposedBridge.log(throwable);
                            }
                        }
                    });
                }
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookSpotifyConnectColor() {
        try {
            int connectLabelLayoutId = resourceId("layout", "connect_device_label");
            int deviceNameId = resourceId("id", "connect_device_name");
            int deviceIconId = resourceId("id", "connect_device_icon");
            ClassData connectLabelClass = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>").paramTypes(Context.class, AttributeSet.class, int.class).usingNumbers(connectLabelLayoutId))).single().getDeclaredClass();
            Method connectRenderMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(connectLabelClass)).matcher(MethodMatcher.create().returnType(void.class).paramTypes(String.class.getName(), null, "boolean", null, String.class.getName()))).single().getMethodInstance(lpparm.classLoader);
            XposedBridge.hookMethod(connectRenderMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        View label = (View) param.thisObject;
                        TextView deviceName = label.findViewById(deviceNameId);
                        ImageView deviceIcon = label.findViewById(deviceIconId);
                        if (deviceName != null) deviceName.setTextColor(ACCENT);
                        if (deviceIcon != null) deviceIcon.setColorFilter(ACCENT, PorterDuff.Mode.SRC_IN);
                    } catch (Throwable throwable) {
                    }
                }
            });
        } catch (Throwable throwable) {
        }
    }

    private void hookCreativeWorkAlbumHeader() {
        try {
            int headerLayoutId = resourceId("layout", "creative_work_header_layout");
            ClassData headerData = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>").usingNumbers(headerLayoutId))).single().getDeclaredClass();
            Class<?> headerClass = headerData.getInstance(lpparm.classLoader);
            creativeWorkGetViewMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(headerData)).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC).returnType(View.class).paramCount(0))).single().getMethodInstance(lpparm.classLoader);
            Method headerColorMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(headerData)).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(void.class).paramTypes(headerData.getName(), "int"))).single().getMethodInstance(lpparm.classLoader);
            XposedBridge.hookAllConstructors(headerClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        applyCreativeWorkAlbumHeader(param.thisObject, null);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
            // Binding can reset text styles and add metadata after construction.
            for (Method method : headerClass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || method.getParameterCount() != 1 || method.getParameterTypes()[0] != Object.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!themeEnabled) return;
                        try {
                            applyCreativeWorkAlbumHeader(param.thisObject, null);
                        } catch (Throwable throwable) {
                            XposedBridge.log(throwable);
                        }
                    }
                });
            }
            XposedBridge.hookMethod(headerColorMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        Object header = param.args[0];
                        int extractedColor = (Integer) param.args[1];
                        applyCreativeWorkAlbumHeader(header, extractedColor);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
            XposedBridge.log("[SpotifyPlus] Album header hooks installed: " + headerClass.getName());
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookModernAlbumHeader() {
        try {
            int expandedId = resourceId("layout", "expanded_header");
            int condensedId = resourceId("layout", "condensed_header");
            int backgroundId = resourceId("id", "cwp_header_artwork_background");
            Constructor<?> constructor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .name("<init>").usingNumbers(expandedId, condensedId))).single().getConstructorInstance(lpparm.classLoader);
            Class<?> headerClass = constructor.getDeclaringClass();
            Method getView = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(headerClass.getName()).returnType(View.class).paramCount(0))).single().getMethodInstance(lpparm.classLoader);
            XC_MethodHook applyHeader = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        applyAlbumHeader(param.thisObject, (View) getView.invoke(param.thisObject), null, true);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            };
            XposedBridge.hookMethod(constructor, applyHeader);
            for (Method method : headerClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class) {
                    XposedBridge.hookMethod(method, applyHeader);
                }
            }
            // The newer header receives artwork colors in a separate callback holder.
            // Its DST_OVER filter would otherwise tint the already themed gradient again.
            List<MethodData> callbacks = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType(void.class).paramTypes(Object.class).usingNumbers(backgroundId)
                    .addInvoke(MethodMatcher.create().name("setColorFilter").declaredClass("android.graphics.drawable.Drawable"))));
            for (MethodData callback : callbacks) {
                XposedBridge.hookMethod(callback.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!themeEnabled) return;
                        try {
                            Object header = findObjectOfType(param.thisObject, headerClass, 1, new IdentityHashMap<>());
                            if (header == null) return;
                            Integer color = param.args[0] instanceof Integer ? (Integer) param.args[0] : null;
                            applyAlbumHeader(header, (View) getView.invoke(header), color, true);
                        } catch (Throwable throwable) {
                            XposedBridge.log(throwable);
                        }
                    }
                });
            }
            XposedBridge.log("[SpotifyPlus] Modern album header hooks installed: " + headerClass.getName() + "; color callbacks: " + callbacks.size());
            if (callbacks.isEmpty()) XposedBridge.log("[SpotifyPlus] No modern album artwork-color callback found");
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookImageHeaderChrome() {
        try {
            int layoutId = resourceId("layout", "image_header_content");
            Constructor<?> constructor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .name("<init>").usingNumbers(layoutId))).single().getConstructorInstance(lpparm.classLoader);
            Class<?> headerClass = constructor.getDeclaringClass();
            Method getView = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(headerClass.getName()).returnType(View.class).paramCount(0))).single().getMethodInstance(lpparm.classLoader);
            int toolbarId = resourceId("id", "toolbar");
            int titleId = resourceId("id", "toolbar_title");
            int backId = resourceId("id", "back_button");
            int backBackgroundId = resourceId("id", "back_button_bg");
            XC_MethodHook applyChrome = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        View root = (View) getView.invoke(param.thisObject);
                        // R8 shares the holder with another component. Only touch the image header.
                        if (root == null || root.findViewById(backId) == null) return;
                        root.setBackgroundColor(BACKGROUND);
                        View toolbar = root.findViewById(toolbarId);
                        if (toolbar != null) toolbar.setBackgroundColor(BACKGROUND);
                        themeTextTree(root.findViewById(titleId), TEXT);
                        View back = root.findViewById(backId);
                        if (back instanceof ImageView) ((ImageView) back).setColorFilter(TEXT, PorterDuff.Mode.SRC_IN);
                        View backBackground = root.findViewById(backBackgroundId);
                        if (backBackground != null && backBackground.getBackground() != null) {
                            backBackground.getBackground().mutate().setTint(BACKGROUND);
                        }
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            };
            XposedBridge.hookMethod(constructor, applyChrome);
            for (Method method : headerClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class) {
                    XposedBridge.hookMethod(method, applyChrome);
                }
            }
            XposedBridge.log("[SpotifyPlus] Artist header chrome hook installed: " + headerClass.getName());
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void hookImageHeaderBody() {
        try {
            // The verified artist heading invokes the same scrim composable as the
            // metadata/action area. Discover it by that semantic anchor, not its name.
            MethodData scrimData = null;
            for (MethodData source : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingStrings("verified_row")))) {
                for (MethodData invoked : source.getInvokes()) {
                    List<String> types = invoked.getParamTypeNames();
                    if (!"void".equals(invoked.getReturnType().getName()) || types.size() != 7
                            || !"float".equals(types.get(1)) || !"float".equals(types.get(2))
                            || !"int".equals(types.get(5)) || !"int".equals(types.get(6))) continue;
                    if (scrimData != null && !scrimData.getDescriptor().equals(invoked.getDescriptor())) {
                        throw new IllegalStateException("Multiple image-header scrim composables found");
                    }
                    scrimData = invoked;
                }
            }
            if (scrimData == null) throw new IllegalStateException("Could not find the image-header scrim composable");
            MethodData gradientData = null;
            for (MethodData invoked : scrimData.getInvokes()) {
                List<String> types = invoked.getParamTypeNames();
                if (!invoked.isConstructor() || !types.contains(List.class.getName())
                        || Collections.frequency(types, "long") != 2) continue;
                if (gradientData != null && !gradientData.getDescriptor().equals(invoked.getDescriptor())) {
                    throw new IllegalStateException("Multiple image-header gradient constructors found");
                }
                gradientData = invoked;
            }
            if (gradientData == null) throw new IllegalStateException("Could not find the image-header gradient constructor");
            int colorsIndex = gradientData.getParamTypeNames().indexOf(List.class.getName());
            XposedBridge.hookMethod(gradientData.getConstructorInstance(lpparm.classLoader), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!themeEnabled || !Boolean.TRUE.equals(imageHeaderBody.get())) return;
                    try {
                        List<?> original = (List<?>) param.args[colorsIndex];
                        if (original == null || original.size() != 2) return;
                        int first = unpackColor((Long) getInstanceFieldValue(original.get(0), long.class));
                        int last = unpackColor((Long) getInstanceFieldValue(original.get(1), long.class));
                        if (first != 0xBF121212 || last != 0xFF121212) return;
                        ArrayList<Object> colors = new ArrayList<>(2);
                        for (Object color : original) {
                            long packed = (Long) getInstanceFieldValue(color, long.class);
                            int alpha = Color.alpha(unpackColor(packed));
                            int themed = (BACKGROUND & 0x00FFFFFF) | (alpha << 24);
                            colors.add(XposedHelpers.newInstance(color.getClass(), packColor(themed)));
                        }
                        param.args[colorsIndex] = colors;
                        imageHeaderBody.set(false);
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
            Class<?> contentInterface = scrimData.getMethodInstance(lpparm.classLoader).getParameterTypes()[3];
            XposedBridge.hookMethod(scrimData.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setObjectExtra("previousImageHeaderBody", imageHeaderBody.get());
                    // The photo uses 0 -> .75; only the body uses .75 -> 1.
                    imageHeaderBody.set(themeEnabled && (Float) param.args[1] == 0.75f && (Float) param.args[2] == 1.0f);
                    Object content = param.args[3];
                    Method provider = encorePaletteProvider;
                    if (Boolean.TRUE.equals(imageHeaderBody.get()) && content != null && provider != null && encorePaletteAccessor != null
                            && provider.getParameterTypes()[1].isInstance(content)) {
                        param.args[3] = Proxy.newProxyInstance(lpparm.classLoader, new Class<?>[]{contentInterface}, new ImageHeaderBodyContent(content));
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Boolean previous = (Boolean) param.getObjectExtra("previousImageHeaderBody");
                    if (previous == null) imageHeaderBody.remove();
                    else imageHeaderBody.set(previous);
                }
            });
            XposedBridge.log("[SpotifyPlus] Artist header body hook installed: " + scrimData.getDescriptor());
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private final class ImageHeaderBodyContent implements InvocationHandler {
        private final Object content;

        private ImageHeaderBodyContent(Object content) {
            this.content = content;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                if ("equals".equals(method.getName())) return proxy == args[0];
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                return "SpotifyPlus image header body";
            }
            try {
                if (!themeEnabled) return method.invoke(content, args);
                Object palette = encorePaletteAccessor.invoke(null, args[0]);
                Object themed = createLightEncorePalette(palette, BACKGROUND, BACKGROUND_HIGHLIGHT, BACKGROUND_PRESS);
                // Provide a real composition local so independently recomposing buttons
                // and metadata retain their theme after this invocation returns.
                encorePaletteProvider.invoke(null, themed, content, args[0], 0);
                return null;
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    private void hookTrackRowColors() {
        try {
            Class<?> entityTitleClass = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .name("<init>").usingNumbers(resourceId("layout", "entity_title_view"), resourceId("id", "title_text"))))
                    .single().getConstructorInstance(lpparm.classLoader).getDeclaringClass();
            int textBase = resourceId("attr", "textBase");
            int textSubdued = resourceId("attr", "textSubdued");
            int textAccent = resourceId("attr", "textBrightAccent");
            Set<String> resolvers = new HashSet<>();
            // Modern entity titles resolve an attribute every time their playback
            // state changes. Keep that state mapping, but supply our theme colors.
            for (MethodData renderer : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .usingNumbers(textBase, textSubdued, textAccent)))) {
                for (MethodData invoked : renderer.getInvokes()) {
                    if (!"int".equals(invoked.getReturnType().getName())
                            || !invoked.getParamTypeNames().equals(java.util.Arrays.asList("int", View.class.getName()))
                            || !resolvers.add(invoked.getDescriptor())) continue;
                    XposedBridge.hookMethod(invoked.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!themeEnabled || !entityTitleClass.isInstance(param.args[1])) return;
                            int attr = (Integer) param.args[0];
                            if (attr == textBase) param.setResult(TEXT);
                            else if (attr == textSubdued) param.setResult(TEXT_SUBDUED);
                            else if (attr == textAccent) param.setResult(ACCENT);
                        }
                    });
                }
            }
            if (resolvers.isEmpty()) throw new IllegalStateException("Could not find the entity title color resolver");
            XposedBridge.log("[SpotifyPlus] Entity title color hook installed: " + entityTitleClass.getName());
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        try {
            // Album tracks and artist Popular tracks both inflate this inner row.
            int rowLayoutId = resourceId("layout", "row_layout");
            int titleId = resourceId("id", "title");
            int subtitleId = resourceId("id", "subtitle");
            Method rowFactory = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramTypes(LayoutInflater.class).usingNumbers(rowLayoutId))).single().getMethodInstance(lpparm.classLoader);
            XposedBridge.hookMethod(rowFactory, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        View row = findRootViewWithId(param.getResult(), titleId, 1);
                        if (row == null) return;
                        TextView title = row.findViewById(titleId);
                        TextView subtitle = row.findViewById(subtitleId);
                        if (title != null) title.setTextColor(trackTextColors(TEXT, true));
                        if (subtitle != null) subtitle.setTextColor(trackTextColors(TEXT_SUBDUED, false));
                    } catch (Throwable throwable) {
                        XposedBridge.log(throwable);
                    }
                }
            });
            XposedBridge.log("[SpotifyPlus] Shared track row hook installed: " + rowFactory);
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        try {
            int titleColorId = resourceId("color", "encore_row_title");
            int subtitleColorId = resourceId("color", "encore_row_subtitle");
            Set<String> hooked = new HashSet<>();
            // Spotify also reads this selector when building styled row text. Hook the
            // appcompat resolver so its cached XML selector cannot restore white text.
            for (MethodData source : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingNumbers(titleColorId)))) {
                for (MethodData invoked : source.getInvokes()) {
                    if (!ColorStateList.class.getName().equals(invoked.getReturnType().getName())
                            || !invoked.getParamTypeNames().equals(java.util.Arrays.asList(Context.class.getName(), "int"))
                            || !hooked.add(invoked.getDescriptor())) continue;
                    XposedBridge.hookMethod(invoked.getMethodInstance(lpparm.classLoader), new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!themeEnabled) return;
                            int id = (Integer) param.args[1];
                            if (id == titleColorId) param.setResult(trackTextColors(TEXT, true));
                            else if (id == subtitleColorId) param.setResult(trackTextColors(TEXT_SUBDUED, false));
                        }
                    });
                }
            }
            if (hooked.isEmpty()) throw new IllegalStateException("Could not find the track color-state resolver");
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private static ColorStateList trackTextColors(int normal, boolean accented) {
        int disabled = (normal & 0x00FFFFFF) | (Math.round(Color.alpha(normal) * 0.5f) << 24);
        return new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled}, {android.R.attr.state_activated},
                {android.R.attr.state_selected}, {}
        }, new int[]{disabled, accented ? ACCENT : normal, accented ? ACCENT : normal, normal});
    }

    private void hookBottomNavigationGradient() {
        try {
            Class<?> gradientViewClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("topColor", "getTopColor()Ljava/lang/Integer;", "bottomColor", "getBottomColor()Ljava/lang/Integer;", "easing"))).single().getInstance(lpparm.classLoader);
            Constructor<?> gradientConstructor = gradientViewClass.getDeclaredConstructor(Context.class, AttributeSet.class);
            XposedBridge.hookMethod(gradientConstructor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!themeEnabled) return;
                    try {
                        ((View) param.thisObject).setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, BACKGROUND}));
                    } catch (Throwable throwable) {
                    }
                }
            });
        } catch (Throwable throwable) {
        }
    }

    public static void onPaletteChanged(ClassLoader classLoader) {
        synchronized (encoreCache) {
            encoreCache.clear();
        }

        synchronized (material3Cache) {
            material3Cache.clear();
        }

        ThemeResourcesHook.applyPalette(BACKGROUND, SURFACE, TINTED, TEXT, TEXT_SUBDUED, ACCENT, ON_ACCENT);

        updateHomeFilterChipColor(classLoader);

        paletteGeneration++;
        refreshComposeHosts();
    }

    private void hookComposeHosts() {
        try {
            ClassData composeHostData = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("Cannot add views to ", "; only Compose content is supported"))).single();
            Class<?> composeHostClass = composeHostData.getInstance(lpparm.classLoader);
            composeCreateMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(composeHostData)).matcher(MethodMatcher.create().returnType(void.class).paramCount(0).addUsingString("createComposition requires", StringMatchType.Contains))).single().getMethodInstance(lpparm.classLoader);
            composeDisposeMethod = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(composeHostData)).matcher(MethodMatcher.create().returnType(void.class).paramCount(0).addInvoke(MethodMatcher.create().name("requestLayout").returnType(void.class).paramCount(0)))).single().getMethodInstance(lpparm.classLoader);
            XposedBridge.hookAllConstructors(composeHostClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    composeHosts.put(param.thisObject, Boolean.TRUE);
                }
            });

        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void applyCreativeWorkAlbumHeader(Object header, Integer extractedColor) throws Throwable {
        if (!themeEnabled) return;
        Method getViewMethod = creativeWorkGetViewMethod;
        if (getViewMethod == null) return;
        View appBar = (View) getViewMethod.invoke(header);
        applyAlbumHeader(header, appBar, extractedColor, false);
    }

    private void applyAlbumHeader(Object header, View appBar, Integer extractedColor, boolean modern) {
        if (!themeEnabled) return;
        if (appBar == null) return;
        View artworkBackground = appBar.findViewById(resourceId("id", modern ? "cwp_header_artwork_background" : "artwork_background"));

        if (extractedColor != null) XposedHelpers.setAdditionalInstanceField(header, "spotifyplus.headerColor", extractedColor);
        else extractedColor = (Integer) XposedHelpers.getAdditionalInstanceField(header, "spotifyplus.headerColor");
        // Match playlist headers: preserve the artwork color at the top and
        // replace only the old opaque #121212 endpoint with the theme background.
        int start = extractedColor == null ? BACKGROUND : extractedColor | 0xFF000000;
        int[] colors = {start, BACKGROUND};
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        if (artworkBackground != null) artworkBackground.setBackground(gradient);
        appBar.setBackgroundColor(BACKGROUND);
        View toolbar = appBar.findViewById(resourceId("id", "toolbar"));
        if (toolbar != null) toolbar.setBackgroundColor(BACKGROUND);
        themeTextTree(appBar.findViewById(resourceId("id", modern ? "cwp_header_title" : "title")), TEXT);
        themeTextTree(appBar.findViewById(resourceId("id", modern ? "cwp_header_creatorsRow" : "creator")), TEXT);
        themeTextTree(appBar.findViewById(resourceId("id", "toolbar_title")), TEXT);
        View metadata = appBar.findViewById(resourceId("id", modern ? "cwp_header_metadataRow" : "metadata_container"));
        themeTextTree(metadata, TEXT_SUBDUED);
        themeTextTree(appBar.findViewById(resourceId("id", modern ? "cwp_header_preTitle" : "preTitle")), ACCENT);
    }

    private static void themeTextTree(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) themeTextTree(group.getChildAt(i), color);
        }
    }

    private static void refreshComposeHosts() {
        List<Object> hosts;

        synchronized (composeHosts) {
            hosts = new ArrayList<>(composeHosts.keySet());
        }

        int refreshed = 0;

        for (Object host : hosts) {
            if (host == null) {
                continue;
            }

            try {
                boolean attached = host instanceof View && ((View) host).isAttachedToWindow();
                Method disposeMethod = composeDisposeMethod;
                Method createMethod = composeCreateMethod;
                if (disposeMethod == null || createMethod == null) continue;
                disposeMethod.invoke(host);
                if (attached) createMethod.invoke(host);

                refreshed++;
            } catch (Throwable throwable) {
                XposedBridge.log(throwable);
            }
        }

    }

    private void hookTrackArtworkColor() {
        try {
            Class<?> playerStateClass = XposedHelpers.findClass("com.spotify.player.model.PlayerState", lpparm.classLoader);
            Class<?> contextTrackClass = XposedHelpers.findClass("com.spotify.player.model.ContextTrack", lpparm.classLoader);
            var playerConsumerMethods = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().returnType(void.class).paramTypes(Object.class).usingStrings("image_url").declaredClass(ClassMatcher.create().fieldCount(2).addInterface("io.reactivex.rxjava3.functions.Consumer").addFieldForType(int.class))));
            if (playerConsumerMethods.isEmpty()) throw new IllegalStateException("[ThemeHook/DexKit] Could not find a two-field Rx Consumer that reads image_url from player state");
            XposedBridge.log("[SpotifyPlus] Auto-theme player-state consumers: " + playerConsumerMethods.stream().map(MethodData::getDescriptor).collect(java.util.stream.Collectors.joining(", ")));
            Set<String> hookedDescriptors = new HashSet<>();
            for (MethodData playerConsumerMethod : playerConsumerMethods) {
                if (!hookedDescriptors.add(playerConsumerMethod.getDescriptor())) continue;
                Method consumerMethod = playerConsumerMethod.getMethodInstance(lpparm.classLoader);
                boolean metadataFlatteningConsumer = getInstanceFields(consumerMethod.getDeclaringClass()).stream().anyMatch(field -> field.getType() == Object.class);
                XposedBridge.hookMethod(consumerMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Map<?, ?> metadata = findCurrentTrackMetadata(param.args[0], playerStateClass, contextTrackClass);
                            if (metadata == null && metadataFlatteningConsumer) metadata = findMapWithKey(param.args[0], "image_url", 4, new IdentityHashMap<>());
                            if (metadata == null) return;
                            Object imageValue = metadata.get("image_url");
                            if (!(imageValue instanceof String) || ((String) imageValue).isEmpty()) return;
                            String imageUrl = (String) imageValue;
                            Integer fallbackColor = null;
                            Object colorValue = metadata.get("extracted_color");
                            if (colorValue instanceof String && !((String) colorValue).isEmpty()) {
                                String encodedColor = (String) colorValue;
                                try {
                                    fallbackColor = Color.parseColor(encodedColor.startsWith("#") ? encodedColor : "#" + encodedColor);
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                            requestArtworkTheme(imageUrl, fallbackColor);
                        } catch (Throwable throwable) {
                            XposedBridge.log(throwable);
                        }
                    }
                });
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private void requestArtworkTheme(String spotifyImageUrl, Integer fallbackColor) {
        if (!themeEnabled || !prefs.getBoolean("auto_theme", false)) {
            lastArtworkUrl = null;
            artworkRequestGeneration.incrementAndGet();
            return;
        }
        String downloadUrl = normalizeArtworkUrl(spotifyImageUrl);

        if (downloadUrl == null) {
            if (fallbackColor != null) {
                paletteHandler.post(() -> applyArtworkTheme(fallbackColor));
            }

            return;
        }

        if (downloadUrl.equals(lastArtworkUrl)) {
            return;
        }

        lastArtworkUrl = downloadUrl;

        int generation = artworkRequestGeneration.incrementAndGet();

        Integer cachedColor = artworkColorCache.get(downloadUrl);

        if (cachedColor != null) {
            paletteHandler.post(() -> {
                if (generation == artworkRequestGeneration.get()) {
                    applyArtworkTheme(cachedColor);
                }
            });

            return;
        }

        artworkExecutor.execute(() -> {
            Integer selectedColor = null;

            try {
                Bitmap artwork = downloadArtwork(downloadUrl);

                if (artwork != null) {
                    selectedColor = selectArtworkColor(artwork);

                    artwork.recycle();
                }
            } catch (Throwable throwable) {
                XposedBridge.log(throwable);
            }

            if (selectedColor == null) {
                selectedColor = fallbackColor;
            }

            if (selectedColor == null) {
                return;
            }

            artworkColorCache.put(downloadUrl, selectedColor);

            final int result = selectedColor;

            paletteHandler.post(() -> {
                if (generation != artworkRequestGeneration.get()) {
                    return;
                }


                applyArtworkTheme(result);
            });
        });
    }

    private static String normalizeArtworkUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }

        String trimmed = imageUrl.trim();

        String spotifyPrefix = "spotify:image:";

        if (trimmed.startsWith(spotifyPrefix)) {
            String imageId = trimmed.substring(spotifyPrefix.length());

            if (imageId.isEmpty()) {
                return null;
            }

            return "https://i.scdn.co/image/" + imageId;
        }

        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }


        return null;
    }

    private static Bitmap downloadArtwork(String imageUrl) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();

            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "SpotifyPlus/ArtworkTheme");

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Artwork request returned HTTP " + status);
            }

            try (InputStream stream = connection.getInputStream()) {
                Bitmap decoded = BitmapFactory.decodeStream(stream);

                if (decoded == null) {
                    return null;
                }

                int width = decoded.getWidth();
                int height = decoded.getHeight();
                int largest = Math.max(width, height);

                if (largest <= 64) {
                    return decoded;
                }

                float scale = 64.0f / largest;

                int scaledWidth = Math.max(1, Math.round(width * scale));

                int scaledHeight = Math.max(1, Math.round(height * scale));

                Bitmap scaled = Bitmap.createScaledBitmap(decoded, scaledWidth, scaledHeight, true);

                if (scaled != decoded) {
                    decoded.recycle();
                }

                return scaled;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int selectArtworkColor(Bitmap bitmap) {
        final int hueBuckets = 24;
        final int saturationBuckets = 4;
        final int valueBuckets = 4;
        final int bucketCount = hueBuckets * saturationBuckets * valueBuckets;

        int[] populations = new int[bucketCount];
        long[] redTotals = new long[bucketCount];
        long[] greenTotals = new long[bucketCount];
        long[] blueTotals = new long[bucketCount];

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        float[] hsv = new float[3];

        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 200) {
                continue;
            }

            Color.colorToHSV(pixel, hsv);

            float hue = hsv[0];
            float saturation = hsv[1];
            float value = hsv[2];

            if (value < 0.07f) {
                continue;
            }

            if (value > 0.94f && saturation < 0.14f) {
                continue;
            }

            int hueBucket = Math.min(hueBuckets - 1, (int) (hue / 360.0f * hueBuckets));
            int saturationBucket = Math.min(saturationBuckets - 1, (int) (saturation * saturationBuckets));
            int valueBucket = Math.min(valueBuckets - 1, (int) (value * valueBuckets));
            int bucket = hueBucket * saturationBuckets * valueBuckets + saturationBucket * valueBuckets + valueBucket;

            populations[bucket]++;
            redTotals[bucket] += Color.red(pixel);
            greenTotals[bucket] += Color.green(pixel);
            blueTotals[bucket] += Color.blue(pixel);
        }

        int bestBucket = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int population = populations[bucket];

            if (population == 0) {
                continue;
            }

            int red = (int) (redTotals[bucket] / population);
            int green = (int) (greenTotals[bucket] / population);
            int blue = (int) (blueTotals[bucket] / population);
            int averageColor = Color.rgb(red, green, blue);

            Color.colorToHSV(averageColor, hsv);

            double saturationWeight = 0.70 + hsv[1] * 0.85;
            double brightnessWeight = 1.15 - Math.abs(hsv[2] - 0.55) * 0.45;

            double score = population * saturationWeight * brightnessWeight;

            if (score > bestScore) {
                bestScore = score;
                bestBucket = bucket;
            }
        }

        if (bestBucket < 0) {
            /*
             * Unusual fully transparent or monochrome image.
             */
            return averageBitmapColor(pixels);
        }

        int population = populations[bestBucket];

        return Color.rgb((int) (redTotals[bestBucket] / population), (int) (greenTotals[bestBucket] / population), (int) (blueTotals[bestBucket] / population));
    }

    private static int averageBitmapColor(int[] pixels) {
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;

        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 200) {
                continue;
            }

            red += Color.red(pixel);
            green += Color.green(pixel);
            blue += Color.blue(pixel);
            count++;
        }

        if (count == 0) {
            return 0xFF303030;
        }

        return Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    private void applyArtworkTheme(int artworkColor) {
        if (!themeEnabled || !prefs.getBoolean("auto_theme", false)) return;
        generateTheme(artworkColor, prefs.getString("auto_theme_mode", "neutral"), lpparm.classLoader);
    }

    private static int blendColors(int first, int second, float secondAmount) {
        float firstAmount = 1.0f - secondAmount;

        return Color.rgb(Math.round(Color.red(first) * firstAmount + Color.red(second) * secondAmount), Math.round(Color.green(first) * firstAmount + Color.green(second) * secondAmount), Math.round(Color.blue(first) * firstAmount + Color.blue(second) * secondAmount));
    }

    private void hookVisibleActivity() {
        try {
            Application application = context instanceof Application ? (Application) context : null;
            if (application == null && context.getApplicationContext() instanceof Application) application = (Application) context.getApplicationContext();
            if (application == null) throw new IllegalStateException("Could not obtain Spotify's Application for activity lifecycle tracking");
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity activity) {
                    if (!context.getPackageName().equals(activity.getPackageName())) return;
                    visibleActivity = new WeakReference<>(activity);
                    boolean stale;
                    synchronized (activityPaletteGenerations) {
                        Integer appliedGeneration = activityPaletteGenerations.get(activity);
                        if (appliedGeneration == null) {
                            activityPaletteGenerations.put(activity, paletteGeneration);
                            stale = false;
                        } else {
                            stale = appliedGeneration != paletteGeneration;
                            if (stale) activityPaletteGenerations.put(activity, paletteGeneration);
                        }
                    }
                    if (stale) paletteHandler.post(() -> recreateActivity(activity));
                }
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
                @Override
                public void onActivityStarted(Activity activity) {}
                @Override
                public void onActivityPaused(Activity activity) {}
                @Override
                public void onActivityStopped(Activity activity) {}
                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override
                public void onActivityDestroyed(Activity activity) {}
            });
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private static void recreateVisibleActivity() {
        Activity activity = visibleActivity.get();

        if (activity == null) {
            return;
        }

        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        synchronized (activityPaletteGenerations) {
            activityPaletteGenerations.put(activity, paletteGeneration);
        }

        recreateActivity(activity);
    }

    private static void recreateActivity(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.getTheme().rebase();
            }

            if (BeautifulLyricsHook.isOverlayShowing(activity)) {
                View content = activity.findViewById(android.R.id.content);
                if (content != null) {
                    content.requestLayout();
                    content.invalidate();
                }
                activity.getWindow().getDecorView().invalidate();
                return;
            }


            RemoveCreateButtonHook.resetSettingsOverlayState();
            activity.recreate();
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private static int bestTextColor(int background) {
        int lightText = 0xFFF8FAFC;
        int darkText = 0xFF101418;

        double lightContrast = contrastRatio(lightText, background);
        double darkContrast = contrastRatio(darkText, background);

        return lightContrast >= darkContrast ? lightText : darkText;
    }

    private static int ensureContrast(int foreground, int background, int contrastTarget, double minimumRatio) {
        int result = foreground;

        for (int i = 0; i < 20 && contrastRatio(result, background) < minimumRatio; i++) {
            result = blendColors(result, contrastTarget, 0.10f);
        }

        return result;
    }

    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);

        double lighter = Math.max(firstLuminance, secondLuminance);
        double darker = Math.min(firstLuminance, secondLuminance);

        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int color) {
        double red = linearColorComponent(Color.red(color) / 255.0);
        double green = linearColorComponent(Color.green(color) / 255.0);
        double blue = linearColorComponent(Color.blue(color) / 255.0);

        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearColorComponent(double component) {
        return component <= 0.04045 ? component / 12.92 : Math.pow((component + 0.055) / 1.055, 2.4);
    }

    private int resourceId(String type, String name) {
        int identifier = context.getResources().getIdentifier(name, type, context.getPackageName());
        if (identifier == 0) throw new IllegalStateException("Missing Spotify resource " + type + "/" + name);
        return identifier;
    }

    private static List<Field> getInstanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static Object getInstanceFieldValue(Object object, Class<?> fieldType) throws Throwable {
        for (Field field : getInstanceFields(object.getClass())) {
            if (field.getType() == fieldType || (!fieldType.isPrimitive() && fieldType.isAssignableFrom(field.getType()))) return field.get(object);
        }
        return null;
    }

    private static Object getOnlyObjectFieldValue(Object object) throws Throwable {
        Object result = null;
        for (Field field : getInstanceFields(object.getClass())) {
            if (field.getType().isPrimitive()) continue;
            Object value = field.get(object);
            if (value == null) continue;
            if (result != null) throw new IllegalStateException("Expected one object field in " + object.getClass());
            result = value;
        }
        return result;
    }

    private static Object getConstructorField(Object object, int parameterIndex, int parameterCount) {
        try {
            Constructor<?> matched = null;
            for (Constructor<?> constructor : object.getClass().getDeclaredConstructors()) {
                if (constructor.getParameterCount() == parameterCount) {
                    if (matched != null) throw new IllegalStateException("Multiple " + parameterCount + "-argument constructors in " + object.getClass());
                    matched = constructor;
                }
            }
            if (matched == null) throw new IllegalStateException("No " + parameterCount + "-argument constructor in " + object.getClass());
            Object value = getInstanceFieldValue(object, matched.getParameterTypes()[parameterIndex]);
            if (value == null) throw new IllegalStateException("Could not map constructor parameter " + parameterIndex + " in " + object.getClass());
            return value;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }

    private static int findEncoreBaseColor(Object backgrounds) {
        try {
            for (Field field : getInstanceFields(backgrounds.getClass())) {
                if (field.getType() != long.class) continue;
                int color = unpackColor(field.getLong(backgrounds));
                if (color == 0xFF121212 || color == 0xFF1F1F1F || color == 0x1AFFFFFF || color == 0xFF1ED760) return color;
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        return 0;
    }

    private static boolean hasPackedColor(Object object, int color) {
        try {
            for (Field field : getInstanceFields(object.getClass())) {
                if (field.getType() == long.class && unpackColor(field.getLong(object)) == color) return true;
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        return false;
    }

    private static View findRootViewWithId(Object object, int id, int depth) {
        return findRootViewWithId(object, id, depth, new IdentityHashMap<>());
    }

    private static View findRootViewWithId(Object object, int id, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (object == null || visited.put(object, Boolean.TRUE) != null) return null;
        if (object instanceof View) {
            View view = (View) object;
            if (view.findViewById(id) != null) {
                View root = view;
                ViewParent parent = root.getParent();
                while (parent instanceof View) {
                    root = (View) parent;
                    parent = root.getParent();
                }
                return root;
            }
        }
        if (depth == 0) return null;
        try {
            for (Field field : getInstanceFields(object.getClass())) {
                if (field.getType().isPrimitive()) continue;
                Object value = field.get(object);
                if (value == null || value instanceof Class || value instanceof String || value instanceof Number || value instanceof Collection || value instanceof Map) continue;
                View root = findRootViewWithId(value, id, depth - 1, visited);
                if (root != null) return root;
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        return null;
    }

    private static Map<?, ?> findMapWithKey(Object object, String key, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (object == null || visited.put(object, Boolean.TRUE) != null) return null;
        if (object instanceof Map) return ((Map<?, ?>) object).containsKey(key) ? (Map<?, ?>) object : null;
        if (depth == 0 || object instanceof Class || object instanceof String || object instanceof Number || object instanceof Boolean || object instanceof Character || object instanceof Context || object instanceof View || object.getClass().isEnum()) return null;
        if (object instanceof Collection) {
            for (Object value : (Collection<?>) object) {
                Map<?, ?> found = findMapWithKey(value, key, depth - 1, visited);
                if (found != null) return found;
            }
            return null;
        }
        try {
            for (Field field : getInstanceFields(object.getClass())) {
                if (field.getType().isPrimitive()) continue;
                Map<?, ?> found = findMapWithKey(field.get(object), key, depth - 1, visited);
                if (found != null) return found;
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        return null;
    }

    private static Map<?, ?> findCurrentTrackMetadata(Object event, Class<?> playerStateClass, Class<?> contextTrackClass) {
        Object playerState = findObjectOfType(event, playerStateClass, 3, new IdentityHashMap<>());
        if (playerState == null) return null;
        Object currentTrack = findCurrentTrackFromAccessors(playerState, contextTrackClass);
        if (currentTrack == null) currentTrack = findObjectOfType(playerState, contextTrackClass, 3, new IdentityHashMap<>());
        if (currentTrack == null) return null;
        return findMapWithKey(currentTrack, "image_url", 2, new IdentityHashMap<>());
    }

    private static Object findCurrentTrackFromAccessors(Object playerState, Class<?> contextTrackClass) {
        for (Method method : playerState.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType().isPrimitive() || method.getReturnType() == String.class || Collection.class.isAssignableFrom(method.getReturnType()) || Map.class.isAssignableFrom(method.getReturnType())) continue;
            try {
                method.setAccessible(true);
                Object currentTrack = findObjectOfType(method.invoke(playerState), contextTrackClass, 2, new IdentityHashMap<>());
                if (currentTrack != null) return currentTrack;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object findObjectOfType(Object object, Class<?> wantedType, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (object == null || visited.put(object, Boolean.TRUE) != null) return null;
        if (wantedType.isInstance(object)) return object;
        if (depth == 0 || object instanceof Class || object instanceof String || object instanceof Number || object instanceof Boolean || object instanceof Character || object instanceof Context || object instanceof View || object instanceof Collection || object instanceof Map || object.getClass().isEnum()) return null;
        try {
            for (Field field : getInstanceFields(object.getClass())) {
                if (field.getType().isPrimitive()) continue;
                Object found = findObjectOfType(field.get(object), wantedType, depth - 1, visited);
                if (found != null) return found;
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        return null;
    }

    private static long packColor(int argb) {
        return (argb & 0xFFFFFFFFL) << 32;
    }

    private static int unpackColor(long composeColor) {
        return (int) (composeColor >>> 32);
    }

    public static void generateTheme(int baseColor, String mode, ClassLoader classLoader) {
        int background = adjustBaseColor(baseColor | 0xFF000000, mode);
        int text = bestTextColor(background);
        int textSubdued = ensureContrast(blendColors(text, background, 0.32f), background, text, 4.5);

        float surfaceAmount = "light".equals(mode) ? 0.035f : "dark".equals(mode) ? 0.09f : 0.07f;
        int surface = blendColors(background, text, surfaceAmount);
        int surfaceHighlight = blendColors(background, text, surfaceAmount + 0.05f);
        int surfacePress = blendColors(background, text, surfaceAmount + 0.11f);

        int accent = createThemeAccent(background, text, mode);
        int onAccent = bestTextColor(accent);

        int tinted = blendColors(background, accent, "neutral".equals(mode) ? 0.24f : 0.20f);
        int tintedHighlight = blendColors(tinted, text, 0.08f);
        int tintedPress = blendColors(tinted, text, 0.15f);

        BACKGROUND = background;
        BACKGROUND_HIGHLIGHT = blendColors(background, text, 0.08f);
        BACKGROUND_PRESS = blendColors(background, text, 0.15f);

        SURFACE = surface;
        SURFACE_HIGHLIGHT = surfaceHighlight;
        SURFACE_PRESS = surfacePress;

        TINTED = tinted;
        TINTED_HIGHLIGHT = tintedHighlight;
        TINTED_PRESS = tintedPress;

        TEXT = text;
        TEXT_SUBDUED = textSubdued;

        ACCENT = accent;
        ACCENT_HIGHLIGHT = blendColors(accent, onAccent, 0.12f);
        ACCENT_PRESS = blendColors(accent, onAccent, 0.22f);

        ON_ACCENT = onAccent;
        ANNOUNCEMENT = accent;
        DECORATIVE = accent;
        DECORATIVE_SUBDUED = blendColors(background, accent, 0.42f);

        onPaletteChanged(classLoader);
        recreateVisibleActivity();
    }

    private static int adjustBaseColor(int color, String mode) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        switch (mode) {
            case "light": {
                hsv[1] = Math.min(hsv[1], 0.42f);
                hsv[2] = Math.max(hsv[2], 0.90f);
                break;
            }

            case "dark": {
                hsv[1] = Math.max(hsv[1], 0.30f);
                hsv[2] = Math.min(hsv[2], 0.24f);
                break;
            }

            default: {
                hsv[1] = Math.max(hsv[1], 0.18f);
                hsv[2] = Math.max(0.16f, Math.min(hsv[2], 0.86f));
                break;
            }
        }

        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static int createThemeAccent(int background, int contrastTarget, String mode) {
        float[] hsv = new float[3];
        Color.colorToHSV(background, hsv);

        hsv[1] = Math.max(hsv[1], 0.48f);

        switch (mode) {
            case "light":
                hsv[2] = Math.min(Math.max(hsv[2], 0.52f), 0.68f);
                break;

            case "dark":
                hsv[2] = Math.max(hsv[2], 0.78f);
                break;

            default:
                hsv[2] = relativeLuminance(background) < 0.42 ? Math.max(hsv[2], 0.76f) : Math.min(hsv[2], 0.48f);
                break;
        }

        return ensureContrast(Color.HSVToColor(hsv), background, contrastTarget, 3.0);
    }
}

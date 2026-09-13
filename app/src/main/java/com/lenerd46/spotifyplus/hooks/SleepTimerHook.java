package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XModuleResources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.lenerd46.spotifyplus.ModuleContextWrapper;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyBottomSheet;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.*;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SleepTimerHook extends SpotifyHook {
    private Object customDuration;
    List<SleepTimerInfo> presets;
    private WeakReference<Activity> lastActivity = new WeakReference<>(null);
    private final Context context;

    public SleepTimerHook(Context context) {
        this.context = context;
    }

    @Override
    protected void hook() {
        try {
            var timeUnitCandidates = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().superClass("java.lang.Enum").addField(FieldMatcher.create().type("java.util.concurrent.TimeUnit"))));
            Class<?> timeUnit = requireSingleClass("Spotify duration-unit enum", timeUnitCandidates, "enum subclass containing a java.util.concurrent.TimeUnit field").getInstance(lpparm.classLoader);
            var sleepTimerListClassCandidates = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("createState(Lcom/spotify/podcastexperience/sleeptimermenuimpl/page/SleepTimerMenuElement$Props;)Lcom/spotify/podcastexperience/sleeptimermenuimpl/page/SleepTimerMenuElement$State;")));
            ClassData sleepTimerListClassData = requireSingleClass("sleep-timer menu state builder class", sleepTimerListClassCandidates, "class using the full SleepTimerMenuElement createState JVM signature");
            var sleepTimerListMethodCandidates = bridge.findMethod(FindMethod.create().searchInClass(Collections.singletonList(sleepTimerListClassData)).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL).paramCount(2)));
            MethodData sleepTimerListMethodData = requireSingleMethod("sleep-timer menu list builder method", sleepTimerListMethodCandidates, "public static final method in " + sleepTimerListClassData.getName() + " with exactly two parameters");
            Method sleepTimerListMethod = sleepTimerListMethodData.getMethodInstance(lpparm.classLoader);
            List<MethodData> durationFactoryMethodCandidates = new ArrayList<>(sleepTimerListMethodData.getInvokes().stream().filter(method -> method.isMethod() && method.getReturnTypeName().equals("long") && method.getParamTypeNames().equals(List.of("int", timeUnit.getName()))).collect(java.util.stream.Collectors.toMap(MethodData::getDescriptor, method -> method, (first, duplicate) -> first, LinkedHashMap::new)).values());
            Method savR = requireSingleMethod("duration factory method", durationFactoryMethodCandidates, "method returning long with parameters (int, " + timeUnit.getName() + ") and directly invoked by " + sleepTimerListMethodData.getDescriptor() + "; direct invocation proves the call site while reflection verifies the resolved method before use; all directly invoked methods were " + describeMethods(sleepTimerListMethodData.getInvokes())).getMethodInstance(lpparm.classLoader);
            if (!Modifier.isStatic(savR.getModifiers())) throw new IllegalStateException(fingerprintMessage("duration factory method", savR + " must be static after resolving it from the sleep-timer list builder", "resolved modifiers: " + Modifier.toString(savR.getModifiers())));

            Map<String, Integer> constructorCounts = new HashMap<>();

            for (MethodData invokedMethod : sleepTimerListMethodData.getInvokes()) {
                if (!invokedMethod.isConstructor()) continue;
                if (!invokedMethod.getParamTypeNames().equals(List.of("long"))) continue;

                String className = invokedMethod.getClassName();
                constructorCounts.merge(className, 1, Integer::sum);
            }

            String className = constructorCounts.entrySet().stream().filter(entry -> entry.getValue() >= 5).max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow(() -> new IllegalStateException(fingerprintMessage("duration menu-item class", "a constructor accepting only long must be invoked at least five times by " + sleepTimerListMethodData.getDescriptor(), "constructor invocation counts: " + constructorCounts)));
            Class<?> customTimeClass = XposedHelpers.findClass(className, lpparm.classLoader);

            var persistentListBuilderClassCandidates = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("capacity must be non-negative.").addInterface("java.util.RandomAccess").addInterface("java.io.Serializable").addFieldForType(Object[].class).addFieldForType(int.class).addFieldForType(boolean.class)));
            ClassData persistentListBuilderClassData = requireSingleClass("persistent-list builder class", persistentListBuilderClassCandidates, "class using \"capacity must be non-negative.\", implementing RandomAccess and Serializable, with Object[], int, and boolean fields");
            var createListMethodCandidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(persistentListBuilderClassData.getName()).paramCount(0)));
            Set<String> builderCalls = sleepTimerListMethodData.getInvokes().stream().map(MethodData::getDescriptor).collect(java.util.stream.Collectors.toSet());
            List<MethodData> invokedCreateMethods = createListMethodCandidates.stream().filter(method -> builderCalls.contains(method.getDescriptor())).collect(java.util.stream.Collectors.toList());
            Method createListMethod = requireSingleMethod("persistent-list creation method", invokedCreateMethods, "public static zero-parameter builder factory directly called by the sleep-timer menu").getMethodInstance(lpparm.classLoader);
            var finalizeListMethodCandidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.STATIC).returnType(persistentListBuilderClassData.getName()).paramTypes(List.class)));
            Set<String> sleepTimerBuilderInvokes = sleepTimerListMethodData.getInvokes().stream().map(MethodData::getDescriptor).collect(java.util.stream.Collectors.toSet());
            List<MethodData> invokedFinalizeListMethodCandidates = finalizeListMethodCandidates.stream().filter(method -> sleepTimerBuilderInvokes.contains(method.getDescriptor())).collect(java.util.stream.Collectors.toList());
            Method finalizeListMethod = requireSingleMethod("persistent-list finalization method", invokedFinalizeListMethodCandidates, "public static method returning " + persistentListBuilderClassData.getName() + ", accepting one java.util.List, and directly invoked by " + sleepTimerListMethodData.getDescriptor() + "; all type-compatible candidates were " + describeMethods(finalizeListMethodCandidates)).getMethodInstance(lpparm.classLoader);

            int sleepTimerHoursPluralId = context.getResources().getIdentifier("context_menu_sleep_timer_hours", "plurals", context.getPackageName());
            int sleepTimerMinutesPluralId = context.getResources().getIdentifier("context_menu_sleep_timer_mins", "plurals", context.getPackageName());
            if (sleepTimerHoursPluralId == 0 || sleepTimerMinutesPluralId == 0) throw new IllegalStateException(fingerprintMessage("sleep-timer plural resources", "Spotify resources named context_menu_sleep_timer_hours and context_menu_sleep_timer_mins", "resolved IDs: hours=" + sleepTimerHoursPluralId + ", minutes=" + sleepTimerMinutesPluralId));
            var customRowInvokeMethodCandidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().returnType(Object.class).paramTypes(Object.class, Object.class, Object.class).usingNumbers(sleepTimerHoursPluralId, sleepTimerMinutesPluralId)));
            MethodData customRowInvokeMethodData = requireSingleMethod("Compose sleep-timer duration-row renderer", customRowInvokeMethodCandidates, "three-Object-parameter method returning Object and using both sleep-timer plural resource IDs " + sleepTimerHoursPluralId + " and " + sleepTimerMinutesPluralId);
            List<MethodData> quantityStringMethodCandidates = new ArrayList<>(customRowInvokeMethodData.getInvokes().stream().filter(this::isComposeQuantityStringFormatter).collect(java.util.stream.Collectors.toMap(MethodData::getDescriptor, method -> method, (first, duplicate) -> first, LinkedHashMap::new)).values());
            Method quantityStringMethod = requireSingleMethod("Compose quantity-string formatter", quantityStringMethodCandidates, "method directly invoked by " + customRowInvokeMethodData.getDescriptor() + " whose DEX descriptor has parameters (int, int, Object[], one reference type) and returns String; all directly invoked methods were " + describeMethods(customRowInvokeMethodData.getInvokes())).getMethodInstance(lpparm.classLoader);

            var kotlinUnitCandidates = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("kotlin.Unit").fieldCount(1).addMethod(MethodMatcher.create().name("toString"))));
            Class<?> kotlinUnitClass = requireSingleClass("Kotlin Unit class", kotlinUnitCandidates, "class using \"kotlin.Unit\", containing one field and a toString method").getInstance(lpparm.classLoader);
            Object kotlinUnit = getStaticValueByType(kotlinUnitClass, kotlinUnitClass);

            var durationOptionClassCandidates = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("createState(Lcom/spotify/podcastexperience/sleeptimermenuimpl/page/SleepTimerDurationOptionElement$Props;)Lcom/spotify/podcastexperience/sleeptimermenuimpl/page/SleepTimerDurationOptionElement$State;")));
            ClassData durationOptionClassData = requireSingleClass("sleep-timer duration option component", durationOptionClassCandidates, "class using the full SleepTimerDurationOptionElement createState JVM signature");
            Class<?> durationOptionClass = durationOptionClassData.getInstance(lpparm.classLoader);
            List<MethodData> durationOptionConstructorCandidates = durationOptionClassData.getMethods().stream().filter(MethodData::isConstructor).filter(method -> method.getParamCount() == 1).collect(java.util.stream.Collectors.toList());
            MethodData durationOptionConstructorData = requireSingleMethod("sleep-timer duration option component constructor", durationOptionConstructorCandidates, "the sole one-parameter constructor in " + durationOptionClassData.getName());
            Class<?> durationControllerClass = durationOptionConstructorData.getParamTypes().get(0).getInstance(lpparm.classLoader);

            int sleepTimerSelectMessageId = context.getResources().getIdentifier("context_menu_sleep_timer_select_message", "string", context.getPackageName());
            if (sleepTimerSelectMessageId == 0) throw new IllegalStateException(fingerprintMessage("sleep-timer selected message resource", "Spotify string resource named context_menu_sleep_timer_select_message", "resolved ID: 0"));
            var rowClickMethodCandidates = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("invokeSuspend").returnType(Object.class).paramTypes(Object.class).usingNumbers(sleepTimerSelectMessageId)));
            List<MethodData> durationRowClickMethodCandidates = rowClickMethodCandidates.stream().filter(method -> method.getUsingFields().stream().anyMatch(usingField -> usingField.getField().getDeclaredClassName().equals(durationOptionClassData.getName()))).collect(java.util.stream.Collectors.toList());
            MethodData overrideRowClickMethodData = requireSingleMethod("sleep-timer duration row click coroutine", durationRowClickMethodCandidates, "invokeSuspend(Object) using string resource " + sleepTimerSelectMessageId + " and reading a field declared by the structurally identified duration component " + durationOptionClassData.getName() + "; all resource-compatible methods were " + describeMethods(rowClickMethodCandidates));
            Method overrideRowClickMethod = overrideRowClickMethodData.getMethodInstance(lpparm.classLoader);

            List<MethodData> durationRequestConstructorCandidates = new ArrayList<>(overrideRowClickMethodData.getInvokes().stream().filter(MethodData::isConstructor).filter(method -> method.getParamTypeNames().equals(List.of("long"))).collect(java.util.stream.Collectors.toMap(MethodData::getDescriptor, method -> method, (first, duplicate) -> first, LinkedHashMap::new)).values());
            MethodData durationRequestConstructorData = requireSingleMethod("sleep-timer duration request constructor", durationRequestConstructorCandidates, "constructor accepting one long directly invoked by " + overrideRowClickMethodData.getDescriptor());
            Constructor<?> durationRequestConstructor = durationRequestConstructorData.getConstructorInstance(lpparm.classLoader);
            List<MethodData> snackbarMessageConstructorCandidates = new ArrayList<>(overrideRowClickMethodData.getInvokes().stream().filter(MethodData::isConstructor).filter(method -> isSleepTimerSnackbarMessageConstructor(method.getParamTypeNames())).collect(java.util.stream.Collectors.toMap(MethodData::getDescriptor, method -> method, (first, duplicate) -> first, LinkedHashMap::new)).values());
            Constructor<?> snackbarMessageConstructor = requireSingleMethod("sleep-timer snackbar message constructor", snackbarMessageConstructorCandidates, "nine-parameter constructor directly invoked by " + overrideRowClickMethodData.getDescriptor() + " with String, Integer, String, Integer in positions 2-5 and boolean last").getConstructorInstance(lpparm.classLoader);

            // Restrictions moved out of the duration controller in 9.1.82.
            // Follow the named player-model API, then resolve its receiver from the controller.
            var restrictionMethods = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType(boolean.class).paramCount(0).addInvoke(MethodMatcher.create()
                            .declaredClass("com.spotify.player.model.Restrictions").name("disallowSleepTimerDurationReasons"))));
            Method canSetDurationMethod = requireSingleMethod("sleep-timer duration restriction method", restrictionMethods,
                    "zero-parameter boolean method invoking Restrictions.disallowSleepTimerDurationReasons")
                    .getMethodInstance(lpparm.classLoader);

            Map<String, MethodData> durationActions = new LinkedHashMap<>();
            for (MethodData method : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType(void.class).paramTypes(Object.class)
                    .addUsingField(FieldMatcher.create().declaredClass(durationRequestConstructorData.getClassName()))))) {
                durationActions.put(method.getDescriptor(), method);
            }
            // Newer request models expose the duration through a getter instead of a field read.
            for (MethodData method : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType(void.class).paramTypes(Object.class).addInvoke(MethodMatcher.create()
                            .declaredClass(durationRequestConstructorData.getClassName()).returnType(long.class).paramCount(0))))) {
                durationActions.put(method.getDescriptor(), method);
            }
            Method durationActionMethod = requireSingleMethod("sleep-timer duration action method", durationActions.values(),
                    "void(Object) consumer reading the duration request field or getter").getMethodInstance(lpparm.classLoader);

            Object minutes = Enum.valueOf((Class<Enum>) timeUnit, "MINUTES");
            SharedPreferences prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

            customDuration = savR.invoke(null, 0, minutes);

            XposedBridge.hookMethod(sleepTimerListMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.hasThrowable() || param.getResult() == null) return;
                    try {
                        lastActivity = new WeakReference<>(References.currentActivity);
                        Object state = param.getResult();
                        List<Field> stateFields = Arrays.stream(state.getClass().getDeclaredFields())
                                .filter(field -> !Modifier.isStatic(field.getModifiers())).collect(java.util.stream.Collectors.toList());
                        List<Field> listFields = stateFields.stream().filter(field -> List.class.isAssignableFrom(field.getType())).collect(java.util.stream.Collectors.toList());
                        if (listFields.size() != 1) throw new IllegalStateException("Expected one sleep-timer state list");
                        Field listField = listFields.get(0);
                        listField.setAccessible(true);
                        List<?> nativeItems = (List<?>) listField.get(state);
                        Gson gson = new Gson();
                        Type type = new TypeToken<List<SleepTimerInfo>>() {
                        }.getType();

                        presets = gson.fromJson(prefs.getString("custom_sleep_timers", "[{\"value\":5,\"unit\":false},{\"value\":10,\"unit\":false},{\"value\":15,\"unit\":false},{\"value\":30,\"unit\":false},{\"value\":45,\"unit\":false},{\"value\":1,\"unit\":true}]"), type);

                        Object list = createListMethod.invoke(null);
                        Object hours = Enum.valueOf((Class<Enum>) timeUnit, "HOURS");

                        if (prefs.getBoolean("custom_sleep_timers_auto_reorder", true)) {
                            sortSleepTimerPresets(presets);
                        }

                        for (var preset : presets) {
                            addTimer(list, preset.value, preset.unit ? hours : minutes, customTimeClass, savR);
                        }

                        Object addCustomTime = XposedHelpers.newInstance(customTimeClass, customDuration);
                        XposedHelpers.callMethod(list, "add", addCustomTime);

                        // Preserve Spotify's end-of-track/episode and cancel rows, including
                        // their availability decisions, instead of calling obfuscated flags.
                        for (Object item : nativeItems) if (!customTimeClass.isInstance(item)) XposedHelpers.callMethod(list, "add", item);
                        Object finalList = finalizeListMethod.invoke(null, list);
                        List<Constructor<?>> constructors = Arrays.stream(state.getClass().getDeclaredConstructors())
                                .filter(c -> c.getParameterCount() == stateFields.size()).collect(java.util.stream.Collectors.toList());
                        if (constructors.size() != 1) throw new IllegalStateException("Ambiguous sleep-timer state constructor");
                        Constructor<?> constructor = constructors.get(0);
                        Object[] values = new Object[stateFields.size()];
                        for (int i = 0; i < values.length; i++) {
                            Field field = stateFields.get(i);
                            field.setAccessible(true);
                            values[i] = field.equals(listField) ? finalList : field.get(state);
                        }
                        constructor.setAccessible(true);
                        param.setResult(constructor.newInstance(values));
                    } catch (Throwable t) {
                        XposedBridge.log("[SpotifyPlus][SleepTimer] Keeping native timer menu: " + t);
                    }
                }
            });

            XposedBridge.hookMethod(quantityStringMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length >= 2 && param.args[0] instanceof Number && ((Number) param.args[0]).intValue() == sleepTimerMinutesPluralId && param.args[1] instanceof Number && ((Number) param.args[1]).intValue() == 0) param.setResult(References.getString(R.string.ui_enter_custom_amount));
                }
            });

            XposedBridge.hookMethod(overrideRowClickMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object rowProps = findCapturedObjectContainingValueOfType(param.thisObject, customTimeClass);
                    if (rowProps == null) return;
                    Object rowItem = findFieldValueByType(rowProps, customTimeClass);
                    Long rowDuration = findLongFieldValue(rowItem);
                    if (rowDuration == null || !(customDuration instanceof Number) || rowDuration.longValue() != ((Number) customDuration).longValue()) return;
                    Object durationOption = findFieldValueByType(param.thisObject, durationOptionClass);
                    Object durationController = findFieldValueByType(durationOption, durationControllerClass);
                    Object snackbarCallback = findOnlyOtherReferenceValue(rowProps, customTimeClass);
                    if (durationController == null || snackbarCallback == null) throw new IllegalStateException("[SleepTimerHook] Matched the custom duration row but could not resolve its controller or callback. Coroutine fields: " + describeInstanceFields(param.thisObject) + "; props fields: " + describeInstanceFields(rowProps));
                    showCustomTimerSheet(durationController, snackbarCallback, canSetDurationMethod, durationRequestConstructor, durationActionMethod, snackbarMessageConstructor, sleepTimerSelectMessageId);
                    param.setResult(kotlinUnit);
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("[SpotifyPlus] SleepTimerHook initialization failed. The exception below identifies the exact DexKit fingerprint or derived structure that failed.");
            XposedBridge.log(e);
        }
    }

    private boolean isComposeQuantityStringFormatter(MethodData method) {
        return method.getDescriptor().matches("L[^;]+;->[^\\(]+\\(II\\[Ljava/lang/Object;L[^;]+;\\)Ljava/lang/String;");
    }

    private boolean isSleepTimerSnackbarMessageConstructor(List<String> paramTypes) {
        return paramTypes.size() == 9 && paramTypes.get(1).equals("java.lang.String") && paramTypes.get(2).equals("java.lang.Integer") && paramTypes.get(3).equals("java.lang.String") && paramTypes.get(4).equals("java.lang.Integer") && paramTypes.get(8).equals("boolean");
    }

    private ClassData requireSingleClass(String label, Collection<ClassData> candidates, String fingerprint) {
        if (candidates.size() != 1) throw new IllegalStateException(fingerprintMessage(label, fingerprint, "expected exactly 1 class but found " + candidates.size() + "; candidates: " + describeClasses(candidates)));
        return candidates.iterator().next();
    }

    private MethodData requireSingleMethod(String label, Collection<MethodData> candidates, String fingerprint) {
        if (candidates.size() != 1) throw new IllegalStateException(fingerprintMessage(label, fingerprint, "expected exactly 1 method but found " + candidates.size() + "; candidates: " + describeMethods(candidates)));
        return candidates.iterator().next();
    }

    private String fingerprintMessage(String label, String fingerprint, String observed) {
        return "[SleepTimerHook/DexKit] Could not resolve " + label + ". Fingerprint: " + fingerprint + ". Observed: " + observed + ". Spotify likely changed this structure; use the listed candidates to update this fingerprint.";
    }

    private String describeClasses(Collection<ClassData> candidates) {
        if (candidates.isEmpty()) return "<none>";
        String description = candidates.stream().limit(20).map(ClassData::getName).collect(java.util.stream.Collectors.joining(", "));
        return candidates.size() > 20 ? description + ", ... (" + candidates.size() + " total)" : description;
    }

    private String describeMethods(Collection<MethodData> candidates) {
        if (candidates.isEmpty()) return "<none>";
        String description = candidates.stream().limit(20).map(MethodData::getDescriptor).collect(java.util.stream.Collectors.joining(", "));
        return candidates.size() > 20 ? description + ", ... (" + candidates.size() + " total)" : description;
    }

    private void addTimer(Object list, int amount, Object unit, Class<?> timerList, Method savR) {
        try {
            Object duration = savR.invoke(null, amount, unit);
            Object item = XposedHelpers.newInstance(timerList, duration);
            XposedHelpers.callMethod(list, "add", item);
        } catch(Exception e) {
            XposedBridge.log(e);
        }
    }

    private Object getStaticValueByType(Class<?> owner, Class<?> valueType) throws IllegalAccessException {
        for (Field field : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !valueType.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            return field.get(null);
        }
        throw new IllegalStateException(fingerprintMessage("Kotlin Unit singleton", "static field in " + owner.getName() + " assignable to " + valueType.getName(), "fields: " + Arrays.toString(owner.getDeclaredFields())));
    }

    private Object findFieldValueByType(Object owner, Class<?> valueType) throws IllegalAccessException {
        if (owner == null) return null;
        for (Field field : getInstanceFields(owner.getClass())) {
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value != null && valueType.isInstance(value)) return value;
        }
        return null;
    }

    private Object findCapturedObjectContainingValueOfType(Object owner, Class<?> nestedValueType) throws IllegalAccessException {
        if (owner == null) return null;
        for (Field field : getInstanceFields(owner.getClass())) {
            if (field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value != null && getInstanceFields(value.getClass()).stream().anyMatch(nestedField -> nestedValueType.isAssignableFrom(nestedField.getType()))) return value;
        }
        return null;
    }

    private Long findLongFieldValue(Object owner) throws IllegalAccessException {
        if (owner == null) return null;
        for (Field field : getInstanceFields(owner.getClass())) {
            if (field.getType() != long.class && field.getType() != Long.class) continue;
            field.setAccessible(true);
            return ((Number) field.get(owner)).longValue();
        }
        return null;
    }

    private Object findOnlyOtherReferenceValue(Object owner, Class<?> excludedValueType) throws IllegalAccessException {
        List<Object> candidates = new ArrayList<>();
        for (Field field : getInstanceFields(owner.getClass())) {
            if (field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value == null || value instanceof String || excludedValueType.isInstance(value)) continue;
            candidates.add(value);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private List<Field> getInstanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())) fields.add(field);
        }
        return fields;
    }

    private String describeInstanceFields(Object owner) {
        if (owner == null) return "<null>";
        return getInstanceFields(owner.getClass()).stream().map(field -> field.getDeclaringClass().getName() + "#" + field.getName() + ":" + field.getType().getName()).collect(java.util.stream.Collectors.joining(", "));
    }

    private void showCustomTimerSheet(Object durationController, Object snackbarCallback, Method canSetDurationMethod, Constructor<?> durationRequestConstructor, Method durationActionMethod, Constructor<?> snackbarMessageConstructor, int sleepTimerSelectMessageId) {
        Activity activity = lastActivity.get();
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            SpotifyBottomSheet sheet = new SpotifyBottomSheet(lpparm.classLoader, activity);

            XModuleResources modResources = References.modResources;
            int themeOverlayLast = R.style.Theme_SpotifyPlus;
            Context themedCtxLast = new ModuleContextWrapper(activity.getApplicationContext(), themeOverlayLast, modResources, ModuleContextWrapper.class.getClassLoader());
            LayoutInflater inflaterLast = LayoutInflater.from(activity.getApplicationContext()).cloneInContext(themedCtxLast);
            View root = inflaterLast.inflate(modResources.getIdentifier("sleep_timer_sheet", "layout", "com.lenerd46.spotifyplus"), null, false);

            TextInputEditText input = root.findViewById(modResources.getIdentifier("input_sleep_timer_amount", "id", "com.lenerd46.spotifyplus"));
            MaterialButtonToggleGroup unit = root.findViewById(modResources.getIdentifier("sleep_timer_unit_toggle", "id", "com.lenerd46.spotifyplus"));
            MaterialSwitch save = root.findViewById(modResources.getIdentifier("switch_sleep_timer_save", "id", "com.lenerd46.spotifyplus"));
            MaterialButton confirm = root.findViewById(modResources.getIdentifier("btn_confirm_sleep_timer_custom", "id", "com.lenerd46.spotifyplus"));
            MaterialButton cancel = root.findViewById(modResources.getIdentifier("btn_cancel_sleep_timer_custom", "id", "com.lenerd46.spotifyplus"));

            confirm.setOnClickListener(v -> {
                String raw = input.getText().toString().trim();
                if (raw.isEmpty()) return;

                long time = Long.parseLong(raw);
                if (time <= 0) return;

                boolean minutesSelected = unit.getCheckedButtonId() == modResources.getIdentifier("btn_sleep_timer_minutes", "id", "com.lenerd46.spotifyplus");
                try {
                    Object restrictions = canSetDurationMethod.getDeclaringClass().isInstance(durationController)
                            ? durationController : findFieldValueByType(durationController, canSetDurationMethod.getDeclaringClass());
                    if (restrictions == null) throw new IllegalStateException("Sleep-timer restrictions receiver missing");
                    if (!(boolean) canSetDurationMethod.invoke(restrictions)) return;
                    long millis = minutesSelected ? TimeUnit.MINUTES.toMillis(time) : TimeUnit.HOURS.toMillis(time);
                    Object durationRequest = durationRequestConstructor.newInstance(millis);
                    Object durationAction = findFieldValueByType(durationController, durationActionMethod.getDeclaringClass());
                    if (durationAction == null) throw new IllegalStateException("Could not find " + durationActionMethod.getDeclaringClass().getName() + " inside " + durationController.getClass().getName());
                    durationActionMethod.invoke(durationAction, durationRequest);
                } catch (Throwable throwable) {
                    XposedBridge.log("[SpotifyPlus] Failed to submit the custom sleep timer duration.");
                    XposedBridge.log(throwable);
                    return;
                }

                if (save.isChecked()) {
                    presets.add(new SleepTimerInfo((int) time, !minutesSelected));

                    Gson gson = new Gson();
                    String json = gson.toJson(presets);

                    activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE).edit().putString("custom_sleep_timers", json).apply();
                }

                sheet.dismiss();
                showTimerSetMessage(snackbarCallback, snackbarMessageConstructor, sleepTimerSelectMessageId);
                activity.onBackPressed();
            });

            cancel.setOnClickListener(v -> {
                sheet.dismiss();
            });

            sheet.create(root);

            input.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    public static int getSpotifyStyle(ClassLoader cl, String name, int fallback) {
        try {
            return XposedHelpers.getStaticIntField(XposedHelpers.findClass("com.spotify.music.R$style", cl), name);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void showTimerSetMessage(Object callback, Constructor<?> snackbarMessageConstructor, int messageId) {
        try {
            Object message = snackbarMessageConstructor.newInstance(null, "", Integer.valueOf(messageId), null, null, null, null, null, false);
            XposedHelpers.callMethod(callback, "invoke", message);
        } catch (Throwable ignored) {
        }
    }

    public static class SleepTimerInfo {
        public int value;
        public boolean unit;

        public SleepTimerInfo() {
        }

        public SleepTimerInfo(int value, boolean unit) {
            this.value = value;
            this.unit = unit;
        }

        String getTitle() {
            return References.getQuantityString(unit ? R.plurals.sleep_timer_hours : R.plurals.sleep_timer_minutes, value, value);
        }
    }

    private void sortSleepTimerPresets(List<SleepTimerInfo> presets) {
        presets.sort(Comparator.comparingLong(this::getSleepTimerDurationMillis));
    }

    private long getSleepTimerDurationMillis(SleepTimerHook.SleepTimerInfo preset) {
        return preset.unit ? java.util.concurrent.TimeUnit.HOURS.toMillis(preset.value) : java.util.concurrent.TimeUnit.MINUTES.toMillis(preset.value);
    }
}

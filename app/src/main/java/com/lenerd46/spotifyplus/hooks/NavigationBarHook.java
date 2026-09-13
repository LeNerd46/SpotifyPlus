package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class NavigationBarHook extends SpotifyHook {
    private final Context context;
    private final SharedPreferences prefs;

    NavigationBarHook(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
    }

    @Override protected void hook() throws Exception {
        int createTitle = context.getResources().getIdentifier("navigationbar_musicappitems_create_title", "string", context.getPackageName());
        if (createTitle == 0) throw new IllegalStateException("Navigation create-title resource missing");
        Map<String, MethodData> constructors = new LinkedHashMap<>();
        for (MethodData factory : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingNumbers(createTitle)))) {
            for (MethodData invoked : factory.getInvokes()) {
                var types = invoked.getParamTypeNames();
                if (invoked.isConstructor() && types.size() == 8 && types.get(1).equals("int")
                        && types.get(5).equals("java.util.Set") && types.get(7).equals("int")) {
                    constructors.put(invoked.getDescriptor(), invoked);
                }
            }
        }
        if (constructors.size() != 1) throw new IllegalStateException("Ambiguous navigation-item constructor: " + constructors.keySet());
        MethodData itemConstructor = constructors.values().iterator().next();
        Class<?> item = itemConstructor.getConstructorInstance(lpparm.classLoader).getDeclaringClass();
        Class<?> kind = itemConstructor.getParamTypes().get(4).getInstance(lpparm.classLoader);
        if (!kind.isEnum()) throw new IllegalStateException("Navigation item kind is not an enum");
        Set<String> names = Arrays.stream(kind.getEnumConstants()).map(v -> ((Enum<?>) v).name()).collect(java.util.stream.Collectors.toSet());
        if (!names.containsAll(Set.of("HOME", "SEARCH", "YOUR_LIBRARY", "CREATE", "PREMIUM"))) {
            throw new IllegalStateException("Unexpected navigation kinds: " + names);
        }
        var fields = Arrays.stream(item.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()) && f.getType() == kind).toList();
        if (fields.size() != 1) throw new IllegalStateException("Ambiguous navigation-kind field");
        Field kindField = fields.get(0);
        kindField.setAccessible(true);
        var setConstructor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>")
                .paramTypes(item, item, item, item, item, int.class))).single().getConstructorInstance(lpparm.classLoader);
        if (!Set.class.isAssignableFrom(setConstructor.getDeclaringClass())) throw new IllegalStateException("Navigation collection is not a Set");
        XposedBridge.hookMethod(setConstructor, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                if (!prefs.getBoolean("remove_create", false)) return;
                for (int i = 0; i < 5; i++) {
                    if (param.args[i] == null) continue;
                    String name = ((Enum<?>) kindField.get(param.args[i])).name();
                    if (name.equals("CREATE") || name.equals("PREMIUM")) param.args[i] = null;
                }
            }
        });
    }
}

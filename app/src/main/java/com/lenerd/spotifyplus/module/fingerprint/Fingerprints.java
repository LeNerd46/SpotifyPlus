package com.lenerd.spotifyplus.module.fingerprint;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Factory methods for hard-coded and DexKit-backed fingerprints. */
public final class Fingerprints {
    private Fingerprints() {}

    public static Fingerprint<Class<?>> namedClass(String id, String name, int score) {
        return context -> List.of(new FingerprintMatch<>(Class.forName(name, false, context.classLoader()), score, id));
    }

    public static Fingerprint<Method> namedMethod(String id, String owner, String name, int score, Class<?>... params) {
        return context -> List.of(new FingerprintMatch<>(Class.forName(owner, false, context.classLoader())
                .getDeclaredMethod(name, params), score, id));
    }

    public static Fingerprint<Method> namedMethodTypes(String id, String owner, String name, int score, String... params) {
        return context -> List.of(new FingerprintMatch<>(Class.forName(owner, false, context.classLoader())
                .getDeclaredMethod(name, load(params, context.classLoader())), score, id));
    }

    public static Fingerprint<Constructor<?>> namedConstructor(String id, String owner, int score, Class<?>... params) {
        return context -> List.of(new FingerprintMatch<>(Class.forName(owner, false, context.classLoader())
                .getDeclaredConstructor(params), score, id));
    }

    public static Fingerprint<Constructor<?>> namedConstructorTypes(String id, String owner, int score, String... params) {
        return context -> List.of(new FingerprintMatch<>(Class.forName(owner, false, context.classLoader())
                .getDeclaredConstructor(load(params, context.classLoader())), score, id));
    }

    public static Fingerprint<Field> namedField(String id, String owner, String name, int score) {
        return context -> List.of(new FingerprintMatch<>(Class.forName(owner, false, context.classLoader())
                .getDeclaredField(name), score, id));
    }

    public static Fingerprint<Class<?>> dexClass(String id, FindClass query, int baseScore) {
        return dexClass(id, query, baseScore, ignored -> 0);
    }

    public static Fingerprint<Class<?>> dexClass(String id, FindClass query, int baseScore, ToIntFunction<ClassData> scorer) {
        return context -> {
            requireBridge(context);
            List<FingerprintMatch<Class<?>>> matches = new ArrayList<>();
            for (ClassData data : context.bridge().findClass(query)) {
                matches.add(new FingerprintMatch<>(data.getInstance(context.classLoader()), baseScore + scorer.applyAsInt(data), id));
            }
            return matches;
        };
    }

    public static Fingerprint<Method> dexMethod(String id, FindMethod query, int baseScore) {
        return dexMethod(id, query, baseScore, ignored -> 0);
    }

    public static Fingerprint<Method> dexMethod(String id, FindMethod query, int baseScore, ToIntFunction<MethodData> scorer) {
        return context -> {
            requireBridge(context);
            List<FingerprintMatch<Method>> matches = new ArrayList<>();
            for (MethodData data : context.bridge().findMethod(query)) if (data.isMethod()) {
                matches.add(new FingerprintMatch<>(data.getMethodInstance(context.classLoader()), baseScore + scorer.applyAsInt(data), id));
            }
            return matches;
        };
    }

    public static Fingerprint<Constructor<?>> dexConstructor(String id, FindMethod query, int baseScore) {
        return context -> {
            requireBridge(context);
            List<FingerprintMatch<Constructor<?>>> matches = new ArrayList<>();
            for (MethodData data : context.bridge().findMethod(query)) if (data.isConstructor()) {
                matches.add(new FingerprintMatch<>(data.getConstructorInstance(context.classLoader()), baseScore, id));
            }
            return matches;
        };
    }

    public static Fingerprint<Field> dexField(String id, FindField query, int baseScore) {
        return dexField(id, query, baseScore, ignored -> 0);
    }

    public static Fingerprint<Field> dexField(String id, FindField query, int baseScore, ToIntFunction<FieldData> scorer) {
        return context -> {
            requireBridge(context);
            List<FingerprintMatch<Field>> matches = new ArrayList<>();
            for (FieldData data : context.bridge().findField(query)) {
                matches.add(new FingerprintMatch<>(data.getFieldInstance(context.classLoader()), baseScore + scorer.applyAsInt(data), id));
            }
            return matches;
        };
    }

    private static void requireBridge(FingerprintContext context) {
        if (context.bridge() == null) throw new IllegalStateException("DexKit bridge is unavailable");
    }

    private static Class<?>[] load(String[] names, ClassLoader loader) throws ClassNotFoundException {
        Class<?>[] result = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = switch (names[i]) {
                case "boolean" -> boolean.class; case "byte" -> byte.class; case "char" -> char.class;
                case "short" -> short.class; case "int" -> int.class; case "long" -> long.class;
                case "float" -> float.class; case "double" -> double.class; case "void" -> void.class;
                default -> Class.forName(names[i], false, loader);
            };
        }
        return result;
    }
}

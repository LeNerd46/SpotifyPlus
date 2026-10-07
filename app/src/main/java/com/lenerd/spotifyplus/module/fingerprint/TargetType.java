package com.lenerd.spotifyplus.module.fingerprint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Serialization for cacheable reflection targets. */
public enum TargetType {
    CLASS("C") {
        @Override String describe(Object value) { return "C|" + ((Class<?>) value).getName(); }
        @Override Object restore(String[] parts, ClassLoader loader) throws Exception {
            require(parts, 2); return load(parts[1], loader);
        }
    },
    METHOD("M") {
        @Override String describe(Object value) {
            Method method = (Method) value;
            return "M|" + method.getDeclaringClass().getName() + "|" + method.getName() + "|" + params(method.getParameterTypes());
        }
        @Override Object restore(String[] parts, ClassLoader loader) throws Exception {
            require(parts, 4);
            return load(parts[1], loader).getDeclaredMethod(parts[2], parseParams(parts[3], loader));
        }
    },
    CONSTRUCTOR("K") {
        @Override String describe(Object value) {
            Constructor<?> constructor = (Constructor<?>) value;
            return "K|" + constructor.getDeclaringClass().getName() + "|" + params(constructor.getParameterTypes());
        }
        @Override Object restore(String[] parts, ClassLoader loader) throws Exception {
            require(parts, 3);
            return load(parts[1], loader).getDeclaredConstructor(parseParams(parts[2], loader));
        }
    },
    FIELD("F") {
        @Override String describe(Object value) {
            Field field = (Field) value;
            return "F|" + field.getDeclaringClass().getName() + "|" + field.getName();
        }
        @Override Object restore(String[] parts, ClassLoader loader) throws Exception {
            require(parts, 3); return load(parts[1], loader).getDeclaredField(parts[2]);
        }
    };

    private final String prefix;
    TargetType(String prefix) { this.prefix = prefix; }

    abstract String describe(Object value);
    abstract Object restore(String[] parts, ClassLoader loader) throws Exception;

    Object restore(String descriptor, ClassLoader loader) throws Exception {
        String[] parts = descriptor.split("\\|", -1);
        if (parts.length == 0 || !parts[0].equals(prefix)) throw new IllegalArgumentException("Wrong target descriptor");
        return restore(parts, loader);
    }

    private static String params(Class<?>[] types) {
        return String.join(",", Arrays.stream(types).map(Class::getName).toList());
    }
    private static Class<?>[] parseParams(String value, ClassLoader loader) throws Exception {
        if (value.isEmpty()) return new Class<?>[0];
        String[] names = value.split(",", -1);
        Class<?>[] result = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) result[i] = load(names[i], loader);
        return result;
    }
    private static Class<?> load(String name, ClassLoader loader) throws Exception {
        return switch (name) {
            case "boolean" -> boolean.class; case "byte" -> byte.class; case "char" -> char.class;
            case "short" -> short.class; case "int" -> int.class; case "long" -> long.class;
            case "float" -> float.class; case "double" -> double.class; case "void" -> void.class;
            default -> Class.forName(name, false, loader);
        };
    }
    private static void require(String[] parts, int count) {
        if (parts.length != count) throw new IllegalArgumentException("Malformed target descriptor");
    }
}

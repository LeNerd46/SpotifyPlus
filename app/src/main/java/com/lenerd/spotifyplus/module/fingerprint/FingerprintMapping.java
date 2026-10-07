package com.lenerd.spotifyplus.module.fingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A stable logical name with any number of alternative fingerprints. */
public final class FingerprintMapping<T> {
    private final String key;
    private final TargetType targetType;
    private final List<Fingerprint<T>> fingerprints;
    private final List<Predicate<T>> validators;
    private final int minimumScore;

    private FingerprintMapping(Builder<T> builder) {
        key = builder.key;
        targetType = builder.targetType;
        fingerprints = List.copyOf(builder.fingerprints);
        validators = List.copyOf(builder.validators);
        minimumScore = builder.minimumScore;
    }

    public String key() { return key; }
    TargetType targetType() { return targetType; }
    List<Fingerprint<T>> fingerprints() { return fingerprints; }
    int minimumScore() { return minimumScore; }
    boolean accepts(T target) {
        try { return validators.stream().allMatch(validator -> validator.test(target)); }
        catch (RuntimeException ignored) { return false; }
    }

    public static <T> Builder<T> builder(String key, TargetType targetType) {
        return new Builder<>(key, targetType);
    }

    public static Builder<Class<?>> forClass(String key) { return builder(key, TargetType.CLASS); }
    public static Builder<Method> forMethod(String key) { return builder(key, TargetType.METHOD); }
    public static Builder<Constructor<?>> forConstructor(String key) { return builder(key, TargetType.CONSTRUCTOR); }
    public static Builder<Field> forField(String key) { return builder(key, TargetType.FIELD); }

    public static final class Builder<T> {
        private final String key;
        private final TargetType targetType;
        private final List<Fingerprint<T>> fingerprints = new ArrayList<>();
        private final List<Predicate<T>> validators = new ArrayList<>();
        private int minimumScore = Integer.MIN_VALUE;

        private Builder(String key, TargetType targetType) {
            if (key == null || key.isBlank() || key.startsWith("@")) throw new IllegalArgumentException("Invalid mapping key");
            this.key = key;
            this.targetType = Objects.requireNonNull(targetType);
        }
        public Builder<T> fingerprint(Fingerprint<T> fingerprint) { fingerprints.add(Objects.requireNonNull(fingerprint)); return this; }
        public Builder<T> validate(Predicate<T> validator) { validators.add(Objects.requireNonNull(validator)); return this; }
        public Builder<T> minimumScore(int score) { minimumScore = score; return this; }
        public FingerprintMapping<T> build() {
            if (fingerprints.isEmpty()) throw new IllegalStateException("Mapping needs at least one fingerprint");
            return new FingerprintMapping<>(this);
        }
    }
}

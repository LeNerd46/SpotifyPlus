package com.lenerd.spotifyplus.module.fingerprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves the best valid result, then stores its reflection descriptor for later launches. */
public final class FingerprintResolver {
    private final FingerprintContext context;
    private final FingerprintCache cache;
    private final Map<String, Object> memory = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    FingerprintResolver(FingerprintContext context, FingerprintCache cache) {
        this.context = context;
        this.cache = cache;
    }

    public <T> T resolve(FingerprintMapping<T> mapping) {
        return find(mapping).orElseThrow(() -> new FingerprintNotFoundException(mapping.key()));
    }

    public <T> Optional<T> find(FingerprintMapping<T> mapping) {
        String memoryKey = mapping.targetType().name() + ":" + mapping.key();
        Object known = memory.get(memoryKey);
        if (known != null && mapping.accepts(cast(known))) return Optional.of(cast(known));
        memory.remove(memoryKey);
        synchronized (locks.computeIfAbsent(memoryKey, ignored -> new Object())) {
            known = memory.get(memoryKey);
            if (known != null && mapping.accepts(cast(known))) return Optional.of(cast(known));
            memory.remove(memoryKey);
            T cached = restore(mapping);
            if (cached != null) return Optional.of(cached);
            return search(mapping);
        }
    }

    private <T> T restore(FingerprintMapping<T> mapping) {
        String descriptor = cache.get(mapping.key());
        if (descriptor == null) return null;
        try {
            T target = cast(mapping.targetType().restore(descriptor, context.classLoader()));
            if (!mapping.accepts(target)) throw new IllegalStateException("cached target failed validation");
            memory.put(mapping.targetType().name() + ":" + mapping.key(), target);
            FingerprintLog.debug(mapping.key() + " -> " + descriptor + " (cache)");
            return target;
        } catch (Exception error) {
            cache.remove(mapping.key());
            FingerprintLog.info("Discarded stale mapping " + mapping.key() + ": " + error.getMessage());
            return null;
        }
    }

    private <T> Optional<T> search(FingerprintMapping<T> mapping) {
        List<FingerprintMatch<T>> matches = new ArrayList<>();
        for (Fingerprint<T> fingerprint : mapping.fingerprints()) {
            try {
                for (FingerprintMatch<T> match : fingerprint.find(context)) {
                    if (match.score() >= mapping.minimumScore() && mapping.accepts(match.target())) matches.add(match);
                }
            } catch (Exception error) {
                FingerprintLog.debug("Fingerprint alternative failed for " + mapping.key() + ": " + error.getMessage());
            }
        }
        Map<String, FingerprintMatch<T>> unique = new LinkedHashMap<>();
        for (FingerprintMatch<T> match : matches) {
            String descriptor = mapping.targetType().describe(match.target());
            FingerprintMatch<T> previous = unique.get(descriptor);
            if (previous == null || match.score() > previous.score()) unique.put(descriptor, match);
        }
        matches = new ArrayList<>(unique.values());
        matches.sort(Comparator.<FingerprintMatch<T>>comparingInt(FingerprintMatch::score).reversed()
                .thenComparing(match -> mapping.targetType().describe(match.target())));
        if (matches.isEmpty()) return Optional.empty();
        FingerprintMatch<T> winner = matches.get(0);
        String descriptor = mapping.targetType().describe(winner.target());
        memory.put(mapping.targetType().name() + ":" + mapping.key(), winner.target());
        cache.put(mapping.key(), descriptor);
        if (matches.size() > 1 && matches.get(1).score() == winner.score()
                && !descriptor.equals(mapping.targetType().describe(matches.get(1).target()))) {
            FingerprintLog.warn("Ambiguous mapping " + mapping.key() + "; selected deterministic winner " + descriptor);
        }
        FingerprintLog.info(mapping.key() + " -> " + descriptor + " via " + winner.source() + " (score " + winner.score() + ")");
        return Optional.of(winner.target());
    }

    @SuppressWarnings("unchecked") private static <T> T cast(Object value) { return (T) value; }

    public static final class FingerprintNotFoundException extends IllegalStateException {
        public FingerprintNotFoundException(String key) { super("No valid fingerprint matched " + key); }
    }
}

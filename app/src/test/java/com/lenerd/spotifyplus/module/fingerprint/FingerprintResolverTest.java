package com.lenerd.spotifyplus.module.fingerprint;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class FingerprintResolverTest {
    @Test public void picksHighestScoringValidCandidate() {
        MemoryCache cache = new MemoryCache();
        FingerprintResolver resolver = resolver(cache);
        FingerprintMapping<Class<?>> mapping = FingerprintMapping.<Class<?>>builder("best", TargetType.CLASS)
                .fingerprint(context -> List.of(new FingerprintMatch<>(String.class, 20, "weak")))
                .fingerprint(context -> List.of(new FingerprintMatch<>(Integer.class, 50, "strong")))
                .validate(type -> type != String.class)
                .build();

        assertSame(Integer.class, resolver.resolve(mapping));
        assertEquals("C|java.lang.Integer", cache.get("best"));
    }

    @Test public void restoresValidatedDescriptorWithoutSearching() {
        MemoryCache cache = new MemoryCache();
        cache.put("cached", "C|java.lang.String");
        AtomicInteger searches = new AtomicInteger();
        FingerprintMapping<Class<?>> mapping = FingerprintMapping.<Class<?>>builder("cached", TargetType.CLASS)
                .fingerprint(context -> { searches.incrementAndGet(); return List.of(); })
                .validate(type -> type == String.class)
                .build();

        assertSame(String.class, resolver(cache).resolve(mapping));
        assertEquals(0, searches.get());
    }

    @Test public void staleDescriptorFallsBackAndReplacesCache() throws Exception {
        MemoryCache cache = new MemoryCache();
        cache.put("method", "M|java.lang.String|missing|");
        Method expected = String.class.getDeclaredMethod("isEmpty");
        FingerprintMapping<Method> mapping = FingerprintMapping.<Method>builder("method", TargetType.METHOD)
                .fingerprint(context -> List.of(new FingerprintMatch<>(expected, 10, "fallback")))
                .build();

        assertEquals(expected, resolver(cache).resolve(mapping));
        assertEquals("M|java.lang.String|isEmpty|", cache.get("method"));
    }

    private static FingerprintResolver resolver(FingerprintCache cache) {
        return new FingerprintResolver(new FingerprintContext(null,
                FingerprintResolverTest.class.getClassLoader()), cache);
    }

    private static final class MemoryCache implements FingerprintCache {
        private final Map<String, String> values = new HashMap<>();
        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String descriptor) { values.put(key, descriptor); }
        @Override public void remove(String key) { values.remove(key); }
    }
}

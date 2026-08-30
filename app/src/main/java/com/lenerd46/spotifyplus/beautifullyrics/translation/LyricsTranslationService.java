package com.lenerd46.spotifyplus.beautifullyrics.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class LyricsTranslationService {
    public static final String DEFAULT_TARGET_LANGUAGE = "en";
    private static final HttpUrl ENDPOINT = new HttpUrl.Builder()
            .scheme("https")
            .host("translate.googleapis.com")
            .addPathSegments("translate_a/single")
            .build();

    private final OkHttpClient client;
    private final HttpUrl endpoint;
    private final ExecutorService executor;
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private final String targetLanguage;
    private volatile boolean cancelled;

    public LyricsTranslationService() {
        this(DEFAULT_TARGET_LANGUAGE);
    }

    public LyricsTranslationService(String targetLanguage) {
        this(new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(), ENDPOINT, 6, targetLanguage);
    }

    LyricsTranslationService(OkHttpClient client, HttpUrl endpoint, int concurrency) {
        this(client, endpoint, concurrency, DEFAULT_TARGET_LANGUAGE);
    }

    LyricsTranslationService(OkHttpClient client, HttpUrl endpoint, int concurrency, String targetLanguage) {
        this.client = client;
        this.endpoint = endpoint;
        this.executor = Executors.newFixedThreadPool(concurrency);
        String normalizedTarget = normalizeTargetLanguage(targetLanguage);
        this.targetLanguage = normalizedTarget.isEmpty() ? DEFAULT_TARGET_LANGUAGE : normalizedTarget;
    }

    public static String normalizeTargetLanguage(String language) {
        if (language == null) return "";
        String normalized = language.trim().replace('_', '-');
        if (!normalized.matches("(?i)[a-z]{2}(?:-[a-z]{2})?")) return "";
        String[] parts = normalized.split("-");
        boolean knownLanguage = false;
        for (String isoLanguage : Locale.getISOLanguages()) {
            if (isoLanguage.equalsIgnoreCase(parts[0])) {
                knownLanguage = true;
                break;
            }
        }
        if (!knownLanguage) return "";
        return parts.length == 1 ? parts[0].toLowerCase(Locale.ROOT) : parts[0].toLowerCase(Locale.ROOT) + "-" + parts[1].toUpperCase(Locale.ROOT);
    }

    public static boolean shouldTranslateLanguage(String language) {
        return shouldTranslateLanguage(language, DEFAULT_TARGET_LANGUAGE);
    }

    public static boolean shouldTranslateLanguage(String language, String targetLanguage) {
        String normalizedTarget = normalizeTargetLanguage(targetLanguage);
        if (language == null || language.isBlank() || normalizedTarget.isEmpty()) return false;
        String sourceLanguage = language.trim().replace('_', '-').split("-")[0];
        String targetBaseLanguage = normalizedTarget.split("-")[0];
        return !sourceLanguage.equalsIgnoreCase(targetBaseLanguage);
    }

    public void translateLines(Collection<String> lines, Completion completion) {
        LinkedHashSet<String> uniqueLines = new LinkedHashSet<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                uniqueLines.add(line.trim());
            }
        }

        if (uniqueLines.isEmpty() || cancelled) {
            completion.onComplete(Collections.emptyMap());
            return;
        }

        ConcurrentHashMap<String, TranslationResult> results = new ConcurrentHashMap<>();
        AtomicInteger remaining = new AtomicInteger(uniqueLines.size());

        for (String line : uniqueLines) {
            executor.execute(() -> {
                try {
                    TranslationResult result = translateLine(line);
                    if (result != null && !cancelled) {
                        results.put(line, result);
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0 && !cancelled) {
                        completion.onComplete(Collections.unmodifiableMap(results));
                        executor.shutdown();
                    }
                }
            });
        }
    }

    private TranslationResult translateLine(String sourceText) {
        HttpUrl url = endpoint.newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("dt", "t")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", targetLanguage)
                .addQueryParameter("q", sourceText)
                .build();
        Call call = client.newCall(new Request.Builder().url(url).get().build());
        calls.add(call);

        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return parseResponse(sourceText, response.body().string());
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            calls.remove(call);
        }
    }

    static TranslationResult parseResponse(String sourceText, String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray()) {
                return null;
            }

            JsonArray root = parsed.getAsJsonArray();
            if (root.isEmpty() || root.get(0).isJsonNull() || !root.get(0).isJsonArray()) {
                return null;
            }

            StringBuilder translated = new StringBuilder();
            for (JsonElement element : root.get(0).getAsJsonArray()) {
                if (!element.isJsonArray()) {
                    continue;
                }
                JsonArray segment = element.getAsJsonArray();
                if (!segment.isEmpty() && segment.get(0).isJsonPrimitive()) {
                    translated.append(segment.get(0).getAsString());
                }
            }

            String translatedText = translated.toString().trim();
            if (translatedText.isEmpty()) {
                return null;
            }

            String detectedLanguage = root.size() > 2 && root.get(2).isJsonPrimitive()
                    ? root.get(2).getAsString()
                    : "";
            return new TranslationResult(sourceText, translatedText, detectedLanguage);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void cancel() {
        cancelled = true;
        for (Call call : calls) {
            call.cancel();
        }
        calls.clear();
        executor.shutdownNow();
    }

    public interface Completion {
        void onComplete(Map<String, TranslationResult> translations);
    }
}

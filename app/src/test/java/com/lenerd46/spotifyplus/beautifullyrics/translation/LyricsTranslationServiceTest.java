package com.lenerd46.spotifyplus.beautifullyrics.translation;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LyricsTranslationServiceTest {
    @Test
    public void parsesFrenchAndSpanishExamples() {
        TranslationResult french = LyricsTranslationService.parseResponse(
                "Nous trouverons ce monde d'amour",
                "[[[\"We will find this world of love\",\"Nous trouverons ce monde d'amour\",null,null,3]],null,\"fr\"]");
        TranslationResult spanish = LyricsTranslationService.parseResponse(
                "Las cuatro paredes de nuestro hogar",
                "[[[\"The four walls of our home\",\"Las cuatro paredes de nuestro hogar\",null,null,3]],null,\"es\"]");

        assertNotNull(french);
        assertEquals("We will find this world of love", french.translatedText);
        assertEquals("fr", french.detectedLanguage);
        assertNotNull(spanish);
        assertEquals("The four walls of our home", spanish.translatedText);
        assertEquals("es", spanish.detectedLanguage);
    }

    @Test
    public void skipsEnglishAndUnknownLanguages() {
        assertTrue(!LyricsTranslationService.shouldTranslateLanguage("en"));
        assertTrue(!LyricsTranslationService.shouldTranslateLanguage("EN"));
        assertTrue(!LyricsTranslationService.shouldTranslateLanguage(""));
        assertTrue(!LyricsTranslationService.shouldTranslateLanguage(null));
        assertTrue(LyricsTranslationService.shouldTranslateLanguage("fr"));
        assertTrue(!LyricsTranslationService.shouldTranslateLanguage("es", "es"));
        assertTrue(!LyricsTranslationService.shouldTranslateLanguage("es", "es-MX"));
        assertTrue(LyricsTranslationService.shouldTranslateLanguage("en", "es"));
        assertEquals("es", LyricsTranslationService.normalizeTargetLanguage(" ES "));
        assertEquals("zh-CN", LyricsTranslationService.normalizeTargetLanguage("zh_cn"));
        assertEquals("", LyricsTranslationService.normalizeTargetLanguage("xx"));
    }

    @Test
    public void concatenatesSegmentsAndRejectsMalformedResponses() {
        TranslationResult result = LyricsTranslationService.parseResponse("bonjour le monde",
                "[[[\"Hello \",\"bonjour \",null,null,3],[\"world\",\"le monde\",null,null,3]],null,\"fr\"]");

        assertNotNull(result);
        assertEquals("Hello world", result.translatedText);
        assertNull(LyricsTranslationService.parseResponse("x", "not-json"));
        assertNull(LyricsTranslationService.parseResponse("x", "[null,null,\"fr\"]"));
    }

    @Test
    public void encodesQueryAndReturnsSuccessfulResult() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("[[[\"Love & peace\",\"Amour & paix\",null,null,3]],null,\"fr\"]"));
            LyricsTranslationService service = new LyricsTranslationService(
                    new OkHttpClient(), server.url("/translate_a/single"), 1);
            AtomicReference<Map<String, TranslationResult>> output = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            service.translateLines(List.of("Amour & paix", "Amour & paix"), results -> {
                output.set(results);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, output.get().size());
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("Amour & paix", request.getRequestUrl().queryParameter("q"));
            assertEquals("en", request.getRequestUrl().queryParameter("tl"));
        }
    }

    @Test
    public void httpFailureCompletesWithNoTranslation() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500));
            LyricsTranslationService service = new LyricsTranslationService(
                    new OkHttpClient(), server.url("/translate_a/single"), 1);
            AtomicReference<Map<String, TranslationResult>> output = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            service.translateLines(List.of("Bonjour"), results -> {
                output.set(results);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(output.get().isEmpty());
        }
    }

    @Test
    public void usesConfiguredTargetLanguage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("[[[\"Hola\",\"Hello\",null,null,3]],null,\"en\"]"));
            LyricsTranslationService service = new LyricsTranslationService(
                    new OkHttpClient(), server.url("/translate_a/single"), 1, "es");
            CountDownLatch latch = new CountDownLatch(1);

            service.translateLines(List.of("Hello"), results -> latch.countDown());

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("es", request.getRequestUrl().queryParameter("tl"));
        }
    }
}

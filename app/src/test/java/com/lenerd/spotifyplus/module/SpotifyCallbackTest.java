package com.lenerd.spotifyplus.module;

import io.github.libxposed.api.XposedInterface;
import org.junit.Test;
import java.lang.reflect.Executable;
import java.util.List;
import static org.junit.Assert.*;

public class SpotifyCallbackTest {
    public static String example(String value) { return value; }

    private static final class Chain implements XposedInterface.Chain {
        final Executable executable;
        final Object receiver;
        final List<Object> args = List.of("native");
        Object result = "original";
        Throwable failure;
        Object[] received;
        int calls;

        Chain() throws Exception {
            executable = SpotifyCallbackTest.class.getMethod("example", String.class);
            receiver = null;
        }

        Chain(Executable executable, Object receiver) {
            this.executable = executable;
            this.receiver = receiver;
        }

        public Executable getExecutable() { return executable; }
        public Object getThisObject() { return receiver; }
        public List<Object> getArgs() { return args; }
        public Object getArg(int index) { return args.get(index); }
        public Object proceed() throws Throwable { return proceed(args.toArray()); }
        public Object proceed(Object[] values) throws Throwable {
            calls++;
            received = values;
            if (failure != null) throw failure;
            return result;
        }
        public Object proceedWith(Object receiver) { throw new AssertionError("Receiver changed"); }
        public Object proceedWith(Object receiver, Object[] values) { throw new AssertionError("Receiver changed"); }
    }

    @Test public void mutableArgumentsReachOriginalWithoutMutatingApiList() throws Throwable {
        Chain chain = new Chain();
        Object result = SpotifyCallback.intercept(chain, callback -> {
            assertSame(chain.executable, callback.getMember());
            callback.getArgs()[0] = "extension";
        }, callback -> assertEquals("original", callback.getResult()));
        assertEquals("original", result);
        assertEquals(List.of("native"), chain.args);
        assertArrayEquals(new Object[]{"extension"}, chain.received);
        assertEquals(1, chain.calls);
    }

    @Test public void skipWithNullStillRunsAfterWithoutInvokingOriginal() throws Throwable {
        Chain chain = new Chain();
        Object result = SpotifyCallback.intercept(chain, callback -> callback.returnAndSkip(null), callback -> {
            assertNull(callback.getResult());
            callback.setResult("extension");
        });
        assertEquals("extension", result);
        assertEquals(0, chain.calls);
    }

    @Test public void afterCanReplaceOriginalResult() throws Throwable {
        Chain chain = new Chain();
        assertEquals("replacement", SpotifyCallback.intercept(chain, callback -> {},
                callback -> callback.setResult("replacement")));
        assertEquals(1, chain.calls);
    }

    @Test public void originalThrowableReachesAfterAndPropagatesUnchanged() throws Throwable {
        Chain chain = new Chain();
        chain.failure = new IllegalStateException("Spotify failed");
        try {
            SpotifyCallback.intercept(chain, callback -> {}, callback -> assertSame(chain.failure, callback.getThrowable()));
            fail("Original exception was swallowed");
        } catch (IllegalStateException error) {
            assertSame(chain.failure, error);
        }
        assertEquals(1, chain.calls);
    }

    @Test public void explicitResultCanRecoverOriginalFailure() throws Throwable {
        Chain chain = new Chain();
        chain.failure = new IllegalStateException("Spotify failed");
        assertNull(SpotifyCallback.intercept(chain, callback -> {}, callback -> callback.setResult(null)));
        assertEquals(1, chain.calls);
    }

    @Test public void constructorAfterSeesInitializedReceiver() throws Throwable {
        StringBuilder receiver = new StringBuilder();
        Chain chain = new Chain(StringBuilder.class.getConstructor(String.class), receiver);
        chain.result = null;
        assertNull(SpotifyCallback.intercept(chain, callback -> {}, callback -> {
            assertEquals(1, chain.calls);
            assertSame(receiver, callback.getThisObject());
            assertSame(chain.executable, callback.getMember());
        }));
    }

    @Test public void invocationStateDoesNotLeakBetweenCalls() throws Throwable {
        Chain skipped = new Chain();
        SpotifyCallback.intercept(skipped, callback -> callback.returnAndSkip("skipped"), callback -> {});
        Chain normal = new Chain();
        assertEquals("original", SpotifyCallback.intercept(normal, callback -> {}, callback -> {}));
        assertEquals(1, normal.calls);
    }
}

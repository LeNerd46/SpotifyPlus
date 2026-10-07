package com.lenerd.spotifyplus.module.hooks;

import org.junit.Test;
import java.util.Base64;
import static org.junit.Assert.*;

public class PlayerRequestsTest {
    public static final class NextRequest {
        NextRequest() { }
    }

    public static final class PreviousRequest {
        private PreviousRequest() { }
    }

    @Test public void skipRequestsDoNotRequirePublicConstructors() throws Exception {
        for (Class<?> type : new Class<?>[]{NextRequest.class, PreviousRequest.class}) {
            // Reproduce the lookup that previously failed before dispatching either command.
            assertThrows(NoSuchMethodException.class, () -> type.getConstructor());
            assertEquals(type, PlayerRequests.create(type).getClass());
        }
    }

    @Test public void optimizedSkipRequestCanHaveNoConstructorAtAll() throws Exception {
        // Valid stateless class bytecode with zero methods, matching R8's constructor removal.
        byte[] bytes = Base64.getDecoder().decode("yv66vgAAADQABQEAFkNvbnN0cnVjdG9ybGVzc1JlcXVlc3QHAAEBABBqYXZhL2xhbmcvT2JqZWN0BwADACEAAgAEAAAAAAAAAAA=");
        Class<?> type = new ClassLoader() {
            Class<?> loadRequest() { return defineClass(null, bytes, 0, bytes.length); }
        }.loadRequest();
        assertEquals(0, type.getDeclaredConstructors().length);
        assertThrows(NoSuchMethodException.class, () -> type.getDeclaredConstructor());
        assertEquals(type, PlayerRequests.create(type).getClass());
    }
}

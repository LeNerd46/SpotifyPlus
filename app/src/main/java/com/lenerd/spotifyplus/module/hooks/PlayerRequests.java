package com.lenerd.spotifyplus.module.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

final class PlayerRequests {
    private PlayerRequests() { }

    static Object create(Class<?> type) throws ReflectiveOperationException {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException missingConstructor) {
            // R8 removes even the default constructor from Spotify's stateless skip requests.
            // Their native call sites allocate them without invoking a class constructor.
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field instance = unsafeType.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type);
        }
    }
}

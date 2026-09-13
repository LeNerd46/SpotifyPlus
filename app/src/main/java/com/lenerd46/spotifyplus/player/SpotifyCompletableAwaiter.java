package com.lenerd46.spotifyplus.player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SpotifyCompletableAwaiter {
    public static void await(Object operation, long timeout, TimeUnit unit) throws Exception {
        Class<?> observer = Class.forName("io.reactivex.rxjava3.core.CompletableObserver", false,
                operation.getClass().getClassLoader());
        await(operation, observer, timeout, unit);
    }

    public static void await(Object operation, Class<?> observerType, long timeout, TimeUnit unit) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Object> subscription = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean();
        Object observer = Proxy.newProxyInstance(observerType.getClassLoader(), new Class<?>[]{observerType},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "onSubscribe":
                            subscription.set(args[0]);
                            if (closed.get()) dispose(subscription.getAndSet(null));
                            return null;
                        case "onComplete": finished.countDown(); return null;
                        case "onError": error.set((Throwable) args[0]); finished.countDown(); return null;
                        case "toString": return "SpotifyPlus crossfade settings observer";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new UnsupportedOperationException(method.toString());
                    }
                });
        try {
            Method subscribe = operation.getClass().getMethod("subscribe", observerType);
            subscribe.setAccessible(true);
            subscribe.invoke(operation, observer);
            if (!finished.await(timeout, unit)) throw new TimeoutException("Spotify crossfade settings did not complete in time");
            rethrow(error.get());
        } catch (InvocationTargetException invocation) {
            rethrow(invocation.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            closed.set(true);
            dispose(subscription.getAndSet(null));
        }
    }

    private static void rethrow(Throwable error) throws Exception {
        if (error instanceof Exception) throw (Exception) error;
        if (error instanceof Error) throw (Error) error;
        if (error != null) throw new IllegalStateException(error);
    }

    private static void dispose(Object subscription) {
        if (subscription == null) return;
        try {
            Method dispose = subscription.getClass().getMethod("dispose");
            dispose.setAccessible(true);
            dispose.invoke(subscription);
        } catch (ReflectiveOperationException ignored) {

        }
    }

    private SpotifyCompletableAwaiter() {}
}

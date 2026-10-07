package com.lenerd.spotifyplus.module;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Member;
import java.util.function.Consumer;

/** Per-invocation state for the extension helpers, backed by the modern hook chain. */
public final class SpotifyCallback {
    private final Member member;
    private final Object thisObject;
    private final Object[] args;
    private Object result;
    private Throwable throwable;
    private boolean skipOriginal;

    private SpotifyCallback(XposedInterface.Chain chain) {
        member = chain.getExecutable();
        thisObject = chain.getThisObject();
        // API 102 exposes an immutable list. Helpers mutate this copy before proceeding.
        args = chain.getArgs().toArray();
    }

    static Object intercept(XposedInterface.Chain chain, Consumer<SpotifyCallback> before,
                            Consumer<SpotifyCallback> after) throws Throwable {
        SpotifyCallback callback = new SpotifyCallback(chain);
        before.accept(callback);
        if (!callback.skipOriginal) {
            try {
                callback.result = chain.proceed(callback.args);
            } catch (Throwable error) {
                callback.throwable = error;
            }
        }
        after.accept(callback);
        if (callback.throwable != null) throw callback.throwable;
        return callback.result;
    }

    public Member getMember() { return member; }
    public Object[] getArgs() { return args; }
    public Object getThisObject() { return thisObject; }
    public Object getResult() { return result; }
    public Throwable getThrowable() { return throwable; }

    public void returnAndSkip(Object result) {
        skipOriginal = true;
        setResult(result);
    }

    public void setResult(Object result) {
        this.result = result;
        throwable = null;
    }
}

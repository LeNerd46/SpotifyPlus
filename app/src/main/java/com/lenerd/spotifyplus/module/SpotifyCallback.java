package com.lenerd.spotifyplus.module;

import android.util.Log;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

public final class SpotifyCallback {
    private final Member member;
    private final Object thisObject;
    private final Object[] args;
    private final Object result;
    private final Object returnObject;
    private final Method returnMethod;
    private final Method resultMethod;
    private final Throwable throwable;

    public SpotifyCallback(Member member, Object thisObject, Object[] args, Object result, Object returnObject, Method returnMethod, Method resultMethod, Throwable throwable) {
        this.member = member;
        this.thisObject = thisObject;
        this.args = args;
        this.result = result;
        this.returnObject = returnObject;
        this.returnMethod = returnMethod;
        this.resultMethod = resultMethod;
        this.throwable = throwable;
    }

    public Member getMember() {
        return member;
    }

    public Object[] getArgs() {
        return args;
    }

    public Object getThisObject() {
        return thisObject;
    }

    public Object getResult() {
        return result;
    }

    public void returnAndSkip(Object result) {
        try {
            returnMethod.invoke(returnObject, result);
        } catch(Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
        }
    }

    public void setResult(Object result) {
        try {
            resultMethod.invoke(returnObject, result);
        } catch(Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
        }
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
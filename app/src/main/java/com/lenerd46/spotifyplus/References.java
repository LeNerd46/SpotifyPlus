package com.lenerd46.spotifyplus;

import android.app.Activity;

import java.lang.ref.WeakReference;

public class References {
    public static WeakReference<Activity> currentActivity = new WeakReference<>(null);
}

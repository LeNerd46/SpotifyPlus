package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import org.json.JSONObject;

public class DebugHook extends SpotifyHook {
    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        SpotifyNativeBridge.registerHandler("ui", this);
        SpotifyNativeBridge.registerHandler("system", this);
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) { }

    @Override
    protected void afterHook(SpotifyCallback callback) { }

    @Override
    public Object handle(String command, Object[] args) {
        try {
            if (command.equals("toast")) {
                String text = (String) args[0];
                boolean longToast = (boolean) args[1];

                if (currentActivity == null) return null;

                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> Toast.makeText(currentActivity, text, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
            } else if (command.equals("navigate")) {
                return navigate((String) args[0], (String) args[1]);
            } else if (command.equals("navigateBack")) {
                if (currentActivity == null) return false;

                currentActivity.runOnUiThread(currentActivity::onBackPressed);
                return true;
            }
        } catch (Exception e) {
            logError(e);
        }

        return null;
    }

    private boolean navigate(String rawUri, String target) throws ReflectiveOperationException {
        Activity activity = currentActivity;
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || rawUri == null || rawUri.isBlank()) return false;

        Uri uri = Uri.parse(rawUri);
        if (uri.getScheme() == null || uri.getScheme().isBlank()) return false;

        // NowPlayingActivity has no ACTION_VIEW filter. Match Spotify's own launch
        // factory, without a shared-element transition from an extension-owned view.
        boolean nowPlaying = ("spotify".equals(target) || "auto".equals(target))
                && ("spotify:now-playing".equals(rawUri) || "spotify:now-playing-view".equals(rawUri));
        Intent intent = nowPlaying
                ? (Intent) classLoader.loadClass("p.ig5").getDeclaredMethod("C", Context.class, boolean.class)
                    .invoke(null, activity, false)
                : new Intent(Intent.ACTION_VIEW, uri);
        if ("spotify".equals(target)) {
            intent.setPackage(activity.getPackageName());
        } else if ("external".equals(target)) {
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;

            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            Intent browserSelector = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_BROWSER
            );
            ResolveInfo browser = activity.getPackageManager().resolveActivity(
                browserSelector,
                PackageManager.MATCH_DEFAULT_ONLY
            );
            if (browser == null || browser.activityInfo == null) return false;

            intent.setPackage(browser.activityInfo.packageName);
        } else if (!"auto".equals(target)) {
            return false;
        }

        if (intent.resolveActivity(activity.getPackageManager()) == null) return false;

        activity.runOnUiThread(() -> {
            try {
                if (!activity.isFinishing() && !activity.isDestroyed()) activity.startActivity(intent);
            } catch (Exception e) {
                logError("Failed to navigate to " + rawUri, e);
            }
        });
        return true;
    }
}

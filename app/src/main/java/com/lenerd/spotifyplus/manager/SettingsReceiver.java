package com.lenerd.spotifyplus.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.lenerd.spotifyplus.MainActivity;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class SettingsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null) return;
            if (!"com.lenerd.spotifyplus.SET_PREF".equals(intent.getAction())) return;

            String key = intent.getStringExtra("key");
            String type = intent.getStringExtra("type");
            if (key == null || type == null) return;

//            SharedPreferences prefs = service.getRemotePreferences("SpotifyPlus");
//            SharedPreferences.Editor editor = prefs.edit();
            Log.d("SpotifyPlus", "Key: " + key + ", Type: " + type);

//            switch (type) {
//                case "boolean":
//                    editor.putBoolean(key, intent.getBooleanExtra("value", false));
//                    break;
//                case "int":
//                    editor.putInt(key, intent.getIntExtra("value", 0));
//                    break;
//                case "long":
//                    editor.putLong(key, intent.getLongExtra("value", 0L));
//                    break;
//                case "float":
//                    editor.putFloat(key, intent.getFloatExtra("value", 0f));
//                    break;
//                case "string":
//                    Log.d("SpotifyPlus", "1");
//                    editor.putString(key, intent.getStringExtra("value"));
//                    Log.d("SpotifyPlus", "2");
//                    break;
//                default:
//                    Log.d("SpotifyPlus", "wtf");
//                    return;
//            }
//
//            Log.d("SpotifyPlus", "3");
//            boolean success = editor.commit();
//            Log.d("SpotifyPlus", "4");
//
//            if (success) {
//                Log.d("SpotifyPlus", "Successfully committed pref!");
//            } else {
//                Log.d("SpotifyPlus", "Did not commit pref :(");
//            }
        } catch(Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
        }
    }
}

package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SettingsFlyoutHook extends SpotifyHook {
    private static final int CUSTOM_VIEW_ID = 0x4f524f52;
    private final Context context;

    public SettingsFlyoutHook(Context ctx) {
        context = ctx;
    }

    @Override
    public void hook() {
        XposedHelpers.findAndHookMethod("p.xvd0", lpparm.classLoader, "B", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object drawer = param.thisObject;
                    FrameLayout fl = (FrameLayout)XposedHelpers.getObjectField(drawer, "R0");

                    // Check if we've already added the button or if Spotify hasn't added its buttons yet
                    if(fl.findViewById((CUSTOM_VIEW_ID)) != null || fl.getChildCount() == 0) return;

                    DisplayMetrics dm = context.getResources().getDisplayMetrics();
                    float offset = dm.heightPixels * (1200f / dm.heightPixels);

                    TextView tv = new TextView(context);
                    tv.setId(CUSTOM_VIEW_ID);
                    tv.setText("⚡ My Custom Item");
                    tv.setTextSize(16f);
                    tv.setPadding(20, 20, 20, 20);
                    tv.setTextColor(Color.WHITE);

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.TOP | Gravity.START;
                    lp.leftMargin = 50;
                    lp.topMargin = (int)offset;
                    tv.setLayoutParams(lp);

                    tv.setBackgroundColor(Color.parseColor("#22FFFFFF"));

                    tv.setOnClickListener(v -> {
                        XposedBridge.log("[SpotifyPlus] Tapped!");

                        Activity activity = References.currentActivity.get();

                        if(activity != null && !activity.isFinishing()) {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                            activity.startActivityForResult(intent, 9072022);
                        }
                    });

                    tv.setElevation(999f);
                    tv.bringToFront();
                    fl.invalidate();

                    XposedBridge.log("[SpotifyPlus] Child Count: " + fl.getChildCount());
                    fl.addView(tv, fl.getChildCount() - 1);
                    XposedBridge.log("[SpotifyPlus] Injected button");
                } catch(Throwable t) {
                    XposedBridge.log("[SpotifyPlus] " + t.getMessage());
                }
            }
        });
    }
}

package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.net.URLEncoder;
import java.util.*;

public class ContextMenu_AddButton extends SpotifyHook {

    private static final Set<Class<?>> HOOKED_ADAPTER_CLASSES =
            Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void hook() {
        try {

            XposedHelpers.findAndHookConstructor("com.spotify.bottomsheet.core.ScrollableContentWithHeaderLayout", lpparm.classLoader, Context.class, AttributeSet.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            final ViewGroup sheet = (ViewGroup) param.thisObject;
                            sheet.post(() -> {
                                View rv = findContextMenuRecycler(sheet);
                                if (rv != null) hookAdapterWhenReady(rv);
                            });
                        }
                    }
            );
        } catch(Exception e) {
            XposedBridge.log(e);
        }
    }

    private View findContextMenuRecycler(ViewGroup root) {
        ArrayDeque<View> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            View v = q.removeFirst();
            if (v.getClass().getSimpleName().equals("RecyclerView")) {
                try {
                    int id = v.getId();
                    if (id != View.NO_ID && v.getResources().getResourceEntryName(id).equals("context_menu_rows")) {
                        return v;
                    }
                } catch (Throwable ignore) {
                }
                return v;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) q.addLast(g.getChildAt(i));
            }
        }
        return null;
    }

    private void hookAdapterWhenReady(View rv) {
        try {
            Object ad = XposedHelpers.callMethod(rv, "getAdapter");
            if (ad != null) {
                hookAdapterClass(ad.getClass());
                return;
            }
        } catch (Throwable ignore) {
        }

        try {
            XposedBridge.hookAllMethods(rv.getClass(), "setAdapter", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object ad = (param.args != null && param.args.length > 0) ? param.args[0] : null;
                    if (ad != null) hookAdapterClass(ad.getClass());
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private void hookAdapterClass(Class<?> cls) {
        if (!HOOKED_ADAPTER_CLASSES.add(cls)) return;

        XposedBridge.hookAllMethods(cls, "getItemCount", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                int orig = (int) param.getResult();
                param.setResult(orig + getCustomRowCount());
            }
        });

        XposedBridge.hookAllMethods(cls, "getItemViewType", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int pos = (int) param.args[0];
                int customRows = getCustomRowCount();
                if (pos < customRows) {
                    param.setResult(1);
                } else {
                    param.args[0] = pos - customRows;
                }
            }
        });

        XposedBridge.hookAllMethods(cls, "onBindViewHolder", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object holder = param.args[0];
                int pos = (int) param.args[1];
                int customRows = getCustomRowCount();

                if (pos == 0) {
                    View item = (View) XposedHelpers.getObjectField(holder, "itemView");
                    if (item != null) {
                        ensureRow(item, true);

                        item.setContentDescription(References.getString(R.string.lyrics_button));
                        item.setOnClickListener(v -> {
                            Activity activity = References.currentActivity;
                            if (activity == null) return;
                            activity.onBackPressed();
                            activity.getWindow().getDecorView().post(() -> BeautifulLyricsHook.showOverlay(activity, false));
                        });
                    }
                    param.setResult(null);
                } else if (hasLastFm() && pos == 1) {
                    View item = (View) XposedHelpers.getObjectField(holder, "itemView");
                    if (item != null) {
                        ensureRow(item, false);

                        item.setContentDescription(References.getString(R.string.open_song_lastfm));
                        item.setOnClickListener(v -> {
                            Pair<String, String> track = References.contextMenuTrack.get();
                            Activity activity = References.currentActivity;
                            if (track == null || activity == null) return;

                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/music/" + URLEncoder.encode(track.first) + "/_/" + URLEncoder.encode(track.second)));
                            activity.startActivity(intent);
                        });
                    }
                    param.setResult(null);
                } else {
                    param.args[1] = pos - customRows;
                }
            }
        });
    }

    private static final int TAG_SPOTIFYPLUS_ROW = 0x53474C60;

    private int getCustomRowCount() {
        return hasLastFm() ? 2 : 1;
    }

    private boolean hasLastFm() {
        SharedPreferences preferences = References.getPreferences();
        return preferences != null && !"null".equals(preferences.getString("last_fm_username", "null"));
    }

    private void ensureRow(View item, boolean lyrics) {
        if (!(item instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) item;

        Object tag = root.getTag(TAG_SPOTIFYPLUS_ROW);
        LinearLayout rowLayout;
        ImageView iconView;
        TextView textView;

        if (tag instanceof LinearLayout) {
            rowLayout = (LinearLayout) tag;
            iconView = (ImageView) rowLayout.getChildAt(0);
            textView = (TextView) rowLayout.getChildAt(1);
        } else {
            root.removeAllViews();

            Context ctx = root.getContext();

            rowLayout = new LinearLayout(ctx);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);

            ViewGroup.LayoutParams rootLp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            root.addView(rowLayout, rootLp);

            iconView = new ImageView(ctx);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dpToPx(ctx, 24),
                    dpToPx(ctx, 24)
            );
            iconLp.rightMargin = dpToPx(ctx, 16);
            rowLayout.addView(iconView, iconLp);

            textView = new TextView(ctx);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            textView.setSingleLine(true);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            rowLayout.addView(textView, textLp);

            root.setTag(TAG_SPOTIFYPLUS_ROW, rowLayout);
        }

        Drawable d = References.modResources.getDrawable(lyrics ? R.drawable.music_note : R.drawable.lastfm);
        iconView.setImageDrawable(d);
        iconView.setImageTintList(null);
        iconView.setColorFilter(null);

        // Spotify is very inconsistent with how they name their buttons in this list, so I'm not really sure what to capitalize?
        textView.setText(lyrics ? References.getString(R.string.lyrics_button) : References.getString(R.string.open_song_lastfm));
    }


    private int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}

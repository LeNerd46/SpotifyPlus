package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.SpotifyPlusSettings;
import com.lenerd.spotifyplus.module.Utils;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.result.FieldDataList;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/// This is the way to add a button to the context menu in older versions of Spotif (probably anything before 9.1.x.x)
///
/// They switched the context menu to jetpack compose in newer versions
///
/// We don't have to check anything, because the ScrollableContentWithHeaderLayout class still exists, however it is entirely unused
///
/// That means nothing is going to run on newer versions
@XposedHooker
public class LegacyContextMenuHook extends SpotifyHook {

    private static final Set<Class<?>> HOOKED_ADAPTER_CLASSES = Collections.synchronizedSet(new HashSet<>());
    private static final Set<ViewGroup> SHEETS = Collections.newSetFromMap(new WeakHashMap<>());

    private Class<?> f2e;
    private Class<?> c3e;
    private Class<?> v6e;
    private Method f2eAcceptMethod;
    private Constructor<?> headerCtor;
    private Method setAdapterMethod;
    private Method getItemCountMethod;
    private Method getItemViewTypeMethod;
    private Method onBindViewHolderMethod;

    private Field c3eField;
    private Field artworkField;
    private Field listField;
    private FieldDataList stringFields;

    @Override
    protected void hookSetup() {
        try {
            final var v6eClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=").fieldCount(4)));
            var h2eClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=").fieldCount(16)));
            var c3eClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("ContextMenuHeader(title=")));
            final var artworkClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("androidx.credentials.TYPE_GET_PUBLIC_KEY_CREDENTIAL_DOM_EXCEPTION/androidx.credentials.TYPE_NO_MODIFICATION_ALLOWED_ERROR")));

            if (v6eClasses.isEmpty() || h2eClasses.isEmpty() || c3eClasses.isEmpty() || artworkClasses.isEmpty()) {
                return;
            }

            final Class<?> h2e = h2eClasses.get(0).getInstance(classLoader);
            final var f2eClasses = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("mainScheduler").methodCount(2).fields(FieldsMatcher.create().add(FieldMatcher.create().type(int.class)).add(FieldMatcher.create().type(h2e)))));
            v6e = v6eClasses.get(0).getInstance(classLoader);
            c3e = c3eClasses.get(0).getInstance(classLoader);
            f2e = f2eClasses.get(0).getInstance(classLoader);
            final Class<?> artworkClass = artworkClasses.get(0).getInstance(classLoader);

            c3eField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(v6e))).matcher(FieldMatcher.create().type(c3e))).get(0).getFieldInstance(classLoader);
            artworkField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(c3e))).matcher(FieldMatcher.create().type(artworkClass))).get(0).getFieldInstance(classLoader);
            listField = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(v6e))).matcher(FieldMatcher.create().type(List.class))).get(0).getFieldInstance(classLoader);
            stringFields = bridge.findField(FindField.create().searchInClass(Collections.singletonList(bridge.getClassData(c3e))).matcher(FieldMatcher.create().type(String.class)));

            f2eAcceptMethod = f2e.getMethod("accept");
            hook(f2eAcceptMethod);

            headerCtor = findClass("com.spotify.bottomsheet.core.ScrollableContentWithHeaderLayout").getConstructor();
            hook(headerCtor);
        } catch (Exception e) {
            logError(e);
        }
    }

    @BeforeInvocation
    public static void before(XposedInterface.BeforeHookCallback callback) {
        LegacyContextMenuHook hook = getHook(LegacyContextMenuHook.class);
        if(hook == null) return;
        hook.beforeHook(buildCallback(callback));
    }

    protected void beforeHook(SpotifyCallback callback) {
        Member member = callback.getMember();
        try {
            if (member == getItemViewTypeMethod) {
                int pos = (int) callback.getArgs()[0];
                if (pos == 0) {
                    callback.returnAndSkip(1);
                } else {
                    callback.getArgs()[0] = pos - 1;
                }
            } else if (member == onBindViewHolderMethod) {
                Object holder = callback.getArgs()[0];
                int pos = (int) callback.getArgs()[1];

                if (pos == 0) {
                    View item = (View) holder.getClass().getDeclaredField("itemView").get(holder);
                    if (item != null) {
                        ensureRow(item);

                        // THIS IS WHERE YOU ADD NEW BUTTONS

                        item.setContentDescription("Open in Last.fm");
                        item.setOnClickListener(v -> {
                            Pair<String, String> track = Utils.currentContextTrack.get();
                            Activity activity = currentActivity;
                            if (track == null || activity == null) return;

                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/music/" + URLEncoder.encode(track.first) + "/_/" + URLEncoder.encode(track.second)));
                            activity.startActivity(intent);
                        });
                    }
                    callback.returnAndSkip(null);
                } else {
                    callback.getArgs()[1] = pos - 1;
                }
            } else if (member == f2eAcceptMethod) {
                String username = SpotifyPlusSettings.lastfmUsername;
                if (username.equals("null")) return;

                Object consumer = callback.getThisObject();
                Object vm = callback.getArgs()[0]; // this should be v6e
                Object headerObject = c3eField.get(vm);

                Object artwork = artworkField.get(headerObject);
                List<?> items = (List<?>) listField.get(vm);

                String title = "";
                String artist = "";
                String subtitle = "";

                String probablyTitle = (String) (stringFields.get(0).getFieldInstance(classLoader)).get(headerObject);
                String probablyArtist = (String) (stringFields.get(1).getFieldInstance(classLoader)).get(headerObject);

                if (probablyArtist.contains("•")) {
                    subtitle = probablyArtist;
                    artist = probablyArtist.split(" • ")[0];
                } else title = probablyArtist;

                if (probablyTitle.contains("•")) {
                    subtitle = probablyTitle;
                    artist = probablyTitle.split(" • ")[0];
                } else title = probablyTitle;

                if (title == null || title.isEmpty() || artist == null || artist.isEmpty()) return;
                final String key = title + "|" + artist;

                if (subtitle.contains("scrobbles")) return;
                Utils.currentContextTrack = new WeakReference<>(Pair.create(artist, title));

                Activity activity = currentActivity;
                OkHttpClient client = new OkHttpClient();
                Request request;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {                                      //  Yeah I know this is bad, but whatever
                    request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8) + "&track=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&format=json&user=" + URLEncoder.encode(username, StandardCharsets.UTF_8)).build();
                } else {
                    request = new Request.Builder().url("https://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=3713c2e0b7493e945555b7f52dc4232e&artist=" + URLEncoder.encode(artist) + "&track=" + URLEncoder.encode(title) + "&format=json&user=" + URLEncoder.encode(username)).build();
                }

                String finalTitle = title;
                String finalSubtitle = subtitle;

                WeakReference<Object> consumerRef = new WeakReference<>(consumer);
                WeakReference<Object> artworkRef = new WeakReference<>(artwork);
                WeakReference<List<?>> itemsRef = new WeakReference<>(items);

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            try {
                                JsonObject root = JsonParser.parseString(response.body().string()).getAsJsonObject();
                                String scrobbles = root.getAsJsonObject("track").get("userplaycount").getAsString();

                                Object consumerObject = consumerRef.get();
                                Object artworkObject = artworkRef.get();
                                List<?> itemsObject = itemsRef.get();

                                if (consumerObject == null || artworkObject == null || itemsObject == null) {
                                    logError("One of these is null");
                                    return;
                                }

                                Object newHeader = c3e.getDeclaredConstructors()[0].newInstance(finalTitle, artwork, finalSubtitle + " • " + scrobbles + " scrobbles");
                                Object newVm = v6e.getDeclaredConstructors()[0].newInstance(v6e, newHeader, items, false);

                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    try {
                                        consumerObject.getClass().getDeclaredMethod("accept").invoke(consumerObject, newVm);
                                    } catch (Throwable t) {
                                        logError(t);
                                    }
                                });
                            } catch (Exception e) {
                                logError("Failed to fetch scrobbles");

                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(() -> {
                                    Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            logError("Failed to fetch scrobbles");

                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> {
                                Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        logError("Failed to fetch scrobbles");

                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> {
                            Toast.makeText(activity, "Failed to fetch scrobbles", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        LegacyContextMenuHook hook = getHook(LegacyContextMenuHook.class);
        if(hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    protected void afterHook(SpotifyCallback callback) {
        Member member = callback.getMember();

        if (member == headerCtor) {
            String username = SpotifyPlusSettings.lastfmUsername;
            if (username.equals("null")) return;
            SHEETS.add((ViewGroup) callback.getThisObject());

            final ViewGroup sheet = (ViewGroup) callback.getThisObject();
            sheet.post(() -> {
                View rv = findContextMenuRecycler(sheet);
                if (rv != null) hookAdapterWhenReady(rv);
            });
        } else if (member == setAdapterMethod) {
            Object ad = (callback.getArgs().length > 0) ? callback.getArgs()[0] : null;
            if (ad != null) hookAdapterClass(ad.getClass());
        } else if (member == getItemCountMethod) {
            int orig = (int) callback.getResult();
            callback.setResult(orig + 1);
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        return null;
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
            Object ad = rv.getClass().getMethod("getAdapter").invoke(rv);
            if (ad != null) {
                hookAdapterClass(ad.getClass());
                return;
            }
        } catch (Throwable ignore) {
        }

        try {
            setAdapterMethod = rv.getClass().getMethod("setAdapter");
            SpotifyHook.hook(setAdapterMethod, LegacyContextMenuHook.class);
        } catch (Throwable ignore) {
        }
    }

    private void hookAdapterClass(Class<?> cls) {
        if (!HOOKED_ADAPTER_CLASSES.add(cls)) return;

        try {
            getItemCountMethod = cls.getMethod("getItemCount");
            getItemViewTypeMethod = cls.getMethod("getItemViewType");
            onBindViewHolderMethod = cls.getMethod("onBindViewHolder");

            hook(getItemCountMethod, LegacyContextMenuHook.class);
            hook(getItemViewTypeMethod, LegacyContextMenuHook.class);
            hook(onBindViewHolderMethod, LegacyContextMenuHook.class);
        } catch (Exception e) {
            logError(e);
        }
    }

    private final int TAG_SPOTIFYPLUS_ROW = 0x53474C60;

    private void ensureRow(View item) {
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

        Drawable d = Utils.getDrawable(currentActivity, R.drawable.lastfm);
        iconView.setImageDrawable(d);
        iconView.setImageTintList(null);
        iconView.setColorFilter(null);

        // Spotify is very inconsistent with how they name their buttons in this list, so I'm not really sure what to capitalize?
        textView.setText("Open in Last.fm");
    }


    private int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
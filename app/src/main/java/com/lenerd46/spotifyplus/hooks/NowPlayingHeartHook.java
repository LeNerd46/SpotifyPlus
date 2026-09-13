package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;

public final class NowPlayingHeartHook extends SpotifyHook {
    public static final String PREFERENCE = "now_playing_heart";

    private static final String NOW_PLAYING_ACTIVITY = "com.spotify.nowplaying.musicinstallation.NowPlayingActivity";
    private static final String ADD_BUTTON = "com.spotify.encoreconsumermobile.elements.addtobutton.AddToButtonView";

    private final Map<View, Boolean> states = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    protected void hook() {
        try {
            hookAddButton();
            XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Heart button hook enabled");
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Unsupported Spotify version: " + t);
        }
    }

    private void hookAddButton() throws Exception {
        Class<?> buttonClass = XposedHelpers.findClass(ADD_BUTTON, lpparm.classLoader);
        var stateTypes = Arrays.stream(buttonClass.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && isAddButtonState(field.getType()))
                .map(Field::getType).distinct().toList();
        if (stateTypes.size() != 1) throw new IllegalStateException("Ambiguous add-button state: " + stateTypes);
        Method bind = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(buttonClass).returnType(void.class).paramTypes(stateTypes.get(0))
                .addUsingField(FieldMatcher.create().declaredClass(buttonClass).type(stateTypes.get(0)))))
                .single().getMethodInstance(lpparm.classLoader);
        XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Add button binder: " + bind);

        XposedBridge.hookMethod(bind, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof ImageView button) || !shouldReplace(button)) return;
                try {
                    boolean collectionAdded = readAddedState(param.args[0]);
                    Boolean playerLiked = readPlayerLikedState(button.getContext());
                    boolean liked = playerLiked != null ? playerLiked : collectionAdded;
                    states.put(button, liked);
                    render(button, liked);
                    button.setOnClickListener(view -> toggle((ImageView) view));

                    // The notification can be published just after this view is bound when a
                    // new track starts. Refresh once so playlist membership never remains
                    // displayed as Liked Songs membership.
                    button.postDelayed(() -> refreshLikedState(button), 250);
                } catch (Throwable t) {
                    XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Could not bind heart state: " + t);
                }
            }
        });
    }

    private static boolean isAddButtonState(Class<?> candidate) {
        int enums = 0;
        int booleans = 0;
        int strings = 0;
        for (Field field : candidate.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType().isEnum()) enums++;
            else if (field.getType() == boolean.class) booleans++;
            else if (field.getType() == String.class) strings++;
        }
        return enums == 1 && booleans == 1 && strings == 2;
    }

    private boolean shouldReplace(View button) {
        Activity activity = activityFrom(button.getContext());
        return activity != null
                && activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE)
                        .getBoolean(PREFERENCE, false)
                && NOW_PLAYING_ACTIVITY.equals(activity.getClass().getName());
    }

    private static Activity activityFrom(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static boolean readAddedState(Object state) throws IllegalAccessException {
        for (Field field : state.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !field.getType().isEnum()) continue;
            field.setAccessible(true);
            return "ADDED".equals(String.valueOf(field.get(state)));
        }
        throw new IllegalStateException("Add button state field not found");
    }

    private void toggle(ImageView button) {
        Boolean playerLiked = readPlayerLikedState(button.getContext());
        boolean wasLiked = playerLiked != null
                ? playerLiked
                : Boolean.TRUE.equals(states.get(button));
        boolean liked = !wasLiked;
        states.put(button, liked);
        render(button, liked);
        button.performHapticFeedback(android.os.Build.VERSION.SDK_INT < 30 ? 1 : 16);

        try {
            sendPlayerCollectionAction(button.getContext(), wasLiked);
            XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Spotify notification "
                    + (wasLiked ? "Unlike" : "Like") + " action sent");
        } catch (Throwable t) {
            states.put(button, wasLiked);
            button.post(() -> render(button, wasLiked));
            XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Like toggle failed: " + t);
        }
    }

    private void refreshLikedState(ImageView button) {
        if (!button.isAttachedToWindow() || !shouldReplace(button)) return;
        Boolean liked = readPlayerLikedState(button.getContext());
        if (liked == null) return;
        states.put(button, liked);
        render(button, liked);
    }

    private static Boolean readPlayerLikedState(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return null;

        String like = spotifyString(context,
                "playbacknotifications_player_content_description_like", "Like");
        String unlike = spotifyString(context,
                "playbacknotifications_player_content_description_unlike", "Unlike");
        try {
            for (StatusBarNotification status : manager.getActiveNotifications()) {
                Notification notification = status.getNotification();
                if (notification == null || notification.actions == null
                        || !Notification.CATEGORY_TRANSPORT.equals(notification.category)) continue;
                for (Notification.Action action : notification.actions) {
                    String title = action == null || action.title == null
                            ? "" : action.title.toString().trim();
                    if (title.equalsIgnoreCase(unlike.trim())) return true;
                    if (title.equalsIgnoreCase(like.trim())) return false;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus][NowPlayingHeart] Could not read Liked Songs state: " + t);
        }
        return null;
    }

    private static void sendPlayerCollectionAction(Context context, boolean wasLiked)
            throws PendingIntent.CanceledException {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) throw new IllegalStateException("Notification manager unavailable");

        String like = spotifyString(context,
                "playbacknotifications_player_content_description_like", "Like");
        String unlike = spotifyString(context,
                "playbacknotifications_player_content_description_unlike", "Unlike");
        String expected = wasLiked ? unlike : like;
        Notification.Action unnamedFallback = null;

        for (StatusBarNotification status : manager.getActiveNotifications()) {
            Notification notification = status.getNotification();
            if (notification == null || notification.actions == null) continue;

            for (int i = 0; i < notification.actions.length; i++) {
                Notification.Action action = notification.actions[i];
                if (action == null || action.actionIntent == null) continue;
                String title = action.title == null ? "" : action.title.toString();
                if (title.trim().equalsIgnoreCase(expected.trim())) {
                    action.actionIntent.send();
                    return;
                }
            }

            // Spotify's media notification always places its collection action first. Only
            // use that as a compatibility fallback when it is not recognizably the opposite
            // operation, so a stale notification can never invert the requested action.
            if (Notification.CATEGORY_TRANSPORT.equals(notification.category)
                    && notification.actions.length >= 4) {
                Notification.Action first = notification.actions[0];
                if (first != null && first.actionIntent != null) {
                    String title = first.title == null ? "" : first.title.toString().trim();
                    boolean knownOpposite = title.equalsIgnoreCase(wasLiked ? like : unlike);
                    if (!knownOpposite) unnamedFallback = first;
                }
            }
        }

        if (unnamedFallback != null) {
            unnamedFallback.actionIntent.send();
            return;
        }
        throw new IllegalStateException("Spotify player " + expected + " action unavailable");
    }

    private static String spotifyString(Context context, String name, String fallback) {
        int id = context.getResources().getIdentifier(name, "string", "com.spotify.music");
        return id == 0 ? fallback : context.getString(id);
    }

    private static void render(ImageView button, boolean added) {
        Context context = button.getContext();
        String drawableName = added ? "encore_icon_heart_active_24" : "encore_icon_heart_24";
        int drawableId = context.getResources().getIdentifier(drawableName, "drawable", "com.spotify.music");
        Drawable drawable = drawableId == 0 ? null : context.getDrawable(drawableId);
        if (drawable == null) throw new IllegalStateException("Spotify heart drawable missing: " + drawableName);
        button.setImageDrawable(drawable);

        String colorName = added ? "encore_accessory_green" : "encore_accessory_white";
        int colorId = context.getResources().getIdentifier(colorName, "color", "com.spotify.music");
        button.setColorFilter(colorId == 0
                ? Color.parseColor(added ? "#1ED760" : "#FFFFFF")
                : context.getColor(colorId));

        String descriptionName = added
                ? "heart_active_button_content_description"
                : "heart_button_content_description";
        int descriptionId = context.getResources().getIdentifier(descriptionName, "string", "com.spotify.music");
        button.setContentDescription(descriptionId == 0
                ? (added ? References.getString(R.string.ui_remove_from_liked_songs) : References.getString(R.string.ui_add_to_liked_songs))
                : context.getString(descriptionId));
        button.setActivated(added);
    }
}

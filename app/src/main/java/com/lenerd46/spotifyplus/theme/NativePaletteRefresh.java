package com.lenerd46.spotifyplus.theme;

import android.content.res.ColorStateList;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import de.robv.android.xposed.XposedHelpers;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class NativePaletteRefresh {
    private final Set<Drawable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Class<?>, List<Field>> colorFields = new ConcurrentHashMap<>();

    public void refresh(View view, PaletteColors old, PaletteColors next) {
        drawable(view.getBackground(), old, next, false);
        drawable(view.getForeground(), old, next, true);
        ColorStateList backgroundTint = colors(view.getBackgroundTintList(), old, next, false);
        if (backgroundTint != view.getBackgroundTintList()) view.setBackgroundTintList(backgroundTint);
        ColorStateList foregroundTint = colors(view.getForegroundTintList(), old, next, true);
        if (foregroundTint != view.getForegroundTintList()) view.setForegroundTintList(foregroundTint);
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            ColorStateList color = colors(text.getTextColors(), old, next, true);
            if (color != text.getTextColors()) text.setTextColor(color);
            color = colors(text.getHintTextColors(), old, next, true);
            if (color != text.getHintTextColors()) text.setHintTextColor(color);
            color = colors(text.getLinkTextColors(), old, next, true);
            if (color != text.getLinkTextColors()) text.setLinkTextColor(color);
            color = colors(text.getCompoundDrawableTintList(), old, next, true);
            if (color != text.getCompoundDrawableTintList()) text.setCompoundDrawableTintList(color);
            for (Drawable icon : text.getCompoundDrawablesRelative()) drawable(icon, old, next, true);
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            refreshArtworkPlaceholder(image, old, next);
            drawable(image.getDrawable(), old, next, true);
            ColorStateList tint = colors(image.getImageTintList(), old, next, true);
            if (tint != image.getImageTintList()) image.setImageTintList(tint);
            if (image.getColorFilter() instanceof PorterDuffColorFilter) {
                PorterDuffColorFilter filter = (PorterDuffColorFilter) image.getColorFilter();
                int color = XposedHelpers.getIntField(filter, "mColor");
                int mapped = old.map(color, next, true);
                if (mapped != color) image.setColorFilter(mapped, (PorterDuff.Mode) XposedHelpers.getObjectField(filter, "mMode"));
            }
        }
        view.invalidate();
    }

    private ColorStateList colors(ColorStateList list, PaletteColors old, PaletteColors next, boolean foreground) {
        if (list == null) return null;
        int[] values = ((int[]) XposedHelpers.getObjectField(list, "mColors")).clone();
        boolean changed = false;
        for (int i = 0; i < values.length; i++) {
            int mapped = old.map(values[i], next, foreground);
            changed |= mapped != values[i];
            values[i] = mapped;
        }
        return changed ? new ColorStateList((int[][]) XposedHelpers.getObjectField(list, "mStateSpecs"), values) : list;
    }

    private void drawable(Drawable drawable, PaletteColors old, PaletteColors next, boolean foreground) {
        if (drawable == null || !visited.add(drawable)) return;

        if (drawable instanceof BitmapDrawable) return;
        if (!(drawable instanceof ColorDrawable) && !(drawable instanceof GradientDrawable)
                && !(drawable instanceof RippleDrawable)) refreshStoredColors(drawable, old, next, foreground);
        if (drawable instanceof ColorDrawable) {
            ColorDrawable solid = (ColorDrawable) drawable;
            int mapped = old.map(solid.getColor(), next, foreground);
            if (mapped != solid.getColor()) solid.setColor(mapped);
        } else if (drawable instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable) drawable;
            int[] stops = gradient.getColors();
            if (stops != null) {

                int end = stops.length - 1;
                int mapped = old.map(stops[end], next, false);
                if (mapped != stops[end]) {
                    stops = stops.clone();
                    stops[end] = mapped;
                    if (Build.VERSION.SDK_INT >= 29) {
                        Object state = XposedHelpers.getObjectField(gradient, "mGradientState");
                        float[] positions = (float[]) XposedHelpers.getObjectField(state, "mPositions");
                        gradient.setColors(stops, positions);
                    } else gradient.setColors(stops);
                }
            } else {
                ColorStateList fill = colors(gradient.getColor(), old, next, foreground);
                if (fill != gradient.getColor()) gradient.setColor(fill);
            }
        }
        if (drawable instanceof RippleDrawable) {
            RippleDrawable ripple = (RippleDrawable) drawable;
            Object state = XposedHelpers.getObjectField(ripple, "mState");
            ColorStateList color = (ColorStateList) XposedHelpers.getObjectField(state, "mColor");
            ripple.setColor(colors(color, old, next, true));
        }
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) drawable(layers.getDrawable(i), old, next, foreground);
        } else if (drawable instanceof StateListDrawable && Build.VERSION.SDK_INT >= 29) {
            StateListDrawable states = (StateListDrawable) drawable;
            for (int i = 0; i < states.getStateCount(); i++) drawable(states.getStateDrawable(i), old, next, foreground);
        } else if (drawable.getCurrent() != drawable) drawable(drawable.getCurrent(), old, next, foreground);
    }

    private void refreshArtworkPlaceholder(ImageView image, PaletteColors old, PaletteColors next) {
        String name = image.getClass().getName();
        if (!name.equals("com.spotify.encoreconsumermobile.elements.artwork.ArtworkView")
                && !name.equals("com.spotify.creativeworkplatform.encore.elements.ArtworkView")) return;

        for (Field field : image.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !Drawable.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            try { drawable((Drawable) field.get(image), old, next, false); }
            catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
        }
    }

    private void refreshStoredColors(Drawable drawable, PaletteColors old, PaletteColors next, boolean foreground) {

        Drawable.ConstantState state = drawable.getConstantState();
        boolean stateChanged = hasChangedColors(state, old, next, foreground);
        boolean directChanged = hasChangedColors(drawable, old, next, true);
        if (!stateChanged && !directChanged) return;
        drawable.mutate();
        if (stateChanged) replaceStoredColors(drawable.getConstantState(), old, next, foreground);
        if (directChanged) replaceStoredColors(drawable, old, next, true);

        XposedHelpers.callMethod(drawable, "onStateChange", (Object) drawable.getState());

        if (stateChanged) XposedHelpers.callMethod(drawable, "onStateChange", (Object) drawable.getState());
        drawable.invalidateSelf();
    }

    private boolean hasChangedColors(Object owner, PaletteColors old, PaletteColors next, boolean foreground) {
        if (owner == null) return false;
        for (Field field : storedColorFields(owner.getClass())) {
            try {
                ColorStateList value = (ColorStateList) field.get(owner);
                if (colors(value, old, next, foreground) != value) return true;
            } catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
        }
        return false;
    }

    private void replaceStoredColors(Object owner, PaletteColors old, PaletteColors next, boolean foreground) {
        if (owner == null) return;
        for (Field field : storedColorFields(owner.getClass())) {
            try {
                ColorStateList value = (ColorStateList) field.get(owner);
                ColorStateList replacement = colors(value, old, next, foreground);
                if (replacement != value) field.set(owner, replacement);
            } catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
        }
    }

    private static List<Field> storedColorFields(Class<?> type) {
        return colorFields.computeIfAbsent(type, key -> {
            List<Field> result = new ArrayList<>();
            for (Class<?> current = key; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType() != ColorStateList.class) continue;
                    field.setAccessible(true);
                    result.add(field);
                }
            }
            return result;
        });
    }
}

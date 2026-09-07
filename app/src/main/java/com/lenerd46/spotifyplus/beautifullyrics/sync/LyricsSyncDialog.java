package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.lenerd46.spotifyplus.References;

final class LyricsSyncDialog extends Dialog {
    private View songBackground;
    private final TextView error;
    private final Button primary;
    private final Button secondary;

    LyricsSyncDialog(Context context, String title, View content, String confirm, String cancel, boolean destructive, Runnable onConfirm) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 20));

        TextView heading = new TextView(context);
        heading.setText(title); heading.setTextColor(Color.WHITE); heading.setTextSize(22);
        heading.setTypeface(null, Typeface.BOLD);

        if (References.beautifulFont != null && References.beautifulFont.get() != null)
            heading.setTypeface(References.beautifulFont.get());
        if (android.os.Build.VERSION.SDK_INT >= 28)
            heading.setAccessibilityHeading(true);

        heading.setPadding(0, 0, 0, dp(context, 16)); card.addView(heading);
        card.addView(content);

        error = new TextView(context); error.setTextSize(14);
        error.setTextColor(0xffff9b9b);
        error.setPadding(0, dp(context, 12), 0, 0);
        error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(error);

        primary = button(context, confirm, true, destructive);
        primary.setOnClickListener(v -> onConfirm.run());
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(-1, -2);
        primaryParams.topMargin = dp(context, 20); card.addView(primary, primaryParams);

        secondary = button(context, cancel, false, false);
        secondary.setOnClickListener(v -> dismiss());

        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(-1, -2);
        secondaryParams.topMargin = dp(context, 8); card.addView(secondary, secondaryParams);
        ScrollView viewport = new ScrollView(context) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                // Paint only the song backdrop over an opaque base, never the lyrics layer.
                int save = canvas.save();
                canvas.translate(0, getScrollY());
                if (songBackground != null && songBackground.isAttachedToWindow()) {
                    int[] source = new int[2], target = new int[2];
                    songBackground.getLocationOnScreen(source);
                    getLocationOnScreen(target);
                    canvas.translate(source[0] - target[0], source[1] - target[1]);
                    songBackground.draw(canvas);
                    canvas.translate(target[0] - source[0], target[1] - source[1]);
                }
                canvas.drawColor(0x99000000);
                canvas.restoreToCount(save);
                super.onDraw(canvas);
                if (songBackground != null) postInvalidateDelayed(33);
            }

            @Override protected void onMeasure(int width, int height) {
                Rect visible = new Rect(); getWindowVisibleDisplayFrame(visible);
                int available = visible.height() > 0 ? visible.height() : getResources().getDisplayMetrics().heightPixels;
                int cap = Math.max(1, available - dp(context, 48));
                if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED) cap = Math.min(cap, MeasureSpec.getSize(height));
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
            }
        };

        viewport.setBackground(shape(context, 0xff181818, 24, 0));
        viewport.setForeground(shape(context, Color.TRANSPARENT, 24, 0x40ffffff));
        viewport.setClipToOutline(true); viewport.addView(card); setContentView(viewport);
        setCanceledOnTouchOutside(true);

        Window window = getWindow();

        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.18f); window.setGravity(Gravity.CENTER);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    @Override public void show() {
        super.show();

        Window window = getWindow();
        if (window != null) {
            Rect visible = new Rect(); window.getDecorView().getWindowVisibleDisplayFrame(visible);
            int width = visible.width() > 0 ? visible.width() : getContext().getResources().getDisplayMetrics().widthPixels;

            window.setLayout(Math.min(dp(getContext(), 420), Math.max(1, width - dp(getContext(), 32))), -2);
        }
    }

    void setSongBackground(View background) {
        songBackground = background;
    }

    void error(String message) {
        error.setText(message); error.setVisibility(View.VISIBLE);
        error.post(() -> error.requestRectangleOnScreen(new Rect(0, 0, error.getWidth(), error.getHeight()), false));
    }

    void setSecondaryAction(Runnable action) {
        secondary.setOnClickListener(v -> action.run());
    }

    void setPrimaryState(String label, boolean enabled) {
        primary.setText(label); primary.setEnabled(enabled);
        error.setVisibility(View.GONE);
    }

    static Button button(Context context, String label, boolean primary, boolean destructive) {
        Button button = new Button(context, null, 0);

        button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD); button.setGravity(Gravity.CENTER);
        button.setSingleLine(false); button.setEllipsize(null);

        button.setMinHeight(dp(context, 48)); button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));

        int foreground = Color.WHITE;
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{0x66ffffff, foreground}));

        int fill = primary ? (destructive ? 0x55b92d3a : 0x38ffffff) : 0x18ffffff;
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff), shape(context, fill, 999, destructive ? 0x99ffb3b3 : primary ? 0x99ffffff : 0x55ffffff), null));

        return button;
    }

    static android.widget.Switch toggle(Context context) {
        android.widget.Switch toggle = new android.widget.Switch(context, null, 0);
        // No default style: opt into touch and keyboard interaction explicitly.
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setShowText(false);
        toggle.setTextOn("");
        toggle.setTextOff("");
        toggle.setSplitTrack(false);
        toggle.setSwitchMinWidth(dp(context, 52));
        toggle.setMinimumHeight(dp(context, 48));
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setThumbTintList(null);
        toggle.setTrackTintList(null);

        GradientDrawable thumb = shape(context, Color.WHITE, 999, 0);
        thumb.setSize(dp(context, 24), dp(context, 24));
        toggle.setThumbDrawable(new android.graphics.drawable.InsetDrawable(thumb, 0, dp(context, 2), 0, dp(context, 2)));
        android.graphics.drawable.StateListDrawable track = new android.graphics.drawable.StateListDrawable();
        GradientDrawable on = shape(context, 0x70ffffff, 999, 0x99ffffff);
        GradientDrawable off = shape(context, 0x18ffffff, 999, 0x55ffffff);
        on.setSize(dp(context, 52), dp(context, 28));
        off.setSize(dp(context, 52), dp(context, 28));
        track.addState(new int[]{android.R.attr.state_checked}, on);
        track.addState(new int[]{}, off);
        toggle.setTrackDrawable(track);
        return toggle;
    }

    static void styleInput(EditText input) {
        Context context = input.getContext();

        input.setTextColor(Color.WHITE); input.setHintTextColor(0xaaffffff); input.setTextSize(18);
        input.setBackgroundTintList(null);
        input.setBackground(shape(context, 0x20000000, 12, 0x55ffffff));
        input.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        input.setMinimumHeight(dp(context, 48));
    }

    private static GradientDrawable shape(Context context, int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(context, radius));
        if (stroke != 0) shape.setStroke(dp(context, 1), stroke);

        return shape;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

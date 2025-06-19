package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SettingItem;
import com.lenerd46.spotifyplus.scripting.events.EventManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsFlyoutHook extends SpotifyHook {
    private final Context context;
    private int buttonCount = 0;

    private static final int SETTINGS_OVERLAY_ID = 0x53504c53;
    private static final int DETAILED_SETTINGS_OVERLAY_ID = 0x53504c54;
    private static final int MARKETPLACE_OVERLAY_ID = 0x53504c55;

    private SharedPreferences prefs;
    private final static ConcurrentHashMap<String, List<SettingItem.SettingSection>> scriptSettings = new ConcurrentHashMap<>();

    public SettingsFlyoutHook(Context ctx) {
        context = ctx;
    }

    @Override
    public void hook() {
        try {
            var drawerClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("getDrawerState()I")));
            var method = bridge.findMethod(FindMethod.create().searchInClass(drawerClass).matcher(MethodMatcher.create().returnType(void.class).modifiers(Modifier.PUBLIC | Modifier.FINAL).paramCount(0).annotationCount(0))).get(0).getMethodInstance(lpparm.classLoader);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        FrameLayout fl = (FrameLayout) bridge.findField(FindField.create().searchInClass(drawerClass).matcher(FieldMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).type(FrameLayout.class))).get(0).getFieldInstance(lpparm.classLoader).get(param.thisObject);
                        Object drawer = param.thisObject;
//                        FrameLayout fl = (FrameLayout)XposedHelpers.getObjectField(drawer, "R0");

                        if(prefs == null) {
                            prefs = References.getPreferences();
                        }

                        LinearLayout settings = createSpotifyButton(0x12032022, "Spotify Plus Settings", createSettingsIcon(), fl);
                        if(settings != null) {
                            settings.setOnClickListener(v -> {
                                showSettingsPage();
                            });
                        }

                        LinearLayout marketplace = createSpotifyButton(0x4f524f52, "Marketplace", createMarketplaceIcon(), fl);
                        if(marketplace != null) {
                            marketplace.setOnClickListener(v -> {
                                showMarketplace();
                            });
                        }

                        if(prefs.getBoolean("social_enabled", false)) {
                            LinearLayout social = createSpotifyButton(0x09172022, "Friends", createUsersIcon(), fl);
                            if(social != null) {
                                social.setOnClickListener(v -> {
                                    SocialHook.showSocialPage();
                                });
                            }
                        }
                    } catch(Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private LinearLayout createSpotifyButton(int id, String text, Drawable icon, FrameLayout fl) {
        // Check if we've already added the button or if Spotify hasn't added its buttons yet
        if(fl.findViewById(id) != null || fl.getChildCount() == 0) return null;

        LinearLayout container = new LinearLayout(context);
        container.setId(id);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        container.setPadding(dpToPx(18), dpToPx(16), dpToPx(24), dpToPx(16));
        container.setElevation(999f);

        ImageView iconView = new ImageView(context);
        iconView.setImageDrawable(icon);
        iconView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
        iconLp.rightMargin = dpToPx(16);
        iconView.setLayoutParams(iconLp);

        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(16f);
        textView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        container.addView(iconView);
        container.addView(textView);

        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.gravity = Gravity.TOP | Gravity.START;

        int topMargin = dpToPx(95 + (4 * 56));
        containerLp.topMargin = topMargin + (dpToPx(50) * buttonCount);
        container.setLayoutParams(containerLp);

        buttonCount++;
        fl.addView(container);
        return container;
    }

    private void showSettingsPage() {
        try {
            Activity activity = References.currentActivity.get();
            if (activity == null || activity.isFinishing()) return;

            ViewGroup rootView = activity.findViewById(android.R.id.content);
            if (rootView == null) return;
            if (rootView.findViewById(SETTINGS_OVERLAY_ID) != null) return;

            FrameLayout overlay = new FrameLayout(activity);
            overlay.setId(SETTINGS_OVERLAY_ID);
            overlay.setClickable(true);
            overlay.setBackgroundColor(Color.parseColor("#141414"));
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout mainContainer = new LinearLayout(activity);
            mainContainer.setOrientation(LinearLayout.VERTICAL);
            mainContainer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout header = createSettingsHeader(activity, overlay, rootView, "Spotify Plus Settings");
            mainContainer.addView(header);

            android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
            LinearLayout contentContainer = new LinearLayout(activity);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            contentContainer.setPadding(0, dpToPx(16), 0, dpToPx(16));

            contentContainer.addView(createSettingsSection(activity, "Hooks", new String[]{
                    "Beautiful Lyrics",
                    "Social",
                    "Controls Test"
            }));

            contentContainer.addView(createSettingsSection(activity, "Scripting", new String[]{
                    "General"
            }));

            if(!scriptSettings.isEmpty()) {
                contentContainer.addView(createSettingsSection(activity, "Script Settings", scriptSettings.keySet().toArray(new String[0])));
            }

            TextView versionText = new TextView(activity);
            versionText.setText("Spotify Plus v0.4 • LeNerd46");
            versionText.setTextColor(Color.WHITE);
            versionText.setTextSize(12f);
            versionText.setGravity(Gravity.CENTER | Gravity.BOTTOM);
            FrameLayout.LayoutParams versionParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            versionParams.bottomMargin = dpToPx(12);
            versionText.setLayoutParams(versionParams);

            scrollView.addView(contentContainer);
            mainContainer.addView(scrollView);

            overlay.addView(mainContainer);
            overlay.addView(versionText);
            rootView.addView(overlay);
            animatePageIn(overlay);

            EventManager.getInstance().dispatchEvent("settingsOpened", null);
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Error showing settings: " + t);
        }
    }

    private LinearLayout createSettingsHeader(Activity activity, FrameLayout overlay, ViewGroup rootView, String titleText) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(16));
        header.setBackgroundColor(Color.parseColor("#272727"));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView backButton = new ImageView(activity);
        backButton.setImageDrawable(createBackArrowIcon());
        backButton.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        backButton.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(40), dpToPx(40)
        ));

        TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        backButton.setBackgroundResource(outValue.resourceId);

        backButton.setOnClickListener(v -> {
            animatePageOut(overlay, () -> {
                rootView.removeView(overlay);
            });
        });

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.weight = 1f;
        titleLp.leftMargin = dpToPx(16);
        title.setLayoutParams(titleLp);

        ImageView searchButton = new ImageView(activity);
        searchButton.setImageDrawable(createSearchIcon());
        searchButton.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        searchButton.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)));
        searchButton.setBackgroundResource(outValue.resourceId);

        header.addView(backButton);
        header.addView(title);
        header.addView(searchButton);

        return header;
    }

    private void showDetailedSettingsPage(String pageTitle, List<SettingItem.SettingSection> sections) {
        try {
            Activity activity = References.currentActivity.get();
            if (activity == null || activity.isFinishing()) return;

            ViewGroup rootView = activity.findViewById(android.R.id.content);
            if (rootView == null) return;

            FrameLayout overlay = new FrameLayout(activity);
            overlay.setId(DETAILED_SETTINGS_OVERLAY_ID);
            overlay.setClickable(true);
            overlay.setBackgroundColor(Color.parseColor("#141414"));
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout mainContainer = new LinearLayout(activity);
            mainContainer.setOrientation(LinearLayout.VERTICAL);
            mainContainer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout header = createSettingsHeader(activity, overlay, rootView, pageTitle);
            mainContainer.addView(header);

            android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
            LinearLayout contentContainer = new LinearLayout(activity);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            contentContainer.setPadding(0, dpToPx(8), 0, dpToPx(16));

            for (SettingItem.SettingSection section : sections) {
                contentContainer.addView(createDetailedSettingsSection(activity, section));
            }

            TextView creditsText = new TextView(activity);
            creditsText.setText("This hook is heavily based on Beautiful Lyrics by Surfbryce");
            creditsText.setTextColor(Color.WHITE);
            creditsText.setTextSize(12f);
            creditsText.setGravity(Gravity.CENTER | Gravity.BOTTOM);
            FrameLayout.LayoutParams versionParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            versionParams.bottomMargin = dpToPx(14);
            versionParams.leftMargin = dpToPx(120);
            versionParams.rightMargin = dpToPx(120);
            creditsText.setLayoutParams(versionParams);

            scrollView.addView(contentContainer);
            mainContainer.addView(scrollView);
            overlay.addView(mainContainer);

            if(pageTitle.equals("Beautiful Lyrics Settings")) {
                overlay.addView(creditsText);
            }

            rootView.addView(overlay);
            animatePageIn(overlay);
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Error showing detailed settings: " + t);
        }
    }

    private LinearLayout createDetailedSettingsSection(Activity activity, SettingItem.SettingSection section) {
        LinearLayout sectionLayout = new LinearLayout(activity);
        sectionLayout.setOrientation(LinearLayout.VERTICAL);
        sectionLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView sectionTitle = new TextView(activity);
        sectionTitle.setText(section.title);
        sectionTitle.setTextColor(Color.WHITE);
        sectionTitle.setTextSize(20f);
        sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sectionTitle.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(16));
        sectionLayout.addView(sectionTitle);

        for (SettingItem item : section.items) {
            sectionLayout.addView(createSettingItemView(activity, item));
        }

        return sectionLayout;
    }

    private LinearLayout createSettingItemView(Activity activity, SettingItem item) {
        LinearLayout itemLayout = new LinearLayout(activity);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setGravity(Gravity.CENTER_VERTICAL);
        itemLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout textContainer = new LinearLayout(activity);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textLp.weight = 1f;
        textLp.rightMargin = dpToPx(16);
        textContainer.setLayoutParams(textLp);

        TextView titleView = new TextView(activity);
        titleView.setText(item.title);
        titleView.setTextColor(item.enabled ? Color.WHITE : Color.parseColor("#666666"));
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textContainer.addView(titleView);

        if (item.description != null && !item.description.isEmpty()) {
            TextView descView = new TextView(activity);
            descView.setText(item.description);
            descView.setTextColor(Color.parseColor("#B3B3B3"));
            descView.setTextSize(14f);
            descView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            descLp.topMargin = dpToPx(4);
            descView.setLayoutParams(descLp);
            textContainer.addView(descView);
        }

        itemLayout.addView(textContainer);

        View control = createControlView(activity, item);
        if (control != null) {
            itemLayout.addView(control);
        }

        if (item.type == SettingItem.Type.NAVIGATION && item.onNavigate != null) {
            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            itemLayout.setBackgroundResource(outValue.resourceId);
            itemLayout.setOnClickListener(v -> item.onNavigate.run());
        }

        return itemLayout;
    }

    private View createControlView(Activity activity, SettingItem item) {
        switch (item.type) {
            case TOGGLE:
                return createToggleControl(activity, item);
            case SLIDER:
                return createSliderControl(activity, item);
            case TEXT_INPUT:
                return createTextInputControl(activity, item);
            case NAVIGATION:
                return createNavigationControl(activity, item);
            case BUTTON:
                return createButtonControl(activity, item);
            default:
                return null;
        }
    }

    private android.widget.Switch createToggleControl(Activity activity, SettingItem item) {
        android.widget.Switch toggle = new android.widget.Switch(activity);
        toggle.setChecked(item.value != null ? (Boolean) item.value : false);
        toggle.setEnabled(item.enabled);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(
                    toggle.isChecked() ? Color.parseColor("#1DB954") : Color.parseColor("#777777")
            ));
            toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(
                    toggle.isChecked() ? Color.parseColor("#4D1DB954") : Color.parseColor("#333333")
            ));
        }

        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.value = isChecked;
            if (item.onValueChange != null) {
                item.onValueChange.onValueChanged(isChecked);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(
                        isChecked ? Color.parseColor("#1DB954") : Color.parseColor("#777777")
                ));
                toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(
                        isChecked ? Color.parseColor("#4D1DB954") : Color.parseColor("#333333")
                ));
            }
        });

        return toggle;
    }

    private LinearLayout createSliderControl(Activity activity, SettingItem item) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(120), ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        android.widget.SeekBar seekBar = new android.widget.SeekBar(activity);
        float min = item.minValue != null ? (Float) item.minValue : 0f;
        float max = item.maxValue != null ? (Float) item.maxValue : 100f;
        float current = item.value != null ? (Float) item.value : min;

        seekBar.setMax((int) (max - min));
        seekBar.setProgress((int) (current - min));

        LinearLayout.LayoutParams seekBarLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        seekBarLp.weight = 1f;
        seekBar.setLayoutParams(seekBarLp);

        TextView valueText = new TextView(activity);
        valueText.setText(String.valueOf((int) current));
        valueText.setTextColor(Color.parseColor("#B3B3B3"));
        valueText.setTextSize(12f);
        valueText.setMinWidth(dpToPx(30));
        valueText.setGravity(Gravity.CENTER);

        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                float value = min + progress;
                valueText.setText(String.valueOf((int) value));
                if (fromUser) {
                    item.value = value;
                    if (item.onValueChange != null) {
                        item.onValueChange.onValueChanged(value);
                    }
                }
            }

            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        container.addView(seekBar);
        container.addView(valueText);
        return container;
    }

    private TextView createTextInputControl(Activity activity, SettingItem item) {
        TextView textView = new TextView(activity);
        textView.setText(item.value != null ? item.value.toString() : "Tap to edit");
        textView.setTextColor(Color.parseColor("#B3B3B3"));
        textView.setTextSize(14f);
        textView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        textView.setBackgroundResource(outValue.resourceId);

        textView.setOnClickListener(v -> showTextInputDialog(activity, item, textView));

        return textView;
    }

    private ImageView createNavigationControl(Activity activity, SettingItem item) {
        ImageView arrow = new ImageView(activity);
        arrow.setImageDrawable(createChevronRightIcon());
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)));
        return arrow;
    }

    private TextView createButtonControl(Activity activity, SettingItem item) {
        TextView button = new TextView(activity);
        button.setText(item.value != null ? item.value.toString() : "Action");
        button.setTextColor(Color.parseColor("#1DB954"));
        button.setTextSize(14f);
        button.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

        TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        button.setBackgroundResource(outValue.resourceId);

        button.setOnClickListener(v -> {
            if (item.onValueChange != null) {
                item.onValueChange.onValueChanged(null);
            }
        });

        return button;
    }

    private Drawable createSearchIcon() {
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2));

        float scale = size / 24f;

        canvas.drawCircle(11f * scale, 11f * scale, 8f * scale, paint);
        canvas.drawLine(21f * scale, 21f * scale, 16.65f * scale, 16.65f * scale, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private Drawable createMarketplaceIcon() {
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float scale = size / 24f;
        float strokeWidth = paint.getStrokeWidth();
        float padding = strokeWidth / 2;

        RectF awningRect = new RectF(
                2f * scale + padding,
                2f * scale + padding,
                22f * scale - padding,
                8f * scale
        );
        canvas.drawRect(awningRect, paint);

        float scallop_width = (awningRect.width() - strokeWidth) / 5f;
        float scallop_y = awningRect.bottom;
        float scallop_radius = scallop_width / 2f;

        for (int i = 0; i < 5; i++) {
            float scallop_x = awningRect.left + strokeWidth/2 + (i * scallop_width) + scallop_radius;
            canvas.drawCircle(scallop_x, scallop_y, scallop_radius, paint);
        }

        canvas.drawLine(
                4f * scale,
                scallop_y + scallop_radius,
                4f * scale,
                20f * scale,
                paint
        );

        canvas.drawLine(
                20f * scale,
                scallop_y + scallop_radius,
                20f * scale,
                20f * scale,
                paint
        );

        RectF storeBody = new RectF(
                4f * scale,
                scallop_y + scallop_radius,
                20f * scale,
                20f * scale
        );
        canvas.drawRoundRect(storeBody, 2f * scale, 2f * scale, paint);

        RectF doorRect = new RectF(
                10f * scale,
                14f * scale,
                14f * scale,
                20f * scale
        );
        canvas.drawRoundRect(doorRect, 1f * scale, 1f * scale, paint);

        canvas.drawCircle(
                12.5f * scale,
                17f * scale,
                0.5f * scale,
                paint
        );

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private void showTextInputDialog(Activity activity, SettingItem item, TextView textView) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle(item.title);

        final android.widget.EditText input = new android.widget.EditText(activity);
        input.setText(item.value != null ? item.value.toString() : "");
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newValue = input.getText().toString();
            item.value = newValue;
            textView.setText(newValue);
            if (item.onValueChange != null) {
                item.onValueChange.onValueChanged(newValue);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // This is where you define new detailed settings pages
    private LinearLayout createSettingsSection(Activity activity, String sectionTitle, String[] items) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView titleView = new TextView(activity);
        titleView.setText(sectionTitle);
        titleView.setTextColor(Color.parseColor("#B3B3B3")); // Spotify's secondary text color
        titleView.setTextSize(14f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(8));
        section.addView(titleView);

        for (String item : items) {
            LinearLayout itemLayout = new LinearLayout(activity);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            itemLayout.setBackgroundResource(outValue.resourceId);

            TextView itemText = new TextView(activity);
            itemText.setText(item);
            itemText.setTextColor(Color.WHITE);
            itemText.setTextSize(16f);
            itemText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

            ImageView arrow = new ImageView(activity);
            arrow.setImageDrawable(createChevronRightIcon());
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                    dpToPx(24), dpToPx(24)
            );
            arrow.setLayoutParams(arrowLp);

            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            textLp.weight = 1f;
            itemText.setLayoutParams(textLp);

            itemLayout.addView(itemText);
            itemLayout.addView(arrow);

            itemLayout.setOnClickListener(v -> {
                switch(item) {
                    // HOOKS
                    case "Controls Test":
                        List<SettingItem.SettingSection> controlTestSections = Arrays.asList(
                                new SettingItem.SettingSection("Content preferences", Arrays.asList(
                                        new SettingItem("Canvas", "Display short, looping visuals on the Now Playing View.", SettingItem.Type.TOGGLE)
                                                .setValue(false)
                                                .setOnValueChange(value -> XposedBridge.log("Canvas: " + value)),

                                        new SettingItem("Allow explicit content", "Explicit content (labeled with the ⚠ tag) is playable.", SettingItem.Type.TOGGLE)
                                                .setValue(true)
                                                .setOnValueChange(value -> XposedBridge.log("Explicit: " + value)),

                                        new SettingItem("Show unplayable songs", "Songs that aren't available (e.g., due to artist removal or region) are still visible.", SettingItem.Type.TOGGLE)
                                                .setValue(true)
                                                .setOnValueChange(value -> XposedBridge.log("Unplayable: " + value))
                                )),

                                new SettingItem.SettingSection("Display preferences", Arrays.asList(
                                        new SettingItem("App language", "Set your default language for the Spotify app, plus notifications and emails.", SettingItem.Type.NAVIGATION)
                                                .setOnNavigate(() -> XposedBridge.log("Navigate to language settings")),

                                        new SettingItem("Audio quality", "Adjust streaming and download quality", SettingItem.Type.SLIDER)
                                                .setValue(80f)
                                                .setRange(0f, 100f)
                                                .setOnValueChange(value -> XposedBridge.log("Quality: " + value))
                                ))
                        );

                        showDetailedSettingsPage("Content and display", controlTestSections);
                        break;

                    case "Beautiful Lyrics":

                        List<SettingItem.SettingSection> lyricsSections = Arrays.asList(
                                new SettingItem.SettingSection("Privacy", Arrays.asList(
                                        new SettingItem("Send Access Token", "Send your Spotify access token to the Beautiful Lyrics API. If disabled, some songs will not load lyrics", SettingItem.Type.TOGGLE)
                                                .setValue(prefs.getBoolean("lyrics_send_token", true))
                                                .setOnValueChange(value -> {
                                                    prefs.edit().putBoolean("lyrics_send_token", (Boolean)value).apply();
                                                })
                                ))
                        );

                        showDetailedSettingsPage("Beautiful Lyrics Settings", lyricsSections);
                        break;

                    case "Social":

                        List<SettingItem.SettingSection> socialSections = Arrays.asList(
                                new SettingItem.SettingSection("Privacy", Arrays.asList(
                                        new SettingItem("Enabled", "Whether to enable the social hooks (requires sending your Spotify access token for authentication)", SettingItem.Type.TOGGLE)
                                                .setValue(prefs.getBoolean("social_enabled", false))
                                                .setOnValueChange(value -> {
                                                    prefs.edit().putBoolean("social_enabled", (Boolean)value).apply();
                                                })
                                ))
                        );

                        showDetailedSettingsPage("Social Settings", socialSections);
                        break;

                    // SCRIPTING
                    case "General":
                        List<SettingItem.SettingSection> generalSections = Arrays.asList(
                                new SettingItem.SettingSection("General", Arrays.asList(
                                        new SettingItem("Enabled", "Sets whether to run scripts or not. This feature is still in development, sorry", SettingItem.Type.TOGGLE)
                                                .setEnabled(false),
                                        new SettingItem("Set Scripts Directory", "Tells the mod where to look for scripts", SettingItem.Type.BUTTON)
                                                .setValue("Select Directory")
                                                .setOnValueChange(value -> {
                                                    if(activity != null && !activity.isFinishing()) {
                                                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                                                        activity.startActivityForResult(intent, 9072022);
                                                    }
                                                })
                                ))
                        );

                        showDetailedSettingsPage("Scripting Settings", generalSections);
                        break;
                }

                if(!scriptSettings.isEmpty()) {
                    showDetailedSettingsPage(item, scriptSettings.get(item));
                }
            });

            section.addView(itemLayout);
        }

        return section;
    }

    private void animatePageIn(View page) {
        page.setTranslationX(page.getContext().getResources().getDisplayMetrics().widthPixels);
        page.setAlpha(0.8f);

        page.animate()
                .translationX(0)
                .alpha(1.0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void animatePageOut(View page, Runnable onComplete) {
        page.animate()
                .translationX(page.getContext().getResources().getDisplayMetrics().widthPixels)
                .alpha(0.8f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(onComplete)
                .start();
    }

    private Drawable createLightningIcon() {
        // idk, this was a placeholder icon
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        float scale = size / 24f;

        path.moveTo(7f * scale, 2f * scale);
        path.lineTo(7f * scale, 13f * scale);
        path.lineTo(10f * scale, 13f * scale);
        path.lineTo(10f * scale, 22f * scale);
        path.lineTo(17f * scale, 10f * scale);
        path.lineTo(13f * scale, 10f * scale);
        path.lineTo(17f * scale, 2f * scale);
        path.close();

        canvas.drawPath(path, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private Drawable createSettingsIcon() {
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2));
        paint.setStrokeCap(Paint.Cap.ROUND);

        float scale = size / 24f;
        float strokeWidth = paint.getStrokeWidth();
        float padding = strokeWidth / 2;

        float lineLength = 16f * scale;
        float knobRadius = 3f * scale;
        float lineSpacing = 6f * scale;

        float startY = 4f * scale + padding;
        float centerX = size / 2f;

        float line1Y = startY;
        float line1StartX = padding + 2f * scale;
        float line1EndX = line1StartX + lineLength;
        float knob1X = line1EndX - 2f * scale;

        canvas.drawLine(line1StartX, line1Y, knob1X - knobRadius, line1Y, paint);
        canvas.drawLine(knob1X + knobRadius, line1Y, line1EndX, line1Y, paint);
        canvas.drawCircle(knob1X, line1Y, knobRadius, paint);

        float line2Y = startY + lineSpacing;
        float line2StartX = padding + 2f * scale;
        float line2EndX = line2StartX + lineLength;
        float knob2X = line2StartX + 2f * scale;

        canvas.drawLine(line2StartX, line2Y, knob2X - knobRadius, line2Y, paint);
        canvas.drawLine(knob2X + knobRadius, line2Y, line2EndX, line2Y, paint);
        canvas.drawCircle(knob2X, line2Y, knobRadius, paint);

        float line3Y = startY + (lineSpacing * 2);
        float line3StartX = padding + 2f * scale;
        float line3EndX = line3StartX + lineLength;
        float knob3X = line3EndX - 2f * scale;

        canvas.drawLine(line3StartX, line3Y, knob3X - knobRadius, line3Y, paint);
        canvas.drawLine(knob3X + knobRadius, line3Y, line3EndX, line3Y, paint);
        canvas.drawCircle(knob3X, line3Y, knobRadius, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private Drawable createBackArrowIcon() {
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float scale = size / 24f;

        Path path = new Path();
        path.moveTo(15f * scale, 6f * scale);
        path.lineTo(9f * scale, 12f * scale);
        path.lineTo(15f * scale, 18f * scale);

        canvas.drawPath(path, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private Drawable createChevronRightIcon() {
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#B3B3B3"));
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float scale = size / 24f;

        // Draw chevron right
        Path path = new Path();
        path.moveTo(9f * scale, 6f * scale);
        path.lineTo(15f * scale, 12f * scale);
        path.lineTo(9f * scale, 18f * scale);

        canvas.drawPath(path, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private Drawable createUsersIcon() {
        int size = dpToPx(24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2));
        paint.setStrokeCap(Paint.Cap.ROUND);

        float scale = size / 24f;

        canvas.drawCircle(8f * scale, 6f * scale, 3f * scale, paint);

        RectF body1 = new RectF(
                2f * scale,
                12f * scale,
                14f * scale,
                24f * scale
        );
        canvas.drawArc(body1, 0, 180, false, paint);

        canvas.drawCircle(16f * scale, 5f * scale, 2.5f * scale, paint);

        RectF body2 = new RectF(
                11f * scale,
                10f * scale,
                21f * scale,
                20f * scale
        );
        canvas.drawArc(body2, 0, 180, false, paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    // SCRIPTING

    private void showMarketplace() {
        try {
            Activity activity = References.currentActivity.get();
            if (activity == null || activity.isFinishing()) return;

            ViewGroup rootView = activity.findViewById(android.R.id.content);
            if (rootView == null) return;
            if (rootView.findViewById(MARKETPLACE_OVERLAY_ID) != null) return;

            FrameLayout overlay = new FrameLayout(activity);
            overlay.setId(MARKETPLACE_OVERLAY_ID);
            overlay.setClickable(true);
            overlay.setBackgroundColor(Color.parseColor("#141414"));
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout mainContainer = new LinearLayout(activity);
            mainContainer.setOrientation(LinearLayout.VERTICAL);
            mainContainer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout header = createSettingsHeader(activity, overlay, rootView, "Marketplace");
            mainContainer.addView(header);

            android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
            LinearLayout contentContainer = new LinearLayout(activity);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            contentContainer.setPadding(0, dpToPx(16), 0, dpToPx(16));

            TextView textView = new TextView(activity);
            textView.setText("This feature is still in development, sorry :(");
            textView.setTextColor(Color.WHITE);
            textView.setGravity(Gravity.CENTER);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setTextSize(18f);

            contentContainer.addView(textView);

            scrollView.addView(contentContainer);
            mainContainer.addView(scrollView);

            overlay.addView(mainContainer);
            rootView.addView(overlay);
            animatePageIn(overlay);
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus] Error showing settings: " + t);
        }
    }

    public static void registerSettingSection(String title, SettingItem.SettingSection section) {
        var sections = scriptSettings.get(title);

        if (sections == null) {
            scriptSettings.put(title, Arrays.asList(section));
        } else {
            sections.add(section);
            scriptSettings.put(title, sections);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
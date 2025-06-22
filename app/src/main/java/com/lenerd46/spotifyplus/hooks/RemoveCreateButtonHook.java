package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;

import java.lang.reflect.*;
import java.util.*;

public class RemoveCreateButtonHook extends SpotifyHook {
    private static final int SETTINGS_OVERLAY_ID = 0x53504c53;

    private static final int DETAILED_SETTINGS_OVERLAY_ID = 0x53504c54;
    private static final int MARKETPLACE_OVERLAY_ID = 0x53504c55;
    private int idToUse = 8001;

    private SharedPreferences prefs;
    private final Context context;

    public RemoveCreateButtonHook(final Context context) { this.context = context; }

    @Override
    protected void hook() {
        try {
            if(prefs == null) {
                prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
            }

            var constructorClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItemSet(item1="))).get(0).getInstance(lpparm.classLoader);
            var parameterClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("NavigationBarItem(icon=").fieldCount(6))).get(0).getInstance(lpparm.classLoader);

            XposedHelpers.findAndHookConstructor(constructorClass, parameterClass, parameterClass, parameterClass, parameterClass, parameterClass , new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if(prefs.getBoolean("remove_create", false)) {
                        for(int i = 0; i < 5; i++) {
                            var item = param.args[i];

                            if(item == null) {
                                continue;
                            }

                            String content = item.toString().toLowerCase();

                            if(content.contains("create") || content.contains("premium")) {
                                XposedBridge.log("[SpotifyPlus] Removing navbar item: " + content);
                                param.args[i] = null;
                            }
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod("p.af4", lpparm.classLoader, "invokeSuspend", Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int number = (int) XposedHelpers.getIntField(param.thisObject, "a");
                    if(number != 18) return;

                    Object[] originalItemsWithNull = (Object[]) XposedHelpers.getObjectField(param.thisObject, "d");
                    if(originalItemsWithNull == null) return;
                    Object[] originalItems = Arrays.stream(originalItemsWithNull).filter(Objects::nonNull).toArray(Object[]::new);

                    Class<?> fvd0 = XposedHelpers.findClass("p.fvd0", lpparm.classLoader);
                    Object newArray = Array.newInstance(fvd0, originalItems.length + 2);

                    for(int i = 0; i < originalItems.length; i++) {
                        Array.set(newArray, i, originalItems[i]);
                    }

                    Object tempalte = originalItems[originalItems.length - 1];

                    Array.set(newArray, originalItems.length, createSideDrawerButton("Spotify Plus Settings", tempalte, fvd0, () -> showSettingsPage()));
                    Array.set(newArray, originalItems.length + 1, createSideDrawerButton("Marketplace", tempalte, fvd0, () -> showMarketplace()));
                    XposedHelpers.setObjectField(param.thisObject, "d", newArray);

                    XposedBridge.log("[SpotifyPlus] Injected new drawer item!");
                }
            });

            XposedHelpers.findAndHookMethod("p.v9u", lpparm.classLoader, "q", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String uri = (String) param.args[0];
//                    XposedBridge.log("[SpotifyPlus] Cosmos Request URI: " + uri);

//                    if(uri.equals("spotifyplus:settings")) {
//                        XposedBridge.log("[SpotifyPlus] Injected custom settings page fr fr");
//                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Could not find class: " + e.getMessage());
        }
    }

    private Object createSideDrawerButton(String title, Object template, Class<?> fvd0, Runnable onClick) {
        try {
            Class<?> dwd0  = XposedHelpers.findClass("p.dwd0", lpparm.classLoader);
            Class<?> cwd0  = XposedHelpers.findClass("p.cwd0", lpparm.classLoader);
            Class<?> bwd0  = XposedHelpers.findClass("p.bwd0", lpparm.classLoader);

            Class<?> qbp = XposedHelpers.findClass("p.qbp", lpparm.classLoader);
            Class<?> zpj0 = XposedHelpers.findClass("p.zpj0", lpparm.classLoader);
            Class<?> cbp = XposedHelpers.findClass("p.cbp", lpparm.classLoader);

            Class<?> wwk = XposedHelpers.findClass("p.wwk", lpparm.classLoader);

            Object originalDwd0 = XposedHelpers.getObjectField(template, "b");
            Object originalProps = XposedHelpers.getObjectField(originalDwd0, "b");
            Object originalBwd0 = XposedHelpers.getObjectField(originalProps, "f");
            Object originalNode = XposedHelpers.getObjectField(originalBwd0, "a");
            Object originalImpression = XposedHelpers.getObjectField(originalBwd0, "c");
            Object originalIcon = XposedHelpers.getObjectField(originalDwd0, "a");

            Object newOnClick = Proxy.newProxyInstance(lpparm.classLoader, new Class[] { qbp }, (proxy, method, args) -> {
                XposedBridge.log("[SpotifyPlus] Clicked button!");
                onClick.run();

                return null;
            });

            Constructor<?> bwd0Ctor = bwd0.getConstructor(zpj0, qbp, cbp);
            Constructor<?> propsCtor = cwd0.getConstructors()[0];

            int mask = 0;
            mask |= 1;
            mask |= 2;
            mask |= 4;
            mask |= 16;

            Object newInstrumentation = bwd0Ctor.newInstance(originalNode, newOnClick, originalImpression);
            Object newProps = propsCtor.newInstance(XposedHelpers.getObjectField(originalProps, "a"), 2131957897, "spotify:home", false, newInstrumentation, false, mask);

            XposedHelpers.setObjectField(newProps, "b", title);
            Object newDwd0 = XposedHelpers.newInstance(dwd0, originalIcon, newProps);

            return XposedHelpers.newInstance(fvd0, idToUse++, newDwd0);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    // SETTINGS STUFF
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

            Map<Integer, String> sections = new HashMap<>();
            sections.put(0, "General");
            sections.put(1, "Beautiful Lyrics");
            sections.put(2, "Social");
            contentContainer.addView(createSettingsSection(activity, "Hooks", sections));

            Map<Integer, String> scriptingSections = new HashMap<>();
            scriptingSections.put(3, "General");
            contentContainer.addView(createSettingsSection(activity, "Scripting", scriptingSections));

//            if(!scriptSettings.isEmpty()) {
//                contentContainer.addView(createSettingsSection(activity, "Script Settings", scriptSettings.keySet().toArray(new String[0])));
//            }

            TextView versionText = new TextView(activity);
            versionText.setText("Spotify Plus v0.5 • LeNerd46");
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
            case DROPDOWN:
                return createDropdownControl(activity, item);
            default:
                return null;
        }
    }

    private View createDropdownControl(Activity activity, SettingItem item) {
        Spinner dropdown = new Spinner(activity);
        List<String> options = item.options != null ? item.options : Collections.emptyList();

        dropdown.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, options));

        if(item.value != null) {
            int idx = options.indexOf(item.value.toString());
            if(idx >= 0) dropdown.setSelection(idx);
        }

        dropdown.setEnabled(item.enabled);

        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newValue = options.get(position);
                item.value = newValue;

                if(item.onValueChange != null) {
                    item.onValueChange.onValueChanged(newValue);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        return dropdown;
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

    // This is where you define new detailed settings pages
    private LinearLayout createSettingsSection(Activity activity, String sectionTitle, Map<Integer, String> items) {
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

        for (var item : items.keySet()) {
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
            itemText.setText(items.get(item));
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
                    case 1: // Beautiful Lyrics
                        List<SettingItem.SettingSection> lyricsSections = Arrays.asList(
                                new SettingItem.SettingSection("Visuals", Arrays.asList(
                                        new SettingItem("Animation Style", "Whether lyrics should be animted more like Apple Music, or Beautiful Lyrics", SettingItem.Type.DROPDOWN)
                                                .setOptions(Arrays.asList("Beautiful Lyrics", "Apple Music"))
                                                .setValue(prefs.getString("lyric_animation_style", "Beautiful Lyrics"))
                                                .setOnValueChange(value -> prefs.edit().putString("lyric_animation_style", (String)value).apply()),
                                        new SettingItem("Interlude Duration", "How much time it takes to show an interlude", SettingItem.Type.DROPDOWN)
                                                .setOptions(Arrays.asList("Beautiful Lyrics", "Spotify Plus", "Apple Music"))
                                                .setValue(prefs.getString("lyric_interlude_duration", "Beautiful Lyrics"))
                                                .setOnValueChange(value -> prefs.edit().putString("lyric_interlude_duration", (String)value).apply())
                                )),
                                new SettingItem.SettingSection("Privacy", Arrays.asList(
                                        new SettingItem("Send Access Token", "Send your Spotify access token to the Beautiful Lyrics API. If disabled, some songs will not load lyrics", SettingItem.Type.TOGGLE)
                                                .setValue(prefs.getBoolean("lyrics_send_token", true))
                                                .setOnValueChange(value -> prefs.edit().putBoolean("lyrics_send_token", (Boolean)value).apply())
                                ))
                        );

                        showDetailedSettingsPage("Beautiful Lyrics Settings", lyricsSections);
                        break;

                    case 2: // Social
                        List<SettingItem.SettingSection> socialSections = Arrays.asList(
                                new SettingItem.SettingSection("Privacy", Arrays.asList( // (requires sending your Spotify access token for authentication)
                                        new SettingItem("Enabled", "Whether to enable the social hooks", SettingItem.Type.TOGGLE)
                                                .setValue(prefs.getBoolean("social_enabled", false))
                                                .setEnabled(true)
                                                .setOnValueChange(value -> prefs.edit().putBoolean("social_enabled", (Boolean)value).apply())
//                                                .setOnValueChange(value -> {
//                                                    try {
//                                                        Class<?> duw = XposedHelpers.findClass("p.duw", lpparm.classLoader);
//                                                        Object request = XposedHelpers.newInstance(duw, 60000L);
//
//                                                        Class<?> oj60 = XposedHelpers.findClass("p.oj60", lpparm.classLoader);
//                                                        Object wrapped = XposedHelpers.newInstance(oj60, request);
//
//                                                        for(Method method : SeekHook.icdInstance.getClass().getDeclaredMethods()) {
//                                                            if(method.getName().equals("apply") && method.getParameterTypes().length == 1) {
//                                                                method.setAccessible(true);
//                                                                method.invoke(SeekHook.icdInstance, wrapped);
//                                                                XposedBridge.log("[SpotifyPlus] Triggered custom seek!");
//                                                            }
//                                                        }
//                                                    } catch (Exception e) {
//                                                        XposedBridge.log(e);
//                                                    }
//                                                })
                                ))
                        );

                        showDetailedSettingsPage("Social Settings", socialSections);
                        break;

                    case 0: // General
                        List<SettingItem.SettingSection> generalGeneralSettings = Arrays.asList(
                                new SettingItem.SettingSection("General", Arrays.asList(
                                        new SettingItem("Check For Updates", "Whether to check for updates on start", SettingItem.Type.TOGGLE)
                                                .setValue(prefs.getBoolean("general_check_updates", true))
                                                .setOnValueChange(value -> prefs.edit().putBoolean("general_check_updates", (Boolean)value).apply())
                                )),
                                new SettingItem.SettingSection("UI", Arrays.asList(
                                        new SettingItem("Remove Create Button", "Removes the create button in the navbar", SettingItem.Type.TOGGLE)
                                                .setValue(prefs.getBoolean("remove_create", false))
                                                .setOnValueChange(value -> prefs.edit().putBoolean("remove_create", (Boolean)value).apply()),
                                        new SettingItem("Startup Page", "What page Spotify should open when you open the app", SettingItem.Type.DROPDOWN)
                                                .setOptions(Arrays.asList("Home", "Search", "Explore", "Library"))
                                                .setValue(prefs.getString("startup_page", "HOME").charAt(0) + prefs.getString("startup_page", "HOME").substring(1).toLowerCase())
                                                .setOnValueChange(value -> prefs.edit().putString("startup_page", ((String)value).toUpperCase()).apply())
                                ))
                        );

                        showDetailedSettingsPage("General Settings", generalGeneralSettings);
                        break;

                    // SCRIPTING
                    case 3: // General
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

//                if(!scriptSettings.isEmpty()) {
//                    showDetailedSettingsPage(item, scriptSettings.get(item));
//                }
            });

            section.addView(itemLayout);
        }

        return section;
    }

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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}

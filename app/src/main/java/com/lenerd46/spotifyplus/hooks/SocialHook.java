package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.lenerd46.spotifyplus.References;
import com.mikhaellopez.circleview.CircleView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.HttpUrl;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class SocialHook extends SpotifyHook{
    private static final int SOCIAL_PAGE_OVERLAY_ID = 0x10032023;

    @Override
    protected void hook() {
        try {
            var okHttp = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().className("okhttp3.Request$Builder")));
            Method requestMadeOrSomething = bridge.findMethod(FindMethod.create().searchInClass(okHttp).matcher(MethodMatcher.create().returnType(void.class).modifiers(Modifier.PUBLIC | Modifier.FINAL).paramTypes(String.class, String.class))).get(1).getMethodInstance(lpparm.classLoader);

            XposedBridge.hookMethod(requestMadeOrSomething, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String headerName = (String) param.args[0];
                    String headerValue = (String) param.args[1];

                    if(headerName != null && headerName.equalsIgnoreCase("authorization")) {
                        String token = headerValue.replace("Bearer ", "").trim();
                        References.accessToken = new WeakReference<>(token);
                    }

                    Object url = XposedHelpers.getObjectField(param.thisObject, "a");
                    if(url.toString().toLowerCase().contains("ads") || url.toString().toLowerCase().contains("ad-")) {
                        param.setResult(null);
                    }

//                    if(url.url().toString().toLowerCase().contains("ad")) {
//                        Throwable t = new Throwable();
//                        XposedBridge.log("[SpotifyPlus] Ad endpoint found: " + url.url().toString());
//                        t.printStackTrace();
//                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public static void showSocialPage() {
        Activity activity = References.currentActivity;

        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if(rootView == null) return;
        if(rootView.findViewById(SOCIAL_PAGE_OVERLAY_ID) != null) return;

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setId(SOCIAL_PAGE_OVERLAY_ID);
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

        LinearLayout header = createSettingsHeader(activity, overlay, rootView, "Friends");
        mainContainer.addView(header);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        LinearLayout contentContainer = new LinearLayout(activity);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(0, dpToPx(16, activity), 0, dpToPx(16, activity));

        contentContainer.addView(createCategory(activity, "Friends"));
        contentContainer.addView(createCategory(activity, "Competition"));
        contentContainer.addView(createCategory(activity, "Other Thing"));

        scrollView.addView(contentContainer);
        mainContainer.addView(scrollView);

        overlay.addView(mainContainer);
        rootView.addView(overlay);
    }

    private static LinearLayout createSettingsHeader(Activity activity, FrameLayout overlay, ViewGroup rootView, String titleText) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(16, activity), dpToPx(24, activity), dpToPx(16, activity), dpToPx(16, activity));
        header.setBackgroundColor(Color.parseColor("#141414"));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView backButton = new ImageView(activity);
        backButton.setImageDrawable(createBackArrowIcon(activity));
        backButton.setPadding(dpToPx(8, activity), dpToPx(8, activity), dpToPx(8, activity), dpToPx(8, activity));
        backButton.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(40, activity), dpToPx(40, activity)
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
        titleLp.leftMargin = dpToPx(16, activity);
        title.setLayoutParams(titleLp);

        header.addView(backButton);
        header.addView(title);

        return header;
    }

    private static LinearLayout createCategory(Activity activity, String title) {
        LinearLayout category = new LinearLayout(activity);
        category.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(16, activity), dpToPx(12, activity), 0, 0);
        category.setLayoutParams(params);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(24f);
        category.addView(titleView);

        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.HORIZONTAL);

        for(int i = 0; i < 5; i++) {
            CircleView circle = new CircleView(activity, null);
            circle.setCircleColor(Color.WHITE);

            LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(64, activity));
            circleParams.setMargins(0, 0, dpToPx(16, activity), 0);
            circle.setLayoutParams(circleParams);

            section.addView(circle);
        }

        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        categoryParams.setMargins(0, 0, dpToPx(12, activity), 0);
        section.setLayoutParams(categoryParams);
        category.addView(section);

        return category;
    }

    private static int dpToPx(int dp, Activity context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
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

    private static void animatePageOut(View page, Runnable onComplete) {
        page.animate()
                .translationX(page.getContext().getResources().getDisplayMetrics().widthPixels)
                .alpha(0.8f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(onComplete)
                .start();
    }

    private static Drawable createBackArrowIcon(Activity activity) {
        int size = dpToPx(24, activity);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2, activity));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float scale = size / 24f;

        Path path = new Path();
        path.moveTo(15f * scale, 6f * scale);
        path.lineTo(9f * scale, 12f * scale);
        path.lineTo(15f * scale, 18f * scale);

        canvas.drawPath(path, paint);

        return new android.graphics.drawable.BitmapDrawable(activity.getResources(), bitmap);
    }
}

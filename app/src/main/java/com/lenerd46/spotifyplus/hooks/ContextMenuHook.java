package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.lenerd46.spotifyplus.ContextMenuItem;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ContextMenuHook extends SpotifyHook {
    public static String currentUri = null;
    public static List<ContextMenuItem> scriptItems = new ArrayList<>();
    private static List<ContextMenuItem> filteredItems;
    private static int extraCount;

    @Override
    protected void hook() {
        try {
            Class<?> adapterClass = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings("viewHolder is not supported."))).get(0).getInstance(lpparm.classLoader);

            XposedHelpers.findAndHookConstructor(
                    "com.spotify.bottomsheet.core.ScrollableContentWithHeaderLayout",
                    lpparm.classLoader,
                    Context.class,
                    AttributeSet.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            currentUri = null;
                            extraCount = 0;
                            filteredItems = null;
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(adapterClass, "getItemCount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int originalCount = (Integer) param.getResult();
                    param.setResult(originalCount + extraCount);
                }
            });

            XposedHelpers.findAndHookMethod(adapterClass, "getItemViewType", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int position = (Integer) param.args[0];
                    Object adapter = param.thisObject;
                    int originalCount = getOriginalItemCount(adapter);

                    if (position == originalCount && originalCount > 0) {
                        try {
                            Method getItemViewTypeMethod = adapter.getClass().getMethod("getItemViewType", int.class);
                            int firstItemViewType = (Integer) getItemViewTypeMethod.invoke(adapter, 0);
                            param.setResult(firstItemViewType);
                        } catch (Exception e) {
                            XposedBridge.log("[SpotifyPlus] Error getting first item view type, using default: " + e.getMessage());
                            param.setResult(0);
                        }
                    }
                }
            });

            for (Method m : adapterClass.getDeclaredMethods()) {
                if (!m.getName().equals("onBindViewHolder") || m.getParameterCount() != 2) continue;

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        int position = (Integer) p.args[1];
                        Object adapter = p.thisObject;

                        int originalCount = getOriginalItemCount(adapter);
                        if (position >= originalCount) return;

                        Field listF = bridge.findField(FindField.create().searchInClass(Arrays.asList(bridge.getClassData(adapterClass))).matcher(FieldMatcher.create().type(List.class))).get(0).getFieldInstance(lpparm.classLoader);
                        listF.setAccessible(true);
                        List<?> data = (List<?>) listF.get(adapter);
                        if (data == null || position >= data.size()) return;

                        Object rowModel = data.get(position);
                        String uri = findSpotifyUri(rowModel);

                        if (uri != null && (currentUri == null || currentUri.isEmpty())) {
                            currentUri = uri;
                            buildFilteredMenu(adapter, originalCount);
                        }
                    }
                });
                break;
            }

            Method[] methods = adapterClass.getDeclaredMethods();
            Method onBindViewHolderMethod = null;

            for (Method method : methods) {
                if (method.getName().equals("onBindViewHolder") && method.getParameterCount() == 2) {
                    onBindViewHolderMethod = method;
                    break;
                }
            }

            if (onBindViewHolderMethod != null) {
                XposedBridge.hookMethod(onBindViewHolderMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object adapter = param.thisObject;
                        Object viewHolder = param.args[0];
                        int position = (Integer) param.args[1];
                        int originalCount = getOriginalItemCount(adapter);

                        if(viewHolder.getClass().getSuperclass() != null) {
                            Object itemView = viewHolder.getClass().getSuperclass().getDeclaredField("itemView").get(viewHolder);
                            View textView = findViewByType((ViewGroup) itemView, "EncoreTextView");
                            XposedBridge.log("[SpotifyPlus] Found item at position " + findItemText(textView));
                        }

                        if (position == originalCount && originalCount > 0 && currentUri != null) {
                            for(int i = 0; i < scriptItems.size(); i++) {
                                final ContextMenuItem item = scriptItems.get(i);

                                if(currentUri.split(":")[1].equals(item.type.toLowerCase())) {
                                    addCustomItem(viewHolder, item);
                                }
                            }

                            param.setResult(null);
                        }
                    }
                });
            } else {
                XposedBridge.log("[SpotifyPlus] Could not find onBindViewHolder method");
            }
        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Error hooking adapter: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getOriginalItemCount(Object adapter) {
        try {
            Field[] fields = adapter.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(adapter);

                if (value instanceof List) {
                    return ((List<?>) value).size();
                } else if (value != null && value.getClass().isArray()) {
                    return Array.getLength(value);
                }
            }

            try {
                Method getItemCountMethod = adapter.getClass().getMethod("getItemCount");
                int count = (Integer) getItemCountMethod.invoke(adapter);
                return Math.max(0, count - 1);
            } catch (Exception e) {
                XposedBridge.log("[SpotifyPlus] Error calling getItemCount: " + e.getMessage());
            }

        } catch (Exception e) {
            XposedBridge.log("[SpotifyPlus] Error getting original item count: " + e.getMessage());
        }
        return 0;
    }

    private void addCustomItem(Object viewHolder, ContextMenuItem item) {
        try {
            Field itemViewField = null;
            Class<?> currentClass = viewHolder.getClass();

            while (currentClass != null && itemViewField == null) {
                try {
                    itemViewField = currentClass.getDeclaredField("itemView");
                    itemViewField.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }

            if (itemViewField == null) {
                XposedBridge.log("[SpotifyPlus] Could not find itemView field");
                return;
            }

            View itemView = (View) itemViewField.get(viewHolder);
            View textView = findViewByType((ViewGroup) itemView, "EncoreTextView");
            View iconView = findViewByType((ViewGroup) itemView, "EncoreIconView");

            if (textView instanceof TextView) {
                ((TextView) textView).setText(item.title);
            }

            if (iconView != null) {
                iconView.setVisibility(View.VISIBLE);
            }

            itemView.setOnClickListener(view -> item.callback.run());

        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private View findViewByType(ViewGroup parent, String className) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getClass().getSimpleName().equals(className)) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View found = findViewByType((ViewGroup) child, className);
                if (found != null) return found;
            }
        }
        return null;
    }
    private @Nullable String findSpotifyUri(Object obj) {
        if (obj == null) return null;

        String s = obj.toString();
        if (s.contains("spotify:")) {
            return s.substring(s.indexOf("spotify:")).trim();
        }

        for (Field f : obj.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object value = f.get(obj);

                if (value instanceof String && ((String) value).startsWith("spotify:")) {
                    return (String) value;
                }
                if (value instanceof Uri && "spotify".equals(((Uri) value).getScheme())) {
                    return value.toString();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void buildFilteredMenu(Object adapter, int originalCount) {
        String type = currentUri.split(":")[1];

        filteredItems = scriptItems.stream().filter(i -> i.type.equalsIgnoreCase(type)).collect(Collectors.toList());
        extraCount = filteredItems.size();

        if(extraCount == 0) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
        });
    }

    private @Nullable String findItemText(View v) {
        if (v instanceof TextView) {
            // Covers TextView, EncoreTextView, etc.
            return ((TextView) v).getText().toString();
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                String text = findItemText(g.getChildAt(i));
                if (text != null && !text.isEmpty()) return text;
            }
        }
        return null;
    }
}

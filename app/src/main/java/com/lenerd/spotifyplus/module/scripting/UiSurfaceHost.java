package com.lenerd.spotifyplus.module.scripting;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.WeakHashMap;

import com.lenerd.spotifyplus.R;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class UiSurfaceHost {
    private static final String TAG = "SpotifyPlus";
    private final String surfaceId;

    private final ViewGroup root;
    private final Context context;
    private final Map<Integer, Object> nodes = new HashMap<>();
    private final Map<Integer, JSONObject> nodeProps = new HashMap<>();
    private final Map<Integer, Integer> parentByChild = new HashMap<>();
    private final Map<Integer, TextWatcher> textWatchers = new HashMap<>();
    private final Set<Integer> suppressedEventNodes = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<ImageView, String> appliedImageSources = new WeakHashMap<>();
    private final Map<ImageView, Integer> imageRequestIds = new WeakHashMap<>();
    private int nextImageRequestId = 1;

    public UiSurfaceHost(String surfaceId, ViewGroup root) {
        this.surfaceId = surfaceId;
        this.root = root;
        this.context = root.getContext();
    }

    public void applyOps(JSONArray ops) {
        try {
            for (int i = 0; i < ops.length(); i++) applyOp(ops.getJSONObject(i));
        } catch (Exception e) {
            Log.e(TAG, "Failed applying UI ops", e);
        }
    }

    private void applyOp(JSONObject op) throws Exception {
        String kind = op.getString("op");

        switch (kind) {
            case "createNode":
                createNode(op);
                break;
            case "createText":
                createText(op);
                break;
            case "appendChild":
                appendChild(op);
                break;
            case "appendToRoot":
                appendToRoot(op);
                break;
            case "removeChild":
                removeChild(op);
                break;
            case "removeFromRoot":
                removeFromRoot(op);
                break;
            case "updateProps":
                updateProps(op);
                break;
            case "updateText":
                updateText(op);
                break;
            case "destroyNode":
                destroyNode(op);
                break;
            case "insertBefore":
                insertBefore(op);
                break;
            case "insertInRootBefore":
                insertInRootBefore(op);
                break;
            default:
                Log.w(TAG, "Unknown op " + kind);
                break;
        }
    }

    private void createNode(JSONObject op) throws Exception {
        int id = op.getInt("id");
        String type = op.getString("type");
        JSONObject props = copyJson(op.optJSONObject("props"));

        Object node = createNodeByType(type);
        if (node == null) {
            Log.w(TAG, "Unknown node type " + type);
            return;
        }

        if (node instanceof View) ((View) node).setId(View.generateViewId());
        nodes.put(id, node);
        nodeProps.put(id, props);

        if (node instanceof View) applyProps((View) node, props);
    }

    private Object createNodeByType(String type) {
        switch (type) {
            case "View":
            case "LinearLayout": {
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                return layout;
            }
            case "FrameLayout":
                return new FrameLayout(context);
            case "RelativeLayout":
                return new RelativeLayout(context);
            case "ScrollView":
                return new ScrollView(context);
            case "HorizontalScrollView":
                return new HorizontalScrollView(context);
            case "PlainView":
                return new View(context);
            case "Text":
            case "TextView":
                return new TextView(context);
            case "EditText":
                return new EditText(context);
            case "Button":
                return new Button(context);
            case "Image":
            case "ImageView":
                return new ImageView(context);
            case "ImageButton":
                return new ImageButton(context);
            case "ProgressBar":
                return new ProgressBar(context);
            case "ProgressBarHorizontal":
                return new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            case "SeekBar":
                return new SeekBar(context);
            case "Switch":
                return new Switch(context);
            case "CheckBox":
                return new CheckBox(context);
            case "RadioButton":
                return new RadioButton(context);
            case "RadioGroup":
                return new RadioGroup(context);
            case "ToggleButton":
                return new ToggleButton(context);
            case "Space":
                return new Space(context);
            default:
                return null;
        }
    }

    private void createText(JSONObject op) throws Exception {
        int id = op.getInt("id");
        nodes.put(id, op.getString("text"));
        nodeProps.put(id, new JSONObject());
    }

    private void appendChild(JSONObject op) throws Exception {
        int parentId = op.getInt("parentId");
        int childId = op.getInt("childId");

        Object parent = nodes.get(parentId);
        Object child = nodes.get(childId);

        if (parent instanceof TextView && child instanceof String) {
            ((TextView) parent).setText((String) child);
            parentByChild.put(childId, parentId);
            return;
        }

        if (parent instanceof ViewGroup && child instanceof View) {
            ViewGroup parentView = (ViewGroup) parent;
            View childView = (View) child;

            if ((parentView instanceof ScrollView || parentView instanceof HorizontalScrollView) && parentView.getChildCount() > 0) {
                Log.w(TAG, "Scroll containers only support one direct child. Replacing existing child.");
                parentView.removeAllViews();
            }

            detachFromParent(childView);
            parentView.addView(childView, buildLayoutParams(parentView, childView, getProps(childId)));
            parentByChild.put(childId, parentId);
            applyProps(childView, getProps(childId));

            if (childView instanceof ImageView) {
                applyImageViewProps((ImageView) childView, getProps(childId));
            }

            return;
        }

        Log.w(TAG, "Unsupported appendChild parent=" + parent + " child=" + child);
    }

    private void appendToRoot(JSONObject op) throws Exception {
        int childId = op.getInt("childId");
        Object child = nodes.get(childId);

        if (!(child instanceof View)) {
            Log.w(TAG, "Root child is not a View: " + childId);
            return;
        }

        View childView = (View) child;
        detachFromParent(childView);
        root.addView(childView, buildLayoutParams(root, childView, getProps(childId)));
        parentByChild.put(childId, -1);
        applyProps(childView, getProps(childId));
        maybeLoadImageForView(childView, getProps(childId));
    }

    private void removeChild(JSONObject op) throws Exception {
        int parentId = op.getInt("parentId");
        int childId = op.getInt("childId");

        Object parent = nodes.get(parentId);
        Object child = nodes.get(childId);

        if (parent instanceof ViewGroup && child instanceof View) {
            ((ViewGroup) parent).removeView((View) child);
            parentByChild.remove(childId);
        }
    }

    private void removeFromRoot(JSONObject op) throws Exception {
        int childId = op.getInt("childId");
        Object child = nodes.get(childId);

        if (child instanceof View) {
            root.removeView((View) child);
            parentByChild.remove(childId);
        }
    }

    private void updateProps(JSONObject op) throws Exception {
        int id = op.getInt("id");
        Object node = nodes.get(id);
        if (!(node instanceof View)) return;

        JSONObject merged = mergeJson(getProps(id), op.optJSONObject("props"));
        nodeProps.put(id, merged);
        applyProps((View) node, merged);
    }

    private void updateText(JSONObject op) throws Exception {
        int id = op.getInt("id");
        String text = op.getString("text");
        Object node = nodes.get(id);

        if (node instanceof String) {
            nodes.put(id, text);

            Integer parentId = parentByChild.get(id);
            if (parentId != null) {
                Object parent = nodes.get(parentId);
                if (parent instanceof TextView) ((TextView) parent).setText(text);
            }
            return;
        }

        if (node instanceof TextView) ((TextView) node).setText(text);
    }

    private void destroyNode(JSONObject op) throws Exception {
        int id = op.getInt("id");
        Object node = nodes.remove(id);
        nodeProps.remove(id);
        parentByChild.remove(id);

        cleanupNodeListeners(id, node);

        if (node instanceof View) detachFromParent((View) node);
    }

    private void insertBefore(JSONObject op) throws Exception {
        int parentId = op.getInt("parentId");
        int childId = op.getInt("childId");
        int beforeChildId = op.getInt("beforeChildId");

        Object parent = nodes.get(parentId);
        Object child = nodes.get(childId);
        Object beforeChild = nodes.get(beforeChildId);

        if (!(parent instanceof ViewGroup parentView) || !(child instanceof View childView)) {
            Log.w(TAG, "insertBefore unsupported parent=" + parent + " child=" + child);
            return;
        }

        if (parentView instanceof ScrollView || parentView instanceof HorizontalScrollView) {
            parentView.removeAllViews();
            detachFromParent(childView);
            parentView.addView(childView, buildLayoutParams(parentView, childView, getProps(childId)));
            parentByChild.put(childId, parentId);
            applyProps(childView, getProps(childId));
            maybeLoadImageForView(childView, getProps(childId));
            return;
        }

        int beforeIndex = -1;
        if (beforeChild instanceof View beforeView) {
            beforeIndex = parentView.indexOfChild(beforeView);
        }

        detachFromParent(childView);

        if (beforeIndex >= 0) {
            parentView.addView(childView, beforeIndex, buildLayoutParams(parentView, childView, getProps(childId)));
        } else {
            parentView.addView(childView, buildLayoutParams(parentView, childView, getProps(childId)));
        }

        parentByChild.put(childId, parentId);
        applyProps(childView, getProps(childId));
        maybeLoadImageForView(childView, getProps(childId));
    }

    private void insertInRootBefore(JSONObject op) throws Exception {
        int childId = op.getInt("childId");
        int beforeChildId = op.getInt("beforeChildId");

        Object child = nodes.get(childId);
        Object beforeChild = nodes.get(beforeChildId);

        if (!(child instanceof View childView)) {
            Log.w(TAG, "insertInRootBefore child is not a View: " + child);
            return;
        }

        int beforeIndex = -1;
        if (beforeChild instanceof View beforeView) {
            beforeIndex = root.indexOfChild(beforeView);
        }

        detachFromParent(childView);

        if (beforeIndex >= 0) {
            root.addView(childView, beforeIndex, buildLayoutParams(root, childView, getProps(childId)));
        } else {
            root.addView(childView, buildLayoutParams(root, childView, getProps(childId)));
        }

        parentByChild.put(childId, -1);
        applyProps(childView, getProps(childId));
        maybeLoadImageForView(childView, getProps(childId));
    }

    private void applyProps(View view, JSONObject props) throws Exception {
        Integer nodeId = findNodeIdForView(view);

        if (nodeId == null) {
            applyCommonProps(view, props);

            if (view.getParent() instanceof ViewGroup) {
                view.setLayoutParams(buildLayoutParams((ViewGroup) view.getParent(), view, props));
            }

            if (view instanceof ViewGroup) applyViewGroupProps((ViewGroup) view, props);
            if (view instanceof LinearLayout) applyLinearLayoutProps((LinearLayout) view, props);
            if (view instanceof RadioGroup) applyRadioGroupProps((RadioGroup) view, props);
            if (view instanceof ImageView) applyImageViewProps((ImageView) view, props);
            if (view instanceof TextView) applyTextViewProps((TextView) view, props);
            if (view instanceof EditText) applyEditTextProps((EditText) view, props);
            if (view instanceof ProgressBar) applyProgressBarProps((ProgressBar) view, props);
            if (view instanceof SeekBar) applySeekBarProps((SeekBar) view, props);
            if (view instanceof CompoundButton) applyCompoundButtonProps((CompoundButton) view, props);
            if (view instanceof Switch) applySwitchProps((Switch) view, props);
            if (view instanceof ToggleButton) applyToggleButtonProps((ToggleButton) view, props);
            if (view instanceof ScrollView) applyScrollViewProps((ScrollView) view, props);
            if (view instanceof HorizontalScrollView)
                applyHorizontalScrollViewProps((HorizontalScrollView) view, props);
            return;
        }

        withSuppressedEvents(nodeId, () -> {
            applyCommonProps(view, props);

            if (view.getParent() instanceof ViewGroup) {
                view.setLayoutParams(buildLayoutParams((ViewGroup) view.getParent(), view, props));
            }

            if (view instanceof ViewGroup) applyViewGroupProps((ViewGroup) view, props);
            if (view instanceof LinearLayout) applyLinearLayoutProps((LinearLayout) view, props);
            if (view instanceof RadioGroup) applyRadioGroupProps((RadioGroup) view, props);
            if (view instanceof ImageView) applyImageViewProps((ImageView) view, props);
            if (view instanceof TextView) applyTextViewProps((TextView) view, props);
            if (view instanceof EditText) applyEditTextProps((EditText) view, props);
            if (view instanceof ProgressBar) applyProgressBarProps((ProgressBar) view, props);
            if (view instanceof SeekBar) applySeekBarProps((SeekBar) view, props);
            if (view instanceof CompoundButton) applyCompoundButtonProps((CompoundButton) view, props);
            if (view instanceof Switch) applySwitchProps((Switch) view, props);
            if (view instanceof ToggleButton) applyToggleButtonProps((ToggleButton) view, props);
            if (view instanceof ScrollView) applyScrollViewProps((ScrollView) view, props);
            if (view instanceof HorizontalScrollView)
                applyHorizontalScrollViewProps((HorizontalScrollView) view, props);
        });

        applyEventProps(view, props, nodeId);
    }

    private void applyEventProps(View view, JSONObject props, int nodeId) throws Exception {
        applyCommonEventProps(view, props, nodeId);

        if (view instanceof EditText) applyEditTextEventProps((EditText) view, props, nodeId);
        if (view instanceof CompoundButton) applyCompoundButtonEventProps((CompoundButton) view, props, nodeId);
        if (view instanceof RadioGroup) applyRadioGroupEventProps((RadioGroup) view, props, nodeId);
        if (view instanceof SeekBar) applySeekBarEventProps((SeekBar) view, props, nodeId);
        if (view instanceof ScrollView) applyScrollEventProps((View) view, props, nodeId);
        if (view instanceof HorizontalScrollView) applyScrollEventProps((View) view, props, nodeId);
    }

    private void applyCommonEventProps(View view, JSONObject props, int nodeId) {
        Integer onClickEventId = getEventId(props, "onClick");
        if (onClickEventId != null) {
            view.setOnClickListener(v -> {
                if (isDispatchSuppressed(nodeId)) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("x", v.getX());
                    payload.put("y", v.getY());
                    sendEventToNode(nodeId, "onClick", onClickEventId, payload);
                } catch (Exception ignored) { }
            });
            view.setClickable(true);
        } else {
            view.setOnClickListener(null);
        }

        Integer onLongClickEventId = getEventId(props, "onLongClick");
        if (onLongClickEventId != null) {
            view.setOnLongClickListener(v -> {
                if (isDispatchSuppressed(nodeId)) return false;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("x", v.getX());
                    payload.put("y", v.getY());
                    sendEventToNode(nodeId, "onLongClick", onLongClickEventId, payload);
                } catch (Exception ignored) {
                }

                return true;
            });

            view.setLongClickable(true);
        } else {
            view.setOnLongClickListener(null);
        }

        Integer onFocusEventId = getEventId(props, "onFocus");
        Integer onBlurEventId = getEventId(props, "onBlur");

        if (onFocusEventId != null || onBlurEventId != null) {
            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (isDispatchSuppressed(nodeId)) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("hasFocus", hasFocus);

                    if (hasFocus && onFocusEventId != null) {
                        sendEventToNode(nodeId, "onFocus", onFocusEventId, payload);
                    } else if (!hasFocus && onBlurEventId != null) {
                        sendEventToNode(nodeId, "onBlur", onBlurEventId, payload);
                    }
                } catch (Exception ignored) {
                }
            });
        } else {
            view.setOnFocusChangeListener(null);
        }
    }

    private void applyEditTextEventProps(EditText view, JSONObject props, int nodeId) {
        TextWatcher existingWatcher = textWatchers.remove(nodeId);
        if (existingWatcher != null) view.removeTextChangedListener(existingWatcher);

        Integer onChangeTextEventId = getEventId(props, "onChangeText");
        if (onChangeTextEventId != null) {
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isDispatchSuppressed(nodeId)) return;

                    try {
                        JSONObject payload = basePayload(nodeId);
                        payload.put("text", String.valueOf(s));
                        sendEventToNode(nodeId, "onChangeText", onChangeTextEventId, payload);
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            view.addTextChangedListener(watcher);
            textWatchers.put(nodeId, watcher);
        }

        Integer onSubmitEditingEventId = getEventId(props, "onSubmitEditing");
        if (onSubmitEditingEventId != null) {
            view.setOnEditorActionListener((v, actionId, event) -> {
                if (isDispatchSuppressed(nodeId)) return false;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("text", v.getText() != null ? v.getText().toString() : "");
                    payload.put("actionId", actionId);
                    sendEventToNode(nodeId, "onSubmitEditing", onSubmitEditingEventId, payload);
                } catch (Exception ignored) {
                }

                return false;
            });
        } else {
            view.setOnEditorActionListener(null);
        }
    }

    private void applyCompoundButtonEventProps(CompoundButton view, JSONObject props, int nodeId) {
        Integer onValueChangeEventId = getEventId(props, "onValueChange");

        if (onValueChangeEventId != null) {
            view.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isDispatchSuppressed(nodeId)) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("checked", isChecked);
                    payload.put("value", isChecked);
                    sendEventToNode(nodeId, "onValueChange", onValueChangeEventId, payload);
                } catch (Exception ignored) {
                }
            });
        } else {
            view.setOnCheckedChangeListener(null);
        }
    }

    private void applyRadioGroupEventProps(RadioGroup view, JSONObject props, int nodeId) {
        Integer onValueChangeEventId = getEventId(props, "onValueChange");

        if (onValueChangeEventId != null) {
            view.setOnCheckedChangeListener((group, checkedViewId) -> {
                if (isDispatchSuppressed(nodeId)) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    Integer checkedNodeId = findNodeIdByAndroidViewId(checkedViewId);
                    if (checkedNodeId != null) payload.put("checkedId", checkedNodeId);
                    else payload.put("checkedId", JSONObject.NULL);
                    payload.put("value", checkedNodeId != null ? checkedNodeId : JSONObject.NULL);
                    sendEventToNode(nodeId, "onValueChange", onValueChangeEventId, payload);
                } catch (Exception ignored) {
                }
            });
        } else {
            view.setOnCheckedChangeListener(null);
        }
    }

    private void applySeekBarEventProps(SeekBar view, JSONObject props, int nodeId) {
        Integer onValueChangeEventId = getEventId(props, "onValueChange");
        Integer onSlidingStartEventId = getEventId(props, "onSlidingStart");
        Integer onSlidingCompleteEventId = getEventId(props, "onSlidingComplete");

        if (onValueChangeEventId == null && onSlidingStartEventId == null && onSlidingCompleteEventId == null) {
            view.setOnSeekBarChangeListener(null);
            return;
        }

        view.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isDispatchSuppressed(nodeId) || onValueChangeEventId == null) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("value", progress);
                    payload.put("fromUser", fromUser);
                    sendEventToNode(nodeId, "onValueChange", onValueChangeEventId, payload);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (isDispatchSuppressed(nodeId) || onSlidingStartEventId == null) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("value", seekBar.getProgress());
                    sendEventToNode(nodeId, "onSlidingStart", onSlidingStartEventId, payload);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isDispatchSuppressed(nodeId) || onSlidingCompleteEventId == null) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("value", seekBar.getProgress());
                    sendEventToNode(nodeId, "onSlidingComplete", onSlidingCompleteEventId, payload);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void applyScrollEventProps(View view, JSONObject props, int nodeId) {
        Integer onScrollEventId = getEventId(props, "onScroll");

        if (onScrollEventId != null) {
            view.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (isDispatchSuppressed(nodeId)) return;

                try {
                    JSONObject payload = basePayload(nodeId);
                    payload.put("x", scrollX);
                    payload.put("y", scrollY);
                    payload.put("oldX", oldScrollX);
                    payload.put("oldY", oldScrollY);
                    sendEventToNode(nodeId, "onScroll", onScrollEventId, payload);
                } catch (Exception ignored) {
                }
            });
        } else {
            view.setOnScrollChangeListener(null);
        }
    }

    private void applyCommonProps(View view, JSONObject props) throws Exception {
        if (props.has("visibility")) view.setVisibility(parseVisibility(props.opt("visibility")));
        if (props.has("enabled")) view.setEnabled(parseBoolean(props.opt("enabled"), view.isEnabled()));
        if (props.has("clickable")) view.setClickable(parseBoolean(props.opt("clickable"), view.isClickable()));
        if (props.has("longClickable")) view.setLongClickable(parseBoolean(props.opt("longClickable"), view.isLongClickable()));
        if (props.has("focusable")) view.setFocusable(parseBoolean(props.opt("focusable"), view.isFocusable()));
        if (props.has("focusableInTouchMode"))
            view.setFocusableInTouchMode(parseBoolean(props.opt("focusableInTouchMode"), view.isFocusableInTouchMode()));
        if (props.has("selected")) view.setSelected(parseBoolean(props.opt("selected"), view.isSelected()));
        if (props.has("activated")) view.setActivated(parseBoolean(props.opt("activated"), view.isActivated()));
        if (props.has("duplicateParentStateEnabled"))
            view.setDuplicateParentStateEnabled(parseBoolean(props.opt("duplicateParentStateEnabled"), view.isDuplicateParentStateEnabled()));
        if (props.has("hapticFeedbackEnabled"))
            view.setHapticFeedbackEnabled(parseBoolean(props.opt("hapticFeedbackEnabled"), view.isHapticFeedbackEnabled()));
        if (props.has("soundEffectsEnabled"))
            view.setSoundEffectsEnabled(parseBoolean(props.opt("soundEffectsEnabled"), view.isSoundEffectsEnabled()));
        if (props.has("alpha")) view.setAlpha((float) parseDouble(props.opt("alpha"), view.getAlpha()));
        if (props.has("rotation")) view.setRotation((float) parseDouble(props.opt("rotation"), view.getRotation()));
        if (props.has("rotationX")) view.setRotationX((float) parseDouble(props.opt("rotationX"), view.getRotationX()));
        if (props.has("rotationY")) view.setRotationY((float) parseDouble(props.opt("rotationY"), view.getRotationY()));
        if (props.has("scaleX")) view.setScaleX((float) parseDouble(props.opt("scaleX"), view.getScaleX()));
        if (props.has("scaleY")) view.setScaleY((float) parseDouble(props.opt("scaleY"), view.getScaleY()));
        if (props.has("translationX"))
            view.setTranslationX(parseSize(props.opt("translationX"), (int) view.getTranslationX()));
        if (props.has("translationY"))
            view.setTranslationY(parseSize(props.opt("translationY"), (int) view.getTranslationY()));
        if (props.has("translationZ"))
            view.setTranslationZ(parseSize(props.opt("translationZ"), (int) view.getTranslationZ()));
        if (props.has("elevation")) view.setElevation(parseSize(props.opt("elevation"), (int) view.getElevation()));
        if (props.has("minimumWidth"))
            view.setMinimumWidth(parseSize(props.opt("minimumWidth"), view.getMinimumWidth()));
        if (props.has("minimumHeight"))
            view.setMinimumHeight(parseSize(props.opt("minimumHeight"), view.getMinimumHeight()));
        if (props.has("contentDescription")) view.setContentDescription(props.optString("contentDescription", null));
        if (props.has("keepScreenOn"))
            view.setKeepScreenOn(parseBoolean(props.opt("keepScreenOn"), view.getKeepScreenOn()));
        if (props.has("fitsSystemWindows"))
            view.setFitsSystemWindows(parseBoolean(props.opt("fitsSystemWindows"), view.getFitsSystemWindows()));
        if (props.has("clipToOutline"))
            view.setClipToOutline(parseBoolean(props.opt("clipToOutline"), view.getClipToOutline()));
        if (props.has("tag")) view.setTag(String.valueOf(props.opt("tag")));
        if (props.has("backgroundColor") || props.has("borderRadius") || props.has("borderWidth") || props.has("borderColor") || props.has("borderTopLeftRadius") || props.has("borderTopRightRadius") || props.has("borderBottomLeftRadius") || props.has("borderBottomRightRadius")) {
            applyBackgroundProps(view, props);
        }
        if (props.has("textAlignment")) view.setTextAlignment(parseTextAlignment(props.optString("textAlignment", "")));

        applyPadding(view, props);
    }

    private void applyBackgroundProps(View view, JSONObject props) {
        Integer color = props.has("backgroundColor") ? parseColor(props.opt("backgroundColor")) : null;
        Integer borderColor = props.has("borderColor") ? parseColor(props.opt("borderColor")) : null;
        int borderWidth = props.has("borderWidth") ? parseSize(props.opt("borderWidth"), 0) : 0;
        int radius = props.has("borderRadius") ? parseSize(props.opt("borderRadius"), 0) : 0;
        boolean hasPerCornerRadius = props.has("borderTopLeftRadius") || props.has("borderTopRightRadius") || props.has("borderBottomLeftRadius") || props.has("borderBottomRightRadius");

        if (color == null && radius <= 0 && borderWidth <= 0 && !hasPerCornerRadius) return;

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color != null ? color : Color.TRANSPARENT);

        if (hasPerCornerRadius) {
            float topLeft = props.has("borderTopLeftRadius") ? parseSize(props.opt("borderTopLeftRadius"), radius) : radius;
            float topRight = props.has("borderTopRightRadius") ? parseSize(props.opt("borderTopRightRadius"), radius) : radius;
            float bottomRight = props.has("borderBottomRightRadius") ? parseSize(props.opt("borderBottomRightRadius"), radius) : radius;
            float bottomLeft = props.has("borderBottomLeftRadius") ? parseSize(props.opt("borderBottomLeftRadius"), radius) : radius;
            drawable.setCornerRadii(new float[]{topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft});
        } else if (radius > 0) {
            drawable.setCornerRadius(radius);
        }

        if (borderWidth > 0) {
            drawable.setStroke(borderWidth, borderColor != null ? borderColor : Color.TRANSPARENT);
        }

        view.setBackground(drawable);
    }

    private void applyPadding(View view, JSONObject props) throws Exception {
        boolean hasAnyPadding = props.has("padding") || props.has("paddingHorizontal") || props.has("paddingVertical") || props.has("paddingLeft") || props.has("paddingTop") || props.has("paddingRight") || props.has("paddingBottom") || props.has("paddingStart") || props.has("paddingEnd");

        if (!hasAnyPadding) return;

        int all = parseSize(props.opt("padding"), -1);
        int horizontal = parseSize(props.opt("paddingHorizontal"), -1);
        int vertical = parseSize(props.opt("paddingVertical"), -1);

        int start = props.has("paddingStart") ? parseSize(props.opt("paddingStart"), view.getPaddingStart()) : (horizontal >= 0 ? horizontal : (all >= 0 ? all : view.getPaddingStart()));
        int top = props.has("paddingTop") ? parseSize(props.opt("paddingTop"), view.getPaddingTop()) : (vertical >= 0 ? vertical : (all >= 0 ? all : view.getPaddingTop()));
        int end = props.has("paddingEnd") ? parseSize(props.opt("paddingEnd"), view.getPaddingEnd()) : (horizontal >= 0 ? horizontal : (all >= 0 ? all : view.getPaddingEnd()));
        int bottom = props.has("paddingBottom") ? parseSize(props.opt("paddingBottom"), view.getPaddingBottom()) : (vertical >= 0 ? vertical : (all >= 0 ? all : view.getPaddingBottom()));

        int left = props.has("paddingLeft") ? parseSize(props.opt("paddingLeft"), view.getPaddingLeft()) : start;
        int right = props.has("paddingRight") ? parseSize(props.opt("paddingRight"), view.getPaddingRight()) : end;

        view.setPaddingRelative(start, top, end, bottom);

        if (props.has("paddingLeft") || props.has("paddingRight")) {
            view.setPadding(left, top, right, bottom);
        }
    }

    private void applyViewGroupProps(ViewGroup view, JSONObject props) throws Exception {
        if (props.has("clipChildren")) view.setClipChildren(parseBoolean(props.opt("clipChildren"), view.getClipChildren()));
        if (props.has("clipToPadding")) view.setClipToPadding(parseBoolean(props.opt("clipToPadding"), view.getClipToPadding()));
    }

    private void applyLinearLayoutProps(LinearLayout layout, JSONObject props) throws Exception {
        if (props.has("orientation")) {
            layout.setOrientation("horizontal".equalsIgnoreCase(props.optString("orientation")) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        }
        if (props.has("gravity")) layout.setGravity(parseGravity(props.optString("gravity", "")));
        if (props.has("weightSum"))
            layout.setWeightSum((float) parseDouble(props.opt("weightSum"), layout.getWeightSum()));
        if (props.has("showDividers")) layout.setShowDividers(parseShowDividers(props.opt("showDividers")));
        if (props.has("dividerPadding"))
            layout.setDividerPadding(parseSize(props.opt("dividerPadding"), layout.getDividerPadding()));
        if (props.has("baselineAligned"))
            layout.setBaselineAligned(parseBoolean(props.opt("baselineAligned"), layout.isBaselineAligned()));
    }

    private void applyRadioGroupProps(RadioGroup view, JSONObject props) throws Exception {
        if (props.has("checkedId")) {
            Object raw = props.opt("checkedId");
            if (raw == null || raw == JSONObject.NULL) {
                view.clearCheck();
            } else if (raw instanceof Number) {
                Object child = nodes.get(((Number) raw).intValue());
                if (child instanceof View childView) view.check(childView.getId());
            }
        }
    }

    private void applyTextViewProps(TextView view, JSONObject props) throws Exception {
        if (props.has("text")) view.setText(String.valueOf(props.opt("text")));
        if (props.has("textColor")) {
            Integer color = parseColor(props.opt("textColor"));
            if (color != null) view.setTextColor(color);
        }
        if (props.has("hint")) view.setHint(String.valueOf(props.opt("hint")));
        if (props.has("hintColor")) {
            Integer color = parseColor(props.opt("hintColor"));
            if (color != null) view.setHintTextColor(color);
        }
        if (props.has("textSizeSp"))
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float) parseDouble(props.opt("textSizeSp"), 14));
        if (props.has("gravity")) view.setGravity(parseGravity(props.optString("gravity", "")));
        if (props.has("maxLines")) view.setMaxLines(props.optInt("maxLines", Integer.MAX_VALUE));
        if (props.has("minLines")) view.setMinLines(props.optInt("minLines", 1));
        if (props.has("lines")) view.setLines(props.optInt("lines", 1));
        if (props.has("singleLine")) view.setSingleLine(parseBoolean(props.opt("singleLine"), false));
        if (props.has("allCaps")) view.setAllCaps(parseBoolean(props.opt("allCaps"), false));
        if (props.has("includeFontPadding"))
            view.setIncludeFontPadding(parseBoolean(props.opt("includeFontPadding"), true));
        if (props.has("letterSpacing"))
            view.setLetterSpacing((float) parseDouble(props.opt("letterSpacing"), view.getLetterSpacing()));
        if (props.has("lineSpacingExtra") || props.has("lineSpacingMultiplier")) {
            float extra = (float) parseDouble(props.opt("lineSpacingExtra"), 0);
            float multiplier = (float) parseDouble(props.opt("lineSpacingMultiplier"), 1);
            view.setLineSpacing(extra, multiplier);
        }
        if (props.has("textStyle"))
            view.setTypeface(view.getTypeface(), parseTextStyle(props.optString("textStyle", "")));
        if (props.has("ellipsize")) view.setEllipsize(parseEllipsize(props.optString("ellipsize", "")));
        if (props.has("textIsSelectable")) view.setTextIsSelectable(parseBoolean(props.opt("textIsSelectable"), false));
        if (props.has("maxLength"))
            view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(props.optInt("maxLength", Integer.MAX_VALUE))});
    }

    private void applyImageViewProps(ImageView view, JSONObject props) throws Exception {
        if (!props.has("clipToOutline")) {
            view.setClipToOutline(hasRoundedCorners(props));
        }
        if (props.has("scaleType")) {
            view.setScaleType(parseScaleType(props.optString("scaleType", "")));
        }

        if (props.has("adjustViewBounds")) {
            view.setAdjustViewBounds(parseBoolean(props.opt("adjustViewBounds"), view.getAdjustViewBounds()));
        }

        if (props.has("cropToPadding")) {
            view.setCropToPadding(parseBoolean(props.opt("cropToPadding"), view.getCropToPadding()));
        }

        if (props.has("tintColor")) {
            Integer color = parseColor(props.opt("tintColor"));
            if (color != null) view.setImageTintList(ColorStateList.valueOf(color));
        }

        String src = props.has("src") ? props.optString("src", null) : null;
        String oldSrc = appliedImageSources.get(view);

        if (Objects.equals(oldSrc, src)) return;

        if (view.getParent() == null) {
            Log.d(TAG, "Skipping image load because view is not attached yet");
            return;
        }

        appliedImageSources.put(view, src);
        loadImage(view, src);
    }

    private boolean hasRoundedCorners(JSONObject props) {
        return parseSize(props.opt("borderRadius"), 0) > 0
            || parseSize(props.opt("borderTopLeftRadius"), 0) > 0
            || parseSize(props.opt("borderTopRightRadius"), 0) > 0
            || parseSize(props.opt("borderBottomLeftRadius"), 0) > 0
            || parseSize(props.opt("borderBottomRightRadius"), 0) > 0;
    }

    private void maybeLoadImageForView(View view, JSONObject props) {
        if (!(view instanceof ImageView imageView)) return;

        Runnable task = () -> {
            String src = props.has("src") ? props.optString("src", null) : null;
            String oldSrc = appliedImageSources.get(imageView);

            if (!Objects.equals(oldSrc, src)) {
                appliedImageSources.put(imageView, src);
                loadImage(imageView, src);
            }
        };

        if (view.isAttachedToWindow()) {
            task.run();
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    task.run();
                }

                @Override
                public void onViewDetachedFromWindow(View v) { }
            });
        }
    }

    private void loadImage(ImageView view, String src) {
        final int requestId = nextImageRequestId++;
        imageRequestIds.put(view, requestId);

        if (src == null || src.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }

        if (src.startsWith("http://") || src.startsWith("https://")) {
            new Thread(() -> {
                try (InputStream stream = new URL(src).openConnection().getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);

                    mainHandler.post(() -> {
                        Integer latestId = imageRequestIds.get(view);
                        if (latestId == null || latestId != requestId) return;

                        Log.d(TAG, "Applying bitmap"
                                + " view=" + System.identityHashCode(view)
                                + " attached=" + view.isAttachedToWindow()
                                + " parent=" + view.getParent()
                                + " width=" + view.getWidth()
                                + " height=" + view.getHeight()
                                + " measuredWidth=" + view.getMeasuredWidth()
                                + " measuredHeight=" + view.getMeasuredHeight()
                                + " visibility=" + view.getVisibility()
                                + " alpha=" + view.getAlpha()
                                + " drawableBefore=" + (view.getDrawable() != null)
                                + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());

                        view.setImageBitmap(bitmap);

                        Log.d(TAG, "Applied bitmap"
                                + " drawableAfter=" + (view.getDrawable() != null)
                                + " attached=" + view.isAttachedToWindow()
                                + " width=" + view.getWidth()
                                + " height=" + view.getHeight()
                                + " measuredWidth=" + view.getMeasuredWidth()
                                + " measuredHeight=" + view.getMeasuredHeight());

                        view.requestLayout();
                        view.invalidate();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed loading image: " + src, e);

                    mainHandler.post(() -> {
                        Integer latestId = imageRequestIds.get(view);
                        if (latestId == null || latestId != requestId) return;

                        view.setImageDrawable(null);
                    });
                }
            }).start();

            return;
        }

        try {
            if (src.startsWith("content://") || src.startsWith("file://") || src.startsWith("android.resource://")) {
                view.setImageURI(Uri.parse(src));
                view.requestLayout();
                view.invalidate();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(src);
            view.setImageBitmap(bitmap);
            view.requestLayout();
            view.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "Failed loading local image: " + src, e);
            view.setImageDrawable(null);
        }
    }

    private ImageView.ScaleType parseScaleType(String value) {
        switch (value.toLowerCase()) {
            case "centercrop":
            case "center_crop":
                return ImageView.ScaleType.CENTER_CROP;
            case "fitcenter":
            case "fit_center":
                return ImageView.ScaleType.FIT_CENTER;
            case "fitxy":
            case "fit_xy":
                return ImageView.ScaleType.FIT_XY;
            case "center":
                return ImageView.ScaleType.CENTER;
            case "centerinside":
            case "center_inside":
                return ImageView.ScaleType.CENTER_INSIDE;
            case "fitstart":
            case "fit_start":
                return ImageView.ScaleType.FIT_START;
            case "fitend":
            case "fit_end":
                return ImageView.ScaleType.FIT_END;
            case "matrix":
                return ImageView.ScaleType.MATRIX;
            default:
                return ImageView.ScaleType.FIT_CENTER;
        }
    }

    private void applyEditTextProps(EditText view, JSONObject props) throws Exception {
        if (props.has("inputType")) view.setInputType(parseInputType(props.opt("inputType")));
        if (props.has("imeOptions")) view.setImeOptions(parseImeOptions(props.opt("imeOptions")));
        if (props.has("selectAllOnFocus")) view.setSelectAllOnFocus(parseBoolean(props.opt("selectAllOnFocus"), false));
        if (props.has("cursorVisible")) view.setCursorVisible(parseBoolean(props.opt("cursorVisible"), true));
    }

    private void applyProgressBarProps(ProgressBar view, JSONObject props) throws Exception {
        if (props.has("indeterminate"))
            view.setIndeterminate(parseBoolean(props.opt("indeterminate"), view.isIndeterminate()));
        if (props.has("min")) view.setMin(props.optInt("min", view.getMin()));
        if (props.has("max")) view.setMax(props.optInt("max", view.getMax()));
        if (props.has("progress")) view.setProgress(props.optInt("progress", view.getProgress()));
        if (props.has("secondaryProgress"))
            view.setSecondaryProgress(props.optInt("secondaryProgress", view.getSecondaryProgress()));
        if (props.has("progressTintColor")) {
            Integer color = parseColor(props.opt("progressTintColor"));
            if (color != null) view.setProgressTintList(ColorStateList.valueOf(color));
        }
        if (props.has("secondaryProgressTintColor")) {
            Integer color = parseColor(props.opt("secondaryProgressTintColor"));
            if (color != null) view.setSecondaryProgressTintList(ColorStateList.valueOf(color));
        }
        if (props.has("progressBackgroundTintColor")) {
            Integer color = parseColor(props.opt("progressBackgroundTintColor"));
            if (color != null) view.setProgressBackgroundTintList(ColorStateList.valueOf(color));
        }
        if (props.has("indeterminateTintColor")) {
            Integer color = parseColor(props.opt("indeterminateTintColor"));
            if (color != null) view.setIndeterminateTintList(ColorStateList.valueOf(color));
        }
    }

    private void applySeekBarProps(SeekBar view, JSONObject props) throws Exception {
        if (props.has("min")) view.setMin(props.optInt("min", view.getMin()));
        if (props.has("thumbTintColor")) {
            Integer color = parseColor(props.opt("thumbTintColor"));
            if (color != null) view.setThumbTintList(ColorStateList.valueOf(color));
        }
        if (props.has("tickMarkTintColor")) {
            Integer color = parseColor(props.opt("tickMarkTintColor"));
            if (color != null) view.setTickMarkTintList(ColorStateList.valueOf(color));
        }
        if (props.has("splitTrack")) view.setSplitTrack(parseBoolean(props.opt("splitTrack"), view.getSplitTrack()));
    }

    private void applyCompoundButtonProps(CompoundButton view, JSONObject props) throws Exception {
        if (props.has("checked")) view.setChecked(parseBoolean(props.opt("checked"), view.isChecked()));
        if (props.has("buttonTintColor")) {
            Integer color = parseColor(props.opt("buttonTintColor"));
            if (color != null) view.setButtonTintList(ColorStateList.valueOf(color));
        }
    }

    private void applySwitchProps(Switch view, JSONObject props) throws Exception {
        if (props.has("textOn")) view.setTextOn(String.valueOf(props.opt("textOn")));
        if (props.has("textOff")) view.setTextOff(String.valueOf(props.opt("textOff")));
        if (props.has("showText")) view.setShowText(parseBoolean(props.opt("showText"), view.getShowText()));
        if (props.has("thumbTintColor")) {
            Integer color = parseColor(props.opt("thumbTintColor"));
            if (color != null) view.setThumbTintList(ColorStateList.valueOf(color));
        }
        if (props.has("trackTintColor")) {
            Integer color = parseColor(props.opt("trackTintColor"));
            if (color != null) view.setTrackTintList(ColorStateList.valueOf(color));
        }
    }

    private void applyToggleButtonProps(ToggleButton view, JSONObject props) throws Exception {
        if (props.has("textOn")) view.setTextOn(String.valueOf(props.opt("textOn")));
        if (props.has("textOff")) view.setTextOff(String.valueOf(props.opt("textOff")));
        if (props.has("disabledAlpha")) view.setAlpha((float) parseDouble(props.opt("disabledAlpha"), view.getDisabledAlpha()));
    }

    private void applyScrollViewProps(ScrollView view, JSONObject props) throws Exception {
        if (props.has("fillViewport"))
            view.setFillViewport(parseBoolean(props.opt("fillViewport"), view.isFillViewport()));
        if (props.has("smoothScrollingEnabled"))
            view.setSmoothScrollingEnabled(parseBoolean(props.opt("smoothScrollingEnabled"), true));
        if (props.has("verticalScrollBarEnabled"))
            view.setVerticalScrollBarEnabled(parseBoolean(props.opt("verticalScrollBarEnabled"), view.isVerticalScrollBarEnabled()));
        if (props.has("horizontalScrollBarEnabled"))
            view.setHorizontalScrollBarEnabled(parseBoolean(props.opt("horizontalScrollBarEnabled"), view.isHorizontalScrollBarEnabled()));
        if (props.has("overScrollMode"))
            view.setOverScrollMode(parseOverScrollMode(props.opt("overScrollMode")));
    }

    private void applyHorizontalScrollViewProps(HorizontalScrollView view, JSONObject props) throws Exception {
        if (props.has("fillViewport"))
            view.setFillViewport(parseBoolean(props.opt("fillViewport"), view.isFillViewport()));
        if (props.has("smoothScrollingEnabled"))
            view.setSmoothScrollingEnabled(parseBoolean(props.opt("smoothScrollingEnabled"), true));
        if (props.has("verticalScrollBarEnabled"))
            view.setVerticalScrollBarEnabled(parseBoolean(props.opt("verticalScrollBarEnabled"), view.isVerticalScrollBarEnabled()));
        if (props.has("horizontalScrollBarEnabled"))
            view.setHorizontalScrollBarEnabled(parseBoolean(props.opt("horizontalScrollBarEnabled"), view.isHorizontalScrollBarEnabled()));
        if (props.has("overScrollMode"))
            view.setOverScrollMode(parseOverScrollMode(props.opt("overScrollMode")));
    }

    private ViewGroup.LayoutParams buildLayoutParams(ViewGroup parent, View child, JSONObject props) throws Exception {
        int width = parseLayoutSize(props.opt("width"), defaultWidth(child));
        int height = parseLayoutSize(props.opt("height"), defaultHeight(child));

        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height, (float) parseDouble(props.opt("layoutWeight"), 0));
            applyMargins(lp, props);
            if (props.has("layoutGravity")) lp.gravity = parseGravity(props.optString("layoutGravity", ""));
            return lp;
        }

        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            applyMargins(lp, props);
            if (props.has("layoutGravity")) lp.gravity = parseGravity(props.optString("layoutGravity", ""));
            return lp;
        }

        if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, height);
            applyMargins(lp, props);
            applyRelativeLayoutRules(lp, props);
            return lp;
        }

        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(width, height);
        applyMargins(lp, props);
        return lp;
    }

    private void applyMargins(ViewGroup.MarginLayoutParams lp, JSONObject props) throws Exception {
        boolean hasAnyMargin = props.has("margin") || props.has("marginHorizontal") || props.has("marginVertical") || props.has("marginLeft") || props.has("marginTop") || props.has("marginRight") || props.has("marginBottom") || props.has("marginStart") || props.has("marginEnd");

        if (!hasAnyMargin) return;

        int all = parseSize(props.opt("margin"), 0);
        int horizontal = parseSize(props.opt("marginHorizontal"), -1);
        int vertical = parseSize(props.opt("marginVertical"), -1);

        int start = props.has("marginStart") ? parseSize(props.opt("marginStart"), lp.getMarginStart()) : (horizontal >= 0 ? horizontal : all);
        int top = props.has("marginTop") ? parseSize(props.opt("marginTop"), lp.topMargin) : (vertical >= 0 ? vertical : all);
        int end = props.has("marginEnd") ? parseSize(props.opt("marginEnd"), lp.getMarginEnd()) : (horizontal >= 0 ? horizontal : all);
        int bottom = props.has("marginBottom") ? parseSize(props.opt("marginBottom"), lp.bottomMargin) : (vertical >= 0 ? vertical : all);

        int left = props.has("marginLeft") ? parseSize(props.opt("marginLeft"), lp.leftMargin) : start;
        int right = props.has("marginRight") ? parseSize(props.opt("marginRight"), lp.rightMargin) : end;

        lp.setMargins(left, top, right, bottom);
        lp.setMarginStart(start);
        lp.setMarginEnd(end);
    }

    private void applyRelativeLayoutRules(RelativeLayout.LayoutParams lp, JSONObject props) throws Exception {
        addRuleIfTrue(lp, props, "alignParentTop", RelativeLayout.ALIGN_PARENT_TOP);
        addRuleIfTrue(lp, props, "alignParentBottom", RelativeLayout.ALIGN_PARENT_BOTTOM);
        addRuleIfTrue(lp, props, "alignParentStart", RelativeLayout.ALIGN_PARENT_START);
        addRuleIfTrue(lp, props, "alignParentEnd", RelativeLayout.ALIGN_PARENT_END);
        addRuleIfTrue(lp, props, "centerInParent", RelativeLayout.CENTER_IN_PARENT);
        addRuleIfTrue(lp, props, "centerHorizontal", RelativeLayout.CENTER_HORIZONTAL);
        addRuleIfTrue(lp, props, "centerVertical", RelativeLayout.CENTER_VERTICAL);

        addAnchorRule(lp, props, "above", RelativeLayout.ABOVE);
        addAnchorRule(lp, props, "below", RelativeLayout.BELOW);
        addAnchorRule(lp, props, "toStartOf", RelativeLayout.START_OF);
        addAnchorRule(lp, props, "toEndOf", RelativeLayout.END_OF);
        addAnchorRule(lp, props, "alignStart", RelativeLayout.ALIGN_START);
        addAnchorRule(lp, props, "alignEnd", RelativeLayout.ALIGN_END);
        addAnchorRule(lp, props, "alignTop", RelativeLayout.ALIGN_TOP);
        addAnchorRule(lp, props, "alignBottom", RelativeLayout.ALIGN_BOTTOM);
    }

    private void addRuleIfTrue(RelativeLayout.LayoutParams lp, JSONObject props, String key, int verb) {
        if (props.has(key) && parseBoolean(props.opt(key), false)) lp.addRule(verb);
    }

    private void addAnchorRule(RelativeLayout.LayoutParams lp, JSONObject props, String key, int verb) {
        if (!props.has(key)) return;

        Object value = props.opt(key);
        if (!(value instanceof Number)) return;

        int anchorNodeId = ((Number) value).intValue();
        Object anchorNode = nodes.get(anchorNodeId);
        if (anchorNode instanceof View) lp.addRule(verb, ((View) anchorNode).getId());
    }

    private JSONObject getProps(int id) {
        JSONObject props = nodeProps.get(id);
        return props != null ? props : new JSONObject();
    }

    private JSONObject copyJson(JSONObject json) throws Exception {
        return json != null ? new JSONObject(json.toString()) : new JSONObject();
    }

    private JSONObject mergeJson(JSONObject base, JSONObject update) throws Exception {
        JSONObject merged = copyJson(base);
        if (update == null) return merged;

        Iterator<String> keys = update.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = update.get(key);
            if (value == JSONObject.NULL) merged.remove(key);
            else merged.put(key, value);
        }

        return merged;
    }

    private int defaultWidth(View view) {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private int defaultHeight(View view) {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private boolean isContainer(View view) {
        return view instanceof LinearLayout || view instanceof FrameLayout || view instanceof RelativeLayout || view instanceof ScrollView || view instanceof HorizontalScrollView;
    }

    private int parseLayoutSize(Object value, int fallback) throws Exception {
        if (value == null || value == JSONObject.NULL) return fallback;
        if (value instanceof Number) return dp(((Number) value).floatValue());

        String text = String.valueOf(value).trim().toLowerCase();
        if ("match_parent".equals(text) || "match".equals(text) || "fill_parent".equals(text) || "fill".equals(text))
            return ViewGroup.LayoutParams.MATCH_PARENT;
        if ("wrap_content".equals(text) || "wrap".equals(text)) return ViewGroup.LayoutParams.WRAP_CONTENT;
        if (text.endsWith("dp")) return dp(Float.parseFloat(text.substring(0, text.length() - 2)));
        if (text.endsWith("sp")) return sp(Float.parseFloat(text.substring(0, text.length() - 2)));
        if (text.endsWith("px")) return Math.round(Float.parseFloat(text.substring(0, text.length() - 2)));
        return fallback;
    }

    private int parseSize(Object value, int fallback) {
        try {
            if (value == null || value == JSONObject.NULL) return fallback;
            if (value instanceof Number) return dp(((Number) value).floatValue());

            String text = String.valueOf(value).trim().toLowerCase();
            if (text.endsWith("dp")) return dp(Float.parseFloat(text.substring(0, text.length() - 2)));
            if (text.endsWith("sp")) return sp(Float.parseFloat(text.substring(0, text.length() - 2)));
            if (text.endsWith("px")) return Math.round(Float.parseFloat(text.substring(0, text.length() - 2)));
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    private int sp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.getResources().getDisplayMetrics()));
    }

    private double parseDouble(Object value, double fallback) {
        try {
            if (value == null || value == JSONObject.NULL) return fallback;
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        if (value == null || value == JSONObject.NULL) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Integer parseColor(Object value) {
        try {
            if (value == null || value == JSONObject.NULL) return null;
            if (value instanceof Number) return ((Number) value).intValue();
            return Color.parseColor(String.valueOf(value));
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse color " + value);
            return null;
        }
    }

    private int parseVisibility(Object value) {
        String text = String.valueOf(value).toLowerCase();
        if ("gone".equals(text)) return View.GONE;
        if ("invisible".equals(text)) return View.INVISIBLE;
        return View.VISIBLE;
    }

    private int parseTextAlignment(String value) {
        switch (value.toLowerCase()) {
            case "center":
                return View.TEXT_ALIGNMENT_CENTER;
            case "textstart":
            case "start":
                return View.TEXT_ALIGNMENT_TEXT_START;
            case "textend":
            case "end":
                return View.TEXT_ALIGNMENT_TEXT_END;
            case "viewstart":
                return View.TEXT_ALIGNMENT_VIEW_START;
            case "viewend":
                return View.TEXT_ALIGNMENT_VIEW_END;
            default:
                return View.TEXT_ALIGNMENT_INHERIT;
        }
    }

    private int parseOverScrollMode(Object value) {
        String text = String.valueOf(value).toLowerCase();
        if ("always".equals(text)) return View.OVER_SCROLL_ALWAYS;
        if ("never".equals(text)) return View.OVER_SCROLL_NEVER;
        return View.OVER_SCROLL_IF_CONTENT_SCROLLS;
    }

    private int parseGravity(String value) {
        if (value == null || value.isEmpty()) return Gravity.NO_GRAVITY;

        int gravity = 0;
        String[] parts = value.toLowerCase().split("\\|");

        for (String rawPart : parts) {
            String part = rawPart.trim();
            switch (part) {
                case "center":
                    gravity |= Gravity.CENTER;
                    break;
                case "center_horizontal":
                case "centerhorizontal":
                    gravity |= Gravity.CENTER_HORIZONTAL;
                    break;
                case "center_vertical":
                case "centervertical":
                    gravity |= Gravity.CENTER_VERTICAL;
                    break;
                case "start":
                    gravity |= Gravity.START;
                    break;
                case "end":
                    gravity |= Gravity.END;
                    break;
                case "left":
                    gravity |= Gravity.LEFT;
                    break;
                case "right":
                    gravity |= Gravity.RIGHT;
                    break;
                case "top":
                    gravity |= Gravity.TOP;
                    break;
                case "bottom":
                    gravity |= Gravity.BOTTOM;
                    break;
            }
        }

        return gravity;
    }

    private int parseShowDividers(Object value) {
        if (value == null || value == JSONObject.NULL) return LinearLayout.SHOW_DIVIDER_NONE;
        String[] parts = String.valueOf(value).toLowerCase().split("\\|");
        int result = LinearLayout.SHOW_DIVIDER_NONE;

        for (String rawPart : parts) {
            String part = rawPart.trim();
            switch (part) {
                case "beginning":
                    result |= LinearLayout.SHOW_DIVIDER_BEGINNING;
                    break;
                case "middle":
                    result |= LinearLayout.SHOW_DIVIDER_MIDDLE;
                    break;
                case "end":
                    result |= LinearLayout.SHOW_DIVIDER_END;
                    break;
            }
        }

        return result;
    }

    private int parseTextStyle(String value) {
        if (value == null || value.isEmpty()) return android.graphics.Typeface.NORMAL;

        int style = android.graphics.Typeface.NORMAL;
        String[] parts = value.toLowerCase().split("\\|");

        for (String rawPart : parts) {
            String part = rawPart.trim();
            if ("bold".equals(part)) style |= android.graphics.Typeface.BOLD;
            if ("italic".equals(part)) style |= android.graphics.Typeface.ITALIC;
        }

        return style;
    }

    private TextUtils.TruncateAt parseEllipsize(String value) {
        switch (value.toLowerCase()) {
            case "start":
                return TextUtils.TruncateAt.START;
            case "middle":
                return TextUtils.TruncateAt.MIDDLE;
            case "marquee":
                return TextUtils.TruncateAt.MARQUEE;
            case "end":
            default:
                return TextUtils.TruncateAt.END;
        }
    }

    private int parseInputType(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null || value == JSONObject.NULL) return InputType.TYPE_CLASS_TEXT;

        int result = 0;
        String[] parts = String.valueOf(value).toLowerCase().split("\\|");

        for (String rawPart : parts) {
            String part = rawPart.trim();
            switch (part) {
                case "text":
                    result |= InputType.TYPE_CLASS_TEXT;
                    break;
                case "textmultiline":
                case "multiline":
                    result |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                    break;
                case "textpassword":
                case "password":
                    result |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
                    break;
                case "email":
                    result |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                    break;
                case "number":
                    result |= InputType.TYPE_CLASS_NUMBER;
                    break;
                case "decimal":
                    result |= InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
                    break;
                case "phone":
                    result |= InputType.TYPE_CLASS_PHONE;
                    break;
            }
        }

        return result != 0 ? result : InputType.TYPE_CLASS_TEXT;
    }

    private int parseImeOptions(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null || value == JSONObject.NULL) return 0;

        int result = 0;
        String[] parts = String.valueOf(value).toLowerCase().split("\\|");

        for (String rawPart : parts) {
            String part = rawPart.trim();
            switch (part) {
                case "actiondone":
                case "done":
                    result |= EditorInfo.IME_ACTION_DONE;
                    break;
                case "actiongo":
                case "go":
                    result |= EditorInfo.IME_ACTION_GO;
                    break;
                case "actionnext":
                case "next":
                    result |= EditorInfo.IME_ACTION_NEXT;
                    break;
                case "actionsearch":
                case "search":
                    result |= EditorInfo.IME_ACTION_SEARCH;
                    break;
                case "actionsend":
                case "send":
                    result |= EditorInfo.IME_ACTION_SEND;
                    break;
            }
        }

        return result;
    }

    private void detachFromParent(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private void sendEventToNode(int targetId, String eventName, int eventId, JSONObject payload) {
        try {
            JSONObject json = new JSONObject();
            json.put("surfaceId", surfaceId);
            json.put("targetId", targetId);
            json.put("eventName", eventName);
            json.put("eventId", eventId);
            json.put("payload", payload != null ? payload : new JSONObject());

            ScriptManager.send("", "event", "react.event", json);
        } catch (Exception e) {
            Log.e(TAG, "Failed sending react evnet to node", e);
        }
    }

    private Integer getEventId(JSONObject props, String key) {
        try {
            if (!props.has(key)) return null;

            Object raw = props.opt(key);
            if (!(raw instanceof JSONObject obj)) return null;
            if (!obj.optString("__type").equals("event_handler")) return null;

            Log.d(TAG, "Get EVENT ID: " + raw);
            return obj.optInt("id");
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer findNodeIdForView(View view) {
        for (Map.Entry<Integer, Object> entry : nodes.entrySet()) {
            if (entry.getValue() == view) return entry.getKey();
        }

        return null;
    }

    private Integer findNodeIdByAndroidViewId(int viewId) {
        if (viewId == View.NO_ID || viewId == -1) return null;

        for (Map.Entry<Integer, Object> entry : nodes.entrySet()) {
            Object node = entry.getValue();
            if (node instanceof View view && view.getId() == viewId) return entry.getKey();
        }

        return null;
    }

    private void withSuppressedEvents(int nodeId, ThrowingRunnable runnable) throws Exception {
        suppressedEventNodes.add(nodeId);
        try {
            runnable.run();
        } finally {
            suppressedEventNodes.remove(nodeId);
        }
    }

    private boolean isDispatchSuppressed(int nodeId) {
        return suppressedEventNodes.contains(nodeId);
    }

    private JSONObject basePayload(int targetId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("targetId", targetId);
        return payload;
    }

    private void cleanupNodeListeners(int nodeId, Object node) {
        suppressedEventNodes.remove(nodeId);

        if (node instanceof View view) {
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            view.setOnFocusChangeListener(null);
            view.setOnScrollChangeListener(null);
        }

        if (node instanceof CompoundButton compoundButton) {
            compoundButton.setOnCheckedChangeListener(null);
        }

        if (node instanceof RadioGroup radioGroup) {
            radioGroup.setOnCheckedChangeListener(null);
        }

        if (node instanceof SeekBar seekBar) {
            seekBar.setOnSeekBarChangeListener(null);
        }

        if (node instanceof ImageView imageView) {
            appliedImageSources.remove(imageView);
            imageRequestIds.remove(imageView);
            imageView.setImageDrawable(null);
        }

        if (node instanceof EditText editText) {
            TextWatcher watcher = textWatchers.remove(nodeId);
            if (watcher != null) editText.removeTextChangedListener(watcher);
            editText.setOnEditorActionListener(null);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

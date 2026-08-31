package com.lenerd.spotifyplus.module.scripting;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptRenderView extends View {
    private final ScriptViewHost host;
    private final ScriptViewHost.RenderNode ownerNode;
    private JSONObject props = new JSONObject();
    private JSONArray displayList = new JSONArray();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.DITHER_FLAG);
    private int onFrameEventId = -1, onSizeChangeEventId = -1, onTouchStartEventId = -1, onTouchMoveEventId = -1, onTouchEndEventId = -1, onTouchCancelEventId = -1, onImageLoadEventId = -1, onImageErrorEventId = -1, onAttachedEventId = -1, onDetachedEventId = -1;
    private boolean frameLoopRunning = false;
    private boolean disposed = false;
    private long lastFrameTimeMs = -1;
    private static final Map<String, Bitmap> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Bitmap> BLUR_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> IMAGE_LOADING = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Choreographer.FrameCallback frameCallback = null;

    ScriptRenderView(Context context, ScriptViewHost host, ScriptViewHost.RenderNode ownerNode) {
        super(context);
        this.host = host;
        this.ownerNode = ownerNode;
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);

        frameCallback = frameTimeNanos -> {
            if (disposed || !isAttachedToWindow() || (onFrameEventId < 0 && !props.optBoolean("autoInvalidate", false))) {
                frameLoopRunning = false;
                return;
            }
            long now = frameTimeNanos / 1000000L;
            long delta = lastFrameTimeMs < 0 ? 0 : Math.max(0, now - lastFrameTimeMs);
            lastFrameTimeMs = now;
            if (onFrameEventId >= 0) sendSimpleEvent("onFrame", onFrameEventId, payload -> {
                payload.put("time", now);
                payload.put("delta", delta);
                payload.put("width", getWidth());
                payload.put("height", getHeight());
            });
            if (props.optBoolean("autoInvalidate", false)) invalidate();
            Choreographer.getInstance().postFrameCallback(frameCallback);
        };
    }

    void applyProps(JSONObject nextProps) {
        try {
            props = nextProps != null ? new JSONObject(nextProps.toString()) : new JSONObject();
        } catch (Exception ignored) {
            props = nextProps != null ? nextProps : new JSONObject();
        }
        JSONArray list = props.optJSONArray("displayList");
        if (list == null) list = props.optJSONArray("nodes");
        try {
            displayList = list != null ? new JSONArray(list.toString()) : new JSONArray();
        } catch (Exception ignored) {
            displayList = list != null ? list : new JSONArray();
        }
        boolean software = props.optBoolean("softwareLayer", false) || displayListHasShadow(displayList);
        setLayerType(software ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= 31 && props.has("renderEffectBlurRadius")) {
            float radius = (float) host.parseDouble(props.opt("renderEffectBlurRadius"), 0);
            setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) : null);
        }
        invalidate();
        maybeStartFrameLoop();
        preloadImages(displayList);
    }

    void setEventIds(Integer onFrame, Integer onSizeChange, Integer onTouchStart, Integer onTouchMove, Integer onTouchEnd, Integer onTouchCancel, Integer onImageLoad, Integer onImageError, Integer onAttached, Integer onDetached) {
        this.onFrameEventId = onFrame != null ? onFrame : -1;
        this.onSizeChangeEventId = onSizeChange != null ? onSizeChange : -1;
        this.onTouchStartEventId = onTouchStart != null ? onTouchStart : -1;
        this.onTouchMoveEventId = onTouchMove != null ? onTouchMove : -1;
        this.onTouchEndEventId = onTouchEnd != null ? onTouchEnd : -1;
        this.onTouchCancelEventId = onTouchCancel != null ? onTouchCancel : -1;
        this.onImageLoadEventId = onImageLoad != null ? onImageLoad : -1;
        this.onImageErrorEventId = onImageError != null ? onImageError : -1;
        this.onAttachedEventId = onAttached != null ? onAttached : -1;
        this.onDetachedEventId = onDetached != null ? onDetached : -1;
        setClickable(hasTouchEvents());
        maybeStartFrameLoop();
    }

    void receiveCommand(String command, JSONObject args) {
        if (command == null) return;
        if ("invalidate".equals(command)) invalidate();
        else if ("setDisplayList".equals(command)) {
            JSONArray list = args != null ? args.optJSONArray("displayList") : null;
            if (list == null && args != null) list = args.optJSONArray("nodes");
            if (list != null) {
                try {
                    displayList = new JSONArray(list.toString());
                } catch (Exception ignored) {
                    displayList = list;
                }
                preloadImages(displayList);
                invalidate();
            }
        } else if ("preloadImage".equals(command)) {
            String src = args != null ? args.optString("src", null) : null;
            if (src != null && !src.isEmpty()) ensureImage(src);
        } else if ("updateNode".equals(command)) {
            if (args == null) return;
            Object rawId = args.opt("id");
            JSONObject patch = args.optJSONObject("props");
            if (rawId != null && patch != null && patchDisplayNode(displayList, String.valueOf(rawId), patch)) {
                preloadImages(displayList);
                invalidate();
            }
        }
    }

    void dispose() {
        disposed = true;
        if (frameLoopRunning) Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameLoopRunning = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        disposed = false;
        if (onAttachedEventId >= 0) sendSimpleEvent("onAttached", onAttachedEventId, null);
        maybeStartFrameLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (onDetachedEventId >= 0) sendSimpleEvent("onDetached", onDetachedEventId, null);
        if (frameLoopRunning) Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameLoopRunning = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (onSizeChangeEventId >= 0) sendSimpleEvent("onSizeChange", onSizeChangeEventId, payload -> {
            payload.put("width", w);
            payload.put("height", h);
            payload.put("oldWidth", oldw);
            payload.put("oldHeight", oldh);
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < displayList.length(); i++) {
            JSONObject node = displayList.optJSONObject(i);
            if (node != null) drawNode(canvas, node, getWidth(), getHeight(), 1f);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = dispatchTouch(event);
        return handled || super.onTouchEvent(event);
    }

    private void maybeStartFrameLoop() {
        if (disposed || frameLoopRunning || !isAttachedToWindow()) return;
        if (onFrameEventId < 0 && !props.optBoolean("autoInvalidate", false)) return;
        frameLoopRunning = true;
        lastFrameTimeMs = -1;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private boolean hasTouchEvents() {
        return onTouchStartEventId >= 0 || onTouchMoveEventId >= 0 || onTouchEndEventId >= 0 || onTouchCancelEventId >= 0;
    }

    private boolean dispatchTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int eventId = -1;
        String eventName = null;
        if (action == MotionEvent.ACTION_DOWN) {
            eventId = onTouchStartEventId;
            eventName = "onTouchStart";
        } else if (action == MotionEvent.ACTION_MOVE) {
            eventId = onTouchMoveEventId;
            eventName = "onTouchMove";
        } else if (action == MotionEvent.ACTION_UP) {
            eventId = onTouchEndEventId;
            eventName = "onTouchEnd";
        } else if (action == MotionEvent.ACTION_CANCEL) {
            eventId = onTouchCancelEventId;
            eventName = "onTouchCancel";
        }
        if (eventId < 0 || eventName == null) return false;
        int pointerIndex = Math.max(0, event.getActionIndex());
        String finalEventName = eventName;
        int finalEventId = eventId;
        sendSimpleEvent(finalEventName, finalEventId, payload -> {
            payload.put("x", event.getX(pointerIndex));
            payload.put("y", event.getY(pointerIndex));
            payload.put("pageX", event.getRawX());
            payload.put("pageY", event.getRawY());
            payload.put("action", finalEventName);
            payload.put("pointerId", event.getPointerId(pointerIndex));
        });
        return true;
    }

    private void drawNode(Canvas canvas, JSONObject node, float parentWidth, float parentHeight, float parentAlpha) {
        if (node == null || !node.optBoolean("visible", true)) return;
        String type = node.optString("type", "group");
        float nodeAlpha = parentAlpha * (float) host.parseDouble(node.has("alpha") ? node.opt("alpha") : node.opt("opacity"), 1);
        if (nodeAlpha <= 0f) return;
        float x = resolveLength(node.opt("x"), parentWidth, 0, false);
        float y = resolveLength(node.opt("y"), parentHeight, 0, false);
        float translateX = resolveLength(firstPresent(node, "translateX", "translationX"), parentWidth, 0, false);
        float translateY = resolveLength(firstPresent(node, "translateY", "translationY"), parentHeight, 0, false);
        float width = resolveLength(node.opt("width"), parentWidth, parentWidth, false);
        float height = resolveLength(node.opt("height"), parentHeight, parentHeight, false);
        if ("circle".equals(type)) {
            float radius = resolveLength(node.opt("radius"), Math.min(parentWidth, parentHeight), Math.min(width, height) / 2f, false);
            width = height = radius * 2f;
        }
        float pivotX = resolveLength(node.opt("pivotX"), width, width / 2f, false);
        float pivotY = resolveLength(node.opt("pivotY"), height, height / 2f, false);
        float rotation = normalizeAngle(node.opt("rotation"));
        float scale = (float) host.parseDouble(node.opt("scale"), 1);
        float scaleX = (float) host.parseDouble(node.opt("scaleX"), scale);
        float scaleY = (float) host.parseDouble(node.opt("scaleY"), scale);
        int save = canvas.save();
        canvas.translate(x + translateX, y + translateY);
        if (rotation != 0f) canvas.rotate(rotation, pivotX, pivotY);
        if (scaleX != 1f || scaleY != 1f) canvas.scale(scaleX, scaleY, pivotX, pivotY);
        if (node.optBoolean("clip", false)) clipNode(canvas, node, width, height);
        if ("group".equals(type) || "layer".equals(type))
            drawChildren(canvas, node.optJSONArray("children"), width, height, nodeAlpha);
        else if ("rect".equals(type) || "roundRect".equals(type))
            drawRectNode(canvas, node, width, height, nodeAlpha, "roundRect".equals(type));
        else if ("circle".equals(type)) drawCircleNode(canvas, node, width, height, nodeAlpha);
        else if ("oval".equals(type)) drawOvalNode(canvas, node, width, height, nodeAlpha);
        else if ("line".equals(type)) drawLineNode(canvas, node, width, height, nodeAlpha);
        else if ("path".equals(type)) drawPathNode(canvas, node, width, height, nodeAlpha);
        else if ("image".equals(type)) drawImageNode(canvas, node, width, height, nodeAlpha);
        else if ("text".equals(type)) drawTextNode(canvas, node, width, height, nodeAlpha);
        else if ("mask".equals(type)) drawMaskNode(canvas, node, width, height, nodeAlpha);
        canvas.restoreToCount(save);
    }

    private void drawChildren(Canvas canvas, JSONArray children, float width, float height, float alpha) {
        if (children == null) return;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child != null) drawNode(canvas, child, width, height, alpha);
        }
    }

    private void drawRectNode(Canvas canvas, JSONObject node, float width, float height, float alpha, boolean forceRound) {
        float radius = resolveLength(firstPresent(node, "radius", "borderRadius"), Math.min(width, height), forceRound ? 0 : 0, false);
        RectF rect = new RectF(0, 0, width, height);
        prepareFillPaint(node, width, height, alpha);
        if (paint.getColor() != Color.TRANSPARENT || paint.getShader() != null) {
            if (radius > 0) canvas.drawRoundRect(rect, radius, radius, paint);
            else canvas.drawRect(rect, paint);
        }
        prepareStrokePaint(node, width, height, alpha);
        if (paint.getStrokeWidth() > 0) {
            if (radius > 0) canvas.drawRoundRect(rect, radius, radius, paint);
            else canvas.drawRect(rect, paint);
        }
    }

    private void drawCircleNode(Canvas canvas, JSONObject node, float width, float height, float alpha) {
        float radius = resolveLength(node.opt("radius"), Math.min(width, height), Math.min(width, height) / 2f, false);
        float cx = resolveLength(node.opt("cx"), width, width / 2f, false);
        float cy = resolveLength(node.opt("cy"), height, height / 2f, false);
        prepareFillPaint(node, width, height, alpha);
        if (paint.getColor() != Color.TRANSPARENT || paint.getShader() != null)
            canvas.drawCircle(cx, cy, radius, paint);
        prepareStrokePaint(node, width, height, alpha);
        if (paint.getStrokeWidth() > 0) canvas.drawCircle(cx, cy, radius, paint);
    }

    private void drawOvalNode(Canvas canvas, JSONObject node, float width, float height, float alpha) {
        RectF rect = new RectF(0, 0, width, height);
        prepareFillPaint(node, width, height, alpha);
        if (paint.getColor() != Color.TRANSPARENT || paint.getShader() != null) canvas.drawOval(rect, paint);
        prepareStrokePaint(node, width, height, alpha);
        if (paint.getStrokeWidth() > 0) canvas.drawOval(rect, paint);
    }

    private void drawLineNode(Canvas canvas, JSONObject node, float width, float height, float alpha) {
        resetPaint(alpha);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(resolveLength(firstPresent(node, "strokeWidth", "lineWidth"), Math.min(width, height), host.dp(1), false));
        paint.setStrokeCap(Paint.Cap.ROUND);
        Integer color = parseNodeColor(firstPresent(node, "stroke", "strokeColor", "color"), Color.WHITE);
        paint.setColor(color != null ? withAlpha(color, alpha) : withAlpha(Color.WHITE, alpha));
        float x1 = resolveLength(node.opt("x1"), width, 0, false);
        float y1 = resolveLength(node.opt("y1"), height, 0, false);
        float x2 = resolveLength(node.opt("x2"), width, width, false);
        float y2 = resolveLength(node.opt("y2"), height, height, false);
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private void drawPathNode(Canvas canvas, JSONObject node, float width, float height, float alpha) {
        Path path = buildPath(node.optJSONArray("commands"), width, height);
        prepareFillPaint(node, width, height, alpha);
        if (paint.getColor() != Color.TRANSPARENT || paint.getShader() != null) canvas.drawPath(path, paint);
        prepareStrokePaint(node, width, height, alpha);
        if (paint.getStrokeWidth() > 0) canvas.drawPath(path, paint);
    }

    private void drawImageNode(Canvas canvas, JSONObject node, float width, float height, float alpha) {
        String src = node.optString("src", node.optString("uri", null));
        if (src == null || src.isEmpty()) return;
        Bitmap bitmap = getBitmapForDraw(src, (float) host.parseDouble(node.opt("blurRadius"), 0));
        if (bitmap == null) {
            ensureImage(src);
            return;
        }
        resetPaint(alpha);
        if (node.has("tintColor")) {
            Integer tint = parseNodeColor(node.opt("tintColor"), null);
            if (tint != null)
                paint.setColorFilter(new PorterDuffColorFilter(withAlpha(tint, alpha), PorterDuff.Mode.SRC_IN));
        }
        float radius = resolveLength(firstPresent(node, "borderRadius", "radius"), Math.min(width, height), 0, false);
        int save = canvas.save();
        if (radius > 0) {
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
        }
        RectF dst = imageDestination(bitmap, width, height, node.optString("resizeMode", node.optString("scaleType", "cover")));
        canvas.drawBitmap(bitmap, null, dst, paint);
        canvas.restoreToCount(save);
    }

    private void drawTextNode(Canvas canvas, JSONObject node, float width, float height, float alpha) {
        String text = String.valueOf(node.opt("text"));
        if (text == null || "null".equals(text)) text = "";
        textPaint.reset();
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);
        textPaint.setDither(true);
        textPaint.setColor(withAlpha(parseNodeColor(firstPresent(node, "fill", "color", "textColor"), Color.WHITE), alpha));
        textPaint.setTextSize(host.sp((float) host.parseDouble(firstPresent(node, "fontSize", "textSizeSp"), 14)));
        String weight = String.valueOf(node.opt("fontWeight"));
        String style = String.valueOf(node.opt("fontStyle"));
        int tfStyle = ("bold".equalsIgnoreCase(weight) || "600".equals(weight) || "700".equals(weight) || "800".equals(weight) || "900".equals(weight)) ? Typeface.BOLD : Typeface.NORMAL;
        if ("italic".equalsIgnoreCase(style)) tfStyle |= Typeface.ITALIC;
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, tfStyle));
        Shader shader = shaderFrom(firstPresent(node, "fill", "gradient"), width, height);
        if (shader != null) textPaint.setShader(shader);
        Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;
        String align = node.optString("textAlign", "left").toLowerCase();
        if ("center".equals(align)) alignment = Layout.Alignment.ALIGN_CENTER;
        else if ("right".equals(align) || "end".equals(align)) alignment = Layout.Alignment.ALIGN_OPPOSITE;
        int textWidth = Math.max(1, Math.round(width));
        int maxLines = node.has("maxLines") ? Math.max(1, node.optInt("maxLines", Integer.MAX_VALUE)) : Integer.MAX_VALUE;
        float lineSpacingExtra = 0;
        if (node.has("lineHeight"))
            lineSpacingExtra = Math.max(0, (float) host.parseDouble(node.opt("lineHeight"), 0) - (textPaint.getTextSize() / getResources().getDisplayMetrics().scaledDensity));
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, textWidth).setAlignment(alignment).setLineSpacing(host.sp(lineSpacingExtra), 1f).setIncludePad(node.optBoolean("includeFontPadding", true)).setMaxLines(maxLines).build();
        layout.draw(canvas);
    }

    private void drawMaskNode(android.graphics.Canvas canvas, JSONObject node, float width, float height, float alpha) {
        JSONArray content = node.optJSONArray("content");
        JSONArray mask = node.optJSONArray("mask");
        if (content == null || mask == null) return;

        android.graphics.RectF bounds = new android.graphics.RectF(0, 0, width, height);
        int layer = canvas.saveLayer(bounds, null);

        drawChildren(canvas, content, width, height, alpha);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        String mode = node.optString("maskMode", "dstIn").toLowerCase();
        android.graphics.PorterDuff.Mode porterDuffMode;
        switch (mode) {
            case "srcin":
            case "src_in":
                porterDuffMode = android.graphics.PorterDuff.Mode.SRC_IN;
                break;
            case "dstin":
            case "dst_in":
            default:
                porterDuffMode = android.graphics.PorterDuff.Mode.DST_IN;
                break;
        }

        paint.setXfermode(new android.graphics.PorterDuffXfermode(porterDuffMode));

        int maskLayer = canvas.saveLayer(bounds, paint);
        drawChildren(canvas, mask, width, height, alpha);
        canvas.restoreToCount(maskLayer);

        paint.setXfermode(null);
        canvas.restoreToCount(layer);
    }

    private Path buildPath(JSONArray commands, float width, float height) {
        Path path = new Path();
        if (commands == null) return path;
        for (int i = 0; i < commands.length(); i++) {
            JSONObject c = commands.optJSONObject(i);
            if (c == null) continue;
            String cmd = c.optString("cmd", "");
            if ("M".equalsIgnoreCase(cmd) || "moveTo".equalsIgnoreCase(cmd))
                path.moveTo(resolveLength(c.opt("x"), width, 0, false), resolveLength(c.opt("y"), height, 0, false));
            else if ("L".equalsIgnoreCase(cmd) || "lineTo".equalsIgnoreCase(cmd))
                path.lineTo(resolveLength(c.opt("x"), width, 0, false), resolveLength(c.opt("y"), height, 0, false));
            else if ("Q".equalsIgnoreCase(cmd) || "quadTo".equalsIgnoreCase(cmd))
                path.quadTo(resolveLength(c.opt("x1"), width, 0, false), resolveLength(c.opt("y1"), height, 0, false), resolveLength(c.opt("x"), width, 0, false), resolveLength(c.opt("y"), height, 0, false));
            else if ("C".equalsIgnoreCase(cmd) || "cubicTo".equalsIgnoreCase(cmd))
                path.cubicTo(resolveLength(c.opt("x1"), width, 0, false), resolveLength(c.opt("y1"), height, 0, false), resolveLength(c.opt("x2"), width, 0, false), resolveLength(c.opt("y2"), height, 0, false), resolveLength(c.opt("x"), width, 0, false), resolveLength(c.opt("y"), height, 0, false));
            else if ("Z".equalsIgnoreCase(cmd) || "close".equalsIgnoreCase(cmd)) path.close();
        }
        return path;
    }

    private void prepareFillPaint(JSONObject node, float width, float height, float alpha) {
        resetPaint(alpha);
        paint.setStyle(Paint.Style.FILL);
        applyShadow(node, alpha);
        Object fill = firstPresent(node, "fill", "color", "backgroundColor");
        Shader shader = shaderFrom(fill, width, height);
        if (shader != null) paint.setShader(shader);
        else paint.setColor(withAlpha(parseNodeColor(fill, Color.TRANSPARENT), alpha));
    }

    private void prepareStrokePaint(JSONObject node, float width, float height, float alpha) {
        resetPaint(alpha);
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = resolveLength(node.opt("strokeWidth"), Math.min(width, height), 0, false);
        paint.setStrokeWidth(strokeWidth);
        if (strokeWidth <= 0) return;
        Object stroke = firstPresent(node, "stroke", "strokeColor", "borderColor");
        Shader shader = shaderFrom(stroke, width, height);
        if (shader != null) paint.setShader(shader);
        else paint.setColor(withAlpha(parseNodeColor(stroke, Color.TRANSPARENT), alpha));
    }

    private void resetPaint(float alpha) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        paint.setAlpha(Math.max(0, Math.min(255, Math.round(alpha * 255f))));
    }

    private void applyShadow(JSONObject node, float alpha) {
        JSONObject shadow = node.optJSONObject("shadow");
        if (shadow == null) return;
        Integer color = parseNodeColor(shadow.opt("color"), Color.BLACK);
        float radius = resolveLength(shadow.opt("radius"), 0, 0, false);
        float dx = resolveLength(shadow.opt("dx"), 0, 0, false);
        float dy = resolveLength(shadow.opt("dy"), 0, 0, false);
        if (radius > 0) paint.setShadowLayer(radius, dx, dy, withAlpha(color != null ? color : Color.BLACK, alpha));
    }

    private void clipNode(Canvas canvas, JSONObject node, float width, float height) {
        float radius = resolveLength(firstPresent(node, "clipRadius", "borderRadius", "radius"), Math.min(width, height), 0, false);
        if (radius > 0) {
            Path path = new Path();
            path.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
            canvas.clipPath(path);
        } else canvas.clipRect(0, 0, width, height);
    }

    private Shader shaderFrom(Object raw, float width, float height) {
        if (!(raw instanceof JSONObject spec)) return null;
        JSONArray colorsJson = spec.optJSONArray("colors");
        if (colorsJson == null || colorsJson.length() == 0) return null;
        int[] colors = new int[colorsJson.length()];
        for (int i = 0; i < colors.length; i++) colors[i] = parseNodeColor(colorsJson.opt(i), Color.TRANSPARENT);
        float[] positions = null;
        JSONArray positionsJson = spec.optJSONArray("positions");
        if (positionsJson != null && positionsJson.length() == colors.length) {
            positions = new float[colors.length];
            for (int i = 0; i < positions.length; i++)
                positions[i] = (float) positionsJson.optDouble(i, i / Math.max(1f, positions.length - 1f));
        }
        String type = spec.optString("type", "linear").toLowerCase();
        if (type.contains("radial")) {
            float cx = resolveLength(spec.opt("centerX"), width, width / 2f, false);
            float cy = resolveLength(spec.opt("centerY"), height, height / 2f, false);
            float radius = resolveLength(spec.opt("radius"), Math.min(width, height), Math.max(width, height) / 2f, false);
            return new RadialGradient(cx, cy, Math.max(1, radius), colors, positions, Shader.TileMode.CLAMP);
        }
        if (type.contains("sweep")) {
            float cx = resolveLength(spec.opt("centerX"), width, width / 2f, false);
            float cy = resolveLength(spec.opt("centerY"), height, height / 2f, false);
            return new SweepGradient(cx, cy, colors, positions);
        }
        float startX = resolveLength(spec.opt("startX"), width, 0, false);
        float startY = resolveLength(spec.opt("startY"), height, 0, false);
        float endX = resolveLength(spec.opt("endX"), width, width, false);
        float endY = resolveLength(spec.opt("endY"), height, 0, false);
        return new LinearGradient(startX, startY, endX, endY, colors, positions, Shader.TileMode.CLAMP);
    }

    private RectF imageDestination(Bitmap bitmap, float width, float height, String mode) {
        String normalized = mode == null ? "cover" : mode.toLowerCase();
        if (normalized.contains("fit_xy") || normalized.contains("fitxy") || "stretch".equals(normalized))
            return new RectF(0, 0, width, height);
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0 || width <= 0 || height <= 0) return new RectF(0, 0, width, height);
        float scale = (normalized.contains("contain") || normalized.contains("fitcenter") || normalized.contains("fit_center")) ? Math.min(width / bw, height / bh) : (normalized.contains("center") && !normalized.contains("crop") ? 1f : Math.max(width / bw, height / bh));
        float drawW = bw * scale;
        float drawH = bh * scale;
        float left = (width - drawW) / 2f;
        float top = (height - drawH) / 2f;
        return new RectF(left, top, left + drawW, top + drawH);
    }

    private Bitmap getBitmapForDraw(String src, float blurRadius) {
        Bitmap bitmap = IMAGE_CACHE.get(src);
        if (bitmap == null) return null;
        if (blurRadius <= 0) return bitmap;
        int radius = Math.max(1, Math.round(blurRadius));
        String key = src + "#blur#" + radius;
        Bitmap cached = BLUR_CACHE.get(key);
        if (cached != null) return cached;
        int sample = Math.max(2, Math.min(24, radius / 2));
        int w = Math.max(1, bitmap.getWidth() / sample);
        int h = Math.max(1, bitmap.getHeight() / sample);
        Bitmap blurred = Bitmap.createScaledBitmap(bitmap, w, h, true);
        BLUR_CACHE.put(key, blurred);
        return blurred;
    }

    private void ensureImage(String src) {
        if (src == null || src.isEmpty() || IMAGE_CACHE.containsKey(src)) return;
        if (!IMAGE_LOADING.add(src)) return;
        new Thread(() -> {
            try {
                Bitmap bitmap;
                if (src.startsWith("http://") || src.startsWith("https://")) {
                    try (InputStream stream = new URL(src).openConnection().getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(stream);
                    }
                } else if (src.startsWith("content://") || src.startsWith("android.resource://")) {
                    try (InputStream stream = host.context.getContentResolver().openInputStream(Uri.parse(src))) {
                        bitmap = BitmapFactory.decodeStream(stream);
                    }
                } else if (src.startsWith("file://")) bitmap = BitmapFactory.decodeFile(Uri.parse(src).getPath());
                else bitmap = BitmapFactory.decodeFile(src);
                if (bitmap == null) throw new IllegalStateException("Bitmap decode returned null");
                IMAGE_CACHE.put(src, bitmap);
                host.mainHandler.post(() -> {
                    IMAGE_LOADING.remove(src);
                    invalidate();
                    if (onImageLoadEventId >= 0) sendSimpleEvent("onImageLoad", onImageLoadEventId, payload -> {
                        payload.put("src", src);
                        payload.put("width", bitmap.getWidth());
                        payload.put("height", bitmap.getHeight());
                    });
                });
            } catch (Exception e) {
                Log.e("SpotifyPlus", "Failed loading ScriptView image: " + src, e);
                host.mainHandler.post(() -> {
                    IMAGE_LOADING.remove(src);
                    if (onImageErrorEventId >= 0) sendSimpleEvent("onImageError", onImageErrorEventId, payload -> {
                        payload.put("src", src);
                        payload.put("error", String.valueOf(e.getMessage()));
                    });
                });
            }
        }).start();
    }

    private void preloadImages(JSONArray nodes) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) continue;
            if ("image".equals(node.optString("type"))) {
                String src = node.optString("src", node.optString("uri", null));
                if (src != null && !src.isEmpty()) ensureImage(src);
            }
            preloadImages(node.optJSONArray("children"));
        }
    }

    private boolean patchDisplayNode(JSONArray nodes, String id, JSONObject patch) {
        if (nodes == null) return false;
        for (int i = 0; i < nodes.length(); i++) {
            try {
                JSONObject node = nodes.optJSONObject(i);
                if (node == null) continue;
                if (id.equals(String.valueOf(node.opt("id")))) {
                    Iterator<String> keys = patch.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        node.put(key, patch.opt(key));
                    }
                    return true;
                }
                if (patchDisplayNode(node.optJSONArray("children"), id, patch)) return true;
            } catch(Exception e) {
                Log.e("SpotifyPlus", "Failed loading ScriptView image: " + id, e);
            }
        }
        return false;
    }

    private boolean displayListHasShadow(JSONArray nodes) {
        if (nodes == null) return false;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) continue;
            if (node.has("shadow")) return true;
            if (displayListHasShadow(node.optJSONArray("children"))) return true;
        }
        return false;
    }

    private Object firstPresent(JSONObject node, String... keys) {
        for (String key : keys) if (node.has(key)) return node.opt(key);
        return null;
    }

    private float resolveLength(Object raw, float base, float fallback, boolean textSp) {
        try {
            if (raw == null || raw == JSONObject.NULL) return fallback;
            if (raw instanceof Number number)
                return textSp ? host.sp(number.floatValue()) : host.dp(number.floatValue());
            String text = String.valueOf(raw).trim().toLowerCase();
            if (text.isEmpty() || "auto".equals(text) || "undefined".equals(text)) return fallback;
            if (text.endsWith("%")) return base * Float.parseFloat(text.substring(0, text.length() - 1)) / 100f;
            if (text.endsWith("px")) return Float.parseFloat(text.substring(0, text.length() - 2));
            if (text.endsWith("sp")) return host.sp(Float.parseFloat(text.substring(0, text.length() - 2)));
            if (text.endsWith("dp")) return host.dp(Float.parseFloat(text.substring(0, text.length() - 2)));
            return textSp ? host.sp(Float.parseFloat(text)) : host.dp(Float.parseFloat(text));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private float normalizeAngle(Object raw) {
        if (raw == null || raw == JSONObject.NULL) return 0f;
        if (raw instanceof Number number) return number.floatValue();
        try {
            String text = String.valueOf(raw).trim().toLowerCase();
            if (text.endsWith("deg")) return Float.parseFloat(text.substring(0, text.length() - 3));
            if (text.endsWith("rad"))
                return (float) (Float.parseFloat(text.substring(0, text.length() - 3)) * 180.0 / Math.PI);
            return Float.parseFloat(text);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private Integer parseNodeColor(Object raw, Integer fallback) {
        Integer color = host.parseColor(raw);
        return color != null ? color : fallback;
    }

    private int withAlpha(int color, float alpha) {
        int baseAlpha = Color.alpha(color);
        int nextAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alpha)));
        return (color & 0x00FFFFFF) | (nextAlpha << 24);
    }

    private void sendSimpleEvent(String eventName, int eventId, EventPayloadWriter writer) {
        try {
            JSONObject payload = host.basePayload(ownerNode.id);
            if (writer != null) writer.write(payload);
            host.sendEventToNode(ownerNode.id, eventName, eventId, payload);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed sending ScriptView event " + eventName, e);
        }
    }

    private interface EventPayloadWriter {
        void write(JSONObject payload) throws Exception;
    }
}


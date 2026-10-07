package com.lenerd.spotifyplus.module.hooks;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.module.scripting.UITargets;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.json.JSONObject;

/** Spotify 9.1.82.2160 header boundary using Spotify's own Compose runtime. */
public final class ComposeHeaderTarget extends SpotifyHook {
    private static final ThreadLocal<Boolean> ORIGINAL = ThreadLocal.withInitial(() -> false);
    private Method header, action, androidView, rememberContext, beginGroup, endGroup, setContent, setParent;
    private Class<?> unary, binary, composeView;
    private Object unit, modifier;

    private Class<?> type(String name) throws ClassNotFoundException { return classLoader.loadClass(name); }

    @Override protected void hookSetup() throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        Class<?> composer = type("p.lf00");
        unary = type("p.w500");
        binary = type("p.j600");
        header = type("p.axj").getDeclaredMethod("h", type("p.axj"), type("p.swj"), composer, int.class);
        action = type("p.axj").getDeclaredMethod("e", type("p.axj"), type("p.swj"), type("p.gzj"), type("p.fzj"), composer, int.class);
        androidView = type("p.q7a1").getDeclaredMethod("a", unary, type("p.ggh0"), unary, unary, unary, composer, int.class, int.class);
        rememberContext = type("p.drf1").getDeclaredMethod("A", composer);
        beginGroup = composer.getMethod("i0", int.class);
        endGroup = composer.getMethod("r", boolean.class);
        composeView = type("androidx.compose.ui.platform.ComposeView");
        setContent = composeView.getMethod("setContent", binary);
        setParent = composeView.getMethod("setParentCompositionContext", type("p.jnh"));
        try {
            unit = type("p.cb91").getField("a").get(null);
            modifier = type("p.dgh0").getField("a").get(null);
        } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        hook(header);
        hook(action);
        UITargets.capability("contextMenu.header", "replace", "before", "after", "overlay");
        UITargets.capability("contextMenu.action", "replace", "before", "after", "overlay");
        log("UI probe: header and AndroidView bindings installed (9.1.82.2160)");
    }


    @Override protected void beforeHook(SpotifyCallback callback) {
        // NativePart leases Spotify's own row, including the live click handler. An additional
        // asynchronous row adapter here would sever the lease's rendering/callback scope.
        if (callback == null || ORIGINAL.get() || MenuUITargets.rendersNativePart()
                || !UITargets.supportsSpotify() || Looper.myLooper() != Looper.getMainLooper()) return;
        Object[] args = callback.getArgs();
        Method renderer = callback.getMember().equals(action) ? action : header;
        int composerIndex = renderer.equals(action) ? 4 : 2;
        Object composer = args[composerIndex];
        // Capture models, never the invocation's composer. Nested content gets its own composer.
        Object[] models = java.util.Arrays.copyOf(args, composerIndex);
        try {
            beginGroup.invoke(composer, renderer.equals(action) ? 0x53504c56 : 0x53504c55);
            try {
                Object parent = rememberContext.invoke(null, composer);
                Object factory = function(unary, values -> new HeaderHost((Context) values[0], parent, renderer, models));
                Object release = function(unary, values -> { ((HeaderHost) values[0]).release(); return unit; });
                Object update = function(unary, values -> { ((HeaderHost) values[0]).update(parent, models); return unit; });
                androidView.invoke(null, factory, modifier, null, release, update, composer, 0, 0);
            } finally { endGroup.invoke(composer, false); }
            callback.returnAndSkip(null);
        } catch (Throwable error) {
            logError("UI probe: Compose bridge failed", new Exception(error));
        }
    }

    private interface Invocation { Object invoke(Object[] args) throws Throwable; }
    private Object function(Class<?> contract, Invocation body) {
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{contract}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "invoke" -> body.invoke(args);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "SpotifyPlusComposeCallback";
                default -> throw new UnsupportedOperationException(method.toString());
            };
        });
    }

    private final class HeaderHost extends FrameLayout {
        String id = "contextMenu:" + UUID.randomUUID();
        final Method renderer;
        final String target;
        final FrameLayout original;
        final View content;
        boolean released;
        Object[] lastModels;
        String lastIdentity;

        HeaderHost(Context context, Object parent, Method renderer, Object[] models) throws Exception {
            super(context);
            this.renderer = renderer;
            target = renderer.equals(action) ? "contextMenu.action" : "contextMenu.header";
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            original = new FrameLayout(context);
            content = (View) composeView.getConstructor(Context.class).newInstance(context);
            setParent.invoke(content, parent);
            original.addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addView(original, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            update(parent, models);
            addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { emit(); }
                public void onViewDetachedFromWindow(View v) { }
            });
            log("UI probe: created " + id);
        }

        void update(Object parent, Object[] models) throws Exception {
            if (released) return;
            boolean identical = lastModels != null && models.length == lastModels.length;
            if (identical) for (int i = 0; i < models.length; i++) if (models[i] != lastModels[i]) { identical = false; break; }
            if (identical) return;
            Object headerModel = models[1].getClass().getMethod("a").invoke(models[1]);
            String identity = ContextMenuHook.uriForHeader(headerModel) + ":" + (models.length > 2 ? models[2].getClass().getField("a").get(models[2]) : "header");
            if (lastIdentity != null && !lastIdentity.equals(identity)) {
                UITargets.close(id);
                if (original.getParent() instanceof ViewGroup group) group.removeView(original);
                removeAllViews();
                addView(original, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                id = "contextMenu:" + UUID.randomUUID();
            }
            lastIdentity = identity;
            lastModels = models;
            setParent.invoke(content, parent);
            setContent.invoke(content, function(binary, values -> {
                boolean previous = ORIGINAL.get();
                ORIGINAL.set(true);
                try {
                    Object[] invocation = java.util.Arrays.copyOf(models, models.length + 2);
                    invocation[models.length] = values[0];
                    invocation[models.length + 1] = 0;
                    renderer.invoke(null, invocation);
                }
                finally { ORIGINAL.set(previous); }
                return unit;
            }));
            if (isAttachedToWindow()) emit();
        }

        void emit() {
            if (released) return;
            try {
                Object headerModel = lastModels[1].getClass().getMethod("a").invoke(lastModels[1]);
                String uri = ContextMenuHook.uriForHeader(headerModel);
                JSONObject data = new JSONObject().put("uri", uri == null ? JSONObject.NULL : uri).put("pageUri", JSONObject.NULL)
                        .put("title", headerModel.getClass().getField("a").get(headerModel))
                        .put("subtitle", headerModel.getClass().getField("c").get(headerModel));
                if (lastModels.length > 2) {
                    Object model = lastModels[2];
                    Object title = model.getClass().getField("e").get(model);
                    Object resource = model.getClass().getField("d").get(model);
                    if (title == null && resource instanceof Integer value) title = getContext().getString(value);
                    data.put("actionId", model.getClass().getField("a").get(model)).put("title", title)
                            .put("enabled", model.getClass().getField("g").get(model));
                }
                UITargets.open(id, target, this, original, data);
            } catch (Exception error) { logError(error); }
        }

        void release() {
            if (released) return;
            released = true;
            UITargets.close(id);
            removeAllViews();
            lastModels = null;
            log("UI probe: released " + id);
        }

    }

    @Override protected void afterHook(SpotifyCallback callback) { }
    @Override public Object handle(String command, Object[] args) { return null; }
}

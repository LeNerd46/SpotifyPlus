package com.lenerd.spotifyplus.module.hooks;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.UITargets;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.json.JSONArray;
import org.json.JSONObject;

/** Whole loaded menus and drawers; parts render through Spotify's own Compose/element runtime. */
public final class MenuUITargets extends SpotifyHook {
    private static final ThreadLocal<Boolean> ORIGINAL = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Part> RENDERING_PART = new ThreadLocal<>();
    static boolean rendersNativePart() { return RENDERING_PART.get() != null; }
    private Method menu, drawer, menuRow, begin, end, androidView, remember, setContent, setParent;
    private Class<?> unary, binary, composeView;
    private Object unit, modifier;
    private Class<?> type(String name) throws ClassNotFoundException { return classLoader.loadClass(name); }
    private static Object field(Object value, String name) throws Exception { return value.getClass().getField(name).get(value); }

    @Override protected void hookSetup() throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        Class<?> composer = type("p.lf00");
        unary = type("p.w500"); binary = type("p.j600");
        menu = type("p.axj").getDeclaredMethod("f", type("p.axj"), type("p.swj"), composer, int.class);
        drawer = type("p.d711").getDeclaredMethod("d1", Object.class, Object.class, Object.class, Object.class, Object.class);
        menuRow = type("p.vt").getDeclaredMethod("a", Object.class, Object.class, Object.class, Object.class, Object.class);
        menuRow.setAccessible(true);
        begin = composer.getMethod("i0", int.class); end = composer.getMethod("r", boolean.class);
        androidView = type("p.q7a1").getDeclaredMethod("a", unary, type("p.ggh0"), unary, unary, unary, composer, int.class, int.class);
        remember = type("p.drf1").getDeclaredMethod("A", composer);
        composeView = type("androidx.compose.ui.platform.ComposeView");
        setContent = composeView.getMethod("setContent", binary);
        setParent = composeView.getMethod("setParentCompositionContext", type("p.jnh"));
        try {
            unit = type("p.cb91").getField("a").get(null);
            modifier = type("p.dgh0").getField("a").get(null);
        }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        hook(menu); hook(drawer); hook(menuRow);
        UITargets.capability("contextMenu.root", "replace", "before", "after", "overlay");
        UITargets.capability("navigation.drawer", "replace", "before", "after", "overlay");
    }

    @Override protected void beforeHook(SpotifyCallback callback) {
        if (callback == null || !UITargets.supportsSpotify() || Looper.myLooper() != Looper.getMainLooper()) return;
        try {
            Object[] args = callback.getArgs();
            Part rendering = RENDERING_PART.get();
            if (rendering != null && rendering.action && callback.getMember().equals(menuRow)
                    && args[1] != null && args[1].getClass().getName().equals("p.lzj")) {
                Object nativeClick = type("p.dr0").getConstructor(int.class, Object.class, Object.class).newInstance(7, args[1], args[2]);
                rendering.capture(() -> type("p.u500").getMethod("invoke").invoke(nativeClick));
                return;
            }
            if (callback.getMember().equals(drawer) && rendering != null && rendering.action
                    && args[1] != null && args[1].getClass().getName().equals("p.w811")) {
                Object handler = args[2];
                Object event = type("p.s811").getField("a").get(null);
                rendering.capture(() -> unary.getMethod("invoke", Object.class).invoke(handler, event));
                return;
            }
            if (ORIGINAL.get()) return;
            boolean isDrawer = callback.getMember().equals(drawer) && args[1] != null
                    && args[1].getClass().getName().equals("p.q711")
                    && field(callback.getThisObject(), "b").getClass().getName().equals("p.t711");
            if (!callback.getMember().equals(menu) && !isDrawer) return;
            int composerIndex = isDrawer ? 3 : 2;
            Object composer = args[composerIndex];
            Method renderer = isDrawer ? drawer : menu;
            Object receiver = isDrawer ? callback.getThisObject() : null;
            Object[] models = Arrays.copyOf(args, composerIndex);
            begin.invoke(composer, isDrawer ? 0x53504c60 : 0x53504c61);
            try {
                Object parent = remember.invoke(null, composer);
                Object factory = function(unary, values -> new Host((Context) values[0], renderer, receiver, parent, models));
                Object release = function(unary, values -> { ((Host) values[0]).release(); return unit; });
                Object update = function(unary, values -> { ((Host) values[0]).update(parent, models); return unit; });
                androidView.invoke(null, factory, modifier, null, release, update, composer, 0, 0);
            } finally { end.invoke(composer, false); }
            callback.returnAndSkip(isDrawer ? unit : null);
        } catch (Throwable error) { logError("Cannot adapt native menu boundary", new Exception(error)); }
    }

    private interface Body { Object run(Object[] args) throws Throwable; }
    private interface Click { void run() throws Exception; }
    private Object function(Class<?> contract, Body body) {
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{contract}, (proxy, method, args) -> switch (method.getName()) {
            case "invoke" -> body.run(args);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "SpotifyPlusMenuContent";
            default -> throw new UnsupportedOperationException(method.toString());
        });
    }

    private final class Part {
        final Host host;
        final String id = UUID.randomUUID().toString(), semanticId, title;
        final boolean action, enabled;
        final Body renderer;
        Click click;
        CompletableFuture<Void> pending;
        View bank;
        boolean mounted;
        Part(Host host, String semanticId, String title, boolean action, boolean enabled, Body renderer) {
            this.host = host; this.semanticId = semanticId; this.title = title;
            this.action = action; this.enabled = enabled; this.renderer = renderer;
        }
        JSONObject describe() throws Exception {
            return new JSONObject().put("id", id).put("semanticId", semanticId).put("title", title == null ? JSONObject.NULL : title)
                    .put("kind", action ? "action" : "content").put("enabled", enabled);
        }
        View create() throws Exception {
            if (bank != null) {
                View content = bank;
                bank = null;
                content.setAlpha(1);
                content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                return content;
            }
            if (mounted) throw new IllegalStateException("Mount each NativePart only once");
            mounted = true;
            View content;
            try { content = host.compose(values -> {
                Part previous = RENDERING_PART.get();
                RENDERING_PART.set(this);
                try { renderer.run(values); }
                finally { RENDERING_PART.set(previous); }
                return unit;
            }); } catch (Exception error) { mounted = false; throw error; }
            content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { }
                public void onViewDetachedFromWindow(View v) { mounted = false; click = null; }
            });
            return content;
        }
        void capture(Click click) {
            this.click = click;
            if (pending != null) host.post(this::dispatch);
        }
        void dispatch() {
            CompletableFuture<Void> result = pending;
            if (result == null || click == null) return;
            pending = null;
            try {
                host.require(id);
                click.run();
                result.complete(null);
            } catch (Exception error) { result.completeExceptionally(error); }
        }
        CompletionStage<Void> invoke() throws Exception {
            if (!action || !enabled) throw new IllegalStateException("Native part is not an enabled action");
            if (pending != null) throw new IllegalStateException("Native action invocation is already pending");
            CompletableFuture<Void> result = new CompletableFuture<>();
            pending = result;
            if (click != null) dispatch();
            else if (!mounted) {
                // Compose the native element to acquire its live Mobius click handler, including effects and dismissal.
                try {
                    bank = create();
                    bank.setAlpha(0);
                    bank.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    host.addView(bank, new FrameLayout.LayoutParams(1, 1));
                } catch (Exception error) {
                    pending = null;
                    result.completeExceptionally(error);
                }
                // Keep the composition alive until this model closes so asynchronous effects are not cancelled.
            }
            host.postDelayed(() -> {
                if (pending != result) return;
                pending = null;
                result.completeExceptionally(new IllegalStateException("Spotify's native action handler is not ready"));
            }, 5000);
            return result;
        }
        void expire() {
            click = null;
            if (pending != null) pending.completeExceptionally(new IllegalStateException("Native action model has expired"));
            pending = null;
            if (bank != null) host.removeView(bank);
        }
    }

    private final class Host extends FrameLayout implements UITargets.Parts {
        final String id = "nativeMenu:" + UUID.randomUUID(), target;
        final Method renderer;
        final Object receiver;
        final FrameLayout original;
        final View content;
        final Map<String, Part> parts = new LinkedHashMap<>();
        final Set<String> retired = new LinkedHashSet<>();
        Object parent;
        Object[] models;
        JSONObject context;
        boolean released;
        Host(Context context, Method renderer, Object receiver, Object parent, Object[] models) throws Exception {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            this.renderer = renderer; this.receiver = receiver; this.parent = parent;
            target = renderer.equals(menu) ? "contextMenu.root" : "navigation.drawer";
            original = new FrameLayout(context);
            content = compose(values -> {
                boolean previous = ORIGINAL.get(); ORIGINAL.set(true);
                try {
                    Object[] invocation = Arrays.copyOf(this.models, this.models.length + 2);
                    invocation[this.models.length] = values[0]; invocation[this.models.length + 1] = 0;
                    renderer.invoke(receiver, invocation);
                } finally { ORIGINAL.set(previous); }
                return unit;
            });
            original.addView(content, new LayoutParams(-1, -1));
            addView(original, new LayoutParams(-1, -1));
            update(parent, models);
            addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { emit(); }
                public void onViewDetachedFromWindow(View v) {
                    UITargets.close(id);
                    for (Part part : parts.values()) part.expire();
                }
            });
        }
        View compose(Body body) throws Exception {
            View result = (View) composeView.getConstructor(Context.class).newInstance(getContext());
            setParent.invoke(result, parent);
            setContent.invoke(result, function(binary, body));
            return result;
        }
        void update(Object parent, Object[] models) throws Exception {
            if (released) return;
            boolean same = this.models != null && this.models.length == models.length;
            if (same) for (int i = 0; i < models.length; i++) if (this.models[i] != models[i]) { same = false; break; }
            this.parent = parent;
            if (same) return;
            for (Part part : parts.values()) { retired.add(part.id); part.expire(); }
            while (retired.size() > 2048) retired.remove(retired.iterator().next());
            parts.clear();
            this.models = models;
            context = new JSONObject().put("uri", JSONObject.NULL).put("pageUri", JSONObject.NULL);
            if (renderer.equals(menu)) menuParts(models);
            else drawerParts(models);
            JSONArray descriptions = new JSONArray();
            for (Part part : parts.values()) descriptions.put(part.describe());
            context.put("parts", descriptions);
            setParent.invoke(content, parent);
            // Reinstalling content invalidates the captured model without retaining a composer.
            setContent.invoke(content, function(binary, values -> {
                boolean previous = ORIGINAL.get(); ORIGINAL.set(true);
                try {
                    Object[] invocation = Arrays.copyOf(this.models, this.models.length + 2);
                    invocation[this.models.length] = values[0]; invocation[this.models.length + 1] = 0;
                    renderer.invoke(receiver, invocation);
                } finally { ORIGINAL.set(previous); }
                return unit;
            }));
            if (isAttachedToWindow()) emit();
        }
        void add(String semanticId, String title, boolean action, boolean enabled, Body renderer) {
            Part part = new Part(this, semanticId, title, action, enabled, renderer);
            parts.put(part.id, part);
        }
        void menuParts(Object[] models) throws Exception {
            Object model = models[1];
            Object header = model.getClass().getMethod("a").invoke(model);
            String uri = ContextMenuHook.uriForHeader(header);
            context.put("uri", uri == null ? JSONObject.NULL : uri).put("title", field(header, "a"))
                    .put("subtitle", field(header, "c"));
            Method headerRenderer = type("p.axj").getMethod("h", type("p.axj"), type("p.swj"), type("p.lf00"), int.class);
            add("header", context.optString("title"), false, true, values -> headerRenderer.invoke(null, models[0], model, values[0], 0));
            Method actionRenderer = type("p.axj").getMethod("e", type("p.axj"), type("p.swj"), type("p.gzj"), type("p.fzj"), type("p.lf00"), int.class);
            for (Object item : (Iterable<?>) model.getClass().getMethod("b").invoke(model)) {
                Object title = field(item, "e"), resource = field(item, "d");
                if (title == null && resource instanceof Integer value) title = getContext().getString(value);
                add(String.valueOf(field(item, "a")), title == null ? null : title.toString(), true, (Boolean) field(item, "g"),
                        values -> actionRenderer.invoke(null, models[0], model, item, field(item, "i"), values[0], 0));
            }
        }
        void drawerParts(Object[] models) throws Exception {
            Object owner = field(receiver, "b"), model = models[1];
            Method headerRenderer = type("p.t711").getMethod("i", type("p.t711"), type("p.q711"), unary, type("p.lf00"), int.class);
            add("profile", null, false, true, values -> headerRenderer.invoke(null, owner, model, models[2], values[0], 0));
            Method rowRenderer = type("p.t711").getMethod("h", type("p.t711"), type("p.o711"), type("p.lf00"), int.class);
            int index = 0;
            for (Object row : (Iterable<?>) field(model, "a")) {
                // Keep Spotify's indexed row and instrumentation, rather than synthesizing an action.
                if (!row.getClass().getName().equals("p.o711")) throw new IllegalStateException("Unrecognized drawer row model");
                Object body = field(field(row, "b"), "a");
                boolean action = body.getClass().getName().equals("p.b811");
                String semanticId = "row:" + index, title = null;
                if (action) {
                    Object resource = field(body, "b");
                    title = resource instanceof Integer value ? getContext().getString(value) : null;
                    semanticId = String.valueOf(field(body, "c"));
                } else if (body.getClass().getName().equals("p.a811")) {
                    Object element = field(field(body, "a"), "a");
                    semanticId = "content:" + element.getClass().getName() + ":" + index;
                }
                index++;
                add(semanticId, title, action, true, values -> rowRenderer.invoke(null, owner, row, values[0], 0));
            }
        }
        Part require(String partId) {
            Part part = parts.get(partId);
            if (released || !isAttachedToWindow() || part == null) throw new IllegalStateException("Native part has expired or its instance has closed");
            return part;
        }
        public View create(String partId) throws Exception {
            // A commit from the previous descriptor can arrive after a native model update.
            // Leave its retired row empty; the subsequent React commit mounts the current IDs.
            // Unknown/cross-instance IDs still fail, and expired action invocation always rejects.
            if (!released && isAttachedToWindow() && retired.contains(partId)) return new FrameLayout(getContext());
            return require(partId).create();
        }
        public CompletionStage<Void> invoke(String partId) throws Exception { return require(partId).invoke(); }
        void emit() {
            if (released) return;
            if (com.lenerd.spotifyplus.BuildConfig.DEBUG) log("UI menu boundary " + target + " parts=" + parts.size()
                    + " attached=" + isAttachedToWindow() + " size=" + getWidth() + "x" + getHeight());
            UITargets.open(id, target, this, original, context, true);
            UITargets.parts(id, this);
        }
        void release() {
            if (released) return;
            released = true;
            UITargets.close(id);
            for (Part part : parts.values()) part.expire();
            parts.clear(); retired.clear(); removeAllViews(); models = null;
        }
    }
    @Override protected void afterHook(SpotifyCallback callback) { }
    @Override public Object handle(String command, Object[] args) { return null; }
}

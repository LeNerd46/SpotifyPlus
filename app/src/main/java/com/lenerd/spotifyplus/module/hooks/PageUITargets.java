package com.lenerd.spotifyplus.module.hooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.UITargets;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;

/** Spotify 9.1.82 Tome page wrapper; the fragment root and page renderer references stay intact. */
public final class PageUITargets extends SpotifyHook {
    private static final int HOST_TAG = 0x53504c58;
    private java.lang.reflect.Method tomeContent;
    private static final Set<String> CONTAINERS = Set.of("android.widget.FrameLayout",
            "androidx.constraintlayout.widget.ConstraintLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout", "p.qtb1");
    @Override protected void hookSetup() throws ClassNotFoundException, NoSuchMethodException {
        hook(classLoader.loadClass("p.cuz").getDeclaredMethod("T0", LayoutInflater.class, ViewGroup.class, Bundle.class));
        tomeContent = classLoader.loadClass("p.z2m0").getDeclaredMethod("c");
        hook(tomeContent);
        for (String area : new String[]{"home", "search", "library", "playlist", "album", "artist"})
            UITargets.capability(area + ".page", "replace", "overlay");
        UITargets.capability("miniPlayer.root", "replace", "overlay");
        for (String name : new String[]{"artist.discography.page", "settings.page", "profile.page", "lyrics.page"})
            UITargets.capability(name, "replace", "overlay");
    }

    @Override protected void beforeHook(SpotifyCallback callback) { }
    @Override protected void afterHook(SpotifyCallback callback) {
        if (callback == null || callback.getThrowable() != null || !UITargets.supportsSpotify()) return;
        try {
            if (callback.getMember().equals(tomeContent)) {
                if (!(callback.getResult() instanceof ViewGroup root) || root.getTag(HOST_TAG) != null) return;
                Object runtime = callback.getThisObject().getClass().getField("a").get(callback.getThisObject());
                Object page = runtime.getClass().getMethod("c").invoke(runtime);
                Object properties = classLoader.loadClass("p.all0").getMethod("a").invoke(page);
                Object property = properties.getClass().getMethod("j0", Class.class).invoke(properties, classLoader.loadClass("p.do30"));
                Object identity = classLoader.loadClass("p.pvl0").getMethod("a").invoke(property);
                if (identity == null) return;
                Object pageType = identity.getClass().getField("a").get(identity);
                if (BuildConfig.DEBUG) log("UI Tome page boundary " + pageType + " page=" + page.getClass().getName());
                String name = PageTargetNames.pageType(pageType instanceof Enum<?> value ? value.name() : null);
                if (name == null) return;
                String uri = null;
                // Discography/profile entity parameters are kept on the page itself.
                if (page.getClass().getName().equals("p.l7r0")) {
                    Object parameters = page.getClass().getField("f").get(page);
                    uri = (String) parameters.getClass().getField("a").get(parameters);
                } else if (page.getClass().getName().equals("p.rkv0")) {
                    Object controller = page.getClass().getField("c").get(page);
                    Object parameters = controller.getClass().getField("e").get(controller);
                    uri = (String) parameters.getClass().getField("a").get(parameters);
                }
                if (uri == null && name.equals("settings.page")) uri = "spotify:settings";
                if (uri == null) {
                    // Entity pages may publish their canonical URI through their page identifier.
                    Object identifier = identity.getClass().getField("b").get(identity);
                    Object value = identifier.getClass().getField("a").get(identifier);
                    if (value instanceof String canonical && name.equals(PageTargetNames.route(canonical))) uri = canonical;
                }
                mount(root, name, uri, false);
                return;
            }
            Object fragment = callback.getThisObject();
            View view = (View) fragment.getClass().getField("h1").get(fragment);
            Bundle args = (Bundle) fragment.getClass().getField("f").get(fragment);
            String uri = route(args);
            if (BuildConfig.DEBUG && view != null) log("UI page boundary " + fragment.getClass().getName() + " root=" + view.getClass().getName()
                    + " route=" + uri);
            boolean miniPlayer = fragment.getClass().getName().equals("p.gqj0")
                    && args != null && !args.getBoolean("embedded_in_bottom_sheet", false);
            String name = miniPlayer ? "miniPlayer.root" : pageTarget(args, uri);
            if (name == null || !(view instanceof ViewGroup root) || !CONTAINERS.contains(view.getClass().getName())) return;
            mount(root, name, uri, miniPlayer);
        } catch (Exception error) { logError(error); }
    }
    private static void mount(ViewGroup root, String name, String uri, boolean miniPlayer) throws Exception {
        if (root.getTag(HOST_TAG) != null) return;
        boolean tome = root.getClass().getName().equals("p.qtb1") && root.getChildCount() == 1;
        View original = tome ? root.getChildAt(0) : miniPlayer && root.getChildCount() > 0 ? root.getChildAt(root.getChildCount() - 1) : null;
        if (original != null) UITargets.capability(name, "replace", "overlay");
        else UITargets.capability(name, "overlay");
        FrameLayout host = new FrameLayout(root.getContext());
        ViewGroup.LayoutParams hostParams = miniPlayer && original != null ? original.getLayoutParams() : new FrameLayout.LayoutParams(-1, -1);
        if (original != null) {
            root.removeView(original);
            host.addView(original, new FrameLayout.LayoutParams(-1, miniPlayer ? -2 : -1));
        } else host.setElevation(1000 * root.getResources().getDisplayMetrics().density);
        root.addView(host, hostParams);
        root.setTag(HOST_TAG, host);
        String id = name + ":" + UUID.randomUUID();
        JSONObject context = new JSONObject().put("uri", uri == null ? JSONObject.NULL : uri)
                .put("pageUri", uri == null ? JSONObject.NULL : uri);
        View.OnAttachStateChangeListener lifecycle = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { UITargets.open(id, name, host, original, context, !miniPlayer); }
            public void onViewDetachedFromWindow(View v) { UITargets.close(id); }
        };
        host.addOnAttachStateChangeListener(lifecycle);
        if (host.isAttachedToWindow()) lifecycle.onViewAttachedToWindow(host);
    }
    private static String route(Bundle args) {
        if (args == null) return null;
        Object parameters = args.get("parameters");
        if (parameters != null) {
            try {
                String type = parameters.getClass().getName();
                if (type.equals("p.l800")) return "spotify:home";
                // Explicit bindings: never infer a screen from a referrer URI.
                String field = switch (type) {
                    case "p.l800", "p.nla", "p.ug3", "p.a23", "p.ggl", "p.vkv0", "p.m7r0" -> "a";
                    case "p.xl80" -> "b";
                    default -> null;
                };
                if (field != null) {
                    Object value = parameters.getClass().getField(field).get(parameters);
                    if (value instanceof String uri) return uri;
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        for (String key : args.keySet()) {
            if (!key.equals("uri") && !key.equals("pageUri")) continue;
            Object value = args.get(key);
            if (value instanceof String text && target(text) != null) return text;
        }
        return null;
    }
    private static String pageTarget(Bundle args, String uri) {
        Object parameters = args == null ? null : args.get("parameters");
        if (parameters != null) {
            String named = switch (parameters.getClass().getName()) {
                case "p.l800" -> "home.page";
                case "p.nla" -> "search.page";
                case "p.ug3" -> "library.page";
                case "p.a23" -> "album.page";
                case "p.ggl" -> uri != null && uri.startsWith("spotify:artist:") ? "artist.page" : null;
                case "p.xl80" -> "playlist.page";
                case "p.vkv0" -> uri != null && uri.startsWith("spotify:artist:") ? "artist.discography.page" : null;
                case "p.m7r0" -> "profile.page";
                default -> null;
            };
            if (named != null) return named;
        }
        return target(uri);
    }
    private static String target(String uri) {
        return PageTargetNames.route(uri);
    }
    @Override public Object handle(String command, Object[] args) { return null; }
}


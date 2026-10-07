package com.lenerd.spotifyplus.module.scripting;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Method;

/** Moves an attached native slot without a window detach/attach lifecycle transition. */
final class NativeContentMover {
    private NativeContentMover() { }
    static void move(View view, ViewGroup destination, int height) {
        if (view.getParent() == destination) return;
        ViewGroup source = view.getParent() instanceof ViewGroup group ? group : null;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, height);
        if (source != null && view.isAttachedToWindow() && destination.isAttachedToWindow()
                && view.getWindowToken() == destination.getWindowToken()) {
            // Both endpoints are module-owned FrameLayouts. Resolve before altering the hierarchy.
            try {
                Method detach = ViewGroup.class.getDeclaredMethod("detachViewFromParent", View.class);
                Method attach = ViewGroup.class.getDeclaredMethod("attachViewToParent", View.class, int.class, ViewGroup.LayoutParams.class);
                detach.setAccessible(true); attach.setAccessible(true);
                int index = source.indexOfChild(view);
                ViewGroup.LayoutParams previous = view.getLayoutParams();
                detach.invoke(source, view);
                try { attach.invoke(destination, view, -1, params); }
                catch (ReflectiveOperationException error) {
                    attach.invoke(source, view, index, previous);
                    throw error;
                }
                source.requestLayout(); source.invalidate();
                destination.requestLayout(); destination.invalidate();
                view.forceLayout();
                return;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot preserve native content during an attached move", error);
            }
        }
        if (source != null) source.removeView(view);
        destination.addView(view, params);
    }
}

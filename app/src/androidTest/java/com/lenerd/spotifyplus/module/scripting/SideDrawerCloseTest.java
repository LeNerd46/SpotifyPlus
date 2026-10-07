package com.lenerd.spotifyplus.module.scripting;

import android.content.Context;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SideDrawerCloseTest {
    @Test
    public void closingDisposesOverlayEvenWhenUnmountCommitIsDroppedAndReopens() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        var bridge = new SpotifyNativeBridge(context.getClassLoader(), context.getFilesDir(), context.getCacheDir(), context);
        FrameLayout[] root = new FrameLayout[1];
        instrumentation.runOnMainSync(() -> {
            root[0] = new FrameLayout(context);
            SpotifyNativeBridge.attachSurfaceHost("sideDrawer", root[0]);
            bridge.registerSurface("sideDrawer");
            assertEquals(1, root[0].getChildCount());
            bridge.commitSurface("sideDrawer", "[{\"op\":\"createNode\",\"id\":1,\"type\":\"View\",\"props\":{}},{\"op\":\"appendToRoot\",\"childId\":1}]");
            // React's unmount is queued, then unregistering rejects that commit.
            bridge.commitSurface("sideDrawer", "[{\"op\":\"removeFromRoot\",\"childId\":1},{\"op\":\"destroyNode\",\"id\":1}]");
            bridge.unregisterSurface("sideDrawer");
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertEquals(0, root[0].getChildCount()));
        try {
            instrumentation.runOnMainSync(() -> {
                SpotifyNativeBridge.attachSurfaceHost("sideDrawer", root[0]);
                bridge.registerSurface("sideDrawer");
                assertEquals(1, root[0].getChildCount());
                bridge.unregisterSurface("sideDrawer");
                // Reopen before the previous close finishes on the main queue.
                SpotifyNativeBridge.attachSurfaceHost("sideDrawer", root[0]);
                bridge.registerSurface("sideDrawer");
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertEquals(1, root[0].getChildCount()));
        } finally {
            bridge.unregisterSurface("sideDrawer");
            instrumentation.waitForIdleSync();
        }
    }
}

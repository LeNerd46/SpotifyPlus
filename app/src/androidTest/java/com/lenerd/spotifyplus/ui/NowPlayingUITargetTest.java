package com.lenerd.spotifyplus.ui;

import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayDeque;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Open Now Playing with the opt-in debug fixture; no playback commands are issued. */
@RunWith(AndroidJUnit4.class)
public class NowPlayingUITargetTest {
    @Test public void originalRestoresAcrossVisibilityAndConfigurationChanges() throws Exception {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        String artist = InstrumentationRegistry.getArguments().getString("artist");
        assertNotNull("Supply the playing track's native artist label", artist);
        assertNotNull(waitFor(automation, artist));
        AccessibilityNodeInfo hide = waitFor(automation, "Hide native page");
        assertNotNull(hide);
        assertTrue(hide.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        AccessibilityNodeInfo restore = waitFor(automation, "Restore native page");
        assertNotNull(restore);
        assertTrue(restore.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        assertNotNull(waitFor(automation, artist));
        try {
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90));
            automation.waitForIdle(300, 5000);
            assertNotNull("React target must survive configuration change", waitFor(automation, "Hide native page"));
            assertNotNull("Original must survive configuration change", waitFor(automation, artist));
        } finally { automation.setRotation(UiAutomation.ROTATION_UNFREEZE); }
        automation.waitForIdle(300, 5000);
        assertNotNull(waitFor(automation, "Hide native page"));
        // Android's input command supplies a keyboard source and valid event timestamps.
        try (var output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("input keyevent 4"))) {
            while (output.read() != -1) { }
        }
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (find(automation, "Hide native page") != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertNull("Dismissal must remove the React page", find(automation, "Hide native page"));
    }
    private AccessibilityNodeInfo waitFor(UiAutomation automation, String text) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo node = find(automation, text);
            if (node != null) return node;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }
    private AccessibilityNodeInfo find(UiAutomation automation, String text) {
        automation.clearCache();
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 3000; visited++) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (text.contentEquals(node.getText() == null ? "" : node.getText())
                    || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }
}

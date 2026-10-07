package com.lenerd.spotifyplus.ui;

import android.app.UiAutomation;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

/** Run with a Spotify context menu open in the debug integration-probe build. */
@RunWith(AndroidJUnit4.class)
public class ComposeHeaderProbeTest {
    @Test public void reactHeaderCanBeReachedAndActivatedThroughAccessibility() {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityNodeInfo title = waitFor(automation, "Spotify Plus · React header proof");
        assertNotNull("React header must be in the accessibility tree", title);
        assertTrue("React text must be visible to accessibility", title.isVisibleToUser());
        AccessibilityNodeInfo before = find(automation, "Before header proof");
        AccessibilityNodeInfo after = find(automation, "After header proof");
        assertNotNull(before); assertNotNull(after);
        Rect beforeBounds = new Rect(), titleBounds = new Rect(), afterBounds = new Rect();
        before.getBoundsInScreen(beforeBounds); title.getBoundsInScreen(titleBounds); after.getBoundsInScreen(afterBounds);
        assertTrue("Before insertion must occupy layout space", beforeBounds.bottom <= titleBounds.top);
        assertTrue("After insertion must follow the replacement", afterBounds.top > titleBounds.bottom);
        assertNotNull("Explicit overlay must be mounted", find(automation, "Overlay proof"));
        AccessibilityNodeInfo button = find(automation, "Expand React content");
        assertNotNull("Expand button must be reachable", button);
        assertTrue("Expand button must expose click", button.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        AccessibilityNodeInfo expanded = waitFor(automation, "Collapse React content");
        assertNotNull("React state must update after accessibility click", expanded);
        assertTrue(expanded.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        assertNotNull(waitFor(automation, "Expand React content"));
        AccessibilityNodeInfo hide = find(automation, "Hide native header");
        assertNotNull(hide);
        assertTrue(hide.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        AccessibilityNodeInfo restore = waitFor(automation, "Restore native header");
        assertNotNull("A replacement must be able to omit Original", restore);
        assertTrue(restore.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        assertNotNull("Original must be mountable again", waitFor(automation, "Hide native header"));
        String subtitle = InstrumentationRegistry.getArguments().getString("subtitle");
        assertNotNull("Supply the native header subtitle to validate Original", subtitle);
        assertNotNull("Restored Original must render native content", waitFor(automation, subtitle));
        AccessibilityNodeInfo failure = find(automation, "Test render failure");
        assertNotNull(failure);
        assertTrue(failure.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (find(automation, "Spotify Plus · React header proof") != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertNull("A failed replacement must be removed", find(automation, "Spotify Plus · React header proof"));
        assertNotNull("Render failure must restore native content", waitFor(automation, subtitle));
        assertNotNull("Another contribution must survive a React failure", find(automation, "After header proof"));
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
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        // Do not prune invisible structural wrappers: Compose deliberately hides AndroidViewHolder.
        while (!queue.isEmpty() && visited++ < 2000) {
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

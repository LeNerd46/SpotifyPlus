package com.lenerd.spotifyplus.ui;

import android.app.UiAutomation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayDeque;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run against the opt-in spotifyplus-ui-parts-probe fixture. Never starts playback or selects a timer. */
@RunWith(AndroidJUnit4.class)
public class PagePartsProbeTest {
    @Test public void pageAndNativeActionRemainAccessible() {
        Bundle args = InstrumentationRegistry.getArguments();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        String uri = args.getString("uri");
        if (uri != null) InstrumentationRegistry.getInstrumentation().getContext().startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.spotify.music").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String click = args.getString("click");
        if (click != null) {
            AccessibilityNodeInfo node = waitFor(automation, click, true);
            assertNotNull("Native/custom action must be accessible: " + click, node);
            while (node != null && !node.isClickable()) node = node.getParent();
            assertNotNull("Action must have a clickable ancestor: " + click, node);
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        }
        String expected = args.getString("expected");
        assertNotNull("Supply expected text or description (prefix)", expected);
        AccessibilityNodeInfo result = waitFor(automation, expected, false);
        assertNotNull("Expected native/React content: " + expected, result);
        assertTrue(result.isVisibleToUser());
    }

    private AccessibilityNodeInfo waitFor(UiAutomation automation, String text, boolean scroll) {
        long deadline = SystemClock.uptimeMillis() + 12000;
        do {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
                queue.add(root);
                AccessibilityNodeInfo scroller = null;
                int visited = 0;
                while (!queue.isEmpty() && visited++ < 3000) {
                    AccessibilityNodeInfo node = queue.removeFirst();
                    if (node.isVisibleToUser() && (startsWith(node.getText(), text)
                            || startsWith(node.getContentDescription(), text))) return node;
                    if (node.isScrollable() && node.isVisibleToUser()) scroller = node;
                    for (int i = 0; i < node.getChildCount(); i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(child);
                    }
                }
                if (scroll && scroller != null) scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            }
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private static boolean startsWith(CharSequence value, String prefix) {
        return value != null && value.toString().regionMatches(true, 0, prefix, 0, prefix.length());
    }
}

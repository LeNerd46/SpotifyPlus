package com.lenerd.spotifyplus.module.scripting;

import android.graphics.Color;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Method;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NestedTextTest {
    private static class RecordingHost extends ScriptViewHost {
        int pressedNode = -1;
        int pressedEvent = -1;
        RecordingHost() {
            super("nested-text-test", new FrameLayout(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        }
        @Override public void sendEventToNode(int targetId, String name, int eventId, JSONObject payload) {
            pressedNode = targetId;
            pressedEvent = eventId;
        }
    }

    private void op(ScriptViewHost host, String json) throws Exception {
        Method method = ScriptViewHost.class.getDeclaredMethod("applyOp", JSONObject.class);
        method.setAccessible(true);
        method.invoke(host, new JSONObject(json));
    }

    private ScriptViewHost.RenderNode node(ScriptViewHost host, int id) throws Exception {
        Method method = ScriptViewHost.class.getDeclaredMethod("getNode", int.class);
        method.setAccessible(true);
        return (ScriptViewHost.RenderNode) method.invoke(host, id);
    }

    private TextPaint paintAt(TextView view, int offset) {
        TextPaint paint = new TextPaint(view.getPaint());
        Spanned text = (Spanned) view.getText();
        for (NestedTextSpan span : text.getSpans(offset, offset + 1, NestedTextSpan.class)) span.updateDrawState(paint);
        return paint;
    }

    @Test public void nestedStylesAndPressesShareOneMeasuredTextView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingHost host = new RecordingHost();
            try {
                op(host, "{op:'createNode',id:1,type:'Text',props:{textSizeSp:16,textColor:'#eeeeee',textIsSelectable:true}}");
                op(host, "{op:'createNode',id:2,type:'Text',props:{fontWeight:'bold'}}");
                op(host, "{op:'createNode',id:3,type:'Text',props:{fontStyle:'italic'}}");
                op(host, "{op:'createText',id:4,text:'hello world again'}");
                op(host, "{op:'createNode',id:5,type:'Text',props:{text:' link',fontFamily:'monospace',textColor:'#58a6ff',backgroundColor:'#343941',textDecorationLine:'underline line-through',onPress:{__type:'event_handler',id:42}}}");
                op(host, "{op:'appendChild',parentId:3,childId:4}");
                op(host, "{op:'appendChild',parentId:2,childId:3}");
                op(host, "{op:'appendChild',parentId:1,childId:2}");
                op(host, "{op:'appendChild',parentId:1,childId:5}");
                op(host, "{op:'appendToRoot',childId:1}");
                TextView view = (TextView) node(host, 1).view;
                assertEquals("hello world again link", view.getText().toString());
                assertTrue(node(host, 1).yogaNode.isMeasureDefined());
                assertEquals(0, node(host, 1).yogaNode.getChildCount());
                assertNull(node(host, 2).view.getParent());
                TextPaint emphasis = paintAt(view, 0);
                assertTrue(emphasis.getTypeface().isBold());
                assertTrue(emphasis.getTypeface().isItalic());
                TextPaint linkPaint = paintAt(view, 19);
                assertEquals(Color.parseColor("#58a6ff"), linkPaint.getColor());
                assertEquals(Color.parseColor("#343941"), linkPaint.bgColor);
                assertTrue(linkPaint.isUnderlineText());
                assertTrue(linkPaint.isStrikeThruText());
                ClickableSpan[] links = ((Spanned) view.getText()).getSpans(19, 20, ClickableSpan.class);
                assertEquals(1, links.length);
                links[0].onClick(view);
                assertEquals(5, host.pressedNode);
                assertEquals(42, host.pressedEvent);
                view.measure(View.MeasureSpec.makeMeasureSpec(90, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
                assertTrue("Paragraph must wrap as one TextView", view.getLineCount() > 1);
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { host.dispose(); }
        });
    }

    @Test public void textAndStyleUpdatesRemovalAndReparentingRebuildTheOuterText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecordingHost host = new RecordingHost();
            try {
                op(host, "{op:'createNode',id:1,type:'Text',props:{}}");
                op(host, "{op:'createNode',id:2,type:'Text',props:{fontWeight:'bold'}}");
                op(host, "{op:'createText',id:3,text:'first'}");
                op(host, "{op:'createText',id:4,text:' tail'}");
                op(host, "{op:'appendChild',parentId:2,childId:3}");
                op(host, "{op:'appendChild',parentId:1,childId:2}");
                op(host, "{op:'appendChild',parentId:1,childId:4}");
                TextView view = (TextView) node(host, 1).view;
                op(host, "{op:'updateText',id:3,text:'changed'}");
                assertEquals("changed tail", view.getText().toString());
                op(host, "{op:'updateProps',id:2,props:{fontWeight:'normal',textColor:'#ff0000'}}");
                assertFalse(paintAt(view, 0).getTypeface().isBold());
                assertEquals(Color.RED, paintAt(view, 0).getColor());
                op(host, "{op:'removeChild',parentId:1,childId:4}");
                assertEquals("changed", view.getText().toString());
                op(host, "{op:'createNode',id:6,type:'Text',props:{}}");
                op(host, "{op:'appendChild',parentId:6,childId:2}");
                assertEquals("", view.getText().toString());
                assertEquals("changed", ((TextView) node(host, 6).view).getText().toString());
                op(host, "{op:'updateProps',id:2,props:{textColor:null}}");
                assertNotEquals(Color.RED, paintAt((TextView) node(host, 6).view, 0).getColor());
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { host.dispose(); }
        });
    }
}

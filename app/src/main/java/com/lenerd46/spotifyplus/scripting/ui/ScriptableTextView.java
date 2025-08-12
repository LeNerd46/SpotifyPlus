package com.lenerd46.spotifyplus.scripting.ui;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

public class ScriptableTextView extends ScriptableObject {
    private final TextView textView;

    public ScriptableTextView() {
        Context context = (Context) org.mozilla.javascript.Context.getCurrentContext().getThreadLocal("context");
        this.textView = new TextView(context);
    }

    @Override
    public String getClassName() {
        return "TextView";
    }

    @JSGetter
    public String getText() {
        return textView.getText().toString();
    }

    @JSSetter
    public void setText(String text) {
        textView.setText(text);
    }

    @JSFunction
    public void append(Object... args) {
        if(args.length == 1 && args[0] instanceof String) {
            textView.append(args[0].toString());
        } else if(args.length == 3 && args[0] instanceof String && args[1] instanceof Number && args[2] instanceof Number) {
            String text = args[0].toString();
            int start = ((Number) args[1]).intValue();
            int end = ((Number) args[2]).intValue();

            textView.append(text, start, end);
        } else {
            throw org.mozilla.javascript.Context.reportRuntimeError("Invalid arguments passed to append()");
        }
    }

    @JSFunction
    public void setPadding(int left, int top, int right, int bottom) {
        textView.setPadding(left, top, right, bottom);
    }

    @JSFunction
    public void setMargin(int left, int top, int right, int bottom) {
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);

        textView.setLayoutParams(params);
    }
}
package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.InputFilter;
import android.text.Layout;
import android.view.Gravity;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/** A native cursor field with padded, rounded syllables and original-word offsets. */
final class LyricsSyllableInput extends EditText {
    private final List<int[]> chips = new ArrayList<>();
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path selectionPath = new Path();
    private final RectF bounds = new RectF();
    private int[] originalOffsets = {0};
    private int[] displayOffsets = {0};
    private boolean updating;
    private Runnable selectionChanged;

    LyricsSyllableInput(Context context) {
        super(context);
        setSingleLine(true);
        setGravity(Gravity.TOP | Gravity.START);
        setShowSoftInputOnFocus(false);
        chipPaint.setColor(0x40ffffff);
    }

    void setSelectionChanged(Runnable action) {
        selectionChanged = action;
    }

    int wordSelectionStart() { return originalOffset(getSelectionStart()); }
    int wordSelectionEnd() { return originalOffset(getSelectionEnd()); }

    private int originalOffset(int display) {
        return originalOffsets[Math.max(0, Math.min(display, originalOffsets.length - 1))];
    }

    void selectWordOffset(int offset) {
        setSelection(displayOffsets[Math.max(0, Math.min(offset, displayOffsets.length - 1))]);
    }

    void setSyllables(String word, SortedSet<Integer> cuts) {
        int selected = wordSelectionStart();
        updating = true;
        chips.clear();
        List<Integer> ends = new ArrayList<>(cuts);
        ends.add(word.length());
        List<Integer> mapping = new ArrayList<>();
        mapping.add(0);
        displayOffsets = new int[word.length() + 1];
        StringBuilder display = new StringBuilder();
        int start = 0;
        for (int end : ends) {
            if (display.length() > 0) {
                display.append(' ');
                mapping.add(start);
            }
            int chipStart = display.length();
            display.append('\u00a0');
            mapping.add(start);
            for (int i = start; i < end; i++) {
                displayOffsets[i] = display.length();
                display.append(word.charAt(i));
                mapping.add(i + 1);
            }
            displayOffsets[end] = display.length();
            display.append('\u00a0');
            mapping.add(end);
            chips.add(new int[]{chipStart, display.length()});
            start = end;
        }
        originalOffsets = new int[mapping.size()];
        for (int i = 0; i < mapping.size(); i++) originalOffsets[i] = mapping.get(i);
        // Padding is presentation only: callers always read/write original word offsets.
        setFilters(new InputFilter[0]);
        setText(display.toString());
        setFilters(new InputFilter[]{(source, from, to, dest, dstart, dend) -> dest.subSequence(dstart, dend)});
        selectWordOffset(selected);
        updating = false;
        if (selectionChanged != null) selectionChanged.run();
        invalidate();
    }

    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (!updating && selectionChanged != null) selectionChanged.run();
    }

    @Override protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null) {
            int save = canvas.save();
            canvas.clipRect(getScrollX() + getCompoundPaddingLeft(), getScrollY(),
                    getScrollX() + getWidth() - getCompoundPaddingRight(), getScrollY() + getHeight());
            canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());
            for (int[] chip : chips) {
                selectionPath.reset();
                layout.getSelectionPath(chip[0], chip[1], selectionPath);
                selectionPath.computeBounds(bounds, true);
                bounds.top = layout.getLineTop(0) + dp(2);
                bounds.bottom = layout.getLineBottom(0) - dp(2);
                canvas.drawRoundRect(bounds, dp(8), dp(8), chipPaint);
            }
            canvas.restoreToCount(save);
        }
        // Native text, selection, caret and horizontal scrolling stay above the chips.
        super.onDraw(canvas);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

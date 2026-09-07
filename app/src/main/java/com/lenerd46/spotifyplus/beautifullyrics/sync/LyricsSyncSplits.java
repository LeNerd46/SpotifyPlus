package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.content.Context;
import android.graphics.Color;
import com.lenerd46.spotifyplus.References;
import android.icu.text.BreakIterator;
import android.widget.*;

import java.util.*;

final class LyricsSyncSplits extends LinearLayout {
    private final String word;
    private final SortedSet<Integer> cuts = new TreeSet<>();
    private final NavigableSet<Integer> positions = new TreeSet<>();
    private final LyricsSyllableInput cursor;
    private final Button toggle;
    private final Button clear;

    LyricsSyncSplits(Context context, LyricsSyncDraft.Word original) {
        super(context);
        setOrientation(VERTICAL);
        word = original.text;
        int offset = 0;

        for (int i = 0; i < original.parts.size() - 1; i++) {
            offset += original.parts.get(i).text.length();
            cuts.add(offset);
        }

        TextView hint = new TextView(context);
        hint.setText("Place the cursor where a syllable ends, then tap Split here. Use the arrows for precise placement.");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(16);
        addView(hint);

        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(word);
        positions.addAll(cuts);

        for (int end = iterator.first(); end != BreakIterator.DONE; end = iterator.next()) {
            positions.add(end);
        }

        cursor = new LyricsSyllableInput(context);
        cursor.setSelectionChanged(this::updateCursorAction);
        LyricsSyncDialog.styleInput(cursor);
        cursor.setTextSize(30);
        if (References.beautifulFont != null && References.beautifulFont.get() != null)
            cursor.setTypeface(References.beautifulFont.get());
        cursor.setContentDescription("Position the syllable split cursor in " + word);

        LinearLayout controls = new LinearLayout(context);

        Button left = LyricsSyncDialog.button(context, "‹", false, false);
        left.setContentDescription("Move cursor left");
        left.setOnClickListener(v -> moveCursor(false));

        Button right = LyricsSyncDialog.button(context, "›", false, false);
        right.setContentDescription("Move cursor right");
        right.setOnClickListener(v -> moveCursor(true));

        toggle = LyricsSyncDialog.button(context, "Split here", false, false);
        toggle.setOnClickListener(v -> {
            int at = cursor.wordSelectionStart();
            if (at <= 0 || at >= word.length() || !positions.contains(at)) return;
            if (!cuts.remove(at)) cuts.add(at);
            refresh();
        });

        LinearLayout field = new LinearLayout(context);
        field.setGravity(android.view.Gravity.CENTER_VERTICAL);
        field.addView(left, new LinearLayout.LayoutParams(dp(48), -2));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -2, 1);
        inputParams.setMargins(dp(8), 0, dp(8), 0);
        field.addView(cursor, inputParams);
        field.addView(right, new LinearLayout.LayoutParams(dp(48), -2));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.setMargins(0, dp(16), 0, dp(12));
        addView(field, fieldParams);

        clear = LyricsSyncDialog.button(context, "Join all syllables", false, false);
        clear.setOnClickListener(v -> {
            cuts.clear();
            refresh();
        });
        LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(0, -2, 1);
        splitParams.setMargins(0, 0, dp(8), 0);
        controls.addView(toggle, splitParams);
        controls.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        addView(controls);
        cursor.requestFocus();
        cursor.selectWordOffset(0);

        refresh();
    }

    String splitText() {
        StringBuilder result = new StringBuilder();
        int start = 0;

        for (int end : cuts) {
            result.append(word, start, end).append('|');
            start = end;
        }

        return result.append(word, start, word.length()).toString();
    }

    private void refresh() {
        updateCursorAction();
        clear.setEnabled(!cuts.isEmpty());
        cursor.setSyllables(word, cuts);
        cursor.setContentDescription("Position the syllable split cursor. Syllables: " + splitText().replace("|", ", "));
    }

    private void moveCursor(boolean right) {
        Integer next = right ? positions.higher(cursor.wordSelectionStart()) : positions.lower(cursor.wordSelectionStart());

        if (next != null) {
            cursor.requestFocus();
            cursor.selectWordOffset(next);
        }
    }

    private void updateCursorAction() {
        if (cursor == null || toggle == null) return;

        int at = cursor.wordSelectionStart();
        toggle.setEnabled(at > 0 && at < word.length() && positions.contains(at) && at == cursor.wordSelectionEnd());
        toggle.setText(cuts.contains(at) ? "Join here" : "Split here");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

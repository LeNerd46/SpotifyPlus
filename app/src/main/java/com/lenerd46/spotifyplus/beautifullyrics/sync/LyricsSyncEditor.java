package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.FlexWrap;
import com.google.gson.JsonObject;
import com.lenerd46.spotifyplus.beautifullyrics.entities.GradientTextView;
import com.lenerd46.spotifyplus.beautifullyrics.entities.SyllableVocals;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.SyllableMetadata;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.beautifullyrics.entities.interludes.InterludeVisual;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.Interlude;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.TimeMetadata;
import com.lenerd46.spotifyplus.beautifullyrics.sync.LyricsSyncDraft.*;

import java.util.*;
import java.io.IOException;

import okhttp3.*;

public final class LyricsSyncEditor extends LinearLayout {
    public interface Host {
        void cancel();

        void saved(JsonObject lyrics);
    }

    private enum Stage {SPLIT, READY, RECORD, FINISHING, EDIT, PREVIEW}

    private final int lyricFontSize;
    private final int lyricLineSpacing;
    private final View songBackground;
    private final LyricsSyncDraft draft;
    private final LyricsSyncPlayer player;
    private final Host host;

    private final String trackId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TextView status;
    private final TextView elapsedTime;
    private final TextView totalTime;
    private final LinearLayout body;
    private final FlexboxLayout actions;
    private final FlexboxLayout interludeRow;

    private InterludeVisual interludeVisual;
    private double[] displayedGap;
    private List<double[]> previewGaps = Collections.emptyList();

    private final ScrollView scroll;
    private final SeekBar seek;
    private final Button play;
    private final Button back;
    private final Button forward;

    private final Map<Part, GradientTextView> labels = new LinkedHashMap<>();
    private final Map<Part, Vocal> partVocals = new IdentityHashMap<>();
    private final Map<Part, LyricsSyncWordVisual> wordVisuals = new IdentityHashMap<>();
    private final Map<Vocal, View> rows = new IdentityHashMap<>();
    private final Map<Vocal, SyllableVocals> previewVocals = new LinkedHashMap<>();

    private double lastPreviewPosition = Double.NaN;
    private final List<Part> queue = new ArrayList<>();
    private final Map<Part, Integer> recordingIndexes = new IdentityHashMap<>();

    private LyricsSyncPass pass;
    private Stage stage = Stage.SPLIT;

    private int index = -1;
    private int pointer = -1;

    private double requestedPosition = -1;
    private double previewEnd = -1;
    private boolean disposed, seeking, armed, saving, initialPass, draggingSeek;
    private boolean previewEntireSong;
    private boolean helpShown;
    private boolean correctLineTimings = true;
    private long seekDeadline;

    private Runnable afterSeek;
    private LyricsSyncDialog dialog;
    private Call submission;
    private Vocal scrolledVocal;

    public LyricsSyncEditor(Context context, JsonObject source, String trackId, LyricsSyncPlayer player, Host host, int lyricFontSize, int lyricLineSpacing, View songBackground) {
        super(context);

        this.lyricFontSize = lyricFontSize;
        this.lyricLineSpacing = lyricLineSpacing;
        this.songBackground = songBackground;
        this.draft = new LyricsSyncDraft(source);
        this.trackId = trackId;
        this.player = player;
        this.host = host;

        setOrientation(VERTICAL);
        setPadding(dp(16), 0, dp(16), dp(16));
        setClipChildren(true);
        setClipToPadding(true);

        setOnApplyWindowInsetsListener((v, insets) -> {
            post(this::updateBottomPadding);
            return insets;
        });

        status = text("Tap a word to split it into syllables. Backing vocals appear below each line.", 14);
        addView(status);

        interludeRow = new FlexboxLayout(context);
        interludeRow.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(56)));

        scroll = new RecordingScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);

        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        scroll.addView(body);

        addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout timeline = new LinearLayout(context);
        timeline.setGravity(Gravity.CENTER_VERTICAL);

        elapsedTime = text("0:00", 13);
        elapsedTime.setSingleLine(true);
        elapsedTime.setContentDescription("Playback position");

        totalTime = text("—", 13);
        totalTime.setSingleLine(true);
        totalTime.setGravity(Gravity.END);
        totalTime.setContentDescription("Song duration");
        timeline.addView(elapsedTime);

        seek = new SeekBar(context);
        seek.setMax((int) Math.min(Integer.MAX_VALUE, player.durationMs()));

        timeline.addView(seek, new LinearLayout.LayoutParams(0, dp(40), 1));
        timeline.addView(totalTime);
        addView(timeline);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) {
                draggingSeek = true;
            }

            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) elapsedTime.setText(format(value / 1000d));
            }

            public void onStopTrackingTouch(SeekBar bar) {
                draggingSeek = false;
                player.seek(bar.getProgress() / 1000d);
            }
        });

        LinearLayout transport = new LinearLayout(context);
        transport.setGravity(Gravity.CENTER);

        back = button("−5 s", () -> player.seek(player.position() - 5));
        play = button("Play", () -> {
            invalidateHold();
            if (player.playing()) player.pause();
            else player.play();
        });

        forward = button("+5 s", () -> player.seek(player.position() + 5));

        for (Button b : new Button[]{back, play, forward}) {
            transport.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }

        addView(transport);

        actions = new FlexboxLayout(context);
        actions.setFlexWrap(FlexWrap.WRAP);
        addView(actions);

        player.pause();
        showSplit();

        handler.post(tick);
    }

    private TextView text(String value, float size) {
        TextView t = new TextView(getContext());

        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(0, dp(5), 0, dp(5));

        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = LyricsSyncDialog.button(getContext(), label, false, false);
        b.setOnClickListener(v -> {
            if (!disposed && !saving) action.run();
        });

        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(true);

        super.dispatchTouchEvent(event);

        if ((event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) && getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(false);

        return true;
    }

    public void showHelpIfNeeded() {
        if (disposed || helpShown || stage != Stage.SPLIT || (dialog != null && dialog.isShowing())) return;

        android.content.SharedPreferences preferences = getContext().getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        if (preferences.getBoolean("lyrics_sync_hide_guide", false)) return;

        helpShown = true;
        dialog = new LyricsSyncDialog(getContext(), "Sync your lyrics", text("1. Prepare: tap a word to split it with the cursor. The highlighted sections in the field show each syllable.\n\n" + "2. Record: tap Confirm, then Start. The song restarts. Press and hold the screen while each syllable is sung. Then release to move on. So when the word starts being sung, press down on the screen. Then when it is finished being sung, you release your finger from the screen.\n\n" + "3. Refine: undo mistakes, redo lines, or tap syllables to adjust their times. Use All −10 ms / All +10 ms to shift the song. Sync backing vocals separately if you want them included.\n\n" + "4. Preview, then Save to share your sync with the community.", 15), "Got it", "Do not show again", false, () -> dialog.dismiss());

        dialog.setSecondaryAction(() -> {
            preferences.edit().putBoolean("lyrics_sync_hide_guide", true).apply();
            dialog.dismiss();
        });

        showDialog();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != scroll) return super.drawChild(canvas, child, drawingTime);

        int save = canvas.save();
        canvas.clipRect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());

        boolean drawn = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);

        return drawn;
    }

    private boolean recordingInputLocked() {
        return stage == Stage.RECORD || stage == Stage.FINISHING;
    }

    private final class RecordingScrollView extends ScrollView {
        private boolean recordingGesture;

        RecordingScrollView(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) recordingGesture = recordingInputLocked();

            if (recordingGesture || recordingInputLocked()) {
                getParent().requestDisallowInterceptTouchEvent(true);
                handleRecordingTouch(event);

                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    recordingGesture = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                }

                return true;
            }

            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            return recordingInputLocked() || super.onGenericMotionEvent(event);
        }

        @Override
        public boolean executeKeyEvent(KeyEvent event) {
            int key = event.getKeyCode();
            boolean scrollKey = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN || key == KeyEvent.KEYCODE_MOVE_HOME || key == KeyEvent.KEYCODE_MOVE_END || key == KeyEvent.KEYCODE_SPACE;

            return (recordingInputLocked() && scrollKey) || super.executeKeyEvent(event);
        }

        @Override
        public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
            if (recordingInputLocked() && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId() || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()))
                return false;

            return super.performAccessibilityAction(action, arguments);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        requestApplyInsets();
        post(this::updateBottomPadding);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateBottomPadding();
    }

    private void updateBottomPadding() {
        if (!isAttachedToWindow() || getHeight() == 0) return;

        WindowInsets insets = getRootWindowInsets();
        int bottomInset = 0;

        if (insets != null)
            bottomInset = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).bottom : insets.getStableInsetBottom();
        int[] rootLocation = new int[2], location = new int[2];

        View root = getRootView();
        root.getLocationOnScreen(rootLocation);

        getLocationOnScreen(location);
        int safeBottom = rootLocation[1] + root.getHeight() - bottomInset;

        Rect visible = new Rect();
        getWindowVisibleDisplayFrame(visible);

        if (visible.bottom > visible.top) safeBottom = Math.min(safeBottom, visible.bottom);

        int padding = dp(16) + Math.max(0, location[1] + getHeight() - safeBottom);
        if (getPaddingBottom() != padding) setPadding(dp(16), 0, dp(16), padding);
    }

    private void action(String label, Runnable action) {
        FlexboxLayout.LayoutParams p = new FlexboxLayout.LayoutParams(-2, -2);

        p.setFlexGrow(1);
        p.setMinWidth(dp(90));
        p.setMargins(dp(2), dp(4), dp(2), 0);

        actions.addView(button(label, action), p);
    }

    private void reset(Stage next, String instructions) {
        stage = next;
        status.setText(instructions);
        actions.removeAllViews();

        if (next == Stage.RECORD) scroll.fling(0);

        status.setVisibility(next == Stage.READY ? VISIBLE : GONE);
        showInterlude(null, 0);

        boolean controls = next != Stage.RECORD && next != Stage.READY;

        seek.setEnabled(controls);
        back.setEnabled(controls);
        forward.setEnabled(controls);
        play.setEnabled(next != Stage.READY);

        scrolledVocal = null;
    }

    private void showSplit() {
        reset(Stage.SPLIT, "Tap a word to add or remove syllable splits.");
        render(true, true);
        action("Confirm", () -> ready(null));
        action("Cancel", host::cancel);
    }

    private void render(boolean background, boolean split) {
        for (LyricsSyncWordVisual visual : wordVisuals.values()) {
            visual.dispose();
        }

        wordVisuals.clear();
        body.removeAllViews();

        displayedGap = null;
        interludeVisual = null;

        interludeRow.removeAllViews();
        labels.clear();
        partVocals.clear();
        rows.clear();
        previewVocals.clear();

        lastPreviewPosition = Double.NaN;

        for (Line line : draft.lines) {
            if (stage == Stage.PREVIEW && line.lead.words.isEmpty() && line.background.stream().noneMatch(Vocal::hasTimings))
                continue;

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(VERTICAL);
            row.setPadding(dp(9), dp(lyricLineSpacing), dp(19), 0);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            addVocal(row, line.lead, false, split);

            if (stage == Stage.EDIT && !line.lead.words.isEmpty()) {
                FlexboxLayout buttons = new FlexboxLayout(getContext());
                buttons.setFlexWrap(FlexWrap.WRAP);
                buttons.addView(button("Redo line", () -> ready(line.lead)));
                buttons.addView(button("Preview line", () -> preview(line)));
                row.addView(buttons);
            }

            if (background) for (Vocal vocal : line.background) {
                if (stage == Stage.PREVIEW && !vocal.hasTimings()) continue;
                addVocal(row, vocal, true, split);

                if (stage == Stage.EDIT) {
                    row.addView(button(vocal.complete() ? "Redo backing vocals" : "Sync backing vocals", () -> ready(vocal)));

                    if (vocal.hasTimings()) row.addView(button("Clear backing timings", () -> {
                        vocal.clearTimings();

                        int y = scroll.getScrollY();
                        render(true, false);

                        scroll.post(() -> scroll.scrollTo(0, y));
                    }));
                }
            }

            body.addView(row);
        }
    }

    private void addVocal(LinearLayout row, Vocal vocal, boolean background, boolean split) {
        rows.put(vocal, row);

        FlexboxLayout words = new FlexboxLayout(getContext());
        words.setFlexWrap(FlexWrap.WRAP);
        words.setClipChildren(false);
        words.setClipToPadding(false);

        if (stage == Stage.PREVIEW && !vocal.words.isEmpty() && vocal.complete()) {
            List<SyllableMetadata> syllables = new ArrayList<>();
            for (Word word : vocal.words) {
                for (int i = 0; i < word.parts.size(); i++) {
                    Part part = word.parts.get(i);
                    SyllableMetadata syllable = new SyllableMetadata();

                    syllable.text = part.text;
                    syllable.startTime = part.start;
                    syllable.endTime = part.end;
                    syllable.isPartOfWord = i < word.parts.size() - 1;

                    syllables.add(syllable);
                }
            }

            row.addView(words);
            previewVocals.put(vocal, new SyllableVocals(words, syllables, background, false, lineFor(vocal).opposite, (Activity) getContext(), lyricFontSize));

            return;
        }

        for (Word word : vocal.words) {
            if (split) {
                LinearLayout group = new LinearLayout(getContext());
                group.setLayoutParams(wordParams());

                for (Part part : word.parts) {
                    TextView label = text(part.text, background ? 18 : lyricFontSize);
                    styleLyric(label);

                    if (word.parts.size() > 1) styleSplitPart(label);
                    group.addView(label);
                }

                group.setContentDescription("Split word " + word.splitText().replace("|", ", "));
                group.setOnClickListener(v -> splitWord(word));
                words.addView(group);
            } else {
                LinearLayout pieces = new LinearLayout(getContext());
                pieces.setLayoutParams(wordParams());
                pieces.setClipChildren(false);
                pieces.setClipToPadding(false);

                for (int i = 0; i < word.parts.size(); i++) {
                    Part part = word.parts.get(i);
                    GradientTextView label = new GradientTextView(getContext());

                    label.setText(part.text);
                    label.setTextColor(Color.WHITE);
                    label.setTextSize(background ? 18 : lyricFontSize);
                    styleLyric(label);

                    labels.put(part, label);
                    partVocals.put(part, vocal);

                    if (word.parts.size() > 1) styleSplitPart(label);
                    wordVisuals.put(part, new LyricsSyncWordVisual(label, background));
                    label.setContentDescription(part.text + (stage == Stage.EDIT ? ", adjust timing" : ""));

                    if (stage == Stage.EDIT) label.setOnClickListener(v -> timing(vocal, part));
                    pieces.addView(label);
                }

                words.addView(pieces);
            }
        }

        row.addView(words);
    }

    private void styleLyric(TextView label) {
        label.setPadding(0, 0, dp(1), 0);

        if (References.beautifulFont != null && References.beautifulFont.get() != null) {
            label.setTypeface(References.beautifulFont.get());
        }
    }

    private void styleSplitPart(TextView label) {
        android.graphics.drawable.GradientDrawable chip = new android.graphics.drawable.GradientDrawable();
        chip.setColor(0x20ffffff);
        chip.setCornerRadius(dp(4));

        label.setBackground(chip);
        label.setPadding(dp(2), 0, dp(2), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(2), 0);
        label.setLayoutParams(params);
    }

    private void showDialog() {
        dialog.setOnDismissListener(d -> {
            if (!disposed) setVisibility(VISIBLE);
        });

        dialog.setSongBackground(songBackground);
        dialog.show();
        setVisibility(INVISIBLE);
    }

    private FlexboxLayout.LayoutParams wordParams() {
        FlexboxLayout.LayoutParams p = new FlexboxLayout.LayoutParams(-2, -2);
        p.setMargins(0, 0, dp(5), 0);

        return p;
    }

    private void splitWord(Word word) {
        LyricsSyncSplits panel = new LyricsSyncSplits(getContext(), word);

        dialog = new LyricsSyncDialog(getContext(), "Split “" + word.text + "”", panel, "Save", "Cancel", false, () -> {
            try {
                word.split(panel.splitText());
                dialog.dismiss();

                int y = scroll.getScrollY();
                render(true, true);
                scroll.post(() -> scroll.scrollTo(0, y));
            } catch (IllegalArgumentException e) {
                dialog.error(e.getMessage());
            }
        });

        showDialog();
    }

    private Line lineFor(Vocal vocal) {
        for (Line l : draft.lines) {
            if (l.lead == vocal || l.background.contains(vocal)) {
                return l;
            }
        }

        throw new IllegalArgumentException();
    }

    private Vocal vocalFor(Part part) {
        for (Line l : draft.lines) {
            if (l.lead.parts().contains(part)) return l.lead;
            for (Vocal b : l.background)
                if (b.parts().contains(part)) {
                    return b;
                }
        }

        return null;
    }

    private void ready(Vocal only) {
        player.pause();
        invalidateHold();
        queue.clear();
        recordingIndexes.clear();

        index = 0;
        initialPass = only == null;

        if (only == null) {
            for (Line l : draft.lines) {
                queue.addAll(l.lead.parts());
            }
        } else {
            queue.addAll(only.parts());
        }

        if (queue.isEmpty()) {
            edit();
            return;
        }

        for (int i = 0; i < queue.size(); i++) {
            recordingIndexes.put(queue.get(i), i);
        }

        pass = new LyricsSyncPass(queue);
        reset(Stage.READY, "Press Start when ready. Hold anywhere in the lyrics for each word or syllable, then release to advance. Split syllables appear in separate chips.");
        render(only != null, false);

        double start = only == null ? 0 : Math.max(0, lineFor(only).lead.start(lineFor(only).originalStart) - (lineFor(only).lead == only ? 3 : 5));

        action("Start", () -> {
            reset(Stage.RECORD, "Starting playback…");
            action("Undo last syllable", this::undo);
            action("Stop and edit", () -> {
                cancelSeek();
                player.pause();
                invalidateHold();

                if (initialPass) commitPass();
                edit();
            });
            jumpAndPlay(start, () -> {
                armed = true;
                status.setText("Hold for the highlighted syllable, then release.");
            });
            focus(queue.get(0));
        });

        action("Back", () -> {
            if (only == null) showSplit();
            else edit();
        });
    }

    private void jumpAndPlay(double seconds, Runnable done) {
        invalidateHold();
        requestedPosition = seconds;
        seekDeadline = SystemClock.elapsedRealtime() + 6000;
        afterSeek = done;
        seeking = true;
        armed = false;
        player.pause();
        player.seek(seconds);
    }

    private void cancelSeek() {
        seeking = false;
        afterSeek = null;
        armed = false;
    }

    private void invalidateHold() {
        if (pass != null) pass.cancelHold();
        pointer = -1;

        updateRecordingVisuals();
    }

    private void updateRecordingVisuals() {
        if (stage != Stage.READY && stage != Stage.RECORD && stage != Stage.FINISHING) return;

        for (Map.Entry<Part, LyricsSyncWordVisual> entry : wordVisuals.entrySet()) {
            Integer at = recordingIndexes.get(entry.getKey());
            LyricsSyncWordVisual.State state = LyricsSyncWordVisual.State.IDLE;

            if (at != null) {
                if (at < index) state = LyricsSyncWordVisual.State.SUNG;
                else if (at == index)
                    state = pass != null && pass.holding() ? LyricsSyncWordVisual.State.HELD : LyricsSyncWordVisual.State.NEXT;
            }

            entry.getValue().setState(state);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean handleRecordingTouch(MotionEvent event) {
        if (stage != Stage.RECORD || seeking || !armed || index >= queue.size()) return true;

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            double position = player.positionAtUptime(event.getEventTime());

            if ((player.hasKnownDuration() && position >= player.durationMs() / 1000d) || !pass.press(position, player.playing()))
                return true;

            pointer = event.getPointerId(0);
            updateRecordingVisuals();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && event.getPointerId(0) == pointer) {
            double end = player.positionAtUptime(event.getEventTime());

            if (pass.release(end, player.playing())) {
                index = pass.index();
                invalidateHold();

                if (index == queue.size()) {
                    commitPass();
                    player.pause();
                    stage = Stage.FINISHING;
                    actions.removeAllViews();
                    play.setEnabled(false);
                    handler.postDelayed(() -> {
                        if (!disposed && stage == Stage.FINISHING) edit();
                    }, 260);
                } else {
                    focus(queue.get(index));
                }
            } else {
                invalidateHold();
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            invalidateHold();
        }

        return true;
    }

    private void commitPass() {
        pass.commit(initialPass);
    }

    private void undo() {
        if (index <= 0 || seeking) return;
        invalidateHold();

        double start = pass.undo();
        index = pass.index();

        jumpAndPlay(Math.max(0, start - 2), () -> armed = true);
        focus(queue.get(index));
    }

    private void focus(Part part) {
        Vocal vocal = vocalFor(part);
        View row = rows.get(vocal);

        if (row != null && scrolledVocal != vocal) {
            scrolledVocal = vocal;
            scroll.post(() -> scroll.smoothScrollTo(0, row.getTop()));
        }

        scroll.setContentDescription("Hold anywhere in the lyrics to sync “" + part.text + "”, syllable " + (index + 1) + " of " + queue.size());
    }

    private void edit() {
        cancelSeek();
        invalidateHold();
        reset(Stage.EDIT, "");
        render(true, false);

        action("Preview song", () -> preview(null));
        action("Save", this::confirmSave);
        action("Discard", this::requestCancel);
        action("All −10 ms", () -> shiftSong(-10));

        ((FlexboxLayout.LayoutParams) actions.getChildAt(3).getLayoutParams()).setWrapBefore(true);
        action("All +10 ms", () -> shiftSong(10));
    }

    private void shiftSong(int milliseconds) {
        try {
            draft.shiftTimings(milliseconds, player.hasKnownDuration() ? player.durationMs() / 1000d : Double.POSITIVE_INFINITY);
            Toast.makeText(getContext(), "All synced vocals moved 10 ms " + (milliseconds < 0 ? "earlier" : "later"), Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            message(e.getMessage());
        }
    }

    private void timing(Vocal vocal, Part part) {
        player.pause();

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        EditText start = timingInput(panel, "Start (milliseconds)", part.start), end = timingInput(panel, "End (milliseconds)", part.end);

        dialog = new LyricsSyncDialog(getContext(), "Timing: " + part.text, panel, "Save", "Cancel", false, () -> {
            try {
                double a = Long.parseLong(start.getText().toString()) / 1000d, b = Long.parseLong(end.getText().toString()) / 1000d;
                List<Part> parts = vocal.parts();
                int at = parts.indexOf(part);

                if (a < 0 || b <= a || (player.hasKnownDuration() && b > player.durationMs() / 1000d))
                    throw new IllegalArgumentException("Start must be before end, within the song.");

                if ((at > 0 && parts.get(at - 1).end > a) || (at + 1 < parts.size() && parts.get(at + 1).start >= 0 && b > parts.get(at + 1).start))
                    throw new IllegalArgumentException("This timing overlaps a neighboring syllable.");

                part.start = a;
                part.end = b;

                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                dialog.error(e instanceof NumberFormatException ? "Enter milliseconds as a whole number." : e.getMessage());
            }
        });
        showDialog();
    }

    private EditText timingInput(LinearLayout panel, String label, double value) {
        panel.addView(text(label, 14));
        LinearLayout row = new LinearLayout(getContext());
        EditText input = new EditText(getContext());

        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        LyricsSyncDialog.styleInput(input);
        input.setContentDescription(label);

        if (value >= 0) input.setText(String.valueOf(Math.round(value * 1000)));

        row.addView(button("−10", () -> nudge(input, -10)));
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button("+10", () -> nudge(input, 10)));
        panel.addView(row);

        return input;
    }

    private void nudge(EditText input, int delta) {
        try {
            input.setText(String.valueOf(Math.max(0, Long.parseLong(input.getText().toString()) + delta)));
        } catch (NumberFormatException ignored) {
            input.setText("0");
        }
    }

    private void preview(Line only) {
        if (only == null && !draft.complete()) {
            message("Finish lead timings, and finish or clear partially synced backing vocals before previewing.");
            return;
        }

        reset(Stage.PREVIEW, "Watch the highlighting with your timings.");
        render(true, false);
        previewGaps = new ArrayList<>();
        List<double[]> intervals = new ArrayList<>();

        for (Line l : draft.lines) {
            List<Vocal> vocals = new ArrayList<>(l.background);
            vocals.add(l.lead);
            for (Vocal v : vocals)
                if (v.hasTimings() && v.complete()) {
                    List<Part> parts = v.parts();
                    intervals.add(new double[]{parts.get(0).start, parts.get(parts.size() - 1).end});
                }
        }

        intervals.sort(Comparator.comparingDouble(v -> v[0]));
        double previousEnd = 0;

        for (double[] interval : intervals) {
            if (interval[0] - previousEnd >= 2) previewGaps.add(new double[]{previousEnd, interval[0]});
            previousEnd = Math.max(previousEnd, interval[1]);
        }

        double start = only == null ? 0 : Math.max(0, only.lead.start(only.originalStart) - 2);
        previewEntireSong = only == null;
        previewEnd = only == null ? Double.POSITIVE_INFINITY : only.originalEnd;

        if (only != null) {
            for (Part p : only.lead.parts()) previewEnd = Math.max(previewEnd, p.end);
            for (Vocal b : only.background) for (Part p : b.parts()) previewEnd = Math.max(previewEnd, p.end);
            previewEnd += 1;
        }

        action("Back to editor", () -> {
            cancelSeek();
            player.pause();
            edit();
        });

        jumpAndPlay(start, () -> {
        });
    }

    public void requestCancel() {
        if (saving) return;
        if (dialog != null && dialog.isShowing()) return;

        if (seeking) {
            cancelSeek();
            if (stage == Stage.RECORD && initialPass) commitPass();
            edit();
        }

        invalidateHold();
        player.pause();

        dialog = new LyricsSyncDialog(getContext(), "Discard this sync?", text("All syllable splits and timings for this song will be lost.", 16), "Discard", "Keep editing", true, () -> {
            dialog.dismiss();
            host.cancel();
        });

        showDialog();
    }

    private void confirmSave() {
        player.pause();
        long unsynced = draft.unsyncedBackingCount();
        if (unsynced > 0) {
            boolean partial = draft.lines.stream().flatMap(line -> line.background.stream())
                    .anyMatch(vocal -> vocal.hasTimings() && !vocal.complete());
            String warning = "You have not synced all the backing vocals. " + unsynced
                    + (unsynced == 1 ? " backing vocal is" : " backing vocals are")
                    + " still incomplete. Untimed backing vocals will be left out of the saved lyrics.";
            if (partial) warning += " Partially synced backing vocals must be finished or have their timings cleared before saving.";
            dialog = new LyricsSyncDialog(getContext(), "Unsynced backing vocals", text(warning, 16),
                    "Continue", "Go back", false, () -> {
                        dialog.dismiss();
                        showSaveConfirmation();
                    });
            showDialog();
            return;
        }
        showSaveConfirmation();
    }

    private void showSaveConfirmation() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.addView(text("You are about the submit these lyrics publicly. These lyrics will be available for everyone", 16));
        panel.addView(text("You can adjust the timings of the lines to match the original line timings if you would like. This will take all of your timings, and line up the beginning with the original line by line timing. This is mainly intended for people using BlueTooth devices. This should compensate for any audio delay.", 16));

        LinearLayout correctionRow = new LinearLayout(getContext());
        correctionRow.setGravity(Gravity.CENTER_VERTICAL);
        correctionRow.setPadding(0, dp(16), 0, dp(8));
        TextView correctionLabel = text("Correct using original line timings", 14);
        correctionLabel.setPadding(0, 0, dp(16), 0);
        correctionRow.addView(correctionLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Switch correction = LyricsSyncDialog.toggle(getContext());
        correction.setContentDescription("Correct using original line timings");
        correction.setChecked(correctLineTimings);
        correction.setOnCheckedChangeListener((button, checked) -> correctLineTimings = checked);
        correctionRow.setOnClickListener(v -> correction.performClick());
        correctionRow.addView(correction, new LinearLayout.LayoutParams(-2, dp(48)));
        panel.addView(correctionRow);

        dialog = new LyricsSyncDialog(getContext(), "Save community sync?", panel, "Save", "Keep editing", false, this::save);
        showDialog();
    }

    private void save() {
        JsonObject result;

        try {
            result = draft.toJson(correctLineTimings);
            if (player.hasKnownDuration() && result.get("EndTime").getAsDouble() > player.durationMs() / 1000d) {
                throw new IllegalArgumentException("Timings extend past the song. Adjust them or turn off line timing correction.");
            }
        } catch (IllegalArgumentException e) {
            dialog.error(e.getMessage());
            return;
        }

        dialog.dismiss();
        player.pause();
        saving = true;
        status.setText("Saving community sync…");
        status.setVisibility(VISIBLE);

        setEnabledRecursively(this, false);
        Request request = new Request.Builder().url("https://spotifyplus-api.devon-shoutz.workers.dev/api/lyrics/" + trackId).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), result.toString())).build();
        submission = new OkHttpClient().newCall(request);

        submission.enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                handler.post(() -> saveFailed("Could not save. Your draft is still here; check your connection and retry."));
            }

            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        handler.post(() -> {
                            if (!disposed) {
                                saving = false;
                                host.saved(result);
                            }
                        });
                    } else {
                        String detail = "Server rejected the sync (" + r.code() + "). Your draft is still here.";

                        if (r.body() != null) try {
                            JsonObject error = com.google.gson.JsonParser.parseString(r.body().string()).getAsJsonObject();
                            if (error.has("error")) detail += " " + error.get("error").getAsString();
                        } catch (Exception ignored) {
                        }

                        String message = detail;
                        handler.post(() -> saveFailed(message));
                    }
                }
            }
        });
    }

    private void saveFailed(String message) {
        if (disposed) return;

        saving = false;
        setEnabledRecursively(this, true);

        status.setText(message);
        status.setVisibility(VISIBLE);
    }

    private void setEnabledRecursively(View v, boolean enabled) {
        v.setEnabled(enabled);

        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursively(group.getChildAt(i), enabled);
        }
    }

    private void message(String text) {
        Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
        status.setText(text);
    }

    private String format(double seconds) {
        int s = (int) Math.max(0, seconds);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void showInterlude(double[] gap, double now) {
        if (gap == null) {
            if (displayedGap != null) {
                body.removeView(interludeRow);
                interludeRow.removeAllViews();
                displayedGap = null;
                interludeVisual = null;
                scrolledVocal = null;

                if (stage == Stage.RECORD && index < queue.size()) focus(queue.get(index));
            }

            return;
        }
        if (displayedGap != gap) {
            displayedGap = gap;
            body.removeView(interludeRow);
            interludeRow.removeAllViews();
            View nextRow = null;

            if (stage == Stage.RECORD && index < queue.size()) {
                nextRow = rows.get(vocalFor(queue.get(index)));
            } else {
                for (Line line : draft.lines) {
                    double start = line.lead.start(line.originalStart);

                    for (Vocal vocal : line.background) {
                        if (vocal.hasTimings()) {
                            start = Math.min(start, vocal.start(start));
                        }
                    }

                    if (start >= gap[1] && rows.get(line.lead) != null) {
                        nextRow = rows.get(line.lead);
                        break;
                    }
                }
            }

            int before = nextRow == null ? body.getChildCount() : body.indexOfChild(nextRow);
            body.addView(interludeRow, Math.max(0, before));

            Interlude interlude = new Interlude();
            interlude.time = new TimeMetadata();
            interlude.time.startTime = gap[0];
            interlude.time.endTime = gap[1];

            interludeVisual = new InterludeVisual(interludeRow, interlude, (Activity) getContext(), true);
            scroll.post(() -> {
                if (!disposed && displayedGap == gap) scroll.smoothScrollTo(0, interludeRow.getTop());
            });
        }

        interludeVisual.animate(now, 1d / 30, false);
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (disposed) return;

            double now = player.position();
            double[] activeGap = null;

            if (seeking) {
                if (Math.abs(now - requestedPosition) < .45) {
                    seeking = false;
                    player.play();

                    Runnable done = afterSeek;
                    afterSeek = null;

                    if (done != null) done.run();
                } else if (SystemClock.elapsedRealtime() > seekDeadline) {
                    cancelSeek();
                    player.pause();

                    if (stage == Stage.RECORD && initialPass) commitPass();

                    edit();
                    message("Spotify did not confirm the seek. Try the line again.");
                }
            }

            boolean knownDuration = player.hasKnownDuration();
            long durationMs = player.durationMs();

            if (!draggingSeek) {
                seek.setMax((int) Math.min(Integer.MAX_VALUE, durationMs));
                seek.setProgress((int) Math.max(0, now * 1000));
                elapsedTime.setText(format(now));
                totalTime.setText(knownDuration ? format(durationMs / 1000d) : "—");
            }

            play.setText(player.playing() ? "Pause" : "Play");
            if (stage == Stage.RECORD) {
                if (!player.playing()) invalidateHold();
                if (!seeking && knownDuration && now >= durationMs / 1000d - .02 && index < queue.size()) {
                    if (initialPass) commitPass();

                    edit();
                    message("Playback ended. Use Redo line to finish any missing timings.");
                } else if (!seeking) {
                    String hint = player.playing() ? "Hold for the highlighted syllable, then release." : "Playback paused. Press Play to continue.";

                    if (!pass.holding() && (index == 0 || vocalFor(queue.get(index)) != vocalFor(queue.get(index - 1))))
                        for (double[] gap : draft.interludes) {
                            if (now >= gap[0] && now < gap[1]) {
                                activeGap = gap;
                            }
                        }

                    status.setText(hint);
                }
            }

            if (stage == Stage.PREVIEW && !seeking) {
                for (double[] gap : previewGaps) {
                    if (now >= gap[0] && now < gap[1]) {
                        activeGap = gap;
                    }
                }
            }

            showInterlude(activeGap, now);
            updateRecordingVisuals();
            Part previewFocus = null;

            if (stage == Stage.PREVIEW) {
                boolean immediate = Double.isNaN(lastPreviewPosition) || Math.abs(now - lastPreviewPosition) > .25;

                for (Map.Entry<Vocal, SyllableVocals> entry : previewVocals.entrySet()) {
                    entry.getValue().animate(now, 1d / 30, immediate);

                    if (entry.getValue().isActive() && previewFocus == null) {
                        previewFocus = entry.getKey().parts().get(0);
                    }
                }

                lastPreviewPosition = now;
            }

            Map<Vocal, Double> vocalEnds = new IdentityHashMap<>();

            if (stage == Stage.PREVIEW || stage == Stage.EDIT) for (Vocal vocal : rows.keySet()) {
                vocalEnds.put(vocal, LyricsSyncPreview.end(vocal));
            }

            for (Map.Entry<Part, GradientTextView> entry : labels.entrySet()) {
                if (stage == Stage.RECORD || stage == Stage.READY || stage == Stage.FINISHING) continue;

                Part part = entry.getKey();
                GradientTextView view = entry.getValue();

                boolean active = part.start >= 0 && now >= part.start && now < part.end;
                boolean sung = LyricsSyncPreview.finished(part, vocalEnds.getOrDefault(partVocals.get(part), Double.POSITIVE_INFINITY), now);

                view.setGradientColors(sung ? new int[]{0x64ffffff, 0x64ffffff} : new int[]{Color.WHITE, 0x78ffffff});
                float progress = part.start < 0 ? 0 : (float) (100 * Math.max(0, Math.min(1, (now - part.start) / Math.max(.001, part.end - part.start))));

                view.setProgress(progress);
                if (active && stage == Stage.PREVIEW && previewFocus == null) previewFocus = part;
            }

            if (previewFocus != null) focus(previewFocus);
            double previewLimit = previewEntireSong ? (knownDuration ? durationMs / 1000d : Double.POSITIVE_INFINITY) : previewEnd;

            if (stage == Stage.PREVIEW && !seeking && now >= previewLimit) {
                player.pause();
                edit();
            }

            handler.postDelayed(this, 33);
        }
    };

    public void dispose() {
        disposed = true;
        cancelSeek();
        invalidateHold();
        handler.removeCallbacksAndMessages(null);

        for (LyricsSyncWordVisual visual : wordVisuals.values()) {
            visual.dispose();
        }

        if (submission != null) submission.cancel();
        if (dialog != null) dialog.dismiss();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus && stage == Stage.RECORD && !disposed) {
            invalidateHold();
            player.pause();

            if (seeking) {
                cancelSeek();
                if (initialPass) commitPass();

                edit();
            }
        }
    }
}

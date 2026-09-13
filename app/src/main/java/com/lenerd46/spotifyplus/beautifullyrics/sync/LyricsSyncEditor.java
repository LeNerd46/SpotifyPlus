package com.lenerd46.spotifyplus.beautifullyrics.sync;

import com.lenerd46.spotifyplus.R;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.annotation.NonNull;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.FlexWrap;
import com.google.gson.JsonObject;
import com.lenerd46.spotifyplus.beautifullyrics.entities.GradientTextView;
import com.lenerd46.spotifyplus.beautifullyrics.entities.SyllableVocals;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.SyllableMetadata;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyUser;
import com.lenerd46.spotifyplus.Utils;
import com.lenerd46.spotifyplus.beautifullyrics.entities.interludes.InterludeVisual;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.Interlude;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.TimeMetadata;
import com.lenerd46.spotifyplus.beautifullyrics.sync.LyricsSyncDraft.*;

import java.util.*;
import java.io.IOException;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedBridge;
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
    private final JsonObject draftSource;
    private boolean draftCleared;
    private String lastSnapshot;
    private long nextAutosave;
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
    private double readyPosition;
    private double previewEnd = -1;
    private boolean disposed, seeking, armed, saving, initialPass, draggingSeek;
    private boolean protectionErrorShown;
    private boolean backingPass;
    private int editorReturnScroll = -1;
    private final List<Part> backingQueue = new ArrayList<>();
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
        LyricsSyncSession restored = LyricsSyncStore.load(context, trackId);
        this.draftSource = restored == null ? source.deepCopy() : restored.source;
        this.draft = restored == null ? new LyricsSyncDraft(source) : restored.draft;
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

        status = text(References.getString(R.string.sync_prepare_hint), 14);
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
        elapsedTime.setContentDescription(References.getString(R.string.sync_playback_position));

        totalTime = text("—", 13);
        totalTime.setSingleLine(true);
        totalTime.setGravity(Gravity.END);
        totalTime.setContentDescription(References.getString(R.string.sync_song_duration));
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

        back = button(References.getString(R.string.sync_seek_backward), () -> player.seek(player.position() - 5));
        play = button(References.getString(R.string.sync_play), () -> {
            invalidateHold();
            if (player.playing()) player.pause();
            else player.play();
        });

        forward = button(References.getString(R.string.sync_seek_forward), () -> player.seek(player.position() + 5));

        for (Button b : new Button[]{back, play, forward}) {
            transport.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }

        addView(transport);

        actions = new FlexboxLayout(context);
        actions.setFlexWrap(FlexWrap.WRAP);
        addView(actions);

        player.pause();
        if (restored == null) showSplit();
        else restoreSession(restored.state);
        cacheProgress();

        handler.post(tick);
    }

    private TextView text(CharSequence value, float size) {
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
            if (!disposed && !saving) {
                action.run();
                cacheProgress();
            }
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
        SpannableString helpText = new SpannableString(
                References.getString(R.string.sync_desc, References.getString(R.string.sync_github_wiki))
        );

        String linkText = References.getString(R.string.sync_github_wiki);
        int start = helpText.toString().indexOf(linkText);
        int end = start + linkText.length();

        if (start >= 0) helpText.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeNerd46/SpotifyPlus/wiki/Syncing-Songs"));
                getContext().startActivity(intent);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView helpView = text(helpText, 15);
        helpView.setMovementMethod(LinkMovementMethod.getInstance());

        dialog = new LyricsSyncDialog(
                getContext(),
                References.getString(R.string.sync_title),
                helpView,
                References.getString(R.string.sync_intro_confirm),
                References.getString(R.string.sync_dismiss),
                false,
                () -> dialog.dismiss()
        );

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
        player.protect(getContext());
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
        reset(Stage.SPLIT, References.getString(R.string.sync_tap_a_word_to_add_or_remove_syllable_splits));
        render(true, true);
        action(References.getString(R.string.sync_confirm), () -> ready(null));
        action(References.getString(R.string.sync_save_leave), this::requestCancel);
        action(References.getString(R.string.sync_discard), this::requestDiscard);
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
                buttons.addView(button(References.getString(R.string.sync_redo), () -> ready(line.lead)));
                buttons.addView(button(References.getString(R.string.sync_preview_line), () -> preview(line)));
                row.addView(buttons);
            }

            if (background) for (Vocal vocal : line.background) {
                if (stage == Stage.PREVIEW && !vocal.hasTimings()) continue;
                addVocal(row, vocal, true, split);

                if (stage == Stage.EDIT) {
                    row.addView(button(vocal.complete() ? References.getString(R.string.sync_redo_backing_vocals) : References.getString(R.string.sync_sync_backing_vocals), () -> ready(vocal)));

                    if (vocal.hasTimings()) row.addView(button(References.getString(R.string.sync_clear_backing_timings), () -> {
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

                group.setContentDescription(References.getString(R.string.sync_split_word, word.splitText().replace("|", ", ")));
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
                    label.setContentDescription(stage == Stage.EDIT ? References.getString(R.string.sync_adjust_syllable, part.text) : part.text);

                    if (stage == Stage.EDIT) label.setOnClickListener(v -> timing(vocal, word, part));
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
            cacheProgress();
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
        splitWord(word, false);
    }

    private void splitWord(Word word, boolean preserveTiming) {
        LyricsSyncSplits panel = new LyricsSyncSplits(getContext(), word);

        dialog = new LyricsSyncDialog(getContext(), References.getString(preserveTiming ? R.string.sync_syllables_title : R.string.sync_split_title, word.text), panel, References.getString(R.string.sleep_timer_save), References.getString(R.string.lastfm_cancel), false, () -> {
            try {
                if (preserveTiming) word.splitWithEvenTimings(panel.splitText());
                else word.split(panel.splitText());
                dialog.dismiss();

                int y = scroll.getScrollY();
                render(true, stage == Stage.SPLIT);
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
        ready(only, null);
    }

    private void ready(Vocal only, JsonObject restored) {
        if (stage == Stage.EDIT) editorReturnScroll = scroll.getScrollY();
        player.pause();
        invalidateHold();
        queue.clear();
        recordingIndexes.clear();

        index = 0;
        initialPass = only == null && !backingPass;

        if (backingPass) {
            queue.addAll(backingQueue);
        } else if (only == null) {
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
        if (restored != null) {
            pass.restore(restored.getAsJsonObject("pass"));
            index = pass.index();
            if (pass.finished()) {
                commitPass();
                edit();
                return;
            }
        }
        reset(Stage.READY, References.getString(R.string.sync_hint));
        render(only != null || backingPass, false);

        Vocal firstVocal = backingPass ? vocalFor(queue.get(0)) : only;
        double start = restored != null ? Math.max(0, restored.get("position").getAsDouble() - 2) : firstVocal == null ? 0 : Math.max(0, lineFor(firstVocal).lead.start(lineFor(firstVocal).originalStart) - (lineFor(firstVocal).lead == firstVocal ? 3 : 5));
        readyPosition = restored == null ? start : restored.get("position").getAsDouble();

        action(restored == null ? References.getString(R.string.sync_start) : References.getString(R.string.ui_resume), () -> {
            reset(Stage.RECORD, References.getString(R.string.sync_starting_playback));
            action(References.getString(R.string.sync_undo), this::undo);
            action(References.getString(R.string.sync_stop_edit), () -> {
                cancelSeek();
                player.pause();
                invalidateHold();

                if (initialPass || backingPass) commitPass();
                edit();
            });
            jumpAndPlay(start, () -> {
                armed = true;
                status.setText(References.getString(R.string.sync_hold_for_the_highlighted_syllable_then_release));
            });
            focus(queue.get(index));
        });

        action(References.getString(R.string.sync_back), () -> {
            if (restored != null && (initialPass || backingPass)) commitPass();
            if (restored != null) {
                edit();
                return;
            }
            if (only == null && !backingPass) showSplit();
            else edit();
        });
        if (restored != null) {
            status.setText(References.getString(R.string.sync_draft_restored));
            updateRecordingVisuals();
            focus(queue.get(index));
        }
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

        if (!pass.holding()) cacheProgress();
        return true;
    }

    private void commitPass() {
        pass.commit(initialPass || backingPass);
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
            scroll.post(() -> {
                if (!disposed && stage == Stage.RECORD && scrolledVocal == vocal)
                    scroll.smoothScrollTo(0, row.getTop());
            });
        }

        scroll.setContentDescription(References.getString(R.string.sync_hold_syllable, part.text, index + 1, queue.size()));
    }

    private void edit() {
        cancelSeek();
        invalidateHold();
        backingPass = false;
        reset(Stage.EDIT, References.getString(R.string.sync_edit_hint));
        render(true, false);

        action(References.getString(R.string.sync_preview), () -> preview(null));
        action(References.getString(R.string.sleep_timer_save), this::confirmSave);
        action(References.getString(R.string.sync_discard), this::requestDiscard);
        action(References.getString(R.string.sync_shift_all_earlier), () -> shiftSong(-10));

        ((FlexboxLayout.LayoutParams) actions.getChildAt(3).getLayoutParams()).setWrapBefore(true);
        action(References.getString(R.string.sync_shift_all_later), () -> shiftSong(10));
        action(References.getString(R.string.sync_save_leave), this::requestCancel);
        action(References.getString(R.string.sync_remaining_backing), this::readyBacking);
        if (editorReturnScroll >= 0) {
            int y = editorReturnScroll;
            editorReturnScroll = -1;
            scroll.fling(0);
            scroll.post(() -> {
                if (!disposed && stage == Stage.EDIT) scroll.scrollTo(0, y);
            });
        }
    }

    private void readyBacking() {
        backingQueue.clear();
        backingQueue.addAll(draft.remainingBackingParts());

        if (backingQueue.isEmpty()) {
            message(References.getString(R.string.sync_all_backing_vocals_are_already_synced));
            return;
        }

        backingPass = true;
        ready(vocalFor(backingQueue.get(0)));
    }

    private void shiftSong(int milliseconds) {
        try {
            draft.shiftTimings(milliseconds, player.hasKnownDuration() ? player.durationMs() / 1000d : Double.POSITIVE_INFINITY);
            Toast.makeText(getContext(), References.getString(milliseconds < 0 ? R.string.sync_shift_earlier : R.string.sync_shift_later), Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            message(e.getMessage());
        }
    }

    private void timing(Vocal vocal, Word word, Part part) {
        player.pause();

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        EditText start = timingInput(panel, References.getString(R.string.sync_start_milliseconds), part.start), end = timingInput(panel, References.getString(R.string.sync_end_milliseconds), part.end);
        Button splits = button(References.getString(R.string.sync_split_or_join_syllables), () -> {
            dialog.dismiss();
            splitWord(word, true);
        });
        LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(-1, -2);
        splitParams.setMargins(0, dp(12), 0, 0);
        panel.addView(splits, splitParams);
        panel.addView(text(References.getString(R.string.sync_split_timing_hint), 13));

        dialog = new LyricsSyncDialog(getContext(), References.getString(R.string.sync_timing_title, part.text), panel, References.getString(R.string.sleep_timer_save), References.getString(R.string.lastfm_cancel), false, () -> {
            try {
                double a = Long.parseLong(start.getText().toString()) / 1000d, b = Long.parseLong(end.getText().toString()) / 1000d;
                List<Part> parts = vocal.parts();
                int at = parts.indexOf(part);

                if (a < 0 || b <= a || (player.hasKnownDuration() && b > player.durationMs() / 1000d))
                    throw new IllegalArgumentException(References.getString(R.string.sync_start_must_be_before_end_within_the_song));

                if (at > 0 && parts.get(at - 1).end > a)
                    throw new IllegalArgumentException(References.getString(R.string.sync_overlap_previous, parts.get(at - 1).text, Math.round(parts.get(at - 1).end * 1000)));
                if (at + 1 < parts.size() && parts.get(at + 1).start >= 0 && b > parts.get(at + 1).start)
                    throw new IllegalArgumentException(References.getString(R.string.sync_overlap_next, parts.get(at + 1).text, Math.round(parts.get(at + 1).start * 1000)));

                part.start = a;
                part.end = b;

                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                dialog.error(e instanceof NumberFormatException ? References.getString(R.string.sync_enter_milliseconds_as_a_whole_number) : e.getMessage());
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
            try {
                draft.toJson(false);
            } catch (TimingProblem problem) {
                showTimingProblem(problem);
            } catch (IllegalArgumentException error) {
                message(error.getMessage());
            }
            return;
        }

        reset(Stage.PREVIEW, References.getString(R.string.sync_watch_the_highlighting_with_your_timings));
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

        action(References.getString(R.string.sync_back_to_editor), () -> {
            cancelSeek();
            player.pause();
            edit();
        });

        jumpAndPlay(start, () -> {
        });
    }

    public void requestCancel() {
        if (saving) return;
        cacheProgress();
        invalidateHold();
        player.pause();
        host.cancel();
    }

    private void requestDiscard() {
        if (saving) return;
        if (dialog != null && dialog.isShowing()) return;

        if (seeking) {
            cancelSeek();
            if (stage == Stage.RECORD && (initialPass || backingPass)) commitPass();
            edit();
        }

        invalidateHold();
        player.pause();

        dialog = new LyricsSyncDialog(getContext(), References.getString(R.string.sync_discard_title), text(References.getString(R.string.sync_discard_subtitle), 16), References.getString(R.string.sync_discard), References.getString(R.string.sync_discard_keep), true, () -> {
            clearDraft();
            dialog.dismiss();
            host.cancel();
        });

        showDialog();
    }

    private void confirmSave() {
        player.pause();
        long unsynced = draft.unsyncedBackingCount();
        if (unsynced > 0) {
            boolean partial = draft.lines.stream().flatMap(line -> line.background.stream()).anyMatch(vocal -> vocal.hasTimings() && !vocal.complete());
            String warning = References.getQuantityString(R.plurals.sync_incomplete_backing, Math.toIntExact(unsynced), unsynced);
            if (partial)
                warning += "\n\n" + References.getString(R.string.sync_partial_backing_warning);
            dialog = new LyricsSyncDialog(getContext(), References.getString(R.string.sync_missing_backing), text(warning, 16),
                    References.getString(R.string.sync_missing_backing_confirm), References.getString(R.string.sync_missing_backing_cancel), false, () -> {
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
        panel.addView(text(References.getString(R.string.sync_submit_desc_0), 16));
        panel.addView(text(References.getString(R.string.sync_submit_desc_1), 16));

        LinearLayout correctionRow = new LinearLayout(getContext());
        correctionRow.setGravity(Gravity.CENTER_VERTICAL);
        correctionRow.setPadding(0, dp(16), 0, dp(8));
        TextView correctionLabel = text(References.getString(R.string.sync_submit_correction), 14);
        correctionLabel.setPadding(0, 0, dp(16), 0);
        correctionRow.addView(correctionLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Switch correction = LyricsSyncDialog.toggle(getContext());
        correction.setContentDescription(References.getString(R.string.sync_submit_correction));
        correction.setChecked(correctLineTimings);
        correction.setOnCheckedChangeListener((button, checked) -> correctLineTimings = checked);
        correctionRow.setOnClickListener(v -> correction.performClick());
        correctionRow.addView(correction, new LinearLayout.LayoutParams(-2, dp(48)));
        panel.addView(correctionRow);

        dialog = new LyricsSyncDialog(getContext(), References.getString(R.string.sync_submit_title), panel, References.getString(R.string.sync_missing_backing_confirm), References.getString(R.string.sync_discard_keep), false, this::showMetadataDialog);
        showDialog();
    }

    private void showMetadataDialog() {
        dialog.dismiss();

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.addView(text(References.getString(R.string.sync_credit_desc), 16));

        RadioGroup choices = new RadioGroup(getContext());
        choices.setOrientation(VERTICAL);
        choices.setPadding(0, dp(12), 0, 0);

        RadioButton spotifyChoice = metadataChoice(References.getString(R.string.sync_credit_0));
        RadioButton customChoice = metadataChoice(References.getString(R.string.sync_credit_1));
        RadioButton anonymousChoice = metadataChoice(References.getString(R.string.sync_credit_2));
        choices.addView(spotifyChoice);
        choices.addView(customChoice);
        choices.addView(anonymousChoice);
        panel.addView(choices);

        TextView spotifyStatus = text(References.getString(R.string.sync_profile_credit_description), 14);
        spotifyStatus.setPadding(dp(36), dp(4), 0, dp(8));
        spotifyStatus.setVisibility(GONE);
        panel.addView(spotifyStatus);

        LinearLayout customFields = new LinearLayout(getContext());
        customFields.setOrientation(VERTICAL);
        customFields.setPadding(dp(36), dp(4), 0, dp(8));

        EditText usernameInput = metadataInput(References.getString(R.string.sync_credit_username_placeholder), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText avatarInput = metadataInput(References.getString(R.string.sync_credit_avatar_placeholder), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        customFields.addView(usernameInput);
        LinearLayout.LayoutParams avatarInputParams = new LinearLayout.LayoutParams(-1, -2);
        avatarInputParams.topMargin = dp(8);
        customFields.addView(avatarInput, avatarInputParams);
        customFields.setVisibility(GONE);
        panel.addView(customFields);

        SpotifyUser[] spotifyUser = new SpotifyUser[1];
        LyricsSyncDialog[] metadataDialog = new LyricsSyncDialog[1];

        choices.setOnCheckedChangeListener((group, checkedId) -> {
            boolean useSpotify = checkedId == spotifyChoice.getId();
            customFields.setVisibility(checkedId == customChoice.getId() ? VISIBLE : GONE);
            spotifyStatus.setVisibility(useSpotify ? VISIBLE : GONE);
            if (metadataDialog[0] != null) metadataDialog[0].setPrimaryState(References.getString(R.string.sync_credit_confirm), true);

            if (!useSpotify || spotifyUser[0] != null) return;

            spotifyStatus.setText(References.getString(R.string.sync_loading_your_spotify_profile));
            Utils.getSpotifyUser().whenComplete((user, error) -> handler.post(() -> {
                if (disposed || dialog != metadataDialog[0] || !metadataDialog[0].isShowing()) return;
                if (error != null) {
                    XposedBridge.log(error);
                    spotifyStatus.setText(References.getString(R.string.sync_profile_load_failed));
                    return;
                }

                spotifyUser[0] = user;
                spotifyStatus.setText(References.getString(user.getProfileImageUrl() == null ? R.string.sync_profile_no_photo : R.string.sync_profile_with_photo, user.getCreditName()));
            }));
        });

        metadataDialog[0] = new LyricsSyncDialog(getContext(), References.getString(R.string.sync_credit_title), panel, References.getString(R.string.sync_credit_confirm), References.getString(R.string.sync_back), false, () -> {
            int selected = choices.getCheckedRadioButtonId();
            if (selected == -1) {
                metadataDialog[0].error(References.getString(R.string.sync_choose_an_option_before_submitting));
                return;
            }

            if (selected == anonymousChoice.getId()) {
                save(null);
                return;
            }

            if (selected == customChoice.getId()) {
                String username = usernameInput.getText().toString().trim();
                String avatar = avatarInput.getText().toString().trim();
                if (username.isEmpty()) {
                    metadataDialog[0].error(References.getString(R.string.sync_enter_a_username_or_choose_another_option));
                    usernameInput.requestFocus();
                    return;
                }
                if (!avatar.isEmpty() && !isHttpUrl(avatar)) {
                    metadataDialog[0].error(References.getString(R.string.sync_enter_a_valid_http_or_https_avatar_url));
                    avatarInput.requestFocus();
                    return;
                }

                save(submissionUser(username, avatar));
                return;
            }

            SpotifyUser loadedUser = spotifyUser[0];
            if (loadedUser != null) {
                save(submissionUser(loadedUser.getCreditName(), loadedUser.getProfileImageUrl()));
                return;
            }

            metadataDialog[0].setPrimaryState(References.getString(R.string.sync_loading_spotify_profile), false);
            Utils.getSpotifyUser().whenComplete((user, error) -> handler.post(() -> {
                if (disposed || dialog != metadataDialog[0] || !metadataDialog[0].isShowing()) return;
                if (error != null) {
                    metadataDialog[0].setPrimaryState(References.getString(R.string.sync_credit_confirm), true);
                    metadataDialog[0].error(References.getString(R.string.sync_profile_connection_error));
                    return;
                }

                spotifyUser[0] = user;
                if (choices.getCheckedRadioButtonId() != spotifyChoice.getId()) {
                    metadataDialog[0].setPrimaryState(References.getString(R.string.sync_credit_confirm), true);
                    return;
                }
                save(submissionUser(user.getCreditName(), user.getProfileImageUrl()));
            }));
        });
        metadataDialog[0].setSecondaryAction(() -> {
            metadataDialog[0].dismiss();
            showSaveConfirmation();
        });
        dialog = metadataDialog[0];
        showDialog();
    }

    private RadioButton metadataChoice(String label) {
        RadioButton choice = new RadioButton(getContext());
        choice.setId(View.generateViewId());
        choice.setText(label);
        choice.setTextColor(Color.WHITE);
        choice.setTextSize(16);
        choice.setGravity(Gravity.CENTER_VERTICAL);
        choice.setMinHeight(dp(48));
        choice.setPadding(dp(4), 0, 0, 0);
        return choice;
    }

    private EditText metadataInput(String hint, int inputType) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        LyricsSyncDialog.styleInput(input);
        return input;
    }

    private boolean isHttpUrl(String value) {
        HttpUrl url = HttpUrl.parse(value);
        return url != null && (url.scheme().equals("http") || url.scheme().equals("https"));
    }

    private JsonObject submissionUser(String username, String avatar) {
        JsonObject user = new JsonObject();
        user.addProperty("username", username.trim());
        if (avatar != null && !avatar.isBlank()) user.addProperty("avatar", avatar.trim());
        return user;
    }

    private void showTimingProblem(TimingProblem problem) {
        edit();
        View first = null;
        List<View> issueRows = new ArrayList<>();
        for (Part part : problem.parts) {
            GradientTextView label = labels.get(part);
            if (label == null) continue;
            label.setBackgroundColor(0xB3A52232);
            label.setContentDescription(References.getString(R.string.sync_timing_attention, part.text));
            View row = rows.get(vocalFor(part));
            if (row != null && !issueRows.contains(row)) {
                issueRows.add(row);
                TextView marker = text(References.getString(R.string.sync_timing_issue, draft.lines.indexOf(lineFor(vocalFor(part))) + 1), 13);
                marker.setTextColor(0xFFFFB4BF);
                ((LinearLayout) row).addView(marker, 0);
            }
            if (first == null) first = row;
        }
        if (issueRows.size() > 1) {
            int[] nextIssue = {0};
            action(References.getString(R.string.sync_next_issue), () -> {
                nextIssue[0] = (nextIssue[0] + 1) % issueRows.size();
                scroll.smoothScrollTo(0, issueRows.get(nextIssue[0]).getTop());
            });
        }
        status.setVisibility(VISIBLE);
        status.setText(References.getString(R.string.sync_fix_timing, problem.getMessage()));
        View target = first;
        if (target != null) scroll.post(() -> {
            if (!disposed && stage == Stage.EDIT) scroll.scrollTo(0, target.getTop());
        });
    }

    private void save(JsonObject user) {
        JsonObject result;

        try {
            result = draft.toJson(correctLineTimings);
            if (player.hasKnownDuration() && result.get("EndTime").getAsDouble() > player.durationMs() / 1000d) {
                throw new IllegalArgumentException(References.getString(R.string.sync_timings_past_end));
            }
        } catch (IllegalArgumentException e) {
            if (e instanceof TimingProblem) {
                dialog.dismiss();
                showTimingProblem((TimingProblem) e);
            } else dialog.error(e.getMessage());
            return;
        }

        dialog.dismiss();
        player.pause();
        saving = true;
        status.setText(References.getString(R.string.sync_saving_community_sync));
        status.setVisibility(VISIBLE);

        setEnabledRecursively(this, false);
        JsonObject requestBody = new JsonObject();
        requestBody.add("lyrics", result);
        if (user != null) requestBody.add("user", user);
        JsonObject fallbackSavedLyrics = result.deepCopy();
        if (user != null) fallbackSavedLyrics.add("SubmittedBy", user.deepCopy());
        Request request = new Request.Builder().url("https://spotifyplus-api.devon-shoutz.workers.dev/api/lyrics/" + trackId).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody.toString())).build();
        submission = new OkHttpClient().newCall(request);

        submission.enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                handler.post(() -> saveFailed(References.getString(R.string.sync_save_connection_error)));
            }

            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        JsonObject savedLyrics = fallbackSavedLyrics;
                        if (r.body() != null) try {
                            savedLyrics = com.google.gson.JsonParser.parseString(r.body().string()).getAsJsonObject();
                        } catch (Exception ignored) {
                        }

                        JsonObject finalSavedLyrics = savedLyrics;
                        handler.post(() -> {
                            if (!disposed) {
                                saving = false;
                                clearDraft();
                                host.saved(finalSavedLyrics);
                            }
                        });
                    } else {
                        String detail = References.getString(R.string.sync_server_rejected, r.code());

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
            if (SystemClock.elapsedRealtime() >= nextAutosave) {
                cacheProgress();
                nextAutosave = SystemClock.elapsedRealtime() + 2000;
            }

            if (!player.ready()) {
                player.pause();
                play.setEnabled(false);
                status.setVisibility(VISIBLE);
                status.setText(player.protectionError() == null ? References.getString(R.string.sync_temporarily_disabling_crossfade) : player.protectionError());

                if (!protectionErrorShown && player.protectionError() != null) {
                    protectionErrorShown = true;
                    message(player.protectionError());
                }

                handler.postDelayed(this, 33);
                return;
            }

            if (!saving) play.setEnabled(stage != Stage.READY && stage != Stage.FINISHING);
            double now = player.position();

            if (player.guardEnd()) {
                cancelSeek();

                if (stage == Stage.RECORD) {
                    if (pass.holding()) pass.release(now, true);
                    pass.commit(true);
                    edit();
                }

                status.setVisibility(VISIBLE);
                status.setText(References.getString(R.string.sync_paused_before_end));
            }
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

                    if (stage == Stage.RECORD && (initialPass || backingPass)) commitPass();

                    edit();
                    message(References.getString(R.string.sync_spotify_did_not_confirm_the_seek_try_the_line));
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

            play.setText(player.playing() ? References.getString(R.string.sync_pause) : References.getString(R.string.sync_play));
            if (stage == Stage.RECORD) {
                if (!player.playing()) invalidateHold();
                if (!seeking && knownDuration && now >= durationMs / 1000d - .02 && index < queue.size()) {
                    if (initialPass || backingPass) commitPass();

                    edit();
                    message(References.getString(R.string.sync_playback_ended));
                } else if (!seeking) {
                    String hint = player.playing() ? References.getString(R.string.sync_hold_for_the_highlighted_syllable_then_release) : References.getString(R.string.sync_playback_paused_press_play_to_continue);

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
        cacheProgress();
        disposed = true;
        player.close();
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
        if (!hasFocus) cacheProgress();

        if (!hasFocus && stage == Stage.RECORD && !disposed) {
            invalidateHold();
            player.pause();

            if (seeking) {
                JsonObject resume = new JsonObject();
                resume.add("pass", pass.snapshot());
                resume.addProperty("position", requestedPosition);
                Vocal only = initialPass ? null : vocalFor(queue.get(0));
                cancelSeek();
                ready(only, resume);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cacheProgress();
        super.onDetachedFromWindow();
    }

    private void clearDraft() {
        draftCleared = true;
        LyricsSyncStore.remove(getContext(), trackId);
    }

    private void cacheProgress() {
        if (disposed || draftCleared) return;

        JsonObject saved = LyricsSyncSession.snapshot(draftSource, draft);
        saved.addProperty("stage", stage.name());
        double position = stage == Stage.READY ? readyPosition : seeking ? requestedPosition : player.position();

        if (stage == Stage.RECORD && pass != null) position = pass.checkpointPosition(position);

        saved.addProperty("position", Math.max(0, position));
        saved.addProperty("scroll", scroll.getScrollY());
        saved.addProperty("correctLineTimings", correctLineTimings);

        if (stage == Stage.READY || stage == Stage.RECORD) {
            saved.addProperty("initialPass", initialPass);
            saved.addProperty("backingPass", backingPass);
            saved.addProperty("editorReturnScroll", editorReturnScroll);

            if (backingPass) {
                List<Part> all = draft.vocals().stream().flatMap(v -> v.parts().stream()).collect(Collectors.toList());
                com.google.gson.JsonArray batch = new com.google.gson.JsonArray();
                for (Part part : backingQueue) batch.add(all.indexOf(part));
                saved.add("backingQueue", batch);
            }

            saved.addProperty("vocal", initialPass ? -1 : draft.vocals().indexOf(vocalFor(queue.get(0))));
            saved.add("pass", pass.snapshot());
        }

        String json = saved.toString();
        if (!json.equals(lastSnapshot)) {
            LyricsSyncStore.save(getContext(), trackId, json);
            lastSnapshot = json;
        }
    }

    private void restoreSession(JsonObject saved) {
        helpShown = true;
        correctLineTimings = saved.get("correctLineTimings").getAsBoolean();
        Stage previous = Stage.valueOf(saved.get("stage").getAsString());

        if (previous == Stage.SPLIT) {
            showSplit();
        } else if (previous == Stage.RECORD || previous == Stage.READY) {
            backingPass = saved.has("backingPass") && saved.get("backingPass").getAsBoolean();
            editorReturnScroll = saved.has("editorReturnScroll") ? saved.get("editorReturnScroll").getAsInt() : -1;
            backingQueue.clear();

            if (backingPass) backingQueue.addAll(LyricsSyncSession.backingQueue(draft, saved));

            Vocal only = saved.get("initialPass").getAsBoolean() ? null : draft.vocals().get(saved.get("vocal").getAsInt());
            ready(only, saved);
        } else {
            edit();
        }

        if (stage != Stage.READY) {
            int y = saved.get("scroll").getAsInt();
            scroll.post(() -> scroll.scrollTo(0, y));
        }

        player.seek(saved.get("position").getAsDouble());
    }
}

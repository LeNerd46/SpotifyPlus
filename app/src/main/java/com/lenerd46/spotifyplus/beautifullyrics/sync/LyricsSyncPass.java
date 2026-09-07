package com.lenerd46.spotifyplus.beautifullyrics.sync;

import java.util.*;

import com.lenerd46.spotifyplus.beautifullyrics.sync.LyricsSyncDraft.Part;

public final class LyricsSyncPass {
    private final List<Part> parts;
    private final List<double[]> captured = new ArrayList<>();

    private double down = -1;
    private boolean correcting;

    public LyricsSyncPass(List<Part> parts) {
        this.parts = new ArrayList<>(parts);
    }

    public int index() {
        return captured.size();
    }

    public boolean finished() {
        return index() == parts.size();
    }

    public boolean holding() {
        return down >= 0;
    }

    public boolean press(double position, boolean playing) {
        if (!playing || finished() || holding() || !Double.isFinite(position) || position < 0) return false;

        if (!captured.isEmpty()) {
            double[] previous = captured.get(index() - 1);
            if (correcting ? position <= previous[0] : position < previous[1]) return false;
        }

        down = position;
        return true;
    }

    public boolean release(double position, boolean playing) {
        double start = down;
        cancelHold();

        if (!playing || start < 0 || !Double.isFinite(position) || position <= start) return false;
        if (correcting && !captured.isEmpty()) {
            double[] previous = captured.get(index() - 1);
            previous[1] = Math.min(previous[1], start);
        }

        correcting = false;
        captured.add(new double[]{start, position});

        return true;
    }

    public void cancelHold() {
        down = -1;
    }

    public double undo() {
        cancelHold();

        if (captured.isEmpty()) return -1;

        correcting = true;
        return captured.remove(index() - 1)[0];
    }

    public void commit(boolean allowPartial) {
        if (!finished() && !allowPartial) throw new IllegalStateException("Finish the retake before applying it");

        for (int i = 0; i < captured.size(); i++) {
            parts.get(i).start = captured.get(i)[0];
            parts.get(i).end = captured.get(i)[1];
        }
    }
}

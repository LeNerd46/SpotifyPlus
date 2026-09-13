package com.lenerd46.spotifyplus.beautifullyrics.sync;

import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import java.util.*;
import com.google.gson.*;

import com.lenerd46.spotifyplus.beautifullyrics.sync.LyricsSyncDraft.Part;

public final class LyricsSyncPass {
    private final List<Part> parts;
    private final List<double[]> captured = new ArrayList<>();

    private double down = -1;
    private double interruptedAt = -1;
    private boolean correcting;

    public LyricsSyncPass(List<Part> parts) {
        this.parts = new ArrayList<>(parts);
    }

    public int index() {
        return captured.size();
    }

    JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.add("captured", new Gson().toJsonTree(captured));
        result.addProperty("correcting", correcting);
        return result;
    }

    void restore(JsonObject saved) {
        List<double[]> restored = new ArrayList<>();
        for (JsonElement element : saved.getAsJsonArray("captured")) {
            JsonArray timing = element.getAsJsonArray();
            double start = timing.get(0).getAsDouble(), end = timing.get(1).getAsDouble();
            if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start ||
                    (!restored.isEmpty() && start < restored.get(restored.size() - 1)[1]))
                throw new IllegalArgumentException("Invalid recording timing");
            restored.add(new double[]{start, end});
        }
        if (restored.size() > parts.size()) throw new IllegalArgumentException("Invalid recording length");
        captured.clear();
        captured.addAll(restored);
        correcting = saved.get("correcting").getAsBoolean();
        cancelHold(); // An interrupted press must be recorded again after resuming.
    }

    public boolean finished() {
        return index() == parts.size();
    }

    public boolean holding() {
        return down >= 0;
    }

    double checkpointPosition(double position) {
        return holding() ? down : interruptedAt >= 0 ? interruptedAt : position;
    }

    public boolean press(double position, boolean playing) {
        if (!playing || finished() || holding() || !Double.isFinite(position) || position < 0) return false;

        if (!captured.isEmpty()) {
            double[] previous = captured.get(index() - 1);
            if (correcting ? position <= previous[0] : position < previous[1]) return false;
        }

        down = position;
        interruptedAt = -1;
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
        interruptedAt = -1;
        captured.add(new double[]{start, position});

        return true;
    }

    public void cancelHold() {
        if (down >= 0) interruptedAt = down;
        down = -1;
    }

    public double undo() {
        cancelHold();
        interruptedAt = -1;

        if (captured.isEmpty()) return -1;

        correcting = true;
        return captured.remove(index() - 1)[0];
    }

    public void commit(boolean allowPartial) {
        if (!finished() && !allowPartial) throw new IllegalStateException(References.getString(R.string.sync_finish_the_retake_before_applying_it));

        for (int i = 0; i < captured.size(); i++) {
            parts.get(i).start = captured.get(i)[0];
            parts.get(i).end = captured.get(i)[1];
        }
    }
}

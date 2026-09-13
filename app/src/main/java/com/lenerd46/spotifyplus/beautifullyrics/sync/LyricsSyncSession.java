package com.lenerd46.spotifyplus.beautifullyrics.sync;

import com.google.gson.*;

import java.util.*;
import java.util.stream.Collectors;

final class LyricsSyncSession {
    final JsonObject source;
    final LyricsSyncDraft draft;
    final JsonObject state;

    LyricsSyncSession(JsonObject saved) {
        if (saved.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown draft version");

        source = saved.getAsJsonObject("source").deepCopy();
        draft = new LyricsSyncDraft(source);
        draft.restore(saved.getAsJsonArray("vocals"));
        state = saved;

        String stage = state.get("stage").getAsString();

        if (!Set.of("SPLIT", "READY", "RECORD", "FINISHING", "EDIT", "PREVIEW").contains(stage))
            throw new IllegalArgumentException("Invalid draft stage");

        double position = state.get("position").getAsDouble();
        if (!Double.isFinite(position) || position < 0) throw new IllegalArgumentException("Invalid draft position");
        if (state.get("scroll").getAsInt() < 0) throw new IllegalArgumentException("Invalid draft scroll");

        if (stage.equals("READY") || stage.equals("RECORD")) {
            List<LyricsSyncDraft.Part> queue = new ArrayList<>();

            if (state.has("backingPass") && state.get("backingPass").getAsBoolean()) {
                queue.addAll(backingQueue(draft, state));
            } else if (state.get("initialPass").getAsBoolean()) {
                for (LyricsSyncDraft.Line line : draft.lines) {
                    queue.addAll(line.lead.parts());
                }
            } else {
                queue.addAll(draft.vocals().get(state.get("vocal").getAsInt()).parts());
            }

            if (queue.isEmpty()) throw new IllegalArgumentException("Empty recording");
            new LyricsSyncPass(queue).restore(state.getAsJsonObject("pass"));
        }
    }

    static List<LyricsSyncDraft.Part> backingQueue(LyricsSyncDraft draft, JsonObject state) {
        List<LyricsSyncDraft.Part> all = draft.vocals().stream().flatMap(v -> v.parts().stream()).collect(Collectors.toList());
        Set<LyricsSyncDraft.Part> backing = Collections.newSetFromMap(new IdentityHashMap<>());

        for (LyricsSyncDraft.Line line : draft.lines) {
            for (LyricsSyncDraft.Vocal vocal : line.background) {
                backing.addAll(vocal.parts());
            }
        }

        List<LyricsSyncDraft.Part> queue = new ArrayList<>();

        for (JsonElement value : state.getAsJsonArray("backingQueue")) {
            LyricsSyncDraft.Part part = all.get(value.getAsInt());
            if (!backing.remove(part)) throw new IllegalArgumentException("Invalid backing recording");

            queue.add(part);
        }

        return queue;
    }

    static JsonObject snapshot(JsonObject source, LyricsSyncDraft draft) {
        JsonObject saved = new JsonObject();
        saved.addProperty("version", 1);
        saved.add("source", source.deepCopy());
        saved.add("vocals", draft.snapshot());
        return saved;
    }
}

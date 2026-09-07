package com.lenerd46.spotifyplus.beautifullyrics.sync;

import com.google.gson.*;

import java.util.*;

public final class LyricsSyncDraft {
    public static final class Part {
        public final String text;
        public double start = -1, end = -1;

        Part(String text) {
            this.text = text;
        }
    }

    public static final class Word {
        public final String text;
        public final List<Part> parts = new ArrayList<>();

        Word(String text) {
            this.text = text;
            parts.add(new Part(text));
        }

        public String splitText() {
            List<String> strings = new ArrayList<>();

            for (Part p : parts) {
                strings.add(p.text);
            }

            return String.join("|", strings);
        }

        public void split(String value) {
            String[] pieces = value.split("\\|", -1);

            if (!String.join("", pieces).equals(text))
                throw new IllegalArgumentException("Keep the original letters; add | only between syllables.");

            for (String piece : pieces) {
                if (piece.isEmpty() || Character.isLowSurrogate(piece.charAt(0)) || Character.isHighSurrogate(piece.charAt(piece.length() - 1)))
                    throw new IllegalArgumentException("Place splits between letters, with no empty syllables.");
            }

            parts.clear();

            for (String piece : pieces) {
                parts.add(new Part(piece));
            }
        }
    }

    public static final class Vocal {
        public final List<Word> words = new ArrayList<>();

        Vocal(String text) {
            for (String w : normalize(text).split(" ")) {
                if (!w.isEmpty()) words.add(new Word(w));
            }
        }

        public List<Part> parts() {
            List<Part> result = new ArrayList<>();

            for (Word w : words) {
                result.addAll(w.parts);
            }

            return result;
        }

        public boolean complete() {
            return parts().stream().allMatch(p -> p.start >= 0 && p.end > p.start);
        }

        public boolean hasTimings() {
            return parts().stream().anyMatch(p -> p.start >= 0 || p.end >= 0);
        }

        public void clearTimings() {
            for (Part p : parts()) {
                p.start = -1;
                p.end = -1;
            }
        }

        public double start(double fallback) {
            List<Part> p = parts();
            return p.isEmpty() || p.get(0).start < 0 ? fallback : p.get(0).start;
        }
    }

    public static final class Line {
        public final Vocal lead;
        public final List<Vocal> background = new ArrayList<>();
        public final double originalStart, originalEnd;
        public final boolean opposite;

        Line(JsonObject source) {
            originalStart = source.get("StartTime").getAsDouble();
            originalEnd = source.get("EndTime").getAsDouble();
            opposite = source.has("OppositeAligned") && source.get("OppositeAligned").getAsBoolean();

            List<String> separated = separateVocals(source.get("Text").getAsString());
            lead = new Vocal(separated.get(0));

            for (int i = 1; i < separated.size(); i++) {
                background.add(new Vocal(separated.get(i)));
            }
        }
    }

    public final List<Line> lines = new ArrayList<>();
    public final List<double[]> interludes = new ArrayList<>();
    public final JsonArray writers;

    public LyricsSyncDraft(JsonObject source) {
        writers = source.has("SongWriters") ? source.getAsJsonArray("SongWriters").deepCopy() : new JsonArray();
        double previousEnd = 0;

        for (JsonElement e : source.getAsJsonArray("Content")) {
            JsonObject item = e.getAsJsonObject();

            if (item.get("Type").getAsString().equals("Vocal")) {
                Line line = new Line(item);

                if (line.originalStart - previousEnd >= 2) {
                    interludes.add(new double[]{previousEnd, line.originalStart});
                }

                lines.add(line);
                previousEnd = line.originalEnd;
            } else if (item.get("Type").getAsString().equals("Interlude")) {
                interludes.add(new double[]{item.get("StartTime").getAsDouble(), item.get("EndTime").getAsDouble()});
            }
        }

        if (lines.isEmpty()) throw new IllegalArgumentException("No lines to sync");
    }

    public static List<String> separateVocals(String input) {
        StringBuilder lead = new StringBuilder(), phrase = new StringBuilder();
        List<String> bg = new ArrayList<>();

        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') {
                if (depth++ == 0) {
                    lead.append(' ');
                    phrase.setLength(0);
                }

                continue;
            }
            if (c == ')' && depth > 0) {
                if (--depth == 0) addPhrase(bg, phrase);
                continue;
            }

            (depth > 0 ? phrase : lead).append(c);
        }

        if (depth > 0) addPhrase(bg, phrase);

        bg.add(0, normalize(lead.toString()));
        return bg;
    }

    private static void addPhrase(List<String> background, StringBuilder phrase) {
        String normalized = normalize(phrase.toString());
        if (!normalized.isEmpty()) background.add(normalized);
    }

    private static String normalize(String s) {
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isSpaceChar(c) || (c >= '\t' && c <= '\r') || c == '\ufeff') {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) result.append(' ');

                result.append(c);
                pendingSpace = false;
            }
        }

        return result.toString();
    }

    public long unsyncedBackingCount() {
        return lines.stream().flatMap(line -> line.background.stream())
                .filter(vocal -> !vocal.complete()).count();
    }

    public boolean complete() {
        return lines.stream().allMatch(l -> l.lead.complete() && l.background.stream().allMatch(v -> !v.hasTimings() || v.complete()));
    }

    public void shiftTimings(int milliseconds, double songEnd) {
        List<Part> timed = new ArrayList<>();
        for (Line line : lines) {
            List<Vocal> vocals = new ArrayList<>(line.background);
            vocals.add(line.lead);
            for (Vocal vocal : vocals) {
                for (Part part : vocal.parts()) {
                    if (part.start >= 0 || part.end >= 0) {
                        timed.add(part);
                    }
                }
            }
        }

        if (timed.isEmpty()) throw new IllegalArgumentException("Record some timings first.");

        for (Part part : timed) {
            for (double value : new double[]{part.start, part.end}) {
                if (value >= 0) {
                    double shifted = shift(value, milliseconds);

                    if (shifted < 0 || shifted > songEnd) {
                        throw new IllegalArgumentException("That shift would move lyrics outside the song.");
                    }
                }
            }
        }

        for (Part part : timed) {
            if (part.start >= 0) part.start = shift(part.start, milliseconds);
            if (part.end >= 0) part.end = shift(part.end, milliseconds);
        }
    }

    private static double shift(double seconds, int milliseconds) {
        return java.math.BigDecimal.valueOf(seconds).add(java.math.BigDecimal.valueOf(milliseconds, 3)).doubleValue();
    }

    private List<double[]> timingAnchors() {
        List<double[]> anchors = new ArrayList<>();

        for (Line line : lines) {
            double recorded = line.lead.start(Double.MAX_VALUE);
            if (line.lead.words.isEmpty()) {
                for (Vocal vocal : line.background) {
                    if (vocal.hasTimings()) recorded = Math.min(recorded, vocal.start(Double.MAX_VALUE));
                }
            }

            if (recorded == Double.MAX_VALUE) continue;
            double original = line.originalStart;
            if (!Double.isFinite(recorded) || !Double.isFinite(original) || recorded < 0 || original < 0 ||
                    (!anchors.isEmpty() && (recorded <= anchors.get(anchors.size() - 1)[0] || original <= anchors.get(anchors.size() - 1)[1]))) {
                throw new IllegalArgumentException("Line starts must increase to correct timings. Adjust the timings or turn off line timing correction.");
            }

            anchors.add(new double[]{recorded, original});
        }

        return anchors;
    }

    private double correctedTime(double time, List<double[]> anchors) {
        if (anchors.isEmpty()) return time;

        double[] first = anchors.get(0), last = anchors.get(anchors.size() - 1);
        if (time == first[0]) return first[1];
        // Keep the song origin fixed when there are vocals before the first anchor.
        if (time < first[0] && first[0] > 0 && first[1] > 0) return time * first[1] / first[0];
        if (time <= first[0]) return time + first[1] - first[0];

        for (int i = 1; i < anchors.size(); i++) {
            double[] before = anchors.get(i - 1), after = anchors.get(i);
            if (time == after[0]) return after[1];
            if (time < after[0]) {
                return before[1] + (time - before[0]) / (after[0] - before[0]) * (after[1] - before[1]);
            }
        }

        // Beyond the final line start, preserve the remaining recorded durations.
        return time + last[1] - last[0];
    }

    private JsonObject vocalJson(Vocal vocal, double fallback, List<double[]> anchors) {
        JsonObject result = new JsonObject();
        JsonArray syllables = new JsonArray();

        double start = fallback, end = fallback, previous = -1;
        for (Word word : vocal.words) {
            for (int i = 0; i < word.parts.size(); i++) {
                Part part = word.parts.get(i);
                if (!Double.isFinite(part.start) || !Double.isFinite(part.end) || part.start < 0 || part.end <= part.start || part.start < previous) {
                    throw new IllegalArgumentException("Sync every syllable and fix overlapping timings before saving.");
                }

                double correctedStart = correctedTime(part.start, anchors), correctedEnd = correctedTime(part.end, anchors);
                if (!Double.isFinite(correctedStart) || !Double.isFinite(correctedEnd) || correctedStart < 0 || correctedEnd <= correctedStart) {
                    throw new IllegalArgumentException("Line timing correction would create invalid timings. Adjust the timings or turn it off.");
                }

                if (syllables.isEmpty()) start = correctedStart;

                end = correctedEnd;
                previous = part.end;

                JsonObject s = new JsonObject();
                s.addProperty("Text", part.text);
                s.addProperty("StartTime", correctedStart);
                s.addProperty("EndTime", correctedEnd);
                s.addProperty("IsPartOfWord", i < word.parts.size() - 1);

                syllables.add(s);
            }
        }

        result.addProperty("StartTime", start);
        result.addProperty("EndTime", end);
        result.add("Syllables", syllables);

        return result;
    }

    public JsonObject toJson() {
        return toJson(false);
    }

    public JsonObject toJson(boolean correctLineTimings) {
        if (!complete()) {
            throw new IllegalArgumentException("Finish the lead timings, and finish or clear any partially synced backing vocals.");
        }

        List<double[]> anchors = correctLineTimings ? timingAnchors() : Collections.emptyList();
        List<JsonObject> content = new ArrayList<>();
        for (Line line : lines) {
            if (line.lead.words.isEmpty() && line.background.stream().noneMatch(Vocal::hasTimings)) continue;

            JsonObject item = new JsonObject();
            item.addProperty("Type", "Vocal");
            item.addProperty("OppositeAligned", line.opposite);

            JsonObject lead = vocalJson(line.lead, line.originalStart, anchors);
            item.add("Lead", lead);

            JsonArray bg = new JsonArray();
            for (Vocal v : line.background) {
                if (v.hasTimings()) {
                    bg.add(vocalJson(v, line.originalStart, anchors));
                }
            }

            if (line.lead.words.isEmpty() && !bg.isEmpty()) {
                double anchor = Double.MAX_VALUE;

                for (JsonElement b : bg) {
                    anchor = Math.min(anchor, b.getAsJsonObject().get("StartTime").getAsDouble());
                }

                lead.addProperty("StartTime", anchor);
                lead.addProperty("EndTime", anchor);
            }

            if (!bg.isEmpty()) {
                item.add("Background", bg);
            }

            content.add(item);
        }

        if (content.isEmpty()) throw new IllegalArgumentException("Sync at least one vocal before saving.");

        double previousStart = -1;
        for (JsonObject item : content) {
            if (start(item) < previousStart) {
                throw new IllegalArgumentException("Backing vocals cross another line. Adjust their timings before saving.");
            }

            previousStart = start(item);
        }

        List<JsonObject> withGaps = new ArrayList<>();
        double end = 0;

        for (JsonObject item : content) {
            double start = start(item);

            if (start - end >= 2) {
                JsonObject gap = new JsonObject();

                gap.addProperty("Type", "Interlude");
                gap.addProperty("StartTime", end);
                gap.addProperty("EndTime", start);

                withGaps.add(gap);
            }

            withGaps.add(item);
            end = Math.max(end, end(item));
        }

        JsonObject result = new JsonObject();
        result.addProperty("Type", "Syllable");
        result.addProperty("Community", true);
        result.add("SongWriters", writers.deepCopy());

        JsonArray array = new JsonArray();
        withGaps.forEach(array::add);

        result.add("Content", array);
        result.addProperty("StartTime", withGaps.get(0).has("StartTime") ? withGaps.get(0).get("StartTime").getAsDouble() : start(withGaps.get(0)));
        result.addProperty("EndTime", end);

        return result;
    }

    private static double start(JsonObject group) {
        JsonObject lead = group.getAsJsonObject("Lead");
        double t = lead.getAsJsonArray("Syllables").isEmpty() ? Double.MAX_VALUE : lead.get("StartTime").getAsDouble();

        if (group.has("Background")) for (JsonElement b : group.getAsJsonArray("Background")) {
            t = Math.min(t, b.getAsJsonObject().get("StartTime").getAsDouble());
        }

        return t;
    }

    private static double end(JsonObject group) {
        double t = group.getAsJsonObject("Lead").get("EndTime").getAsDouble();

        if (group.has("Background")) for (JsonElement b : group.getAsJsonArray("Background")) {
            t = Math.max(t, b.getAsJsonObject().get("EndTime").getAsDouble());
        }

        return t;
    }
}

package com.lenerd46.spotifyplus.entities;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.lenerd46.spotifyplus.XposedLoader;
import com.lenerd46.spotifyplus.entities.lyrics.*;
import de.robv.android.xposed.XposedBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LyricUtilities {
    private static final String[] rightToLeftLanguages = {
            // Persian
            "pes", "urd",

            // Arabic Languages
            "arb", "uig",

            // Hebrew Languages
            "heb", "ydd",

            // Mende Languages
            "men"
    };

    private static NaturalAlignment getNaturalAlignment(String language) {
        for(String lang : rightToLeftLanguages) {
            if(language.equals(lang)) {
                return NaturalAlignment.RIGHT;
            } else {
                return NaturalAlignment.LEFT;
            }
        }

        return NaturalAlignment.LEFT;
    }

    private static String getLanguage(String text) {
        final LanguageDetector detector = LanguageDetectorBuilder.fromAllLanguages().build();
        final Language detectedLanguage = detector.detectLanguageOf(text);

        return detectedLanguage.getIsoCode639_1().toString();
    }

    public static TransformedLyrics transformLyrics(ProviderLyrics providedLyrics) {
        TransformedLyrics lyrics = new TransformedLyrics();
        lyrics.lyrics = providedLyrics;

        XposedBridge.log(lyrics.lyrics.toString());

        List<TimeMetadata> vocalTimes = new ArrayList<TimeMetadata>();

        if(lyrics.lyrics.staticLyrics instanceof StaticSyncedLyrics) {
            String textToProcess = lyrics.lyrics.staticLyrics.lines.stream().map(x -> x.text).collect(Collectors.joining("\n"));

            lyrics.language = getLanguage(textToProcess);
            lyrics.naturalAlignment = getNaturalAlignment(lyrics.language);
        } else if(lyrics.lyrics.lineLyrics instanceof LineSyncedLyrics) {

            try {
                List<String> lines = new ArrayList<>();
                List<LineVocal> lineVocals = new ArrayList<>();

                for(Object vocalGroup : lyrics.lyrics.lineLyrics.content) {
                    Gson gson = new Gson();
                    JsonElement jsonElement = gson.toJsonTree(vocalGroup);
                    LineVocal vocal = gson.fromJson(jsonElement, LineVocal.class);

                    if(vocal != null) {
                        lines.add(vocal.text);
                        lineVocals.add(vocal);

                        TimeMetadata time = new TimeMetadata();
                        time.startTime = vocal.startTime;
                        time.endTime = vocal.endTime;

                        vocalTimes.add(time);
                    }
                }

                lyrics.lyrics.lineLyrics.content = new ArrayList<>(lineVocals);
                String textToProcess = String.join("\n", lines);

                lyrics.language = getLanguage(textToProcess);
                lyrics.naturalAlignment = getNaturalAlignment(lyrics.language);

                // Romanization

                // Check if first vocal group needs an interlude before it
                boolean addedStartInterlude = false;
                var firstVocalGroup = vocalTimes.get(0);

                TimeMetadata time = new TimeMetadata();
                time.startTime = 0;
                time.endTime = firstVocalGroup.startTime - 0.25d;

                if(firstVocalGroup.startTime >= 2) {
                    vocalTimes.add(0, time);
                    var newList = new ArrayList<>(lyrics.lyrics.lineLyrics.content);

                    Interlude interlude = new Interlude();
                    interlude.time = time;
                    newList.add(0, interlude);

                    lyrics.lyrics.lineLyrics.content = newList;
                    addedStartInterlude = true;
                }

                for(int i = vocalTimes.size() - 1; i > (addedStartInterlude ? 1 : 0); i--) {
                    var endingVocalGroup = vocalTimes.get(i);
                    var startingvocalGroup = vocalTimes.get(i - 1);

                    if(endingVocalGroup.startTime - startingvocalGroup.endTime >= 2) {
                        vocalTimes.add(i, time);

                        TimeMetadata newTime = new TimeMetadata();
                        newTime.startTime = startingvocalGroup.endTime;
                        newTime.endTime = endingVocalGroup.startTime;

                        Interlude interlude = new Interlude();
                        interlude.time = newTime;
                        lyrics.lyrics.lineLyrics.content.add(i, interlude);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if(lyrics.lyrics.syllableLyrics instanceof SyllableSyncedLyrics) {
            List<String> lines = new ArrayList<>();
            List<SyllableVocalSet> vocalLines = new ArrayList<>();

            for(Object vocalGroup : lyrics.lyrics.syllableLyrics.content) {
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(vocalGroup);
                SyllableVocalSet vocalSet = gson.fromJson(jsonElement, SyllableVocalSet.class);

                if(vocalSet != null) {
                    String text = vocalSet.lead.syllables.get(0).text;

                    for(int i = 1; i < vocalSet.lead.syllables.size(); i++) {
                        var syllable = vocalSet.lead.syllables.get(i);
                        text += (syllable.isPartOfWord ? "" : " ") + syllable.text;
                    }

                    lines.add(text);
                    vocalLines.add(vocalSet);

                    double startTime = vocalSet.lead.startTime;
                    double endTime = vocalSet.lead.endTime;

                    if(vocalSet.background != null) {
                        for(var backgroundVocal : vocalSet.background) {
                            startTime = Math.min(startTime, backgroundVocal.startTime);
                            endTime = Math.max(endTime, backgroundVocal.endTime);
                        }
                    }

                    TimeMetadata time = new TimeMetadata();
                    time.startTime = startTime;
                    time.endTime = endTime;

                    vocalTimes.add(time);
                }
            }

            String textToProcess = String.join("\n", lines);

            lyrics.language = getLanguage(textToProcess);
            lyrics.naturalAlignment = getNaturalAlignment(lyrics.language);
            lyrics.lyrics.syllableLyrics.content = new ArrayList<>(vocalLines);

            // Romanization

            // Check if first vocal group needs an interlude before it
            boolean addedStartInterlude = false;
            var firstVocalGroup = vocalTimes.get(0);

            TimeMetadata time = new TimeMetadata();
            time.startTime = 0;
            time.endTime = firstVocalGroup.startTime - 0.25d;

            if(firstVocalGroup.startTime >= 2) {
                vocalTimes.add(0, time);
                var newList = new ArrayList<>(lyrics.lyrics.syllableLyrics.content);

                Interlude interlude = new Interlude();
                interlude.time = time;
                newList.add(0, interlude);

                lyrics.lyrics.syllableLyrics.content = newList;
                addedStartInterlude = true;
            }

            for(int i = vocalTimes.size() - 1; i > (addedStartInterlude ? 1 : 0); i--) {
                var endingVocalGroup = vocalTimes.get(i);
                var startingvocalGroup = vocalTimes.get(i - 1);

                if(endingVocalGroup.startTime - startingvocalGroup.endTime >= 2) {
                    vocalTimes.add(i, time);

                    TimeMetadata newTime = new TimeMetadata();
                    newTime.startTime = startingvocalGroup.endTime;
                    newTime.endTime = endingVocalGroup.startTime - 0.25d;

                    Interlude interlude = new Interlude();
                    interlude.time = newTime;
                    lyrics.lyrics.syllableLyrics.content.add(i, interlude);
                }
            }
        }

        return lyrics;
    }
}

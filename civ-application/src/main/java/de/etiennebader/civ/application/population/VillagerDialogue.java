package de.etiennebader.civ.application.population;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Worüber man mit einem Dorfbewohner reden kann (Issue #18), und welche Stimmungslage seine Antwort
 * bestimmt - keine echte Sprachverarbeitung, nur vorformulierte, nach Thema und Stimmung ausgewählte Antworten,
 * die {@code civ-server} in Texte übersetzt.
 */
public final class VillagerDialogue {

    /** Ein Gesprächsthema. */
    public enum Topic {
        GREETING, WEATHER, WORK, POLITICS;

        /** Alle Themenwörter für Befehlsvorschläge, klein geschrieben. */
        public static List<String> words() {
            return Arrays.stream(values()).map(topic -> topic.name().toLowerCase(Locale.ROOT)).toList();
        }
    }

    /** Ein Thema aus seinem Befehlswort, z. B. {@code "work"}; leer bei einem unbekannten Wort. */
    public static Optional<Topic> parseTopic(String word) {
        try {
            return Optional.of(Topic.valueOf(word.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Die drei Stimmungslagen, nach denen die Antwort ausgewählt wird. */
    public enum Mood {
        CONTENT, NEUTRAL, DISCONTENT
    }

    private VillagerDialogue() {
    }

    /** In welcher Stimmungslage ein Dorfbewohner gerade ist, für die Antwortauswahl. */
    public static Mood moodOf(Satisfaction satisfaction) {
        Objects.requireNonNull(satisfaction, "satisfaction");
        int score = satisfaction.score();
        if (score >= Satisfaction.CONTENT) {
            return Mood.CONTENT;
        }
        return score < -Satisfaction.CONTENT ? Mood.DISCONTENT : Mood.NEUTRAL;
    }
}

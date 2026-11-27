package de.etiennebader.civ.server.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.religion.ReligionId;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Ein Amt im Klerus einer Religion, von ihrer Führung frei erfunden: ein Name („Priester“, „Hüterin der Schrift“ …),
 * die Rechte, die das Amt verleiht, und die Amtsträger.
 */
record ClergyRole(ReligionId religion, String name, Set<Right> rights, Set<ActorRef> holders) {

    static final int MAX_NAME = 32;
    static final int MAX_PER_RELIGION = 12;

    /** Was ein Amtsträger darf. */
    enum Right {
        /** Dorfbewohner und Spieler segnen. */
        BLESS("segnen"),
        /** Taufen und damit in die Religion aufnehmen. */
        BAPTISE("taufen"),
        /** Gottesdienste halten. */
        SERVICE("gottesdienst"),
        /** Die Heilige Schrift bearbeiten. */
        SCRIPTURE("schrift");

        private final String word;

        Right(String word) {
            this.word = word;
        }

        String word() {
            return word;
        }

        static java.util.Optional<Right> ofWord(String word) {
            for (Right right : values()) {
                if (right.word.equalsIgnoreCase(word.strip())) {
                    return java.util.Optional.of(right);
                }
            }
            return java.util.Optional.empty();
        }
    }

    ClergyRole {
        Objects.requireNonNull(religion, "religion");
        Objects.requireNonNull(name, "name");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Amtsname muss 1 bis " + MAX_NAME + " Zeichen haben");
        }
        rights = new LinkedHashSet<>(rights);
        holders = new LinkedHashSet<>(holders);
    }
}

package de.etiennebader.civ.domain.media;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.shared.ArticleId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Zeitungsartikel.
 *
 * @param number    laufende Nummer über alle Zeitungen, ab 1
 * @param topic     das Thema, das der Artikel in den Vordergrund rückt (Framing); {@link Platform#NONE} ohne Thema
 * @param subject   über wen oder was berichtet wird, z. B. ein Kandidat, eine Partei, eine Gemeinde
 * @param tendency  Haltung zum Gegenstand von {@value #MIN_TENDENCY} (scharf kritisch) bis {@value #MAX_TENDENCY}
 *                  (lobend)
 * @param towns     Gemeinden, in denen die Ausgabe erscheint
 * @param readers   Dorfbewohner, die ihn gelesen haben
 */
public record Article(ArticleId id, int number, PolityId newspaper, ActorRef author, String title, String body,
                      Platform topic, Optional<ActorRef> subject, int tendency, Day day, List<PolityId> towns,
                      int readers) {

    public static final int MIN_TENDENCY = -2;
    public static final int MAX_TENDENCY = 2;
    public static final int MAX_TITLE = 64;
    public static final int MAX_BODY = 4000;

    public Article {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(newspaper, "newspaper");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(day, "day");
        towns = List.copyOf(towns);
        title = title.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE || body.length() > MAX_BODY) {
            throw new IllegalArgumentException("Titel 1 bis " + MAX_TITLE + ", Text bis " + MAX_BODY + " Zeichen");
        }
        if (tendency < MIN_TENDENCY || tendency > MAX_TENDENCY || readers < 0 || number < 0) {
            throw new IllegalArgumentException("Ungültiger Artikel");
        }
        if (subject.isEmpty() && tendency != 0) {
            throw new IllegalArgumentException("Tendenz ohne Gegenstand");
        }
    }

    Article numbered(int newNumber) {
        return new Article(id, newNumber, newspaper, author, title, body, topic, subject, tendency, day, towns,
                readers);
    }

    /** Ob er an diesem Tag noch wirkt: am Erscheinungstag und den {@code days - 1} Tagen danach. */
    public boolean freshOn(Day today, int days) {
        return !day.isAfter(today) && day.plus(days).isAfter(today);
    }
}

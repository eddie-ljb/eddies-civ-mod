package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;

/**
 * Die Verfassung einer Polity. Bis sie ausgerufen ist, ist sie ein Entwurf, den die Spitze frei gestalten darf.
 * Danach steht die Präambel fest, und Verfassungsartikel ({@code StandardParameters#constitutional()}) ändern sich
 * nur noch durch Abstimmung mit der dort festgelegten Mehrheit.
 *
 * @param proclaimedOn an welchem Tag sie ausgerufen wurde; leer, solange sie ein Entwurf ist
 * @param preamble     freier Text, höchstens {@link #MAX_PREAMBLE} Zeichen
 */
public record Constitution(Optional<Day> proclaimedOn, String preamble) {

    public static final int MAX_PREAMBLE = 600;
    public static final Constitution DRAFT = new Constitution(Optional.empty(), "");

    public Constitution {
        Objects.requireNonNull(proclaimedOn, "proclaimedOn");
        Objects.requireNonNull(preamble, "preamble");
        preamble = preamble.strip();
        if (preamble.length() > MAX_PREAMBLE) {
            throw new IllegalArgumentException("Präambel zu lang: " + preamble.length());
        }
    }

    public boolean proclaimed() {
        return proclaimedOn.isPresent();
    }

    Constitution proclaim(Day day) {
        return new Constitution(Optional.of(day), preamble);
    }

    Constitution withPreamble(String text) {
        return new Constitution(proclaimedOn, text);
    }
}

package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ergebnis eines Friedensschlusses nach einem entschiedenen Gefecht: Die unterlegene Seite
 * zahlt der siegreichen Reparationen.
 *
 * <p>Gebietsabtretungen ({@code Gebiete erobern}, siehe Issue #27) sind bewusst nicht Teil dieser
 * Ausbaustufe — das würde eine Abstimmung mit dem Territory-Modul (Chunk-Besitz) erfordern, die
 * hier nicht mehr geleistet wurde; siehe "bewusst nicht umgesetzte Teile" in der Doku.
 *
 * @param winner       die siegreiche Polity
 * @param loser        die unterlegene Polity
 * @param reparations  von {@code loser} an {@code winner} zu zahlender Betrag, in der kleinsten
 *                     Währungseinheit; {@code 0} bei einem Unentschieden oder verhandeltem Frieden
 */
public record PeaceTerms(PolityId winner, PolityId loser, long reparations) {

    public PeaceTerms {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
        if (winner.equals(loser)) {
            throw new IllegalArgumentException("Sieger und Verlierer dürfen nicht dieselbe Polity sein");
        }
        if (reparations < 0) {
            throw new IllegalArgumentException("Reparationen dürfen nicht negativ sein: " + reparations);
        }
    }
}

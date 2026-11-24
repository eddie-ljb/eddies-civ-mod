package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ergebnis eines entschiedenen Kriegs: Der Sieger erhält je nach Höhe seines Siegs <em>Siegpunkte</em> und gibt sie
 * für Reparationen und Gebiet (Grenz-Chunks) aus.
 *
 * <p>Solange {@link #settled()} {@code false} ist, hat der Sieger seine Punkte noch nicht verteilt; {@code reparations}
 * und {@code conquest} sind dann 0. Beim Verteilen ({@link WarRegistry#settleSpoils}) werden beide festgeschrieben.
 *
 * @param winner      die siegreiche Polity
 * @param loser       die unterlegene Polity
 * @param reparations von {@code loser} an {@code winner} gezahlter Betrag, in der kleinsten Währungseinheit
 * @param conquest    Zahl der Grenz-Chunks, die der Sieger vom Verlierer gefordert hat
 * @param points      Siegpunkte: je höher der Sieg, desto mehr; {@code 0} bei einem verhandelten Frieden
 * @param settled     ob der Sieger die Punkte bereits ausgegeben hat
 */
public record PeaceTerms(PolityId winner, PolityId loser, long reparations, int conquest, int points,
                         boolean settled) {

    /** Altes Format ohne Punkte: gilt als bereits verteilt. */
    public PeaceTerms(PolityId winner, PolityId loser, long reparations, int conquest) {
        this(winner, loser, reparations, conquest, 0, true);
    }

    public PeaceTerms(PolityId winner, PolityId loser, long reparations) {
        this(winner, loser, reparations, 0);
    }

    public PeaceTerms {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
        if (winner.equals(loser)) {
            throw new IllegalArgumentException("Sieger und Verlierer dürfen nicht dieselbe Polity sein");
        }
        if (conquest < 0) {
            throw new IllegalArgumentException("Eroberung darf nicht negativ sein");
        }
        if (reparations < 0) {
            throw new IllegalArgumentException("Reparationen dürfen nicht negativ sein: " + reparations);
        }
        if (points < 0) {
            throw new IllegalArgumentException("Siegpunkte dürfen nicht negativ sein: " + points);
        }
    }

    /** Frisch entschieden: Punkte stehen zur Verteilung bereit. */
    static PeaceTerms decided(PolityId winner, PolityId loser, int points) {
        return new PeaceTerms(winner, loser, 0, 0, points, false);
    }

    /** Die Punkte sind verteilt. */
    PeaceTerms settledWith(long reparations, int conquest) {
        return new PeaceTerms(winner, loser, reparations, conquest, points, true);
    }
}

package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;

/**
 * Ergebnis eines Gefechts.
 *
 * @param frontShift Verschiebung der Front (positiv zugunsten des Angreifers des Kriegs)
 * @param front      Frontstand danach, -100 (Verteidiger gewinnt) bis 100 (Angreifer gewinnt)
 * @param decided    Friedensbedingungen, wenn die Front damit entschieden ist
 */
public record BattleReport(WarId war, BattleKind kind, long aggressorPower, long defenderPower, int frontShift,
                           int front, int woundedAggressor, int woundedDefender, Optional<PeaceTerms> decided,
                           Day day) {

    public BattleReport {
        Objects.requireNonNull(war, "war");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(decided, "decided");
        Objects.requireNonNull(day, "day");
    }
}

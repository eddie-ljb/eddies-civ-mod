package de.etiennebader.civ.domain.conjuncture;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Random;

/**
 * Konjunkturphase und -stärke einer Wirtschaftszone (siehe {@code ConjunctureService} in {@code civ-server} für die
 * Zuordnung von Gemeinden zu Zonen; üblicherweise der oberste Bund, dem eine Gemeinde angehört).
 *
 * <p>Phase und Zykluslänge einer Zone sind rein aus ihrer Kennung abgeleitet: eine feste, aus der {@link PolityId}
 * ausgewürfelte Zykluslänge ({@value #MIN_CYCLE_DAYS}–{@value #MAX_CYCLE_DAYS} Tage) und ein ebenso fester Versatz
 * lassen die vier {@link BusinessPhase Phasen} nacheinander durchlaufen. Dadurch braucht der Zustand keine eigene
 * Speicherung (kein Neustart-Drift, keine Datenbankmigration) und ist trotzdem reproduzierbar: derselbe Tag ergibt
 * immer dieselbe Phase.
 *
 * <p>Zonen beeinflussen sich gegenseitig über die globale Stimmung: {@link #effectiveMultiplier} mischt den eigenen
 * Phasenwert mit dem Durchschnitt aller Zonen. Eine Regierungsmaßnahme (Rechtsparameter
 * {@code conjuncture_stimulus_percent}) dämpft den Effekt zusätzlich.
 */
public final class BusinessCycle {

    /** Kürzeste mögliche Zykluslänge einer Zone in Tagen. */
    public static final int MIN_CYCLE_DAYS = 24;
    /** Längste mögliche Zykluslänge einer Zone in Tagen. */
    public static final int MAX_CYCLE_DAYS = 48;

    /** Anteil der globalen Stimmung am Effekt einer einzelnen Zone (0..1). */
    private static final double GLOBAL_COUPLING = 0.15;
    /** Höchste Dämpfung durch eine Regierungsmaßnahme bei 100 % Stimulus. */
    private static final double MAX_DAMPENING = 0.80;
    private static final double MIN_MULTIPLIER = 0.5;
    private static final double MAX_MULTIPLIER = 1.5;
    private static final int PHASES = BusinessPhase.values().length;
    private static final long OFFSET_SALT = 0x5DEECE66DL;

    private BusinessCycle() {
    }

    /** Die Konjunkturphase einer Zone an einem bestimmten Tag. */
    public static BusinessPhase phaseOf(PolityId zone, Day day) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(day, "day");
        int cycleDays = cycleDaysOf(zone);
        int offset = offsetOf(zone, cycleDays);
        int position = Math.floorMod(day.value() + offset, cycleDays);
        int phaseLength = Math.max(1, cycleDays / PHASES);
        int index = Math.min(PHASES - 1, position / phaseLength);
        return BusinessPhase.values()[index];
    }

    /**
     * Der tatsächliche Effekt der Konjunktur auf diese Zone an diesem Tag: die eigene Phase, gemischt mit der
     * globalen Stimmung und gedämpft durch eine Regierungsmaßnahme.
     *
     * @param globalAverageMultiplier Durchschnitt von {@link BusinessPhase#baseMultiplier()} über alle bekannten
     *                                Zonen an diesem Tag; siehe {@code ConjunctureService#globalAverageMultiplier}
     * @param stimulusPercent         0..100, Rechtsparameter {@code conjuncture_stimulus_percent}: dämpft die
     *                                Abweichung vom neutralen Wert 1.0 um bis zu {@value #MAX_DAMPENING} entsprechenden
     *                                Anteil
     * @return Multiplikator, begrenzt auf {@value #MIN_MULTIPLIER}..{@value #MAX_MULTIPLIER}
     */
    public static double effectiveMultiplier(PolityId zone, Day day, double globalAverageMultiplier,
                                             long stimulusPercent) {
        double own = phaseOf(zone, day).baseMultiplier();
        double coupled = own * (1 - GLOBAL_COUPLING) + globalAverageMultiplier * GLOBAL_COUPLING;
        double deviation = coupled - 1.0;
        double dampening = Math.clamp(stimulusPercent, 0, 100) / 100.0 * MAX_DAMPENING;
        double damped = 1.0 + deviation * (1.0 - dampening);
        return Math.clamp(damped, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    private static int cycleDaysOf(PolityId zone) {
        return MIN_CYCLE_DAYS + new Random(seed(zone)).nextInt(MAX_CYCLE_DAYS - MIN_CYCLE_DAYS + 1);
    }

    private static int offsetOf(PolityId zone, int cycleDays) {
        return new Random(seed(zone) ^ OFFSET_SALT).nextInt(cycleDays);
    }

    private static long seed(PolityId zone) {
        var id = zone.value();
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }
}

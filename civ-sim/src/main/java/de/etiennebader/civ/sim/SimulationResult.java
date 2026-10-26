package de.etiennebader.civ.sim;

import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.sim.metrics.DailyMetrics;
import de.etiennebader.civ.sim.scenario.Scenario;

import java.util.Map;
import java.util.Objects;

/**
 * Ergebnis eines vollständigen Simulationslaufs.
 *
 * @param scenario     das gespielte Szenario
 * @param finalDay     die Kennzahlen des letzten Tages
 * @param rejections   abgelehnte Vorhaben nach Fehlercode, über den gesamten Lauf
 * @param totalEvents  Anzahl aller veröffentlichten Domain Events
 * @param saves        Statistik der Speichervorgänge, {@link SaveStatistics#NONE} ohne Autosave
 */
public record SimulationResult(Scenario scenario, DailyMetrics finalDay, Map<ErrorCode, Long> rejections,
                               long totalEvents, SaveStatistics saves) {

    public SimulationResult {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(finalDay, "finalDay");
        rejections = Map.copyOf(rejections);
        Objects.requireNonNull(saves, "saves");
    }
}

package de.etiennebader.civ.sim.scenario;

import java.util.List;

/**
 * Ein Szenario ist unvollständig oder enthält ungültige Werte. Sammelt alle Fehler auf einmal,
 * damit man eine Datei nicht Fehler für Fehler korrigieren muss.
 */
public final class InvalidScenarioException extends RuntimeException {

    private final List<String> problems;

    public InvalidScenarioException(String source, List<String> problems) {
        super("Ungültiges Szenario '" + source + "':\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}

package de.etiennebader.civ.server.conjuncture;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.conjuncture.BusinessCycle;
import de.etiennebader.civ.domain.conjuncture.BusinessPhase;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Konjunkturphasen je Wirtschaftszone (siehe {@link BusinessCycle}).
 *
 * <p>Eine Wirtschaftszone ist der höchste Bund, dem eine Gemeinde angehört (Nation &gt; Land &gt; Gemeinde),
 * dieselbe Regel wie bei der Kartenfarbe ({@code TerritoryColor}); eine Gemeinde ohne Bund ist ihre eigene Zone.
 * Firmen sind in diesem Modell nicht geografisch verankert (sie haben keine Gemeinde, in der sie sitzen) und
 * wirtschaften deshalb mit dem Durchschnitt aller Zonen statt einer einzelnen; siehe {@code docs/issue-06-konjunktur.md}
 * für die genaue Begründung und mögliche Erweiterung.
 */
public final class ConjunctureService {

    private final WorldQueries queries;

    public ConjunctureService(WorldQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /** Wirtschaftszone einer Gemeinde: der höchste Bund, dem sie angehört, sonst sie selbst. */
    public PolityView zoneOf(PolityView town) {
        Objects.requireNonNull(town, "town");
        List<PolityView> ancestors = queries.ancestorsOf(town.id());
        return ancestors.isEmpty() ? town : ancestors.get(ancestors.size() - 1);
    }

    /** Konjunkturphase der Zone einer Gemeinde an einem Tag. */
    public BusinessPhase phaseOf(PolityView town, Day day) {
        return BusinessCycle.phaseOf(zoneOf(town).id(), day);
    }

    /**
     * Tatsächlicher Effekt der Konjunktur auf die Zone einer Gemeinde an einem Tag: ihre Phase, gemischt mit der
     * globalen Stimmung und gedämpft durch eine etwaige Regierungsmaßnahme ({@link StandardParameters#CONJUNCTURE_STIMULUS_PERCENT})
     * der Zone selbst.
     */
    public double multiplierOf(PolityView town, Day day) {
        PolityView zone = zoneOf(town);
        long stimulus = queries.effectiveLaws(zone).valueOf(StandardParameters.CONJUNCTURE_STIMULUS_PERCENT);
        return BusinessCycle.effectiveMultiplier(zone.id(), day, globalAverageMultiplier(day), stimulus);
    }

    /**
     * Durchschnitt der rohen Phasenwerte (ohne Kopplung/Dämpfung) über alle bekannten Wirtschaftszonen an diesem Tag;
     * 1.0 (neutral), wenn es keine gibt. Dient sowohl als Kopplungsgröße für einzelne Zonen ({@link #multiplierOf})
     * als auch, mangels geografischer Verankerung, als Ersatzwert für Firmen (siehe Klassenkommentar).
     */
    public double globalAverageMultiplier(Day day) {
        Set<PolityId> zones = new LinkedHashSet<>();
        for (PolityView polity : queries.polities()) {
            if (polity.kind() == PolityKind.TERRITORIAL) {
                zones.add(zoneOf(polity).id());
            }
        }
        if (zones.isEmpty()) {
            return 1.0;
        }
        double sum = 0;
        for (PolityId zone : zones) {
            sum += BusinessCycle.phaseOf(zone, day).baseMultiplier();
        }
        return sum / zones.size();
    }

    /** Ansicht für Menü, Befehl und Webseite. */
    public record ConjunctureView(String zoneName, BusinessPhase phase, double multiplier) {
    }

    public ConjunctureView describe(PolityView town, Day day) {
        PolityView zone = zoneOf(town);
        return new ConjunctureView(zone.name().value(), phaseOf(town, day), multiplierOf(town, day));
    }
}

package de.etiennebader.civ.domain.conjuncture;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCycleTest {

    private final TestActors actors = new TestActors(7);
    private final PolityId zone = actors.polityId();
    private final PolityId otherZone = actors.polityId();

    @Test
    @DisplayName("Dieselbe Zone und derselbe Tag ergeben immer dieselbe Phase")
    void deterministic() {
        assertEquals(BusinessCycle.phaseOf(zone, Day.of(123)), BusinessCycle.phaseOf(zone, Day.of(123)));
        assertEquals(BusinessCycle.effectiveMultiplier(zone, Day.of(123), 1.0, 0),
                BusinessCycle.effectiveMultiplier(zone, Day.of(123), 1.0, 0));
    }

    @Test
    @DisplayName("Über einen langen Zeitraum durchläuft eine Zone alle vier Phasen")
    void cyclesThroughAllPhases() {
        Set<BusinessPhase> seen = EnumSet.noneOf(BusinessPhase.class);
        for (long day = 0; day < BusinessCycle.MAX_CYCLE_DAYS * 3L; day++) {
            seen.add(BusinessCycle.phaseOf(zone, Day.of(day)));
        }
        assertEquals(EnumSet.allOf(BusinessPhase.class), seen);
    }

    @Test
    @DisplayName("Zwei verschiedene Zonen laufen nicht zwangsläufig im Gleichschritt")
    void zonesAreIndependent() {
        boolean everDiffer = false;
        for (long day = 0; day < BusinessCycle.MAX_CYCLE_DAYS * 2L; day++) {
            if (BusinessCycle.phaseOf(zone, Day.of(day)) != BusinessCycle.phaseOf(otherZone, Day.of(day))) {
                everDiffer = true;
                break;
            }
        }
        assertTrue(everDiffer, "Erwartet, dass sich unabhängige Zonen irgendwann in der Phase unterscheiden");
    }

    @Test
    @DisplayName("Der Multiplikator bleibt stets im erlaubten Bereich 0.5..1.5")
    void multiplierStaysBounded() {
        for (long day = 0; day < 200; day++) {
            for (long stimulus : new long[] {0, 25, 50, 100}) {
                double multiplier = BusinessCycle.effectiveMultiplier(zone, Day.of(day), 1.4, stimulus);
                assertTrue(multiplier >= 0.5 && multiplier <= 1.5,
                        "Multiplikator außerhalb des Bereichs: " + multiplier);
            }
        }
    }

    @Test
    @DisplayName("Eine Regierungsmaßnahme (Stimulus) dämpft die Abweichung vom neutralen Wert 1.0")
    void stimulusDampensDeviation() {
        Day day = Day.of(5);
        double undamped = BusinessCycle.effectiveMultiplier(zone, day, 1.0, 0);
        double damped = BusinessCycle.effectiveMultiplier(zone, day, 1.0, 100);
        double deviationUndamped = Math.abs(undamped - 1.0);
        double deviationDamped = Math.abs(damped - 1.0);
        if (deviationUndamped > 0.0001) {
            assertTrue(deviationDamped < deviationUndamped,
                    "Stimulus sollte die Abweichung verringern: " + deviationUndamped + " -> " + deviationDamped);
        }
    }

    @Test
    @DisplayName("Die globale Stimmung zieht den Effekt einer Zone in ihre Richtung")
    void globalAverageCouplesZones() {
        Day day = Day.of(9);
        double withLowGlobal = BusinessCycle.effectiveMultiplier(zone, day, 0.5, 0);
        double withHighGlobal = BusinessCycle.effectiveMultiplier(zone, day, 1.5, 0);
        assertTrue(withHighGlobal > withLowGlobal,
                "Eine höhere globale Stimmung sollte den Effekt einer Zone anheben");
    }
}

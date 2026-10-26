package de.etiennebader.civ.sim.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeedsTest {

    private final Needs needs = new Needs();

    @Test
    @DisplayName("Zugehörigkeit wächst ohne Gemeinde bis 1 und verschwindet mit Beitritt")
    void belongingGrowsAndResets() {
        for (int day = 0; day < 20; day++) {
            needs.update(false, false, 0, 100, 0.1, 0.1);
        }
        assertEquals(1.0, needs.belonging(), 1e-9);
        assertEquals(0.0, needs.housing(), 1e-9);

        needs.update(true, true, 0, 100, 0.1, 0.1);
        assertEquals(0.0, needs.belonging(), 1e-9);
        assertEquals(0.1, needs.housing(), 1e-9);
    }

    @Test
    @DisplayName("Sicherheit: 1 ohne Geld, 0 ab Erreichen der Rücklage")
    void securityFollowsBalance() {
        needs.update(true, false, 0, 100, 0, 0);
        assertEquals(1.0, needs.security(), 1e-9);

        needs.update(true, false, 25, 100, 0, 0);
        assertEquals(0.75, needs.security(), 1e-9);

        needs.update(true, false, 500, 100, 0, 0);
        assertEquals(0.0, needs.security(), 1e-9);
    }
}

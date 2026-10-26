package de.etiennebader.civ.domain.law;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterKeyTest {

    @Test
    @DisplayName("Gleichheit nur über die ID")
    void equalityByIdOnly() {
        assertEquals(ParameterKey.of("rate", Long.class, 0L), ParameterKey.of("rate", Long.class, 9L));
    }

    @Test
    @DisplayName("accepts prüft Typ und Regel")
    void acceptsChecksTypeAndConstraint() {
        ParameterKey<Integer> positive = ParameterKey.constrained("count", Integer.class, 1, value -> value > 0);

        assertTrue(positive.accepts(5));
        assertFalse(positive.accepts(0));
        assertFalse(positive.accepts(5L));
        assertFalse(positive.accepts("5"));
    }

    @Test
    @DisplayName("Standardwert muss die eigene Regel erfüllen")
    void defaultMustSatisfyConstraint() {
        assertThrows(IllegalArgumentException.class,
                () -> ParameterKey.constrained("count", Integer.class, 0, value -> value > 0));
    }
}

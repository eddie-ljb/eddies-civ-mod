package de.etiennebader.civ.domain.law;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionParametersTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ExtensionParameters.resetForTests();
    }

    @Test
    @DisplayName("Angemeldete Parameter stehen im Standardkatalog und lassen sich dekodieren")
    void appearsInCatalog() {
        ParameterKey<Boolean> key = ExtensionParameters.register(ParameterKey.of("ae2.me_allowed", Boolean.class, true));

        ParameterCatalog catalog = ParameterCatalog.standard();

        assertTrue(catalog.find("ae2.me_allowed").isPresent());
        assertEquals(ParameterValue.of(key, false), catalog.decode("ae2.me_allowed", "false"));
    }

    @Test
    @DisplayName("IDs ohne Namensraum oder mit Standard-ID werden abgelehnt")
    void rejectsBadIds() {
        assertThrows(IllegalArgumentException.class,
                () -> ExtensionParameters.register(ParameterKey.of("ohne_namensraum", Boolean.class, true)));
        assertThrows(IllegalArgumentException.class,
                () -> ExtensionParameters.register(ParameterKey.of("Mod:Name", Boolean.class, true)));
        assertThrows(IllegalArgumentException.class,
                () -> ExtensionParameters.register(StandardParameters.TAX_EMBASSIES));
    }

    @Test
    @DisplayName("Gleiche Anmeldung ist idempotent, abweichende Definition derselben ID ein Fehler")
    void duplicates() {
        ParameterKey<Boolean> first = ExtensionParameters.register(ParameterKey.of("a.b", Boolean.class, true));

        assertSame(first, ExtensionParameters.register(ParameterKey.of("a.b", Boolean.class, true)));
        assertThrows(IllegalArgumentException.class,
                () -> ExtensionParameters.register(ParameterKey.of("a.b", Boolean.class, false)));
        assertEquals(1, ExtensionParameters.all().size());
    }

    @Test
    @DisplayName("Nach dem Einfrieren sind neue Anmeldungen verboten, bekannte weiter abfragbar")
    void frozen() {
        ExtensionParameters.register(ParameterKey.of("a.b", Boolean.class, true));
        ExtensionParameters.freeze();

        assertThrows(IllegalStateException.class,
                () -> ExtensionParameters.register(ParameterKey.of("a.c", Boolean.class, true)));
        ExtensionParameters.register(ParameterKey.of("a.b", Boolean.class, true));
    }
}

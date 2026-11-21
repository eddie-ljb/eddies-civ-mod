package de.etiennebader.civ.domain.polity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartyProfileTest {

    @Test
    @DisplayName("Ohne Angabe entscheidet der Vorsitz allein")
    void defaultsToChairMode() {
        assertEquals(ProgramMode.CHAIR, new PartyProfile(0, 0, PartyProfile.DEFAULT_EMBLEM).mode());
    }

    @Test
    @DisplayName("Richtung außerhalb von ±100 ist ungültig")
    void rejectsOutOfRangeDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> new PartyProfile(101, 0, PartyProfile.DEFAULT_EMBLEM));
        assertThrows(IllegalArgumentException.class,
                () -> new PartyProfile(0, -101, PartyProfile.DEFAULT_EMBLEM));
    }

    @Test
    @DisplayName("Wappen muss eine Item-ID sein")
    void rejectsInvalidEmblem() {
        assertThrows(IllegalArgumentException.class,
                () -> new PartyProfile(0, 0, "not an item id"));
    }

    @Test
    @DisplayName("with*-Methoden ändern nur das jeweilige Feld")
    void withMethodsChangeOnlyThatField() {
        PartyProfile profile = new PartyProfile(50, -20, "minecraft:red_banner").withMode(ProgramMode.VOTE);

        PartyProfile redirected = profile.withDirection(-10, 10);
        assertEquals(-10, redirected.order());
        assertEquals(10, redirected.economy());
        assertEquals(profile.emblem(), redirected.emblem());
        assertEquals(profile.mode(), redirected.mode());

        PartyProfile rebranded = profile.withEmblem("minecraft:blue_banner");
        assertEquals("minecraft:blue_banner", rebranded.emblem());
        assertEquals(profile.order(), rebranded.order());

        PartyProfile switched = profile.withMode(ProgramMode.CHAIR);
        assertEquals(ProgramMode.CHAIR, switched.mode());
        assertEquals(profile.order(), switched.order());
        assertEquals(profile.emblem(), switched.emblem());
    }

    @Test
    @DisplayName("Platform spiegelt die stärkste Achse, in der Mitte NONE")
    void platformReflectsStrongestAxis() {
        assertEquals(Platform.NONE, new PartyProfile(5, -5, PartyProfile.DEFAULT_EMBLEM).platform());
        assertEquals(Platform.ORDER, new PartyProfile(80, 0, PartyProfile.DEFAULT_EMBLEM).platform());
        assertEquals(Platform.FREEDOM, new PartyProfile(-80, 0, PartyProfile.DEFAULT_EMBLEM).platform());
        assertEquals(Platform.LOW_TAXES, new PartyProfile(0, -80, PartyProfile.DEFAULT_EMBLEM).platform());
        assertEquals(Platform.INVESTMENT, new PartyProfile(0, 80, PartyProfile.DEFAULT_EMBLEM).platform());
    }
}

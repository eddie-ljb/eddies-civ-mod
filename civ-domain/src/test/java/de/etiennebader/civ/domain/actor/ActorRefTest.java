package de.etiennebader.civ.domain.actor;

import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ActorRefTest {

    private final TestActors actors = new TestActors();

    @Test
    @DisplayName("Kanonische Ordnung: Spieler vor Villagern vor Polities, dann nach UUID")
    void canonicalOrderSortsByKindThenUuid() {
        PolityRef polity = actors.polity();
        PersonRef villager = actors.villager();
        PlayerRef first = actors.player();
        PlayerRef second = actors.player();
        PlayerRef lower = first.uuid().compareTo(second.uuid()) < 0 ? first : second;
        PlayerRef higher = lower == first ? second : first;

        List<ActorRef> sorted = new ArrayList<>(List.of(polity, higher, villager, lower));
        sorted.sort(ActorRef.CANONICAL_ORDER);

        assertEquals(List.of(lower, higher, villager, polity), sorted);
    }

    @Test
    @DisplayName("uuid() liefert die UUID der zugrunde liegenden ID")
    void uuidExposesUnderlyingId() {
        PlayerRef player = actors.player();

        assertEquals(player.id().value(), player.uuid());
        assertInstanceOf(ActorRef.class, player);
    }
}

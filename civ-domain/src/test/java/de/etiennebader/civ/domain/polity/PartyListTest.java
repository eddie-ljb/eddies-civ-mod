package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyListTest {

    private static PlayerRef player(long seed) {
        return new PlayerRef(new PlayerId(new UUID(0, seed)));
    }

    @Test
    @DisplayName("encode/decode ist ein Rundweg")
    void encodeDecodeRoundTrip() {
        List<ActorRef> order = List.of(player(1), player(2), player(3));
        assertEquals(order, PartyList.decode(PartyList.encode(order)));
    }

    @Test
    @DisplayName("leere Liste kodiert zu leerem Text und zurück")
    void emptyListRoundTrips() {
        assertEquals("", PartyList.encode(List.of()));
        assertTrue(PartyList.decode("").isEmpty());
        assertTrue(PartyList.decode("   ").isEmpty());
    }

    @Test
    @DisplayName("decode überspringt ungültige Einträge statt die ganze Liste zu verwerfen")
    void decodeSkipsInvalidEntries() {
        PlayerRef valid = player(42);
        String text = "PLAYER:" + valid.uuid() + ",KAPUTT,PLAYER:nicht-valide-uuid";
        assertEquals(List.of(valid), PartyList.decode(text));
    }

    @Test
    @DisplayName("apply bringt gelistete Kandidaten in Listenreihenfolge zuerst, Rest danach in Kandidatur-Reihenfolge")
    void applyOrdersListedFirst() {
        PlayerRef a = player(1);
        PlayerRef b = player(2);
        PlayerRef c = player(3);
        PlayerRef d = player(4);
        String list = PartyList.encode(List.of(c, a));

        assertEquals(List.of(c, a, b, d), PartyList.apply(list, List.of(a, b, c, d)));
    }

    @Test
    @DisplayName("ohne gesetzte Liste bleibt die Kandidatur-Reihenfolge unverändert")
    void applyWithoutListKeepsCandidacyOrder() {
        PlayerRef a = player(1);
        PlayerRef b = player(2);

        assertEquals(List.of(a, b), PartyList.apply("", List.of(a, b)));
    }

    @Test
    @DisplayName("listet nur Kandidaten, die tatsächlich kandidieren; Listeneinträge ohne Kandidatur fallen weg")
    void applyIgnoresListedNonCandidates() {
        PlayerRef a = player(1);
        PlayerRef b = player(2);
        PlayerRef notRunning = player(99);
        String list = PartyList.encode(List.of(notRunning, b, a));

        assertEquals(List.of(b, a), PartyList.apply(list, List.of(a, b)));
    }
}

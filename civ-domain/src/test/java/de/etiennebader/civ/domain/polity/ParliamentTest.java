package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParliamentTest {

    private static final Day DAY = Day.of(1);

    private final TestActors actors = new TestActors();
    private PlayerRef founder;
    private PlayerRef bob;
    private PersonRef villager;
    private Polity polity;

    @BeforeEach
    void found() {
        founder = actors.player();
        bob = actors.player();
        villager = actors.villager();
        polity = Polity.found(actors.polityId(), PolityName.of("Nordhain"), founder, CurrencyId.EMERALD, seatIn(0, 0),
                DAY);
        polity.admit(bob, MembershipType.CITIZEN, DAY);
        polity.admit(villager, MembershipType.RESIDENT, DAY);
        polity.pullEvents();
    }

    @Test
    @DisplayName("Sainte-Laguë: 300:100 bei drei Sitzen ergibt 2:1; Listen bekommen höchstens so viele Sitze wie Kandidaten")
    void sainteLague() {
        Map<String, Long> votes = new LinkedHashMap<>();
        votes.put("rot", 300L);
        votes.put("blau", 100L);
        assertEquals(Map.of("rot", 2, "blau", 1), SeatAllocation.sainteLague(votes, Map.of(), 3));
        assertEquals(Map.of("rot", 3, "blau", 1), SeatAllocation.sainteLague(votes, Map.of(), 4));
        assertEquals(Map.of("rot", 1, "blau", 1), SeatAllocation.sainteLague(votes, Map.of("rot", 1, "blau", 1), 4),
                "übrige Sitze bleiben frei, wenn keine Liste mehr Kandidaten hat");

        Map<String, Long> bundestag = new LinkedHashMap<>();
        bundestag.put("a", 5_000L);
        bundestag.put("b", 3_000L);
        bundestag.put("c", 1_500L);
        bundestag.put("d", 0L);
        assertEquals(Map.of("a", 5, "b", 3, "c", 2, "d", 0), SeatAllocation.sainteLague(bundestag, Map.of(), 10));
    }

    @Test
    @DisplayName("Parlament einsetzen: Präsidium zuerst, Fraktionen, Austritt kostet den Sitz, Auflösung")
    void seatAndLeave() {
        ElectionId election = new ElectionId(UUID.randomUUID());
        polity.callElection(election, Office.PARLIAMENT, Day.of(3), DAY);
        polity.standForElection(election, bob, DAY);
        polity.standForElection(election, villager, DAY);
        PolityId party = actors.polityId();
        polity.seatParliament(election, List.of(new Parliament.Seat(bob, Optional.of(party), 200),
                new Parliament.Seat(villager, Optional.empty(), 50)), Optional.of(Day.of(13)), Day.of(3));

        Parliament parliament = polity.parliament();
        assertTrue(parliament.seated());
        assertEquals(Optional.of(bob), parliament.president());
        assertEquals(2, parliament.factions().size());
        assertTrue(parliament.expiredOn(Day.of(13)));
        assertFalse(parliament.expiredOn(Day.of(12)));
        assertTrue(polity.elections().isEmpty());
        assertInstanceOf(ParliamentElected.class, polity.pullEvents().getLast());

        Polity restored = PolitySnapshot.of(polity).restore();
        assertEquals(parliament, restored.parliament());

        polity.leave(bob, DAY);
        assertEquals(Optional.of(villager), polity.parliament().president());
        assertTrue(polity.dissolveParliament(DAY));
        assertFalse(polity.parliament().seated());
        assertFalse(polity.dissolveParliament(DAY));
    }

    @Test
    @DisplayName("Nachrücker derselben Partei übernehmen frei gewordene Sitze; Parteilose haben keine")
    void successors() {
        PlayerRef carla = actors.player();
        polity.admit(carla, MembershipType.CITIZEN, DAY);
        ElectionId election = new ElectionId(UUID.randomUUID());
        polity.callElection(election, Office.PARLIAMENT, Day.of(3), DAY);
        polity.standForElection(election, bob, DAY);
        polity.standForElection(election, founder, DAY);
        PolityId party = actors.polityId();
        polity.seatParliament(election, List.of(new Parliament.Seat(bob, Optional.of(party), 300),
                        new Parliament.Seat(founder, Optional.empty(), 100)),
                List.of(new Parliament.Seat(carla, Optional.of(party), 50)), Optional.empty(), Day.of(3));
        assertEquals(Optional.of(carla), polity.parliament().successorOf(bob).map(Parliament.Seat::member));

        polity.leave(bob, DAY);
        assertEquals(List.of(carla, founder), polity.parliament().seats().stream().map(Parliament.Seat::member).toList());
        assertTrue(polity.parliament().reserves().isEmpty());
        assertEquals(carla, PolitySnapshot.of(polity).restore().parliament().president().orElseThrow());
    }

    @Test
    @DisplayName("Wahlergebnisse, Versprechen mit Bilanz und Plakate")
    void campaignRecords() {
        ElectionId election = new ElectionId(UUID.randomUUID());
        polity.callElection(election, Office.MAYOR, Day.of(3), DAY);
        polity.standForElection(election, bob, DAY);
        de.etiennebader.civ.domain.law.ParameterValue<Long> lowTax = de.etiennebader.civ.domain.law.ParameterValue
                .of(de.etiennebader.civ.domain.law.StandardParameters.SALES_TAX_PERCENT, 5L);
        Promise promise = new Promise(bob, Office.MAYOR, "Umsatzsteuer 5 %", List.of(lowTax), DAY);
        polity.promise(promise);
        assertThrows(NotACandidateException.class, () -> polity.promise(
                new Promise(founder, Office.MAYOR, "Alles wird gut", List.of(), DAY)));
        assertEquals(Optional.of(0.0), promise.keptShare(polity.laws()));
        assertEquals(Optional.empty(), new Promise(bob, Office.MAYOR, "Alles wird gut", List.of(), DAY)
                .keptShare(polity.laws()));

        for (int i = 0; i < Polity.KEPT_RESULTS + 2; i++) {
            polity.recordResult(new ElectionResult(new ElectionId(UUID.randomUUID()), Office.MAYOR, Day.of(i),
                    List.of(new ElectionResult.District(Optional.of(polity.id()), Map.of(bob, 200L))), List.of(bob)));
        }
        assertEquals(Polity.KEPT_RESULTS, polity.results().size());
        assertEquals(Optional.of(bob), polity.results().getLast().districts().getFirst().leader());

        Poster poster = new Poster(seatIn(0, 0), bob, bob, DAY);
        assertTrue(polity.putUpPoster(poster));
        Polity restored = PolitySnapshot.of(polity).restore();
        assertEquals(polity.results(), restored.results());
        assertEquals(polity.promises(), restored.promises());
        assertEquals(List.of(poster), restored.posters());
        assertTrue(polity.takeDownPoster(seatIn(0, 0)));
        assertFalse(polity.takeDownPoster(seatIn(0, 0)));
        polity.leave(bob, DAY);
        assertTrue(polity.promises().isEmpty(), "wer geht, nimmt seine Versprechen mit");
    }

    @Test
    @DisplayName("Das Parlament ist kein Einzelamt: weder besetzen noch mit closeElection beenden; nur Kandidaten bekommen Sitze")
    void notASingleOffice() {
        assertThrows(IllegalArgumentException.class,
                () -> polity.fillOffice(Office.PARLIAMENT, bob, Optional.empty(), DAY));
        ElectionId election = new ElectionId(UUID.randomUUID());
        polity.callElection(election, Office.PARLIAMENT, Day.of(3), DAY);
        assertThrows(IllegalArgumentException.class,
                () -> polity.closeElection(election, Optional.empty(), Optional.empty(), DAY));
        assertThrows(NotACandidateException.class, () -> polity.seatParliament(election,
                List.of(new Parliament.Seat(bob, Optional.empty(), 100)), Optional.empty(), DAY));
        polity.seatParliament(election, List.of(), Optional.empty(), DAY);
        assertFalse(polity.parliament().seated(), "ohne Sitze bleibt alles, wie es ist");
    }
}

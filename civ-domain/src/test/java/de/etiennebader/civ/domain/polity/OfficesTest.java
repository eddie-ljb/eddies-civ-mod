package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficesTest {

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
    @DisplayName("Ein Amt besetzen und frei machen löst Events aus; ein Dorfbewohner ist nie kommissarischer Bürgermeister")
    void fillAndVacate() {
        polity.fillOffice(Office.TREASURER, bob, Optional.of(Day.of(5)), DAY);
        assertTrue(polity.holds(bob, Office.TREASURER));
        assertTrue(polity.officeTerm(Office.TREASURER).orElseThrow().expiredOn(Day.of(5)));
        assertFalse(polity.officeTerm(Office.TREASURER).orElseThrow().expiredOn(Day.of(4)));

        assertTrue(polity.vacateOffice(Office.TREASURER, DAY));
        assertFalse(polity.vacateOffice(Office.TREASURER, DAY));
        assertEquals(List.of(new OfficeFilled(polity.id(), Office.TREASURER, bob, DAY),
                new OfficeVacated(polity.id(), Office.TREASURER, bob, DAY)), polity.pullEvents());
        assertEquals(Optional.of(founder), polity.mayor());
    }

    @Test
    @DisplayName("Nur Mitglieder bekleiden Ämter; eine Wahl je Amt; Stimmen nur für Kandidaten")
    void rules() {
        PlayerRef stranger = actors.player();
        assertDomainError(ErrorCode.NOT_A_MEMBER, () -> polity.fillOffice(Office.PLANNER, stranger, Optional.empty(), DAY));

        ElectionId election = ElectionId.generate(actors.ids());
        polity.callElection(election, Office.MAYOR, Day.of(3), DAY);
        assertDomainError(ErrorCode.ELECTION_ALREADY_OPEN,
                () -> polity.callElection(ElectionId.generate(actors.ids()), Office.MAYOR, Day.of(3), DAY));
        assertDomainError(ErrorCode.NOT_A_CANDIDATE, () -> polity.castBallot(election, founder, bob, DAY));
        polity.standForElection(election, bob, DAY);
        polity.castBallot(election, founder, bob, DAY);
        polity.castBallot(election, founder, bob, DAY);
        assertEquals(1L, polity.electionFor(Office.MAYOR).orElseThrow().votesFor(bob), "Stimme ersetzt, nicht doppelt");
        assertDomainError(ErrorCode.UNKNOWN_ELECTION,
                () -> polity.standForElection(ElectionId.generate(actors.ids()), bob, DAY));

        polity.closeElection(election, Optional.of(bob), Optional.empty(), Day.of(3));
        assertEquals(Optional.of(bob), polity.mayor());
        assertTrue(polity.elections().isEmpty());
    }

    @Test
    @DisplayName("Wiederherstellen prüft Amtsinhaber und doppelte Wahlen")
    void restoreValidates() {
        PolitySnapshot base = PolitySnapshot.of(polity);
        PlayerRef stranger = actors.player();
        PolitySnapshot strangerHoldsOffice = new PolitySnapshot(base.id(), base.name(), base.kind(), base.tier(),
                base.currency(), base.foundedOn(), base.seat(), base.members(), base.statutes(), base.proposals(),
                List.of(new OfficeTerm(Office.MAYOR, stranger, DAY, Optional.empty())), List.of());
        assertThrows(IllegalArgumentException.class, strangerHoldsOffice::restore);

        Election first = new Election(ElectionId.generate(actors.ids()), Office.PLANNER, DAY, Day.of(2), List.of(),
                List.of());
        Election second = new Election(ElectionId.generate(actors.ids()), Office.PLANNER, DAY, Day.of(2), List.of(),
                List.of());
        PolitySnapshot twoElections = new PolitySnapshot(base.id(), base.name(), base.kind(), base.tier(),
                base.currency(), base.foundedOn(), base.seat(), base.members(), base.statutes(), base.proposals(),
                List.of(), List.of(first, second));
        assertThrows(IllegalArgumentException.class, twoElections::restore);

        Polity restored = new PolitySnapshot(base.id(), base.name(), base.kind(), base.tier(), base.currency(),
                base.foundedOn(), base.seat(), base.members(), base.statutes(), base.proposals(),
                List.of(new OfficeTerm(Office.PLANNER, bob, DAY, Optional.of(Day.of(9)))), List.of(first)).restore();
        assertTrue(restored.holds(bob, Office.PLANNER));
        assertEquals(Optional.of(first), restored.electionFor(Office.PLANNER));
    }
}

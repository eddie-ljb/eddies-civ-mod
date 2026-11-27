package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.government.CoalitionBuilder.Outcome;
import de.etiennebader.civ.domain.government.CoalitionBuilder.Party;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalitionTest {

    private static PolityId id() {
        return new PolityId(UUID.randomUUID());
    }

    private static ActorRef actor() {
        return new PlayerRef(new PlayerId(UUID.randomUUID()));
    }

    @Test
    void strongestPartyBuildsMajorityWithCompatiblePartner() {
        PolityId left = id();
        PolityId centre = id();
        PolityId right = id();
        // 10 Sitze: Mitte 4, Links 3, Rechts 3 -> Mitte braucht einen Partner
        var result = CoalitionBuilder.form(List.of(new Party(left, 3, 0, -80), new Party(centre, 4, 0, 0),
                new Party(right, 3, 0, 80)), 10, 100, true, Day.of(1));
        assertEquals(Outcome.COALITION, result.outcome());
        Coalition c = result.coalition().orElseThrow();
        assertEquals(centre, c.leader());
        assertEquals(2, c.parties().size());
        assertFalse(c.minority());
    }

    @Test
    void leftAndRightNeverGovernTogether() {
        PolityId left = id();
        PolityId right = id();
        // beide zusammen hätten die Mehrheit, passen aber nicht zusammen
        var strict = CoalitionBuilder.form(List.of(new Party(left, 4, 0, -90), new Party(right, 4, 0, 90)), 9, 100,
                false, Day.of(1));
        assertEquals(Outcome.NEW_ELECTION, strict.outcome());
        assertEquals(2, strict.failed().size());
        var minority = CoalitionBuilder.form(List.of(new Party(left, 4, 0, -90), new Party(right, 4, 0, 90)), 9, 100,
                true, Day.of(1));
        assertEquals(Outcome.MINORITY, minority.outcome());
        assertTrue(minority.coalition().orElseThrow().minority());
    }

    @Test
    void nextPartyTriesWhenStrongestFails() {
        PolityId a = id();
        PolityId b = id();
        PolityId c = id();
        // A (4) ist weit von allen entfernt; B (3) und C (3) bilden zusammen mit 6 von 10 die Mehrheit
        var result = CoalitionBuilder.form(List.of(new Party(a, 4, 100, 100), new Party(b, 3, -50, -50),
                new Party(c, 3, -60, -40)), 10, 100, false, Day.of(1));
        assertEquals(Outcome.COALITION, result.outcome());
        assertEquals(List.of(a), result.failed());
        assertEquals(b, result.coalition().orElseThrow().leader());
    }

    @Test
    void ministriesFollowSeatStrength() {
        PolityId big = id();
        PolityId small = id();
        Map<String, PolityId> allocation = CoalitionBuilder.allocate(List.of("kanzleramt", "finanzen", "inneres",
                "bildung"), List.of(big, small), Map.of(big, 6, small, 2));
        assertEquals(big, allocation.get("kanzleramt"));
        assertEquals(3, allocation.values().stream().filter(big::equals).count());
        assertEquals(1, allocation.values().stream().filter(small::equals).count());
    }

    @Test
    void subsidyTapersWithWealth() {
        Subsidy sub = Subsidy.create("Kindergeld", Optional.empty(), "finanzen", 100, Optional.empty(), 1000, 2000, true);
        assertEquals(100, sub.amountFor(500));
        assertEquals(100, sub.amountFor(1000));
        assertEquals(50, sub.amountFor(1500));
        assertEquals(0, sub.amountFor(2000));
        assertEquals(0, sub.amountFor(5000));
        Subsidy flat = Subsidy.create("Hilfe", Optional.empty(), "finanzen", 10, Optional.of(Day.of(5)), 0, 0, true);
        assertEquals(10, flat.amountFor(1_000_000));
        assertTrue(flat.runsOn(Day.of(5)));
        assertFalse(flat.runsOn(Day.of(6)));
    }

    @Test
    void subsidyNeedsMajorityWhenPending() {
        Government g = new Government();
        g.create("Finanzministerium", "", Selection.APPOINTMENT);
        g.addSubsidy(Subsidy.create("Hilfe", Optional.empty(), "finanzministerium", 10, Optional.empty(), 0, 0, false));
        ActorRef a = actor();
        ActorRef b = actor();
        assertFalse(g.voteSubsidy("Hilfe", a, true, 3));
        assertTrue(g.voteSubsidy("Hilfe", b, true, 3));
        assertTrue(g.requireSubsidy("Hilfe").active());
        assertEquals(ErrorCode.SUBSIDY_ALREADY_ACTIVE,
                assertThrows(GovernmentException.class, () -> g.voteSubsidy("Hilfe", a, true, 3)).code());
    }

    @Test
    void ministerElectionPicksMostVotes() {
        Government g = new Government();
        g.create("Bildung", "", Selection.ELECTION);
        g.create("Finanzen", "", Selection.APPOINTMENT);
        assertEquals(ErrorCode.NOT_ELECTED_OFFICE,
                assertThrows(GovernmentException.class, () -> g.callElection("Finanzen", Day.of(3))).code());
        g.callElection("Bildung", Day.of(3));
        ActorRef a = actor();
        ActorRef b = actor();
        g.stand("Bildung", a);
        g.stand("Bildung", b);
        assertEquals(ErrorCode.ALREADY_CANDIDATE,
                assertThrows(GovernmentException.class, () -> g.stand("Bildung", a)).code());
        g.castBallot("Bildung", actor(), b);
        g.castBallot("Bildung", actor(), b);
        g.castBallot("Bildung", actor(), a);
        assertEquals(Optional.of(b), g.closeElection("Bildung"));
        assertTrue(g.isMinister(b, "Bildung"));
        assertTrue(g.election("bildung").isEmpty());
    }

    @Test
    void governmentInstallAndDismiss() {
        Government g = new Government();
        g.create("Kanzleramt", "", Selection.APPOINTMENT);
        g.create("Bildung", "", Selection.ELECTION);
        PolityId party = id();
        ActorRef chancellor = actor();
        g.install(new Coalition(List.of(party), false, Day.of(1), List.of()), Map.of("kanzleramt", chancellor));
        assertTrue(g.isMinister(chancellor, "Kanzleramt"));
        g.addAgreement("income_tax_percent", "5");
        assertEquals(1, g.coalition().orElseThrow().agreement().size());
        g.dismissGovernment();
        assertTrue(g.coalition().isEmpty());
        assertTrue(g.require("Kanzleramt").minister().isEmpty());
        assertEquals(ErrorCode.NO_COALITION, assertThrows(GovernmentException.class, g::dismissGovernment).code());
        assertEquals(Portfolio.CHANCELLERY, g.require("Kanzleramt").portfolio());
        assertEquals(ErrorCode.NOT_IN_PORTFOLIO, assertThrows(GovernmentException.class,
                () -> g.update("Kanzleramt", m -> m.withDecrees(true))).code());
    }
}

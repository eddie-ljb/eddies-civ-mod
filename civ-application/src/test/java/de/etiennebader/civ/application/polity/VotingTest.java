package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingTest {

    private static final ParameterValue<Boolean> OPEN_BORDERS = ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true);

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Als einziger Bürger gilt der eigene Antrag sofort")
    void soleCitizenDecidesAlone() {
        var result = propose(alice);

        assertEquals(VoteOutcome.ACCEPTED, result.outcome());
        assertTrue(polity().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        assertTrue(polity().proposals().isEmpty());
    }

    @Test
    @DisplayName("Mehrheit der Bürger entscheidet sofort; Dorfbewohner und Einwohner stimmen nicht")
    void majorityDecides() {
        join(bob, MembershipType.CITIZEN);
        join(carla, MembershipType.CITIZEN);
        join(fixture.villager(), MembershipType.RESIDENT);

        var result = propose(alice);
        assertEquals(VoteOutcome.OPEN, result.outcome());
        assertFalse(polity().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));

        assertEquals(VoteOutcome.ACCEPTED, vote(result, bob, true));
        assertTrue(polity().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
    }

    @Test
    @DisplayName("Ablehnung, sobald die Hälfte dagegen ist; zweiter Antrag zur selben Regel wird abgewiesen")
    void rejection() {
        join(bob, MembershipType.CITIZEN);
        var result = propose(alice);
        assertEquals(ErrorCode.PROPOSAL_ALREADY_OPEN, assertThrows(DomainException.class, () -> propose(bob)).code());

        assertEquals(VoteOutcome.REJECTED, vote(result, bob, false));
        assertFalse(polity().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        assertTrue(polity().proposals().isEmpty());
    }

    @Test
    @DisplayName("Nach Fristablauf entscheidet die Mehrheit der abgegebenen Stimmen")
    void tallyAtDeadline() {
        join(bob, MembershipType.CITIZEN);
        join(carla, MembershipType.CITIZEN);
        propose(alice);

        assertTrue(fixture.app.tallyProposals().execute(DAY.plus(1)).isEmpty());
        var tallied = fixture.app.tallyProposals().execute(DAY.plus(2));

        assertEquals(1, tallied.size());
        assertEquals(VoteOutcome.ACCEPTED, tallied.getFirst().outcome());
        assertTrue(polity().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
    }

    @Test
    @DisplayName("Nur Bürger dürfen beantragen und abstimmen")
    void onlyCitizens() {
        join(bob, MembershipType.RESIDENT);
        join(carla, MembershipType.CITIZEN);
        var result = propose(alice);

        assertEquals(ErrorCode.NOT_A_CITIZEN, assertThrows(DomainException.class, () -> propose(bob)).code());
        assertEquals(ErrorCode.NOT_A_CITIZEN, assertThrows(DomainException.class, () -> vote(result, bob, true)).code());
    }

    private ProposeLawChange.Result propose(PlayerRef citizen) {
        return fixture.app.proposeLawChange().execute(new ProposeLawChange.Command(nordhain, citizen, OPEN_BORDERS, DAY));
    }

    private VoteOutcome vote(ProposeLawChange.Result proposal, PlayerRef citizen, boolean inFavour) {
        return fixture.app.castVote().execute(new CastVote.Command(nordhain, proposal.proposal(), citizen, inFavour,
                Day.of(1)));
    }

    private void join(ActorRef member, MembershipType type) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, member, type, DAY));
    }

    private PolityView polity() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }
}

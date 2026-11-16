package de.etiennebader.civ.application.constitution;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.CastVote;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.ProposeLawChange;
import de.etiennebader.civ.application.polity.VoteOutcome;
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

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstitutionTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Entwurf: Spitze schreibt Präambel und ruft aus; danach steht die Präambel fest")
    void proclaim() {
        join(bob);
        assertEquals(ErrorCode.NOT_OFFICE_HOLDER, code(() -> fixture.app.proclaimConstitution()
                .execute(new ProclaimConstitution.Command(nordhain, bob, DAY))));
        fixture.app.changePreamble().execute(new ChangePreamble.Command(nordhain, alice, "  Wir, die Bürger.  ", DAY));
        assertEquals("Wir, die Bürger.", town().constitution().preamble());
        assertEquals(ErrorCode.PREAMBLE_TOO_LONG, code(() -> fixture.app.changePreamble()
                .execute(new ChangePreamble.Command(nordhain, alice, "x".repeat(601), DAY))));

        fixture.app.proclaimConstitution().execute(new ProclaimConstitution.Command(nordhain, alice, Day.of(3)));
        assertEquals(Optional.of(Day.of(3)), town().constitution().proclaimedOn());
        assertEquals(ErrorCode.CONSTITUTION_ALREADY_PROCLAIMED, code(() -> fixture.app.changePreamble()
                .execute(new ChangePreamble.Command(nordhain, alice, "Neu", DAY))));
        assertEquals(ErrorCode.CONSTITUTION_ALREADY_PROCLAIMED, code(() -> fixture.app.proclaimConstitution()
                .execute(new ProclaimConstitution.Command(nordhain, alice, DAY))));
    }

    @Test
    @DisplayName("Nach dem Ausrufen: Verfassungsartikel nicht mehr verordnen, gewöhnliches Recht schon")
    void articlesNeedVote() {
        ParameterValue<Boolean> byVote = new ParameterValue<>(StandardParameters.LAWS_BY_VOTE, true);
        fixture.app.proclaimConstitution().execute(new ProclaimConstitution.Command(nordhain, alice, DAY));
        assertEquals(ErrorCode.CONSTITUTION_REQUIRES_VOTE, code(() -> fixture.app.amendLaw()
                .execute(new AmendLaw.Command(nordhain, alice, byVote, DAY))));
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                new ParameterValue<>(StandardParameters.EXPLOSIONS_ALLOWED, true), DAY));
        assertTrue(town().laws().valueOf(StandardParameters.EXPLOSIONS_ALLOWED));
    }

    @Test
    @DisplayName("Verfassungsartikel brauchen zwei Drittel: 2 von 3 reichen, 1 Gegenstimme von 3 lässt es offen")
    void supermajority() {
        join(bob);
        join(carla);
        fixture.app.proclaimConstitution().execute(new ProclaimConstitution.Command(nordhain, alice, DAY));
        ParameterValue<Long> termDays = new ParameterValue<>(StandardParameters.TERM_DAYS, 10L);

        ProposeLawChange.Result result = fixture.app.proposeLawChange()
                .execute(new ProposeLawChange.Command(nordhain, alice, termDays, DAY));
        assertEquals(VoteOutcome.OPEN, result.outcome());
        assertEquals(VoteOutcome.OPEN, vote(result, carla, false), "2 von 3 sind noch möglich");
        assertEquals(VoteOutcome.ACCEPTED, vote(result, bob, true));
        assertEquals(10L, town().laws().valueOf(StandardParameters.TERM_DAYS));

        ParameterValue<Long> percent = new ParameterValue<>(StandardParameters.AMENDMENT_MAJORITY_PERCENT, 100L);
        ProposeLawChange.Result unanimous = fixture.app.proposeLawChange()
                .execute(new ProposeLawChange.Command(nordhain, alice, percent, DAY));
        assertEquals(VoteOutcome.OPEN, vote(unanimous, carla, false));
        assertEquals(VoteOutcome.REJECTED, vote(unanimous, bob, false), "zwei Drittel nicht mehr erreichbar");

        ProposeLawChange.Result deadline = fixture.app.proposeLawChange()
                .execute(new ProposeLawChange.Command(nordhain, alice, percent, DAY));
        vote(deadline, bob, false);
        fixture.app.tallyProposals().execute(Day.of(5));
        assertEquals(66L, town().laws().valueOf(StandardParameters.AMENDMENT_MAJORITY_PERCENT),
                "1 dafür, 1 dagegen: keine zwei Drittel der abgegebenen Stimmen");
        assertFalse(town().proposals().iterator().hasNext());
    }

    private VoteOutcome vote(ProposeLawChange.Result proposal, PlayerRef voter, boolean inFavour) {
        return fixture.app.castVote().execute(new CastVote.Command(nordhain, proposal.proposal(), voter, inFavour, DAY));
    }

    private void join(PlayerRef player) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, player, MembershipType.CITIZEN, DAY));
    }

    private PolityView town() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }

    private static ErrorCode code(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }
}

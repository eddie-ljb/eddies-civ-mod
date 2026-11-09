package de.etiennebader.civ.server.vote;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(41);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Mit laws_by_vote wird eine Rechtsänderung zum Antrag, über den die Bürger abstimmen")
    void lawChangeBecomesVote() {
        server.commands().found(caller(alice), "Nordhain");
        server.commands().amendLaw(caller(alice), "laws_by_vote", "ja");
        server.commands().join(caller(bob), "Nordhain");

        Reply proposed = server.commands().amendLaw(caller(alice), "outsiders_may_build", "ja");
        assertEquals(MessageKey.PROPOSAL_OPENED.key(), proposed.lines().getFirst().key());
        assertFalse(town().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        List<Announcement> announced = server.takeAnnouncements();
        assertEquals(1, announced.size());
        assertEquals(town().id(), announced.getFirst().town().orElseThrow());

        assertEquals(1, server.voting().proposalsOf(town(), bob).size());
        assertTrue(server.voting().vote(caller(bob), 1, true).success());
        assertTrue(town().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        assertEquals(MessageKey.PROPOSAL_ACCEPTED.key(), server.takeAnnouncements().getFirst().message().key());
        assertFalse(server.voting().vote(caller(bob), 1, true).success());
    }

    @Test
    @DisplayName("Ohne laws_by_vote gilt eine Änderung sofort")
    void withoutVotingLawsApplyImmediately() {
        server.commands().found(caller(alice), "Nordhain");

        server.commands().amendLaw(caller(alice), "outsiders_may_build", "ja");

        assertTrue(town().laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        assertTrue(town().proposals().isEmpty());
    }

    private PolityView town() {
        return server.queries().municipalityOf(alice).orElseThrow();
    }

    private static Caller caller(PlayerRef player) {
        return new Caller(player, "Spieler", new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64));
    }
}

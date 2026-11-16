package de.etiennebader.civ.server.constitution;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.vote.ProposalView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstitutionServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(61);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Entwurf, Präambel, Ausrufen; danach geht die Verfassungsänderung in die Abstimmung mit zwei Dritteln")
    void lifecycle() {
        towns();
        ConstitutionView draft = server.constitutions().view(named("Nordhain"), alice);
        assertFalse(draft.proclaimed());
        assertTrue(draft.head());
        assertEquals(StandardParameters.constitutional().size(), draft.articles().size());
        assertEquals(Optional.of(named("Nordhain")), server.constitutions().governing(at(8, 8)));
        assertTrue(server.constitutions().governing(at(5000, 5000)).isEmpty());

        assertFalse(server.constitutions().setPreamble(caller(bob), "Ich nicht").success());
        assertTrue(server.constitutions().setPreamble(caller(alice), "Wir, die Bürger von Nordhain.").success());
        assertTrue(server.constitutions().amend(caller(alice), "term_days", "7").success(), "Entwurf: Verordnung");
        assertEquals(7L, named("Nordhain").laws().valueOf(StandardParameters.TERM_DAYS));
        assertEquals(MessageKey.UNKNOWN_ARTICLE.key(),
                server.constitutions().amend(caller(alice), "explosions_allowed", "ja").lines().getFirst().key());

        assertTrue(server.constitutions().proclaim(caller(alice), Optional.empty()).success());
        assertFalse(server.constitutions().setPreamble(caller(alice), "Neu").success());

        Reply proposed = server.constitutions().amend(caller(alice), "term_days", "14");
        assertEquals(MessageKey.PROPOSAL_OPENED.key(), proposed.lines().getFirst().key());
        Reply decree = server.commands().amendLaw(caller(alice), "election_days", "5");
        assertEquals(MessageKey.PROPOSAL_OPENED.key(), decree.lines().getFirst().key(),
                "auch /civ recht setzen stellt bei Verfassungsartikeln einen Antrag");
        ConstitutionView proclaimed = server.constitutions().view(named("Nordhain"), bob);
        assertEquals(2, proclaimed.amendments().size());
        ProposalView amendment = proclaimed.amendments().getFirst();
        assertEquals(66L, amendment.requiredPercent());

        assertTrue(server.voting().vote(caller(bob), named("Nordhain").id(), amendment.id(), true).success());
        assertEquals(14L, named("Nordhain").laws().valueOf(StandardParameters.TERM_DAYS), "2 von 2 Bürgern");
        assertTrue(server.constitutions().show(caller(bob), Optional.empty()).lines().size() > 8);
    }

    @Test
    @DisplayName("Bund: Vorsitz statt Bürgermeister, kein Bauamt, Wahl und Kandidatur über die Polity-ID")
    void federationOrgans() {
        towns();
        server.federations().found(caller(alice), "land", "Westmark");
        PolityView land = named("Westmark");
        List<OfficeView> organs = server.constitutions().view(land, alice).offices();
        assertEquals(List.of(Office.MAYOR, Office.TREASURER), organs.stream().map(OfficeView::office).toList());
        assertEquals(TranslationKeys.FEDERATION_HEAD, organs.getFirst().name().key());

        assertTrue(server.offices().callElection(caller(alice), land.id(), Office.MAYOR).success());
        assertTrue(server.offices().stand(caller(bob), land.id(), Office.MAYOR, Platform.ORDER).success());
        assertTrue(server.offices().vote(caller(alice), land.id(), Office.MAYOR, bob).success());
        assertFalse(server.offices().vote(caller(carla), land.id(), Office.MAYOR, bob).success(), "kein Mitglied");
        assertTrue(server.constitutions().show(caller(carla), Optional.of("Westmark")).success());

        assertTrue(server.constitutions().proclaim(caller(alice), Optional.empty()).success(),
                "Alice führt Westmark als oberste Ebene");
        assertTrue(named("Westmark").constitution().proclaimed());
        assertFalse(named("Nordhain").constitution().proclaimed());
    }

    private void towns() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(carla, "Carla");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
        assertTrue(server.commands().found(caller(carla), "Eichenfurt").success());
    }

    private PolityView named(String name) {
        return server.queries().polityNamed(name).orElseThrow();
    }

    private static BlockPosition at(int x, int z) {
        return new BlockPosition(DimensionId.OVERWORLD, x, 64, z);
    }

    private Caller caller(PlayerRef player) {
        if (player.equals(carla)) {
            return new Caller(player, "Carla", at(800, 800));
        }
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", at(8, 8));
    }
}

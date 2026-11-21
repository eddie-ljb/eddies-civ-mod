package de.etiennebader.civ.server.federation;

import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.CastBallot;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerFederationsTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(7);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final Day day = Day.of(1);

    private PersonRef ambitiousVillager() {
        while (true) {
            PersonRef villager = new PersonRef(PersonId.generate(ids));
            if (VillagerAmbitions.isAmbitious(villager)) {
                return villager;
            }
        }
    }

    /** Alice gründet Nordhain; ein ehrgeiziger Dorfbewohner gewinnt die Bürgermeisterwahl. */
    private PersonRef villagerMayor() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8)),
                "Nordhain").success());
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        PersonRef villager = ambitiousVillager();
        server.app().joinPolity().execute(new JoinPolity.Command(town.id(), villager, MembershipType.CITIZEN, day));
        ElectionId election = server.app().callElection()
                .execute(new CallElection.Command(town.id(), alice, Office.MAYOR, day));
        server.app().standForElection().execute(new StandForElection.Command(town.id(), election, villager, day));
        server.app().castBallot().execute(new CastBallot.Command(town.id(), election, villager, villager, day));
        server.app().castBallot().execute(new CastBallot.Command(town.id(), election, alice, villager, day));
        server.app().runElections().execute(day.plus(5));
        assertEquals(java.util.Optional.of(villager), server.queries().polity(town.id()).orElseThrow().mayor());
        return villager;
    }

    @Test
    @DisplayName("Ein Dorfbewohner als Bürgermeister gründet ein Land und lädt einen Spielerort dazu ein")
    void foundsAndExpands() {
        PersonRef villager = villagerMayor();
        server.playerJoined(carla, "Carla");
        assertTrue(server.commands().found(new Caller(carla, "Carla", new BlockPosition(DimensionId.OVERWORLD, 800, 64, 800)),
                "Eichenfurt").success());

        PolityView federation = null;
        for (int i = 0; i < 600 && (federation == null
                || server.federations().invitationsFor(server.queries().polityNamed("Eichenfurt").orElseThrow().id()).isEmpty()); i++) {
            server.villagerFederations().endOfDay(Day.of(10 + i));
            federation = server.queries().federations().stream().findFirst().orElse(null);
        }
        assertTrue(federation != null, "ein Bund entstand");
        assertEquals("land", federation.level());
        assertEquals(java.util.Optional.of(villager), federation.mayor());
        PolityView eichenfurt = server.queries().polityNamed("Eichenfurt").orElseThrow();
        assertTrue(server.federations().invitationsFor(eichenfurt.id()).contains(federation.id()),
                "ein von einem Spieler geführter Ort wird eingeladen, nicht eingegliedert");
        assertTrue(server.queries().parentOf(eichenfurt.id()).isEmpty());
        assertEquals(PolityKind.FEDERATION, federation.kind());
    }

    @Test
    @DisplayName("Ohne ehrgeizige Bürgermeister geschieht nichts")
    void nothingWithoutAmbition() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8)),
                "Nordhain").success());
        for (int i = 0; i < 50; i++) {
            assertFalse(server.villagerFederations().endOfDay(Day.of(i)));
        }
        assertTrue(server.queries().federations().isEmpty());
    }
}

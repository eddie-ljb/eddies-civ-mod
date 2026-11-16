package de.etiennebader.civ.server.party;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.office.ElectionView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(81);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Gründen, beitreten, Richtung, Wappen; Kandidaten treten mit dem Parteiprogramm an")
    void playersAndElections() {
        town();
        assertEquals(MessageKey.UNKNOWN_DIRECTION.key(),
                server.parties().found(caller(alice), "links", "Linke").lines().getFirst().key());
        assertTrue(server.parties().found(caller(alice), "steuern", "Steuerzahlerbund").success());
        assertFalse(server.parties().found(caller(alice), "ordnung", "Zweite").success(), "nur eine Partei");
        assertTrue(server.parties().join(caller(bob), "steuerzahlerbund").success());
        assertFalse(server.parties().setDirection(caller(bob), 0, 50).success(), "nur der Vorsitz");
        assertTrue(server.parties().setEmblem(caller(alice), "minecraft:green_banner").success());
        assertFalse(server.parties().setDirection(caller(alice), 0, 150).success());
        assertTrue(server.parties().info(caller(bob), Optional.empty()).success());
        PartyView view = server.parties().parties(bob).getFirst();
        assertEquals(Platform.LOW_TAXES, view.platform());
        assertEquals(2, view.players());
        assertTrue(view.member());
        assertFalse(view.head());

        assertTrue(server.offices().callElection(caller(alice), "buergermeister").success());
        assertTrue(server.offices().stand(caller(bob), "buergermeister").success());
        ElectionView.Candidate candidate = server.offices().officesOf(nordhain(), alice).getFirst()
                .election().orElseThrow().candidates().getFirst();
        assertEquals(Platform.LOW_TAXES, candidate.platform(), "ohne eigenes Programm gilt das der Partei");
        assertEquals(Optional.of("Steuerzahlerbund"), candidate.party());

        assertTrue(server.parties().leave(caller(bob)).success());
        assertTrue(server.parties().dissolve(caller(alice)).success());
        assertTrue(server.queries().parties().isEmpty());
    }

    @Test
    @DisplayName("Am Tagesende gründen gleichgesinnte Dorfbewohner eine Partei")
    void villagersFound() {
        town();
        int found = 0;
        while (found < 6) {
            PersonRef villager = new PersonRef(PersonId.generate(ids));
            Personality personality = Personality.of(villager);
            if (personality.order() >= 50 && Math.abs(personality.thrift()) <= 25) {
                server.villagers().seen(villager, new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8), Day.of(1));
                found++;
            }
        }
        server.takeAnnouncements();
        server.endDayNow();
        PolityView party = server.queries().parties().getFirst();
        assertEquals(PolityKind.PARTY, party.kind());
        assertTrue(party.name().value().startsWith("Ordnungspartei"));
        assertEquals(6, party.memberCount());
        assertTrue(party.officeTerm(Office.MAYOR).isPresent());
        assertTrue(server.takeAnnouncements().stream()
                .anyMatch(a -> a.message().key().equals(MessageKey.VILLAGER_PARTY_FOUNDED.key())));
    }

    @Test
    @DisplayName("Baukasten: PvP, Monster und Tiere nach Gemeinde- bzw. Bundesrecht")
    void gameRules() {
        town();
        BlockPosition inside = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);
        BlockPosition wild = new BlockPosition(DimensionId.OVERWORLD, 5000, 64, 5000);
        assertTrue(server.protection().allowsPvp(inside));
        server.commands().amendLaw(caller(alice), "pvp_allowed", "nein");
        server.commands().amendLaw(caller(alice), "hostile_mobs_allowed", "nein");
        assertFalse(server.protection().allowsPvp(inside));
        assertTrue(server.protection().checkPvp(bob, inside).isPresent());
        assertTrue(server.protection().allowsPvp(wild));
        assertFalse(server.protection().allowsHostileMobs(inside));
        PlayerRef stranger = new PlayerRef(PlayerId.generate(ids));
        assertFalse(server.protection().mayHurtAnimal(stranger, inside));
        assertTrue(server.protection().mayHurtAnimal(bob, inside));
        assertTrue(server.protection().mayHurtAnimal(stranger, wild));
    }

    private void town() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
    }

    private PolityView nordhain() {
        return server.queries().polityNamed("Nordhain").orElseThrow();
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}

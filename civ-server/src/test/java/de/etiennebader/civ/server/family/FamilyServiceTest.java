package de.etiennebader.civ.server.family;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyServiceTest {

    private static final BlockPosition HERE = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);

    private final SeededIdGenerator ids = new SeededIdGenerator(27);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    private List<PersonRef> populate(int count) {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(new Caller(alice, "Alice", HERE), "Nordhain").success());
        List<PersonRef> villagers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PersonRef villager = new PersonRef(PersonId.generate(ids));
            server.villagers().seen(villager, HERE, Day.of(1));
            villagers.add(villager);
        }
        return villagers;
    }

    private void run(FamilyService family, int days) {
        for (int day = 2; day < 2 + days; day++) {
            family.endOfDay(Day.of(day));
        }
    }

    @Test
    @DisplayName("Dorfbewohner heiraten, bekommen Kinder und halten Regeln ein")
    void livesPlayOut() {
        List<PersonRef> villagers = populate(16);
        FamilyService family = server.family();
        run(family, 400);

        var registry = family.registry();
        assertFalse(registry.marriages().isEmpty(), "in 400 Tagen heiratet jemand");
        assertFalse(registry.children().isEmpty(), "und bekommt Kinder");
        assertFalse(family.takeBirths().isEmpty());
        assertTrue(family.takeBirths().isEmpty(), "Geburten werden nur einmal abgeholt");
        for (var marriage : registry.marriages()) {
            assertTrue(villagers.contains(marriage.first()) && villagers.contains(marriage.second()));
            assertTrue(marriage.children() <= de.etiennebader.civ.domain.family.Family.MAX_CHILDREN);
        }
        long married = villagers.stream().filter(registry::isMarried).count();
        assertEquals(registry.marriages().size() * 2L, married, "jeder höchstens einmal verheiratet");
    }

    @Test
    @DisplayName("Dieselbe Welt ergibt denselben Verlauf")
    void deterministic() {
        populate(12);
        FamilyService family = server.family();
        run(family, 150);
        var snapshot = family.registry().snapshot();

        SeededIdGenerator otherIds = new SeededIdGenerator(27);
        CivServerFixture other = CivServerFixture.start(otherIds);
        PlayerRef player = new PlayerRef(PlayerId.generate(otherIds));
        other.playerJoined(player, "Alice");
        assertTrue(other.commands().found(new Caller(player, "Alice", HERE), "Nordhain").success());
        for (int i = 0; i < 12; i++) {
            other.villagers().seen(new PersonRef(PersonId.generate(otherIds)), HERE, Day.of(1));
        }
        run(other.family(), 150);
        assertEquals(snapshot.marriages(), other.family().registry().snapshot().marriages());
    }

    @Test
    @DisplayName("Ehen, Beziehungen und Kinder überstehen einen Neustart")
    void persistence(@TempDir Path dir) {
        populate(16);
        Path file = dir.resolve("family.tsv");
        FamilyService writer = new FamilyService(server.queries(), new Announcements(), Optional.of(file));
        run(writer, 300);
        var expected = writer.registry().snapshot();
        assertFalse(expected.marriages().isEmpty());

        FamilyService reader = new FamilyService(server.queries(), new Announcements(), Optional.of(file));
        var actual = reader.registry().snapshot();
        assertEquals(expected.marriages(), actual.marriages());
        assertEquals(expected.children(), actual.children());
        assertEquals(expected.divorces(), actual.divorces());
        assertEquals(expected.bonds(), actual.bonds());
    }

    @Test
    @DisplayName("Die Auskunft über einen Verheirateten nennt Ehepartner und Kinder")
    void describeMentionsSpouse() {
        populate(16);
        FamilyService family = server.family();
        run(family, 400);
        PersonRef married = family.registry().marriages().get(0).first();
        assertFalse(family.describe(married).isEmpty());
    }

    @Test
    @DisplayName("Geburten bleiben vorgemerkt, bis das Spiel sie auf die Welt gebracht hat")
    void pendingBirthsUntilBorn() {
        populate(16);
        FamilyService family = server.family();
        run(family, 400);
        var pending = family.pendingBirths();
        assertFalse(pending.isEmpty());
        assertEquals(pending, family.pendingBirths(), "Ansehen entfernt nichts");
        family.born(pending.get(0).id());
        assertEquals(pending.size() - 1, family.pendingBirths().size());
    }

    @Test
    @DisplayName("Befehle zeigen Ehepaare und Kinder der eigenen Gemeinde")
    void commands() {
        populate(16);
        Caller alice = new Caller(this.alice, "Alice", HERE);
        assertEquals(de.etiennebader.civ.server.command.MessageKey.FAMILY_LIST_EMPTY.key(),
                server.family().couples(alice).lines().getFirst().key());
        assertEquals(de.etiennebader.civ.server.command.MessageKey.FAMILY_CHILDREN_EMPTY.key(),
                server.family().kids(alice).lines().getFirst().key());
        run(server.family(), 400);
        var couples = server.family().couples(alice);
        assertTrue(couples.success());
        assertEquals(de.etiennebader.civ.server.command.MessageKey.FAMILY_LIST_HEADER.key(),
                couples.lines().getFirst().key());
        assertEquals(server.family().registry().marriages().size() + 1, couples.lines().size());
        assertEquals(de.etiennebader.civ.server.command.MessageKey.FAMILY_CHILD_ENTRY.key(),
                server.family().kids(alice).lines().getFirst().key());
        assertFalse(server.family().couples(new Caller(new PlayerRef(PlayerId.generate(ids)), "Nobody", HERE)).success());
    }
}

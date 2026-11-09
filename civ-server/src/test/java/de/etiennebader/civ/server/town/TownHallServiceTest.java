package de.etiennebader.civ.server.town;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownHallServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(51);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final BlockPosition seat = new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64);

    @Test
    @DisplayName("Der Bürgermeister verlegt das Gemeindehaus im eigenen Gebiet; der alte Sitz wird gemeldet")
    void mayorMovesSeat() {
        server.commands().foundWithTownHall(caller(alice, seat), Optional.of("Nordhain"), seat);
        server.commands().join(caller(bob, seat), "Nordhain");
        BlockPosition newSeat = new BlockPosition(DimensionId.OVERWORLD, 2, 64, 2);

        assertFalse(server.townHalls().moveSeat(caller(bob, newSeat), newSeat).reply().success());
        var change = server.townHalls().moveSeat(caller(alice, newSeat), newSeat);

        assertTrue(change.reply().success());
        assertEquals(Optional.of(seat), change.oldSeat());
        assertEquals(newSeat, server.queries().municipalityOf(alice).orElseThrow().seat().orElseThrow());
    }

    @Test
    @DisplayName("Nur der Bürgermeister löst auf; danach ist das Gebiet frei")
    void mayorDissolves() {
        server.commands().foundWithTownHall(caller(alice, seat), Optional.of("Nordhain"), seat);
        server.commands().join(caller(bob, seat), "Nordhain");

        assertFalse(server.townHalls().dissolve(caller(bob, seat)).reply().success());
        var dissolved = server.townHalls().dissolve(caller(alice, seat));

        assertTrue(dissolved.reply().success());
        assertEquals(Optional.of(seat), dissolved.oldSeat());
        assertTrue(server.queries().polities().isEmpty());
        assertTrue(server.queries().municipalityOf(bob).isEmpty());
        assertEquals(1, server.takeAnnouncements().size());
    }

    private static Caller caller(PlayerRef player, BlockPosition position) {
        return new Caller(player, "Spieler", position);
    }
}

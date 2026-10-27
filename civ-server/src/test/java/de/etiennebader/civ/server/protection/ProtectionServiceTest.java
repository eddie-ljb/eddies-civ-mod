package de.etiennebader.civ.server.protection;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(13);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef dave = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final ProtectionService protection = server.protection();
    private final BlockPosition townHall = new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64);
    private final BlockPosition inTown = new BlockPosition(DimensionId.OVERWORLD, 2, 64, 2);

    @BeforeEach
    void foundTown() {
        server.commands().foundWithTownHall(new Caller(alice, "Alice", townHall), Optional.of("Nordhain"), townHall);
    }

    @Test
    @DisplayName("Fremde bekommen einen Hinweis mit dem Namen der Gemeinde")
    void deniesOutsiderWithMessage() {
        assertEquals(Optional.of(Message.of(MessageKey.PROTECTED_TERRITORY, "Nordhain")),
                protection.check(dave, ProtectedAction.BUILD, inTown));
        assertTrue(protection.check(alice, ProtectedAction.BUILD, inTown).isEmpty());
    }

    @Test
    @DisplayName("Ein Gesetz öffnet das Gebiet für Fremde")
    void lawOpensTerritory() {
        server.commands().amendLaw(new Caller(alice, "Alice", inTown), "outsiders_may_build", "ja");

        assertTrue(protection.check(dave, ProtectedAction.BUILD, inTown).isEmpty());
    }

    @Test
    @DisplayName("Das Gemeindehaus ist geschützt; ein Admin kann den Schutz für sich umgehen und wieder einschalten")
    void bypassForAdmins() {
        assertEquals(Optional.of(Message.of(MessageKey.PROTECTED_SEAT, "Nordhain")),
                protection.check(alice, ProtectedAction.BUILD, townHall));

        assertTrue(protection.toggleBypass(alice));
        assertTrue(protection.check(alice, ProtectedAction.BUILD, townHall).isEmpty());
        assertFalse(protection.toggleBypass(alice));
        assertTrue(protection.check(alice, ProtectedAction.BUILD, townHall).isPresent());
    }
}

package de.etiennebader.civ.application.query;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.protection.DenialReason;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.seat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldQueriesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
    private final WorldQueries queries = fixture.app.queries();

    @Test
    @DisplayName("Die Gemeinde lässt sich über den Standort ihres Gemeindehauses finden")
    void findsPolityBySeat() {
        assertEquals(nordhain, queries.polityWithSeat(seat(0, 0)).orElseThrow().id());
        assertTrue(queries.polityWithSeat(seat(1, 0)).isEmpty());
    }

    @Test
    @DisplayName("Der Gebietsschutz wird mit dem aktuellen Arbeitsstand geprüft")
    void checksAccessAgainstWorkingState() {
        PlayerRef outsider = fixture.player();
        BlockPosition inTown = new BlockPosition(DimensionId.OVERWORLD, 3, 70, 3);

        assertTrue(queries.access(alice, ProtectedAction.BUILD, inTown).isEmpty());
        assertEquals(DenialReason.FOREIGN_TERRITORY,
                queries.access(outsider, ProtectedAction.BUILD, inTown).orElseThrow().reason());
        assertEquals(DenialReason.SEAT, queries.access(alice, ProtectedAction.BUILD, seat(0, 0)).orElseThrow().reason());
    }
}

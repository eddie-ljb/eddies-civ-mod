package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.chunk;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DissolveAndMoveTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Auflösen räumt Grundstücke und Gebiet, die Kasse geht an den Erben")
    void dissolveClearsEverything() {
        fixture.app.createPlot().execute(new CreatePlot.Command(nordhain, Area.ofChunk(chunk(0, 0)), PlotType.PUBLIC, DAY));
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(300), DAY));
        fixture.app.transferFunds().execute(new TransferFunds.Command(alice, fixture.app.queries().polity(nordhain)
                .orElseThrow().asActor(), emeralds(200), TransferReason.GRANT, DAY));

        assertEquals(emeralds(200), fixture.app.dissolvePolity().execute(new DissolvePolity.Command(nordhain, alice, DAY)));

        assertTrue(fixture.app.queries().polity(nordhain).isEmpty());
        assertTrue(fixture.app.queries().territory().claimAt(chunk(0, 0)).isEmpty());
        assertTrue(fixture.app.queries().municipalityOf(alice).isEmpty());
        assertEquals(emeralds(300), fixture.app.queries().ledger().balanceOf(alice, EMERALD));
    }

    @Test
    @DisplayName("Der Sitz lässt sich nur ins eigene Gebiet verlegen")
    void movesSeatWithinTerritory() {
        BlockPosition inside = new BlockPosition(DimensionId.OVERWORLD, 3, 70, 3);
        fixture.app.moveSeat().execute(new MoveSeat.Command(nordhain, alice, inside, DAY));
        assertEquals(inside, fixture.app.queries().polity(nordhain).orElseThrow().seat().orElseThrow());

        BlockPosition outside = new BlockPosition(DimensionId.OVERWORLD, 300, 70, 300);
        assertEquals(ErrorCode.NOT_SOVEREIGN, assertThrows(DomainException.class,
                () -> fixture.app.moveSeat().execute(new MoveSeat.Command(nordhain, alice, outside, DAY))).code());
    }
}

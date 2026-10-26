package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.FundsTransferred;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotTransferred;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotUseCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
    private final PolityRef nordhainActor = new PolityRef(nordhain);

    @Test
    @DisplayName("Verkauf: Grundstück und Kaufpreis wechseln gemeinsam")
    void sellTransfersPlotAndMoney() {
        PlotId plot = createPlot(PlotType.RESIDENTIAL);
        deposit(bob, 500);
        fixture.events.clear();

        sell(plot, nordhainActor, bob, 300);

        assertTrue(land().plot(plot).orElseThrow().isHeldBy(bob));
        assertEquals(emeralds(200), fixture.app.queries().ledger().balanceOf(bob, EMERALD));
        assertEquals(emeralds(300), fixture.app.queries().ledger().balanceOf(nordhainActor, EMERALD));
        assertEquals(1, fixture.events.eventsOfType(PlotTransferred.class).size());
        assertEquals(1, fixture.events.eventsOfType(FundsTransferred.class).size());
    }

    @Test
    @DisplayName("Käufer kann nicht zahlen → INSUFFICIENT_FUNDS, nichts ändert sich")
    void sellWithoutFundsChangesNothing() {
        PlotId plot = createPlot(PlotType.RESIDENTIAL);
        deposit(bob, 100);
        fixture.events.clear();

        assertEquals(ErrorCode.INSUFFICIENT_FUNDS,
                assertThrows(DomainException.class, () -> sell(plot, nordhainActor, bob, 300)).code());

        assertTrue(land().plot(plot).orElseThrow().isHeldBy(nordhainActor));
        assertEquals(emeralds(100), fixture.app.queries().ledger().balanceOf(bob, EMERALD));
        assertTrue(fixture.events.events().isEmpty());
    }

    @Test
    @DisplayName("Botschaft an einen Spieler verkaufen → INVALID_EMBASSY_HOLDER, kein Geld bewegt")
    void embassySaleToPlayerMovesNoMoney() {
        PlotId embassy = createPlot(PlotType.EMBASSY);
        deposit(bob, 500);

        assertEquals(ErrorCode.INVALID_EMBASSY_HOLDER,
                assertThrows(DomainException.class, () -> sell(embassy, nordhainActor, bob, 100)).code());
        assertEquals(emeralds(500), fixture.app.queries().ledger().balanceOf(bob, EMERALD));
    }

    @Test
    @DisplayName("Verkauf zum Preis 0 überträgt nur das Grundstück")
    void freeSaleTransfersOnlyPlot() {
        PlotId plot = createPlot(PlotType.RESIDENTIAL);

        sell(plot, nordhainActor, bob, 0);

        assertTrue(land().plot(plot).orElseThrow().isHeldBy(bob));
        assertTrue(fixture.events.eventsOfType(FundsTransferred.class).isEmpty());
    }

    @Test
    @DisplayName("Vergabe und Auflösung eines Grundstücks")
    void assignAndDissolve() {
        PlotId plot = createPlot(PlotType.PUBLIC);

        fixture.app.assignPlot().execute(new AssignPlot.Command(nordhain, plot, alice, DAY));
        assertTrue(land().plot(plot).orElseThrow().isHeldBy(alice));

        fixture.app.dissolvePlot().execute(new DissolvePlot.Command(nordhain, plot, DAY));
        assertTrue(land().plots().isEmpty());
    }

    private PlotId createPlot(PlotType type) {
        return fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, 0, 0, 7, 7), type, DAY));
    }

    private void sell(PlotId plot, ActorRef seller, ActorRef buyer, long price) {
        fixture.app.sellPlot().execute(new SellPlot.Command(nordhain, plot, seller, buyer, emeralds(price), DAY));
    }

    private void deposit(PlayerRef account, long amount) {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(account, emeralds(amount), DAY));
    }

    private LandView land() {
        return fixture.app.queries().land(nordhain);
    }
}

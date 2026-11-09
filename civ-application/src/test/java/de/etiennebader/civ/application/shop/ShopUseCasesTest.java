package de.etiennebader.civ.application.shop;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopUseCasesTest {

    private static final BlockPosition IN_TOWN = new BlockPosition(DimensionId.OVERWORLD, 4, 64, 4);
    private static final BlockPosition WILDERNESS = new BlockPosition(DimensionId.OVERWORLD, 900, 64, 900);

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Kauf im Gemeindegebiet: Umsatzsteuer geht an die Gemeinde, der Rest an den Inhaber")
    void salesTaxGoesToTown() {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                ParameterValue.of(StandardParameters.SALES_TAX_PERCENT, 10L), DAY));
        open(IN_TOWN, 50);
        deposit(bob, 1_000);

        BuyFromShop.Receipt receipt = buy(IN_TOWN, 4);

        assertEquals(emeralds(200), receipt.total());
        assertEquals(emeralds(20), receipt.tax());
        assertEquals(emeralds(180), balance(alice));
        assertEquals(emeralds(20), fixture.app.queries().ledger()
                .balanceOf(fixture.app.queries().polity(nordhain).orElseThrow().asActor(), EMERALD));
        assertEquals(emeralds(800), balance(bob));
    }

    @Test
    @DisplayName("In der Wildnis keine Steuer; ohne Geld kein Kauf; geschlossene Läden verkaufen nichts")
    void wildernessAndErrors() {
        open(WILDERNESS, 50);
        deposit(bob, 60);

        assertEquals(emeralds(0), buy(WILDERNESS, 1).tax());
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, assertThrows(DomainException.class, () -> buy(WILDERNESS, 1)).code());

        fixture.app.closeShop().execute(new CloseShop.Command(WILDERNESS, Optional.of(alice), DAY));
        assertTrue(fixture.app.queries().shops().shops().isEmpty());
        assertEquals(ErrorCode.UNKNOWN_SHOP, assertThrows(DomainException.class, () -> buy(WILDERNESS, 1)).code());
    }

    private void open(BlockPosition position, long price) {
        fixture.app.openShop().execute(new OpenShop.Command(position, alice, Commodity.of("minecraft:bread"),
                emeralds(price), DAY));
    }

    private BuyFromShop.Receipt buy(BlockPosition position, int quantity) {
        return fixture.app.buyFromShop().execute(new BuyFromShop.Command(position, bob, quantity, DAY));
    }

    private void deposit(PlayerRef player, long amount) {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(player, emeralds(amount), DAY));
    }

    private de.etiennebader.civ.domain.shared.Money balance(PlayerRef player) {
        return fixture.app.queries().ledger().balanceOf(player, EMERALD);
    }
}

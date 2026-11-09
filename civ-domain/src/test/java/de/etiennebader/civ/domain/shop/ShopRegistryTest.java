package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopRegistryTest {

    private static final Day DAY = Day.of(1);
    private static final BlockPosition CHEST = new BlockPosition(DimensionId.OVERWORLD, 5, 64, 5);
    private static final Commodity BREAD = Commodity.of("minecraft:bread");

    private final SeededIdGenerator ids = new SeededIdGenerator(71);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final ShopRegistry shops = ShopRegistry.empty();

    @Test
    @DisplayName("Inhaber eröffnet und ändert seinen Laden; Fremde können ihn weder übernehmen noch schließen")
    void ownerControlsShop() {
        shops.open(CHEST, alice, BREAD, emeralds(20), DAY);
        Shop changed = shops.open(CHEST, alice, BREAD, emeralds(25), Day.of(3));

        assertEquals(DAY, changed.openedOn());
        assertEquals(emeralds(25), shops.shopAt(CHEST).orElseThrow().price());
        assertEquals(ErrorCode.NOT_SHOP_OWNER, assertThrows(DomainException.class,
                () -> shops.open(CHEST, bob, BREAD, emeralds(1), DAY)).code());
        assertEquals(ErrorCode.NOT_SHOP_OWNER, assertThrows(DomainException.class,
                () -> shops.close(CHEST, bob, DAY)).code());
        assertEquals(List.of(changed), shops.shopsOf(alice));

        shops.pullEvents();
        shops.close(CHEST, alice, DAY);
        assertTrue(shops.shopAt(CHEST).isEmpty());
        assertEquals(List.of(new ShopClosed(CHEST, DAY)), shops.pullEvents());
        assertEquals(ErrorCode.UNKNOWN_SHOP, assertThrows(DomainException.class,
                () -> shops.close(CHEST, alice, DAY)).code());
    }

    private static Money emeralds(long units) {
        return Money.of(units, CurrencyId.EMERALD);
    }
}

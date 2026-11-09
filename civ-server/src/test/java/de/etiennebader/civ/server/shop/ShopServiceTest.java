package de.etiennebader.civ.server.shop;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.market.ItemTraits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopServiceTest {

    private static final ItemTraits BREAD = new ItemTraits("minecraft:bread", "item.minecraft.bread",
            ItemTraits.Rarity.COMMON, 64, false, false);
    private static final BlockPosition CHEST = new BlockPosition(DimensionId.OVERWORLD, 4, 64, 4);

    private final SeededIdGenerator ids = new SeededIdGenerator(81);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final ShopService shops = server.shops();

    @Test
    @DisplayName("Laden nur, wo man bauen darf; Kauf prüft Lager und Geld")
    void openAndBuy() {
        server.commands().found(new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8)), "Nordhain");
        assertEquals(Reply.failure(Message.of(MessageKey.SHOP_NOT_HERE)), shops.open(at(bob), CHEST, BREAD, "1"));
        assertTrue(shops.open(at(alice), CHEST, BREAD, "0,5").success());
        server.bank().deposit(at(bob), 2);

        assertFalse(shops.buy(at(alice), CHEST, BREAD, 1, 10).success());
        assertEquals(Reply.failure(Message.of(MessageKey.SHOP_OUT_OF_STOCK, 3)), shops.buy(at(bob), CHEST, BREAD, 4, 3));
        assertTrue(shops.buy(at(bob), CHEST, BREAD, 3, 3).success());
        assertEquals(50, server.bank().balanceOf(bob).amount());
        assertEquals(150, server.bank().balanceOf(alice).amount());
        assertFalse(shops.buy(at(bob), CHEST, BREAD, 2, 10).success());

        assertFalse(shops.close(at(bob), CHEST).success());
        assertTrue(shops.close(at(alice), CHEST).success());
        assertTrue(shops.shopAt(CHEST).isEmpty());
    }

    private static Caller at(PlayerRef player) {
        return new Caller(player, "Spieler", CHEST);
    }
}

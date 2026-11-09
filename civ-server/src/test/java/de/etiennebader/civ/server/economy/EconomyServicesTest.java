package de.etiennebader.civ.server.economy;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.bank.BankService;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.market.BasePrices;
import de.etiennebader.civ.server.market.ItemTraits;
import de.etiennebader.civ.server.market.MarketService;
import de.etiennebader.civ.server.territory.Relation;
import de.etiennebader.civ.server.territory.TerritoryChange;
import de.etiennebader.civ.server.territory.TerritoryWatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyServicesTest {

    private static final ItemTraits DIAMOND = item("minecraft:diamond", ItemTraits.Rarity.COMMON, 64, false);
    private static final ItemTraits OAK_LOG = item("minecraft:oak_log", ItemTraits.Rarity.COMMON, 64, true);

    private final SeededIdGenerator ids = new SeededIdGenerator(11);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final BankService bank = server.bank();
    private final MarketService market = server.market();

    @Test
    @DisplayName("Geldbeträge: Komma oder Punkt, höchstens zwei Nachkommastellen, nur positiv")
    void parsesMoney() {
        assertEquals(1250, MoneyFormat.parse("12,5"));
        assertEquals(25, MoneyFormat.parse("0.25"));
        assertEquals(300, MoneyFormat.parse("3"));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("1,234"));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("abc"));
        assertEquals("12", MoneyFormat.whole(1205));
        assertEquals("05", MoneyFormat.cents(1205));
    }

    @Test
    @DisplayName("Einzahlen, Abheben und Überweisen verändern die Kontostände")
    void depositWithdrawTransfer() {
        server.players().record(bob, "Bob");
        assertTrue(bank.deposit(caller(alice), 10).success());
        assertEquals(1000, bank.balanceOf(alice).amount());

        assertTrue(bank.withdraw(caller(alice), 3).success());
        assertEquals(700, bank.balanceOf(alice).amount());
        assertEquals(7, bank.withdrawableEmeralds(alice));

        assertTrue(bank.transfer(caller(alice), "bob", "2,5").success());
        assertEquals(450, bank.balanceOf(alice).amount());
        assertEquals(250, bank.balanceOf(bob).amount());
    }

    @Test
    @DisplayName("Zu viel abheben, an sich selbst oder an Unbekannte überweisen wird abgelehnt")
    void rejectsInvalidBanking() {
        server.players().record(alice, "Alice");
        bank.deposit(caller(alice), 1);
        assertFalse(bank.withdraw(caller(alice), 2).success());
        assertEquals(Reply.failure(de.etiennebader.civ.server.command.Message.of(MessageKey.TRANSFER_TO_SELF)),
                bank.transfer(caller(alice), "Alice", "1"));
        assertFalse(bank.transfer(caller(alice), "Niemand", "1").success());
        assertFalse(bank.transfer(caller(alice), "Alice", "x").success());
        assertFalse(bank.deposit(caller(alice), 0).success());
    }

    @Test
    @DisplayName("Gemeindekasse: Mitglieder zahlen ein, nur der Bürgermeister entnimmt")
    void treasury() {
        server.commands().found(caller(alice), "Nordhain");
        server.commands().join(caller(bob), "Nordhain");
        bank.deposit(caller(bob), 5);

        assertTrue(bank.payIntoTreasury(caller(bob), "5").success());
        assertFalse(bank.takeFromTreasury(caller(bob), "1").success());
        assertTrue(bank.takeFromTreasury(caller(alice), "2").success());
        assertEquals(200, bank.balanceOf(alice).amount());
    }

    @Test
    @DisplayName("Verkaufen schöpft Geld, das Lager wächst, und der Rückkauf ist teurer als der Erlös")
    void sellAndBuyBack() {
        server.players().record(alice, "Alice");
        assertTrue(market.sell(caller(alice), DIAMOND, 4).success());
        long earned = bank.balanceOf(alice).amount();
        assertTrue(earned > 0);
        assertEquals(4, market.quote(DIAMOND).orElseThrow().stock());

        assertFalse(market.buy(caller(alice), DIAMOND, 5).success());
        Reply bought = market.buy(caller(alice), DIAMOND, 1);
        assertTrue(bought.success());
        assertEquals(3, market.quote(DIAMOND).orElseThrow().stock());
        assertTrue(bank.balanceOf(alice).amount() < earned * 3 / 4);
        assertEquals(1, market.offers(10).size());
    }

    @Test
    @DisplayName("Viele Verkäufe drücken den Preis")
    void supplyLowersPrice() {
        long before = market.quote(OAK_LOG).orElseThrow().sellPrice().amount();
        market.sell(caller(alice), OAK_LOG, 640);
        assertTrue(market.quote(OAK_LOG).orElseThrow().sellPrice().amount() < before);
    }

    @Test
    @DisplayName("Ohne Geld kann man nichts kaufen; veränderte Items werden nicht angekauft")
    void rejectsInvalidTrades() {
        market.sell(caller(alice), DIAMOND, 2);
        assertFalse(market.buy(caller(bob), DIAMOND, 1).success());
        ItemTraits enchanted = new ItemTraits("minecraft:diamond_sword", "item.minecraft.diamond_sword",
                ItemTraits.Rarity.COMMON, 1, false, true);
        assertFalse(market.sell(caller(alice), enchanted, 1).success());
    }

    @Test
    @DisplayName("Grundpreise: Vanilla-Items haben Preise, Spawn-Eier und Gemeindehäuser nicht")
    void basePrices() {
        assertTrue(BasePrices.of(DIAMOND).getAsLong() > BasePrices.of(OAK_LOG).getAsLong());
        assertTrue(BasePrices.of(item("minecraft:zombie_spawn_egg", ItemTraits.Rarity.COMMON, 64, false)).isEmpty());
        assertTrue(BasePrices.of(item("eddies-civ-mod:town_hall", ItemTraits.Rarity.COMMON, 1, true)).isEmpty());
        assertTrue(BasePrices.of(item("othermod:gizmo", ItemTraits.Rarity.RARE, 16, false)).getAsLong() > 0);
        assertTrue(BasePrices.of(item("minecraft:diamond_pickaxe", ItemTraits.Rarity.COMMON, 1, false)).getAsLong()
                > BasePrices.of(item("minecraft:iron_pickaxe", ItemTraits.Rarity.COMMON, 1, false)).getAsLong());
    }

    @Test
    @DisplayName("Grenzübertritte: Wildnis → eigene Gemeinde → Wildnis, gleicher Chunk bleibt still")
    void watchesBorders() {
        server.commands().found(caller(alice), "Nordhain");
        TerritoryWatcher watcher = server.territory();
        ChunkCoord far = new ChunkCoord(DimensionId.OVERWORLD, 40, 40);
        ChunkCoord home = new ChunkCoord(DimensionId.OVERWORLD, 0, 0);

        assertTrue(watcher.moved(alice, far).isEmpty());
        Optional<TerritoryChange> entered = watcher.moved(alice, home);
        assertEquals(Relation.OWN, entered.orElseThrow().relation());
        assertTrue(watcher.moved(alice, home).isEmpty());
        assertEquals(Relation.WILDERNESS, watcher.moved(alice, far).orElseThrow().relation());
        assertEquals(Relation.FOREIGN, watcher.moved(bob, home).orElseThrow().relation());
    }

    private static ItemTraits item(String id, ItemTraits.Rarity rarity, int stack, boolean block) {
        return new ItemTraits(id, "item." + id.replace(':', '.'), rarity, stack, block, false);
    }

    private static Caller caller(PlayerRef player) {
        return new Caller(player, "Spieler", new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(70));
    }
}

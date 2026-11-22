package de.etiennebader.civ.server.currency;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyServiceTest {

    private static final CurrencyId GOLD = new CurrencyId("GOLD");

    private final SeededIdGenerator ids = new SeededIdGenerator(44);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final CurrencyService currencies = server.currencies();

    private static Caller at(PlayerRef player) {
        return new Caller(player, "Spieler", new BlockPosition(DimensionId.OVERWORLD, 0, 64, 0));
    }

    @Test
    void createRateAndPayoutInCurrencyItem() {
        assertTrue(currencies.create(at(alice), "gold", "G", "2,5", "Gold").success());
        assertFalse(currencies.create(at(bob), "GOLD", "X", "1", "Doppelt").success()); // Code vergeben
        assertFalse(currencies.changeRate(at(bob), "GOLD", "9").success()); // Fremder darf nicht
        assertTrue(server.bank().payoutItem(GOLD).isEmpty()); // noch kein Aussehen
        assertFalse(server.bank().withdrawIn(at(alice), 1, GOLD).success());

        assertTrue(currencies.setItem(at(alice), "GOLD", "minecraft:gold_ingot").success());
        assertFalse(currencies.setItem(at(alice), "GOLD", "kein item").success());
        assertEquals("minecraft:gold_ingot", server.bank().payoutItem(GOLD).orElseThrow());

        server.bank().deposit(at(alice), 10); // 10 Smaragde = 1000 Einheiten
        assertEquals(4, server.bank().withdrawableIn(alice, GOLD)); // 10 E / 2,5 E je Gold
        Reply paid = server.bank().withdrawIn(at(alice), 3, GOLD); // kostet 7,5 Smaragde
        assertTrue(paid.success(), paid.toString());
        assertEquals(250, server.bank().balanceOf(alice).amount());
        assertFalse(server.bank().withdrawIn(at(alice), 2, GOLD).success()); // nur noch 2,5 E = 1 Gold
        assertTrue(server.bank().depositIn(at(alice), 3, GOLD).success());
        assertEquals(1_000, server.bank().balanceOf(alice).amount());
    }

    @Test
    void conversionAndListing() {
        currencies.create(at(alice), "GOLD", "G", "2", "Gold");
        assertTrue(currencies.convert("3", "GOLD", "EMERALD").success());
        assertFalse(currencies.convert("3", "NOPE", "EMERALD").success());
        assertEquals(3, currencies.list().lines().size()); // Kopfzeile, Smaragd, Gold
    }

    @Test
    void loansThroughCreditService() {
        server.bank().deposit(at(alice), 50);
        assertFalse(server.credit().take(at(bob), "Spieler", "5", 30, "EMERALD").success());
        assertTrue(server.credit().openBank(at(alice)).success());
        assertFalse(server.credit().openBank(at(alice)).success());
        assertTrue(server.credit().overview(at(bob)).success());
    }
}

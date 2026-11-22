package de.etiennebader.civ.server.founding;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.ranking.RankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundingFeesTest {

    private static final FoundingFees FEES = new FoundingFees(MoneyFormat.emeralds(10), MoneyFormat.emeralds(5),
            Map.of("land", MoneyFormat.emeralds(20), "nation", MoneyFormat.emeralds(40)), MoneyFormat.emeralds(30),
            true);

    private final SeededIdGenerator ids = new SeededIdGenerator(9);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids,
            CivServerFixture.SETTINGS.withFounding(FEES));

    private Caller alice() {
        return new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }

    private long balance() {
        return server.bank().balanceOf(alice).amount();
    }

    private void deposit(int emeralds) {
        server.playerJoined(alice, "Alice");
        assertTrue(server.bank().deposit(alice(), emeralds).success());
    }

    @Test
    @DisplayName("Gemeinde: ohne genug Geld keine Gründung und keine Abbuchung")
    void municipalityNeedsMoney() {
        deposit(9);

        Reply reply = server.commands().foundWithTownHall(alice(), Optional.of("Nordhain"), alice().position());

        assertFalse(reply.success());
        assertEquals(MessageKey.FOUNDING_NOT_ENOUGH_MONEY.key(), reply.lines().getFirst().key());
        assertTrue(server.queries().municipalityOf(alice).isEmpty());
        assertEquals(MoneyFormat.emeralds(9), balance());
    }

    @Test
    @DisplayName("Gemeinde: genau einmal abgebucht; scheiternde Gründung kostet nichts")
    void municipalityChargedOnce() {
        deposit(25);

        assertFalse(server.commands().foundWithTownHall(alice(), Optional.of("X"), alice().position()).success());
        assertEquals(MoneyFormat.emeralds(25), balance());

        assertTrue(server.commands().foundWithTownHall(alice(), Optional.of("Nordhain"), alice().position()).success());
        assertEquals(MoneyFormat.emeralds(15), balance());
        assertEquals(MoneyFormat.emeralds(10), server.rankings().serverBalance().amount());
    }

    @Test
    @DisplayName("Bund: Vorgänger-Ebene ist Pflicht, die Gemeindekasse zahlt, Abbuchung nur bei Erfolg")
    void federationNeedsPreviousLevelAndTreasuryPays() {
        deposit(40);
        assertTrue(server.commands().found(alice(), "Nordhain").success()); // Admin-Weg ist kostenlos
        assertTrue(server.bank().payIntoTreasury(alice(), "25").success());
        assertEquals(MoneyFormat.emeralds(15), balance());

        Reply skipped = server.federations().found(alice(), "nation", "Reich");
        assertEquals(MessageKey.FEDERATION_NEEDS_PREVIOUS_LEVEL.key(), skipped.lines().getFirst().key());
        assertEquals(0, server.rankings().serverBalance().amount());

        assertTrue(server.federations().found(alice(), "land", "Westmark").success());
        assertEquals(MoneyFormat.emeralds(15), balance(), "der Gründer zahlt nicht privat");
        assertEquals(MoneyFormat.emeralds(20), server.rankings().serverBalance().amount());

        Reply poor = server.federations().found(alice(), "nation", "Reich");
        assertEquals(MessageKey.FOUNDING_NOT_ENOUGH_MONEY.key(), poor.lines().getFirst().key());
        assertEquals(MoneyFormat.emeralds(20), server.rankings().serverBalance().amount());
    }

    @Test
    @DisplayName("Serverkonto: Gebühren erhöhen die Geldmenge für das Preisniveau nicht")
    void serverAccountIsNotMarketSupply() {
        deposit(25);
        var currency = CivServerFixture.SETTINGS.currency();
        long before = server.queries().marketSupply(currency).amount();
        assertEquals(MoneyFormat.emeralds(25), before);

        assertTrue(server.commands().foundWithTownHall(alice(), Optional.of("Nordhain"), alice().position()).success());

        assertEquals(MoneyFormat.emeralds(10), server.rankings().serverBalance().amount());
        assertEquals(MoneyFormat.emeralds(15), server.queries().marketSupply(currency).amount());
    }

    @Test
    @DisplayName("Ranglisten: Serverkonto zählt nicht mit, Ebenenfilter und Reihenfolge")
    void rankingsExcludeServerAccount() {
        deposit(15);
        assertTrue(server.commands().foundWithTownHall(alice(), Optional.of("Nordhain"), alice().position()).success());
        assertTrue(server.companies().found(alice(), "1", "Acme").success() == false); // unter Mindestkapital

        var players = server.rankings().top(RankingService.Category.RICHEST_PLAYERS, Optional.empty(), 10);
        assertEquals(1, players.size());
        assertEquals("Alice", players.getFirst().name());
        assertEquals(MoneyFormat.emeralds(5), players.getFirst().value());
        assertEquals(MoneyFormat.emeralds(10), server.rankings().serverBalance().amount());
        assertEquals(1, server.rankings().top(RankingService.Category.LARGEST_TOWNS, Optional.empty(), 10).size());
        assertTrue(server.rankings().show("gibtsnicht", Optional.empty()).lines().getFirst().key()
                .equals(MessageKey.RANKING_UNKNOWN_CATEGORY.key()));
        assertTrue(server.rankings().show("spieler", Optional.empty()).success());
    }

    @Test
    @DisplayName("Firma: Gebühr plus Kapital müssen gedeckt sein")
    void companyFeeAndCapital() {
        deposit(15);

        Reply poor = server.companies().found(alice(), "11", "Acme");
        assertEquals(MessageKey.FOUNDING_NOT_ENOUGH_MONEY.key(), poor.lines().getFirst().key());
        assertEquals(MoneyFormat.emeralds(15), balance());

        assertTrue(server.companies().found(alice(), "10", "Acme").success());
        assertEquals(0, balance());
    }
}

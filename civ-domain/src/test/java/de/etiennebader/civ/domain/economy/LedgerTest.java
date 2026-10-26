package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    private static final CurrencyId EMERALD = CurrencyId.EMERALD;
    private static final Day DAY = Day.of(1);

    private final TestActors actors = new TestActors();
    private final ActorRef alice = actors.player();
    private final ActorRef bob = actors.villager();
    private final Ledger ledger = Ledger.empty();

    @Test
    @DisplayName("Unbekanntes Konto hat Kontostand 0")
    void unknownAccountHasZeroBalance() {
        assertEquals(Money.zero(EMERALD), ledger.balanceOf(alice, EMERALD));
    }

    @Test
    @DisplayName("Geldschöpfung erhöht Kontostand und Geldmenge")
    void mintIncreasesBalanceAndSupply() {
        ledger.mint(alice, emeralds(100), SupplyChange.COMMODITY_DEPOSIT, DAY);

        assertEquals(emeralds(100), ledger.balanceOf(alice, EMERALD));
        assertEquals(emeralds(100), ledger.totalSupply(EMERALD));
        assertEquals(List.of(new MoneyMinted(alice, emeralds(100), SupplyChange.COMMODITY_DEPOSIT, DAY)), ledger.pullEvents());
    }

    @Test
    @DisplayName("Transfer mit Deckung: Kontostände korrekt, Event FundsTransferred")
    void transferWithFunds() {
        ledger.mint(alice, emeralds(100), SupplyChange.ADMIN, DAY);
        ledger.pullEvents();

        ledger.transfer(alice, bob, emeralds(30), TransferReason.TRADE, DAY);

        assertEquals(emeralds(70), ledger.balanceOf(alice, EMERALD));
        assertEquals(emeralds(30), ledger.balanceOf(bob, EMERALD));
        assertEquals(List.of(new FundsTransferred(alice, bob, emeralds(30), TransferReason.TRADE, DAY)), ledger.pullEvents());
    }

    @Test
    @DisplayName("Transfer ohne Deckung → INSUFFICIENT_FUNDS, nichts verändert")
    void transferWithoutFunds() {
        ledger.mint(alice, emeralds(5), SupplyChange.ADMIN, DAY);
        ledger.pullEvents();

        var exception = assertDomainError(ErrorCode.INSUFFICIENT_FUNDS,
                () -> ledger.transfer(alice, bob, emeralds(20), TransferReason.TRADE, DAY));

        assertEquals(List.of(emeralds(15), emeralds(5), emeralds(20)), exception.arguments());
        assertEquals(emeralds(5), ledger.balanceOf(alice, EMERALD));
        assertEquals(Money.zero(EMERALD), ledger.balanceOf(bob, EMERALD));
        assertTrue(ledger.pullEvents().isEmpty());
    }

    @Test
    @DisplayName("tryTransfer ohne Deckung liefert Rejected mit Fehlbetrag")
    void tryTransferReportsMissingAmount() {
        ledger.mint(alice, emeralds(5), SupplyChange.ADMIN, DAY);

        TransferResult result = ledger.tryTransfer(alice, bob, emeralds(20), TransferReason.LAND_TAX, DAY);

        assertEquals(new TransferResult.Rejected(emeralds(15)), result);
        assertFalse(result.isCompleted());
    }

    @Test
    @DisplayName("tryTransfer mit Deckung liefert Completed mit Event")
    void tryTransferCompletes() {
        ledger.mint(alice, emeralds(50), SupplyChange.ADMIN, DAY);

        TransferResult result = ledger.tryTransfer(alice, bob, emeralds(20), TransferReason.LAND_TAX, DAY);

        assertEquals(emeralds(20), assertInstanceOf(TransferResult.Completed.class, result).event().amount());
    }

    @Test
    @DisplayName("Nicht positive Beträge und Überweisung an sich selbst sind Programmierfehler")
    void rejectsInvalidArguments() {
        ledger.mint(alice, emeralds(50), SupplyChange.ADMIN, DAY);

        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(alice, bob, emeralds(0), TransferReason.TRADE, DAY));
        assertThrows(IllegalArgumentException.class, () -> ledger.mint(alice, emeralds(-1), SupplyChange.ADMIN, DAY));
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(alice, alice, emeralds(1), TransferReason.TRADE, DAY));
    }

    @Test
    @DisplayName("Auszahlen und wieder einzahlen: Kontostand gleich, Bargeldumlauf 0")
    void withdrawAndDepositRoundTrip() {
        ledger.mint(alice, emeralds(100), SupplyChange.ADMIN, DAY);

        ledger.withdrawCash(alice, emeralds(40), DAY);
        assertEquals(emeralds(60), ledger.balanceOf(alice, EMERALD));
        assertEquals(emeralds(40), ledger.cashInCirculation(EMERALD));

        ledger.depositCash(alice, emeralds(40), DAY);
        assertEquals(emeralds(100), ledger.balanceOf(alice, EMERALD));
        assertEquals(Money.zero(EMERALD), ledger.cashInCirculation(EMERALD));
        assertEquals(emeralds(100), ledger.totalSupply(EMERALD));
    }

    @Test
    @DisplayName("Bargeld wechselt von Hand zu Hand: Einzahlung durch einen anderen Akteur")
    void cashCanBeDepositedByAnotherActor() {
        ledger.mint(alice, emeralds(100), SupplyChange.ADMIN, DAY);
        ledger.withdrawCash(alice, emeralds(40), DAY);

        ledger.depositCash(bob, emeralds(40), DAY);

        assertEquals(emeralds(40), ledger.balanceOf(bob, EMERALD));
    }

    @Test
    @DisplayName("Auszahlung ohne Deckung → INSUFFICIENT_FUNDS")
    void rejectsWithdrawalWithoutFunds() {
        assertDomainError(ErrorCode.INSUFFICIENT_FUNDS, () -> ledger.withdrawCash(alice, emeralds(1), DAY));
    }

    @Test
    @DisplayName("Mehr einzahlen als im Umlauf → CASH_INTEGRITY")
    void rejectsDepositAboveCirculation() {
        ledger.mint(alice, emeralds(100), SupplyChange.ADMIN, DAY);
        ledger.withdrawCash(alice, emeralds(10), DAY);

        assertDomainError(ErrorCode.CASH_INTEGRITY, () -> ledger.depositCash(alice, emeralds(11), DAY));
        assertEquals(emeralds(10), ledger.cashInCirculation(EMERALD));
    }

    @Test
    @DisplayName("Geldvernichtung senkt Kontostand und Geldmenge, nicht über das Guthaben hinaus")
    void burnDecreasesBalanceAndSupply() {
        ledger.mint(alice, emeralds(100), SupplyChange.ADMIN, DAY);

        ledger.burn(alice, emeralds(30), SupplyChange.COMMODITY_WITHDRAWAL, DAY);

        assertEquals(emeralds(70), ledger.balanceOf(alice, EMERALD));
        assertEquals(emeralds(70), ledger.totalSupply(EMERALD));
        assertDomainError(ErrorCode.INSUFFICIENT_FUNDS,
                () -> ledger.burn(alice, emeralds(71), SupplyChange.COMMODITY_WITHDRAWAL, DAY));
    }

    @Test
    @DisplayName("Währungen sind getrennt")
    void currenciesAreSeparate() {
        CurrencyId taler = new CurrencyId("TALER");
        ledger.mint(alice, emeralds(10), SupplyChange.ADMIN, DAY);

        assertEquals(Money.zero(taler), ledger.balanceOf(alice, taler));
        assertDomainError(ErrorCode.INSUFFICIENT_FUNDS,
                () -> ledger.transfer(alice, bob, Money.of(1, taler), TransferReason.TRADE, DAY));
    }

    @Test
    @DisplayName("10.000 zufällige Operationen mit festem Seed: Konten + Bargeld = Geldmenge")
    void conservesMoneyUnderRandomOperations() {
        List<ActorRef> accounts = Stream.<ActorRef>concat(
                Stream.generate(actors::player).limit(5),
                Stream.generate(actors::villager).limit(5)).toList();
        SplittableRandom random = new SplittableRandom(2026);

        for (int step = 0; step < 10_000; step++) {
            ActorRef actor = accounts.get(random.nextInt(accounts.size()));
            ActorRef other = accounts.get(random.nextInt(accounts.size()));
            Money amount = emeralds(1 + random.nextInt(200));
            Day day = Day.of(step);
            switch (random.nextInt(6)) {
                case 0 -> ledger.mint(actor, amount, SupplyChange.COMMODITY_DEPOSIT, day);
                case 1 -> ignoreInsufficientFunds(() -> ledger.burn(actor, amount, SupplyChange.COMMODITY_WITHDRAWAL, day));
                case 2 -> ignoreInsufficientFunds(() -> ledger.withdrawCash(actor, amount, day));
                case 3 -> {
                    Money deposit = emeralds(Math.min(amount.amount(), ledger.cashInCirculation(EMERALD).amount()));
                    if (deposit.isPositive()) {
                        ledger.depositCash(actor, deposit, day);
                    }
                }
                default -> {
                    if (!actor.equals(other)) {
                        ledger.tryTransfer(actor, other, amount, TransferReason.TRADE, day);
                    }
                }
            }
            accounts.forEach(account -> assertFalse(ledger.balanceOf(account, EMERALD).isNegative()));
        }

        Money accountsAndCash = ledger.totalDeposits(EMERALD).plus(ledger.cashInCirculation(EMERALD));
        assertEquals(ledger.totalSupply(EMERALD), accountsAndCash);
        assertEquals(ledger.totalDeposits(EMERALD), accounts.stream()
                .map(account -> ledger.balanceOf(account, EMERALD))
                .reduce(Money.zero(EMERALD), Money::plus));
        assertTrue(ledger.totalSupply(EMERALD).isPositive());
    }

    private static void ignoreInsufficientFunds(Runnable operation) {
        try {
            operation.run();
        } catch (InsufficientFundsException expected) {
            // fehlende Deckung ist im Zufallstest ein normaler Fall
        }
    }

    private static Money emeralds(long amount) {
        return Money.of(amount, EMERALD);
    }
}

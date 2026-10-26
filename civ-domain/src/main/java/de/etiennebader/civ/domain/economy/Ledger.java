package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Weltweites Aggregat aller Konten und des Bargeldumlaufs, vergleichbar mit dem Hauptbuch
 * einer Zentralbank. Die Kasse einer Polity ist das Konto ihrer {@code PolityRef}.
 *
 * <p>Invarianten je Währung:
 * <ul>
 *     <li>Kein Kontostand und kein Bargeldumlauf ist negativ.</li>
 *     <li>Summe aller Kontostände + umlaufendes Bargeld = Geldmenge.</li>
 * </ul>
 * Geld entsteht und verschwindet nur durch {@link #mint} und {@link #burn}; Überweisungen,
 * Ein- und Auszahlungen verschieben es nur.
 */
public final class Ledger extends AggregateRoot<LedgerEvent> {

    private final Map<ActorRef, Map<CurrencyId, Long>> balances = new HashMap<>();
    private final Map<CurrencyId, Long> cashInCirculation = new HashMap<>();
    private final Map<CurrencyId, Long> supply = new HashMap<>();

    private Ledger() {
    }

    public static Ledger empty() {
        return new Ledger();
    }

    /** Kontostand eines Akteurs; 0, wenn er noch kein Konto in dieser Währung hat. */
    public Money balanceOf(ActorRef account, CurrencyId currency) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(currency, "currency");
        long amount = balances.getOrDefault(account, Map.of()).getOrDefault(currency, 0L);
        return Money.of(amount, currency);
    }

    /**
     * Überweist Buchgeld von einem Konto auf ein anderes.
     *
     * @throws InsufficientFundsException wenn das Guthaben nicht reicht; dann ändert sich nichts
     */
    public void transfer(ActorRef from, ActorRef to, Money amount, TransferReason reason, Day day) {
        TransferResult result = tryTransfer(from, to, amount, reason, day);
        if (result instanceof TransferResult.Rejected) {
            throw new InsufficientFundsException(amount, balanceOf(from, amount.currency()));
        }
    }

    /**
     * Wie {@link #transfer}, aber fehlende Deckung führt zu {@link TransferResult.Rejected}
     * statt zu einer Exception, z. B. beim Steuereinzug.
     */
    public TransferResult tryTransfer(ActorRef from, ActorRef to, Money amount, TransferReason reason, Day day) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(day, "day");
        requirePositive(amount);
        if (from.equals(to)) {
            throw new IllegalArgumentException("Überweisung an sich selbst: " + from);
        }
        Money available = balanceOf(from, amount.currency());
        if (available.compareTo(amount) < 0) {
            return new TransferResult.Rejected(amount.minus(available));
        }
        adjustBalance(from, amount.currency(), -amount.amount());
        adjustBalance(to, amount.currency(), amount.amount());
        FundsTransferred event = new FundsTransferred(from, to, amount, reason, day);
        raise(event);
        return new TransferResult.Completed(event);
    }

    /**
     * Zahlt Buchgeld als Bargeld aus: Das Konto sinkt, der Bargeldumlauf steigt.
     *
     * @throws InsufficientFundsException wenn das Guthaben nicht reicht
     */
    public void withdrawCash(ActorRef account, Money amount, Day day) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(day, "day");
        requirePositive(amount);
        requireBalance(account, amount);
        adjustBalance(account, amount.currency(), -amount.amount());
        adjust(cashInCirculation, amount.currency(), amount.amount());
        raise(new CashWithdrawn(account, amount, day));
    }

    /**
     * Zahlt Bargeld auf ein Konto ein: Der Bargeldumlauf sinkt, das Konto steigt.
     *
     * <p>Einzahlungen über dem gesamten umlaufenden Bargeld werden abgelehnt. Das fängt grobe
     * Item-Duplikation ab; Banknoten mit Seriennummer folgen in Phase 2.
     *
     * @throws CashIntegrityException wenn mehr eingezahlt werden soll, als im Umlauf ist
     */
    public void depositCash(ActorRef account, Money amount, Day day) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(day, "day");
        requirePositive(amount);
        Money circulating = cashInCirculation(amount.currency());
        if (amount.compareTo(circulating) > 0) {
            throw new CashIntegrityException(amount, circulating);
        }
        adjust(cashInCirculation, amount.currency(), -amount.amount());
        adjustBalance(account, amount.currency(), amount.amount());
        raise(new CashDeposited(account, amount, day));
    }

    /** Schöpft Geld: Kontostand und Geldmenge steigen. */
    public void mint(ActorRef to, Money amount, SupplyChange reason, Day day) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(day, "day");
        requirePositive(amount);
        adjust(supply, amount.currency(), amount.amount());
        adjustBalance(to, amount.currency(), amount.amount());
        raise(new MoneyMinted(to, amount, reason, day));
    }

    /**
     * Vernichtet Geld: Kontostand und Geldmenge sinken.
     *
     * @throws InsufficientFundsException wenn das Guthaben nicht reicht
     */
    public void burn(ActorRef from, Money amount, SupplyChange reason, Day day) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(day, "day");
        requirePositive(amount);
        requireBalance(from, amount);
        adjustBalance(from, amount.currency(), -amount.amount());
        adjust(supply, amount.currency(), -amount.amount());
        raise(new MoneyBurned(from, amount, reason, day));
    }

    /** Bargeld dieser Währung, das sich gerade als Items im Umlauf befindet. */
    public Money cashInCirculation(CurrencyId currency) {
        return Money.of(cashInCirculation.getOrDefault(Objects.requireNonNull(currency, "currency"), 0L), currency);
    }

    /** Gesamte Geldmenge dieser Währung, also Buchgeld und Bargeld. */
    public Money totalSupply(CurrencyId currency) {
        return Money.of(supply.getOrDefault(Objects.requireNonNull(currency, "currency"), 0L), currency);
    }

    /** Summe aller Kontostände dieser Währung, also das gesamte Buchgeld. */
    public Money totalDeposits(CurrencyId currency) {
        Objects.requireNonNull(currency, "currency");
        long total = 0;
        for (Map<CurrencyId, Long> accounts : balances.values()) {
            total = Math.addExact(total, accounts.getOrDefault(currency, 0L));
        }
        return Money.of(total, currency);
    }

    private void requireBalance(ActorRef account, Money amount) {
        Money available = balanceOf(account, amount.currency());
        if (available.compareTo(amount) < 0) {
            throw new InsufficientFundsException(amount, available);
        }
    }

    private static void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Betrag muss positiv sein: " + amount);
        }
    }

    private void adjustBalance(ActorRef account, CurrencyId currency, long delta) {
        adjust(balances.computeIfAbsent(account, ignored -> new HashMap<>()), currency, delta);
    }

    private static void adjust(Map<CurrencyId, Long> amounts, CurrencyId currency, long delta) {
        long updated = Math.addExact(amounts.getOrDefault(currency, 0L), delta);
        if (updated < 0) {
            throw new IllegalStateException("Invariante verletzt: negativer Bestand " + updated + " " + currency);
        }
        amounts.put(currency, updated);
    }
}

package de.etiennebader.civ.server.founding;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.SpendFunds;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.MoneyFormat;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Gründungskosten für Gemeinden, Bünde und Firmen. Die Gebühr verlässt den Umlauf
 * ({@link SupplyChange#FOUNDING_FEE}); sie wird erst abgebucht, wenn die Gründung gelungen ist.
 * Scheitert die Gründung, wird also nichts abgebucht, und es gibt nie doppelte Abbuchungen.
 *
 * @param municipality         Kosten einer Gemeinde in Hundertsteln eines Smaragds
 * @param company              Gründungsgebühr einer Firma (zusätzlich zum Kapital)
 * @param federationByLevel    Kosten je Bundebene (Ebenen-ID → Hundertstel)
 * @param federationDefault    Kosten einer Bundebene, die nicht in {@code federationByLevel} steht
 * @param requirePreviousLevel ob ein Bund nur von der Ebene direkt darunter gegründet werden darf
 *                             (Land nur aus einer Gemeinde, Nation nur aus einem Land usw.)
 */
public record FoundingFees(long municipality, long company, Map<String, Long> federationByLevel,
                           long federationDefault, boolean requirePreviousLevel) {

    /** Keine Kosten, keine Vorgänger-Pflicht (bisheriges Verhalten). */
    public static final FoundingFees FREE = new FoundingFees(0, 0, Map.of(), 0, false);

    /** Standard für neue Server: Gemeinde 50, Firma 20, Land 200, Nation 1000, sonst 500 Smaragde. */
    public static final FoundingFees DEFAULT = new FoundingFees(MoneyFormat.emeralds(50), MoneyFormat.emeralds(20),
            Map.of("land", MoneyFormat.emeralds(200), "nation", MoneyFormat.emeralds(1000)),
            MoneyFormat.emeralds(500), true);

    public FoundingFees {
        if (municipality < 0 || company < 0 || federationDefault < 0
                || federationByLevel.values().stream().anyMatch(cost -> cost < 0)) {
            throw new IllegalArgumentException("Gründungskosten dürfen nicht negativ sein");
        }
        federationByLevel = Map.copyOf(federationByLevel);
    }

    /** Kosten einer Bundebene. */
    public long federation(String levelId) {
        return federationByLevel.getOrDefault(levelId, federationDefault);
    }

    /** Eine Abrechnung gegen die Kassen einer laufenden Anwendung. */
    public Billing billing(CivApplication app, CurrencyId currency) {
        return new Billing(app, currency);
    }

    /** Prüft Guthaben vorab und bucht nach erfolgreicher Gründung ab. */
    public static final class Billing {

        private final CivApplication app;
        private final CurrencyId currency;

        private Billing(CivApplication app, CurrencyId currency) {
            this.app = Objects.requireNonNull(app, "app");
            this.currency = Objects.requireNonNull(currency, "currency");
        }

        /** Das Guthaben des Zahlers, oder {@code null}, wenn es für die Kosten reicht. */
        public Money shortfallBalance(ActorRef payer, long cost) {
            if (cost <= 0) {
                return null;
            }
            Money balance = app.queries().ledger().balanceOf(payer, currency);
            return balance.amount() >= cost ? null : balance;
        }

        /**
         * Führt die Gründung aus und bucht danach die Gebühr ab. Wirft die Gründung, passiert keine Abbuchung.
         * Das Guthaben muss der Aufrufer vorher mit {@link #shortfallBalance} geprüft haben.
         */
        public <T> T chargeAfter(ActorRef payer, long cost, Day day, Supplier<T> founding) {
            T result = founding.get();
            if (cost > 0) {
                app.spendFunds().execute(new SpendFunds.Command(payer, Money.of(cost, currency),
                        SupplyChange.FOUNDING_FEE, day));
            }
            return result;
        }
    }
}

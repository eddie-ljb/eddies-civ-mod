package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Ergebnis von {@link Ledger#tryTransfer}: Fehlende Deckung ist dort ein erwartbarer Fall und kein Fehler.
 */
public sealed interface TransferResult {

    boolean isCompleted();

    /** Die Überweisung wurde ausgeführt. */
    record Completed(FundsTransferred event) implements TransferResult {

        public Completed {
            Objects.requireNonNull(event, "event");
        }

        @Override
        public boolean isCompleted() {
            return true;
        }
    }

    /** Die Überweisung wurde mangels Deckung nicht ausgeführt; {@code missing} fehlte. */
    record Rejected(Money missing) implements TransferResult {

        public Rejected {
            Objects.requireNonNull(missing, "missing");
            if (!missing.isPositive()) {
                throw new IllegalArgumentException("Fehlbetrag muss positiv sein: " + missing);
            }
        }

        @Override
        public boolean isCompleted() {
            return false;
        }
    }
}

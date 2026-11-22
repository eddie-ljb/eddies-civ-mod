package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;

/** Ereignisse des Kreditregisters. */
public sealed interface CreditEvent extends DomainEvent {

    record BankLicensed(ActorRef bank, Day occurredOn) implements CreditEvent {
    }

    record BankClosed(ActorRef bank, Day occurredOn) implements CreditEvent {
    }

    record LoanIssued(LoanId loan, Day occurredOn) implements CreditEvent {
    }

    record LoanRepaid(LoanId loan, long amount, boolean complete, Day occurredOn) implements CreditEvent {
    }

    record LoanDefaulted(LoanId loan, Day occurredOn) implements CreditEvent {
    }
}

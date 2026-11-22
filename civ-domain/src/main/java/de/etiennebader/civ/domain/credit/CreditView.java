package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.ActorRef;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lesender Zugriff auf das Kreditregister. */
public interface CreditView {

    boolean isBank(ActorRef actor);

    Set<ActorRef> banks();

    /** Banken, bei denen Kreditnehmer ohne Angebot direkt Kredite aufnehmen dürfen. */
    Set<ActorRef> selfServiceBanks();

    default boolean isSelfService(ActorRef bank) {
        return selfServiceBanks().contains(bank);
    }

    Optional<LoanOffer> offer(LoanId id);

    List<LoanOffer> offers();

    /** Kreditscore; Akteure ohne Verlauf haben {@link CreditScore#START}. */
    int scoreOf(ActorRef actor);

    /** Alle Kreditscores, die vom Startwert abweichen. */
    Map<ActorRef, Integer> scores();

    Optional<Loan> loan(LoanId id);

    List<Loan> loans();

    List<Loan> loansOfBorrower(ActorRef borrower);

    List<Loan> loansOfLender(ActorRef lender);
}

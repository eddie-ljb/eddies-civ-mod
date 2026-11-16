package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine Seite hat gekündigt; der Vertrag endet am angegebenen Tag.
 */
public record ContractNoticeGiven(ContractId contract, ActorRef by, Day lastDay, Day occurredOn) implements ContractEvent {

    public ContractNoticeGiven {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(lastDay, "lastDay");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}

package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Angebot wurde abgelehnt oder zurückgezogen.
 */
public record ContractDeclined(ContractId contract, ActorRef by, Day occurredOn) implements ContractEvent {

    public ContractDeclined {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}

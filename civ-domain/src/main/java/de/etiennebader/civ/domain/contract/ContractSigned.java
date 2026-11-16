package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Vertrag wurde unterschrieben und gilt.
 */
public record ContractSigned(ContractId contract, ActorRef signer, Day occurredOn) implements ContractEvent {

    public ContractSigned {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}

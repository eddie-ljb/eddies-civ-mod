package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Vertrag wurde angeboten.
 */
public record ContractOffered(ContractId contract, ActorRef offeror, ActorRef counterparty, ContractKind kind, Day occurredOn) implements ContractEvent {

    public ContractOffered {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(offeror, "offeror");
        Objects.requireNonNull(counterparty, "counterparty");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}

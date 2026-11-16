package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Vertrag ist beendet.
 */
public record ContractEnded(ContractId contract, EndReason reason, Day occurredOn) implements ContractEvent {

    public ContractEnded {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}

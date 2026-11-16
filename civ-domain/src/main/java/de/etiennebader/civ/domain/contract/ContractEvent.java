package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.DomainEvent;

/**
 * Alle Events des {@link ContractRegistry}-Aggregats.
 */
public sealed interface ContractEvent extends DomainEvent
        permits ContractOffered, ContractSigned, ContractNoticeGiven, ContractEnded, ContractDeclined {

    ContractId contract();
}

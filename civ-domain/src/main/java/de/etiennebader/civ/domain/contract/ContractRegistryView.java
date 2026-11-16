package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.ContractId;

import java.util.List;
import java.util.Optional;

/**
 * Lesender Zugriff auf alle Verträge.
 */
public interface ContractRegistryView {

    /** Alle Verträge, auch beendete, in Reihenfolge ihrer Nummer. */
    List<Contract> contracts();

    Optional<Contract> contract(ContractId id);

    default Optional<Contract> byNumber(int number) {
        return contracts().stream().filter(contract -> contract.number() == number).findFirst();
    }

    /** Verträge, an denen der Akteur beteiligt ist, die neuesten zuerst. */
    default List<Contract> of(ActorRef actor) {
        return contracts().reversed().stream().filter(contract -> contract.involves(actor)).toList();
    }

    /** Geltende Verträge. */
    default List<Contract> active() {
        return contracts().stream().filter(Contract::active).toList();
    }
}

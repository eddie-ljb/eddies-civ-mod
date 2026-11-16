package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Alle Verträge der Welt. Beendete und abgelehnte bleiben als Nachweis erhalten, höchstens {@value #KEPT_CLOSED}.
 */
public final class ContractRegistry extends AggregateRoot<ContractEvent> implements ContractRegistryView {

    /** So viele beendete oder abgelehnte Verträge bleiben gespeichert, die neuesten. */
    public static final int KEPT_CLOSED = 300;

    private final Map<ContractId, Contract> contracts = new LinkedHashMap<>();

    private ContractRegistry() {
    }

    public static ContractRegistry empty() {
        return new ContractRegistry();
    }

    public static ContractRegistry restore(Collection<Contract> stored) {
        ContractRegistry registry = new ContractRegistry();
        stored.forEach(contract -> registry.contracts.put(contract.id(), contract));
        return registry;
    }

    /** Die nächste freie Nummer. */
    public int nextNumber() {
        return contracts.values().stream().mapToInt(Contract::number).max().orElse(0) + 1;
    }

    /** Ein neues Angebot; der Anbieter unterschreibt damit. */
    public Contract offer(Contract contract, Day day) {
        Objects.requireNonNull(contract, "contract");
        if (contract.status() != ContractStatus.OFFERED || contracts.containsKey(contract.id())) {
            throw new IllegalArgumentException("Kein neues Angebot: " + contract.id());
        }
        contracts.put(contract.id(), contract);
        raise(new ContractOffered(contract.id(), contract.offeror(), contract.counterparty(), contract.kind(), day));
        return contract;
    }

    /**
     * Die Gegenseite unterschreibt; der Vertrag gilt ab heute. Zahlungen bei Unterschrift bewegt der Aufrufer.
     *
     * @throws ContractStateException wenn der Akteur nicht die Gegenseite ist oder der Vertrag nicht angeboten ist
     */
    public Contract sign(ContractId id, ActorRef signer, Day day) {
        Contract contract = require(id);
        if (contract.status() != ContractStatus.OFFERED || !contract.counterparty().equals(signer)) {
            throw new ContractStateException(contract.number());
        }
        Contract signed = contract.signed(day);
        contracts.put(id, signed);
        raise(new ContractSigned(id, signer, day));
        return signed;
    }

    /**
     * Lehnt ein Angebot ab (Gegenseite) oder zieht es zurück (Anbieter).
     *
     * @throws ContractStateException wenn es kein offenes Angebot des Akteurs ist
     */
    public void decline(ContractId id, ActorRef actor, Day day) {
        Contract contract = require(id);
        if (contract.status() != ContractStatus.OFFERED || !contract.involves(actor)) {
            throw new ContractStateException(contract.number());
        }
        contracts.put(id, contract.declined());
        raise(new ContractDeclined(id, actor, day));
        prune();
    }

    /**
     * Eine Seite kündigt; der Vertrag endet nach Ablauf von {@code noticeDays}, spätestens zur vereinbarten Laufzeit.
     *
     * @throws ContractStateException wenn der Vertrag nicht gilt oder der Akteur keine Partei ist
     */
    public Contract giveNotice(ContractId id, ActorRef actor, long noticeDays, Day day) {
        Contract contract = require(id);
        if (!contract.active() || !contract.involves(actor)) {
            throw new ContractStateException(contract.number());
        }
        Day lastDay = day.plus(Math.max(0, noticeDays));
        Contract noticed = contract.withNotice(lastDay);
        contracts.put(id, noticed);
        raise(new ContractNoticeGiven(id, actor, noticed.endsOn().orElse(lastDay), day));
        return noticed;
    }

    /** Beendet einen geltenden Vertrag. Rückzahlungen bewegt der Aufrufer. */
    public Contract end(ContractId id, EndReason reason, Day day) {
        Objects.requireNonNull(reason, "reason");
        Contract contract = require(id);
        if (!contract.active()) {
            throw new ContractStateException(contract.number());
        }
        Contract ended = contract.ended(reason);
        contracts.put(id, ended);
        raise(new ContractEnded(id, reason, day));
        prune();
        return ended;
    }

    private void prune() {
        List<ContractId> closed = contracts.values().stream()
                .filter(contract -> contract.status() == ContractStatus.ENDED
                        || contract.status() == ContractStatus.DECLINED)
                .map(Contract::id)
                .toList();
        for (int index = 0; index < closed.size() - KEPT_CLOSED; index++) {
            contracts.remove(closed.get(index));
        }
    }

    @Override
    public List<Contract> contracts() {
        return List.copyOf(contracts.values());
    }

    @Override
    public Optional<Contract> contract(ContractId id) {
        return Optional.ofNullable(contracts.get(Objects.requireNonNull(id, "id")));
    }

    private Contract require(ContractId id) {
        Contract contract = contracts.get(Objects.requireNonNull(id, "id"));
        if (contract == null) {
            throw new UnknownContractException(id.value());
        }
        return contract;
    }
}

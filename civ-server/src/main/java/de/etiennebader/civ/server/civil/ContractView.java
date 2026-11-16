package de.etiennebader.civ.server.civil;

import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.contract.ContractStatus;
import de.etiennebader.civ.domain.shared.ContractId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Vertrag aus Sicht eines Beteiligten.
 *
 * @param other      Name der Gegenseite
 * @param pays       ob der Betrachter zahlt
 * @param perDay     tägliche Zahlung als Text, z. B. „0,50“
 * @param address    Anschrift des Mietobjekts
 * @param canSign    ob der Betrachter unterschreiben kann
 * @param canDecline ob er ablehnen oder zurückziehen kann
 * @param canNotice  ob er kündigen kann
 */
public record ContractView(ContractId id, int number, ContractKind kind, ContractStatus status, String title,
                           String text, String offeror, String counterparty, String other, boolean pays,
                           String perDay, String once, String deposit, long termDays, Optional<String> address,
                           long offeredOn, Optional<Long> startedOn, Optional<Long> endsOn, Optional<String> endReason,
                           boolean canSign, boolean canDecline, boolean canNotice) {

    public ContractView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
    }
}

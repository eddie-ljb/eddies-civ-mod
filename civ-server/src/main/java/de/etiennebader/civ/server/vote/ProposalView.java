package de.etiennebader.civ.server.vote;

import de.etiennebader.civ.domain.shared.ProposalId;
import de.etiennebader.civ.server.command.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine offene Abstimmung für Menüs und Listen.
 *
 * @param number    laufende Nummer innerhalb der Gemeinde, ab 1
 * @param law       Name der Regelung
 * @param value     vorgeschlagener Wert
 * @param ownVote   die Stimme des Betrachters, falls er schon abgestimmt hat
 * @param decidedOn Tag, an dem spätestens ausgezählt wird
 */
public record ProposalView(int number, ProposalId id, Message law, Object value, String proposer, long inFavour,
                           long against, long electorate, Optional<Boolean> ownVote, long decidedOn) {

    public ProposalView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(law, "law");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(ownVote, "ownVote");
    }
}

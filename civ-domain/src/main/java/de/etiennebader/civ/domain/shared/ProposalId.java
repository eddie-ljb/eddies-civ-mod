package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität einer Abstimmung über eine Rechtsänderung.
 */
public record ProposalId(UUID value) {

    public ProposalId {
        Objects.requireNonNull(value, "value");
    }

    public static ProposalId generate(IdGenerator ids) {
        return new ProposalId(ids.next());
    }
}

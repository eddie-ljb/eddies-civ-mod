package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität eines Vertrags.
 */
public record ContractId(UUID value) {

    public ContractId {
        Objects.requireNonNull(value, "value");
    }

    public static ContractId generate(IdGenerator ids) {
        return new ContractId(ids.next());
    }
}

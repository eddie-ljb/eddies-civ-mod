package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.shared.IdGenerator;

import java.util.Objects;
import java.util.UUID;

/** Identität eines Kredits. */
public record LoanId(UUID value) {

    public LoanId {
        Objects.requireNonNull(value, "value");
    }

    public static LoanId generate(IdGenerator ids) {
        return new LoanId(ids.next());
    }
}

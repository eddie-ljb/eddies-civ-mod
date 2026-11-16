package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.shared.IdGenerator;

import java.util.Objects;
import java.util.UUID;

/** Kennung eines Gerichtsverfahrens. */
public record CaseId(UUID value) {

    public CaseId {
        Objects.requireNonNull(value, "value");
    }

    public static CaseId generate(IdGenerator ids) {
        return new CaseId(ids.next());
    }
}

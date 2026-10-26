package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.DomainException;

import java.util.Objects;

/**
 * Ein Claim wurde abgelehnt. Der Fehlercode ergibt sich aus dem {@link ClaimRejection}-Grund.
 */
public final class ClaimRejectedException extends DomainException {

    private final ClaimRejection rejection;

    public ClaimRejectedException(ClaimRejection rejection, ChunkCoord target) {
        super(Objects.requireNonNull(rejection, "rejection").errorCode(), target.x(), target.z());
        this.rejection = rejection;
    }

    public ClaimRejection rejection() {
        return rejection;
    }
}

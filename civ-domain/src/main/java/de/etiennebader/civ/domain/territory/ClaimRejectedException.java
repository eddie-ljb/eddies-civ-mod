package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;

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

    /** Für {@link ClaimRejection#INSUFFICIENT_FUNDS}: {@code missing} ist der fehlende Betrag. */
    public ClaimRejectedException(ClaimRejection rejection, ChunkCoord target, Money missing) {
        super(Objects.requireNonNull(rejection, "rejection").errorCode(), target.x(), target.z(), missing);
        this.rejection = rejection;
    }

    public ClaimRejection rejection() {
        return rejection;
    }
}

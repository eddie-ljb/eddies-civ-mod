package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Grund, aus dem ein Claim abgelehnt wird, mit dem zugehörigen Fehlercode für den Spieler.
 */
public enum ClaimRejection {
    ALREADY_CLAIMED(ErrorCode.CHUNK_ALREADY_CLAIMED),
    NOT_ADJACENT(ErrorCode.CLAIM_NOT_ADJACENT),
    LIMIT_REACHED(ErrorCode.CLAIM_LIMIT_REACHED),
    OUTPOST_LIMIT_REACHED(ErrorCode.OUTPOST_LIMIT_REACHED);

    private final ErrorCode errorCode;

    ClaimRejection(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

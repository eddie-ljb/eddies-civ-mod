package de.etiennebader.civ.domain.shared;

/**
 * Ein vom Spieler stammender Wert verletzt die Regeln eines Value Objects,
 * z. B. ein zu kurzer Name.
 */
public final class InvalidValueException extends DomainException {

    public InvalidValueException(ErrorCode code, Object... arguments) {
        super(code, arguments);
    }
}

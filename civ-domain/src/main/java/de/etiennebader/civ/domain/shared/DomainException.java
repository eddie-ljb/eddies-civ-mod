package de.etiennebader.civ.domain.shared;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Basis aller fachlichen Fehler. Trägt einen {@link ErrorCode} und die Argumente für
 * die übersetzte Spielermeldung. Tests prüfen den Code, nie den Text.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final List<Object> arguments;

    protected DomainException(ErrorCode code, Object... arguments) {
        super(describe(code, arguments));
        this.code = Objects.requireNonNull(code, "code");
        this.arguments = List.of(arguments);
    }

    public ErrorCode code() {
        return code;
    }

    public List<Object> arguments() {
        return arguments;
    }

    private static String describe(ErrorCode code, Object[] arguments) {
        return code + " " + Arrays.toString(arguments);
    }
}

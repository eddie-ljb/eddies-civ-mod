package de.etiennebader.civ.domain.testsupport;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prüft fachliche Fehler über ihren {@link ErrorCode}, nie über den Text.
 */
public final class DomainAssertions {

    private DomainAssertions() {
    }

    public static DomainException assertDomainError(ErrorCode expected, Executable action) {
        DomainException exception = assertThrows(DomainException.class, action);
        assertEquals(expected, exception.code(), () -> "Unerwarteter Fehlercode: " + exception.getMessage());
        return exception;
    }
}

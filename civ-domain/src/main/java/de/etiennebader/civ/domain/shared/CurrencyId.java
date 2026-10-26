package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Kennung einer Währung, z. B. {@code EMERALD} für die smaragdgedeckte Leitwährung.
 */
public record CurrencyId(String code) {

    // Muss vor den Konstanten stehen, weil deren Konstruktor das Muster bereits verwendet.
    private static final Pattern VALID_CODE = Pattern.compile("[A-Z_]{2,16}");

    public static final CurrencyId EMERALD = new CurrencyId("EMERALD");

    public CurrencyId {
        Objects.requireNonNull(code, "code");
        if (!VALID_CODE.matcher(code).matches()) {
            throw new InvalidValueException(ErrorCode.INVALID_CURRENCY_CODE, code);
        }
    }

    @Override
    public String toString() {
        return code;
    }
}

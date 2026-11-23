package de.etiennebader.civ.domain.economy;

/**
 * Wechselkurs einer Währung zu Smaragd: {@code numerator} Einheiten dieser Währung entsprechen
 * {@code denominator} Smaragd-Einheiten (Hundertstel Smaragd, siehe {@code MoneyFormat}).
 *
 * <p>Ganzzahlig und ohne Rundungsfehler bei der Umrechnung in beide Richtungen, solange mit
 * {@link #toEmeraldUnits} bzw. {@link #fromEmeraldUnits} gerechnet wird (Abrunden auf die nächste
 * ganze Einheit).
 */
public record ExchangeRate(long numerator, long denominator) {

    public ExchangeRate {
        if (numerator <= 0) {
            throw new IllegalArgumentException("Zähler muss positiv sein: " + numerator);
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("Nenner muss positiv sein: " + denominator);
        }
    }

    /** 1:1 zu Smaragd-Einheiten. */
    public static final ExchangeRate PARITY = new ExchangeRate(1, 1);

    /** Wandelt einen Betrag dieser Währung in Smaragd-Einheiten um (abgerundet). */
    public long toEmeraldUnits(long currencyUnits) {
        return Math.floorDiv(Math.multiplyExact(currencyUnits, denominator), numerator);
    }

    /** Wandelt einen Betrag in Smaragd-Einheiten in diese Währung um (abgerundet). */
    public long fromEmeraldUnits(long emeraldUnits) {
        return Math.floorDiv(Math.multiplyExact(emeraldUnits, numerator), denominator);
    }
}

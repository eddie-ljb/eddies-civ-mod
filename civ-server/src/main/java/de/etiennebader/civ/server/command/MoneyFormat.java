package de.etiennebader.civ.server.command;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Umrechnung zwischen Geldbeträgen in Einheiten (Hundertstel Smaragd) und der Darstellung für Spieler.
 */
public final class MoneyFormat {

    /** So viele Einheiten ergeben einen Smaragd. */
    public static final long UNITS_PER_EMERALD = 100;

    private static final Pattern AMOUNT = Pattern.compile("\\d{1,12}([.,]\\d{1,2})?");

    private MoneyFormat() {
    }

    /** Ganze Smaragde in Einheiten. */
    public static long emeralds(long emeralds) {
        return Math.multiplyExact(emeralds, UNITS_PER_EMERALD);
    }

    /**
     * Liest eine Eingabe wie {@code 12}, {@code 12,5} oder {@code 0.25} als Smaragde.
     *
     * @return den Betrag in Einheiten
     * @throws IllegalArgumentException wenn die Eingabe keine positive Zahl mit höchstens zwei Nachkommastellen ist
     */
    public static long parse(String input) {
        String normalized = input.strip().replace(',', '.');
        if (!AMOUNT.matcher(input.strip()).matches()) {
            throw new IllegalArgumentException("Kein gültiger Betrag: " + input);
        }
        long units = new BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        if (units <= 0) {
            throw new IllegalArgumentException("Betrag muss positiv sein: " + input);
        }
        return units;
    }

    /** Die ganzen Smaragde eines Betrags, z. B. {@code 12} bei 12,05. */
    public static String whole(long units) {
        return (units < 0 ? "-" : "") + Math.abs(units) / UNITS_PER_EMERALD;
    }

    /** Die zwei Nachkommastellen eines Betrags, z. B. {@code 05} bei 12,05. */
    public static String cents(long units) {
        return String.format("%02d", Math.abs(units) % UNITS_PER_EMERALD);
    }
}

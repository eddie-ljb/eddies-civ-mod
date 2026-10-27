package de.etiennebader.civ.domain.shared;

import java.util.Locale;

/**
 * Eindeutiger Code jedes fachlichen Fehlers.
 *
 * <p>Der Kern enthält keine Fehlertexte. Die Minecraft-Schicht übersetzt den Code über
 * {@link #translationKey()} mit den Sprachdateien ({@code de_de.json}, {@code en_us.json})
 * in eine Meldung für den Spieler. Die Argumente einer {@link DomainException} werden
 * in der hier dokumentierten Reihenfolge als Platzhalter eingesetzt.
 */
public enum ErrorCode {

    // shared
    /** Argumente: erste Währung, zweite Währung. */
    CURRENCY_MISMATCH,
    /** Argumente: ungültiger Code. */
    INVALID_CURRENCY_CODE,

    // law
    /** Argumente: Titel, Mindestlänge, Höchstlänge. */
    INVALID_STATUTE_TITLE,
    /** Keine Argumente. */
    STATUTE_WITHOUT_VALUES,
    /** Argumente: Parameter-ID. */
    DUPLICATE_PARAMETER,
    /** Argumente: Parameter-ID, ungültiger Wert. */
    INVALID_PARAMETER_VALUE,
    /** Argumente: Gesetz-ID. */
    UNKNOWN_STATUTE,
    /** Argumente: Gesetz-ID. */
    STATUTE_ALREADY_IN_FORCE,

    // polity
    /** Argumente: Name, Mindestlänge, Höchstlänge. */
    INVALID_POLITY_NAME,
    /** Argumente: Gründer. */
    ILLEGAL_FOUNDER,
    /** Argumente: Akteur. */
    ALREADY_MEMBER,
    /** Argumente: Akteur. */
    NOT_A_MEMBER,
    /** Argumente: Akteur. */
    LAST_MEMBER,

    // territory
    /** Argumente: Chunk-X, Chunk-Z. */
    CHUNK_ALREADY_CLAIMED,
    /** Argumente: Chunk-X, Chunk-Z. */
    CLAIM_NOT_ADJACENT,
    /** Argumente: Chunk-X, Chunk-Z. */
    CLAIM_LIMIT_REACHED,
    /** Argumente: Chunk-X, Chunk-Z. */
    OUTPOST_LIMIT_REACHED,
    /** Argumente: Chunk-X, Chunk-Z. */
    NOT_SOVEREIGN,

    // land
    /** Argumente: Chunk-X, Chunk-Z des ersten Chunks außerhalb des Gebiets. */
    PLOT_OUTSIDE_TERRITORY,
    /** Argumente: ID des überlappenden Grundstücks. */
    PLOT_OVERLAP,
    /** Argumente: vorgesehener Inhaber. */
    INVALID_EMBASSY_HOLDER,
    /** Argumente: Akteur, Grundstück-ID. */
    NOT_PLOT_HOLDER,
    /** Argumente: Grundstück-ID. */
    UNKNOWN_PLOT,

    // economy
    /** Argumente: fehlender Betrag, verfügbarer Betrag, benötigter Betrag. */
    INSUFFICIENT_FUNDS,
    /** Argumente: einzuzahlender Betrag, umlaufendes Bargeld. */
    CASH_INTEGRITY,

    // application: Regeln, die mehrere Aggregate betreffen
    /** Argumente: Polity-ID. */
    UNKNOWN_POLITY,
    /** Argumente: Akteur, Name der anderen Gemeinde. */
    MEMBER_OF_OTHER_POLITY,
    POLITY_NAME_TAKEN,
    NOT_A_CITIZEN,
    /** Argumente: Chunk-X, Chunk-Z. */
    CHUNK_HAS_PLOTS;

    private static final String TRANSLATION_PREFIX = "eddies-civ-mod.error.";

    /** Schlüssel in den Sprachdateien, z. B. {@code eddies-civ-mod.error.already_member}. */
    public String translationKey() {
        return TRANSLATION_PREFIX + name().toLowerCase(Locale.ROOT);
    }
}

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

    // economy / currencies
    /** Argumente: Währungscode. */
    UNKNOWN_CURRENCY,
    /** Argumente: Währungscode oder Symbol, das schon vergeben ist. */
    CURRENCY_ALREADY_EXISTS,
    /** Argumente: Währungscode der eingebauten, unveränderlichen Währung. */
    CURRENCY_PROTECTED,
    /** Argumente: Währungscode, Akteur. */
    NOT_CURRENCY_GOVERNOR,
    /** Argumente: Betrag, der nicht negativ sein darf. */
    NEGATIVE_CONVERSION_AMOUNT,

    // credit
    /** Argumente: Akteur. */
    NOT_A_BANK,
    /** Argumente: Akteur. */
    ALREADY_A_BANK,
    /** Keine Argumente. */
    INVALID_LOAN_TERMS,
    /** Argumente: Kredit-ID. */
    UNKNOWN_LOAN,
    /** Argumente: Kredit-ID. */
    LOAN_NOT_ACTIVE,
    /** Argumente: Kredit-ID, Akteur. */
    NOT_LOAN_BORROWER,
    /** Argumente: Zins in Prozent, erlaubter Höchstzins in Prozent. */
    INTEREST_ABOVE_LIMIT,
    /** Argumente: Kreditscore, Mindestscore. */
    CREDIT_SCORE_TOO_LOW,
    /** Keine Argumente. */
    LENDING_NOT_ALLOWED,
    /** Argumente: Angebots-ID. */
    UNKNOWN_LOAN_OFFER,
    /** Argumente: Angebots-ID, Akteur. */
    NOT_OFFER_PARTY,
    /** Argumente: Bank. */
    BANK_NOT_SELF_SERVICE,

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
    /** Argumente: Abstimmungs-ID. */
    UNKNOWN_PROPOSAL,
    /** Argumente: Parameter-ID. */
    PROPOSAL_ALREADY_OPEN,
    /** Argumente: Amt. */
    ELECTION_ALREADY_OPEN,
    /** Argumente: Wahl-ID. */
    UNKNOWN_ELECTION,
    /** Argumente: Akteur. */
    NOT_A_CANDIDATE,
    /** Amtszeitbegrenzung erreicht. */
    TERM_LIMIT_REACHED,
    CANDIDACY_OUTSIDE_PARTY_AREA,
    /** Verbannt: Beitritt bis zum Ende der Verbannung verboten. */
    EXILED,
    /** Argumente: Akteur. */
    ALREADY_CANDIDATE,
    /** Wahlkreiswahl: Kandidatur ohne oder mit unbekanntem Wahlkreis. Argumente: Wahlkreis (oder leer). */
    INVALID_CANDIDACY_DISTRICT,
    /** Wahlkreiswahl: Stimme für einen Kandidaten eines anderen Wahlkreises. Argumente: Kandidat. */
    VOTE_OUTSIDE_DISTRICT,

    // territory
    /** Argumente: Chunk-X, Chunk-Z. */
    CHUNK_ALREADY_CLAIMED,
    /** Argumente: Chunk-X, Chunk-Z. */
    CLAIM_NOT_ADJACENT,
    /** Argumente: Chunk-X, Chunk-Z. */
    CLAIM_LIMIT_REACHED,
    /** Argumente: Chunk-X, Chunk-Z. */
    OUTPOST_LIMIT_REACHED,
    /** Argumente: Chunk-X, Chunk-Z, fehlender Betrag. */
    CLAIM_INSUFFICIENT_FUNDS,
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
    /** Argumente: Grundstück-ID. */
    PLOT_NOT_FOR_SALE,
    /** Argumente: Akteur. */
    NOT_SHOP_OWNER,
    /** Argumente: Position. */
    UNKNOWN_SHOP,

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
    /** Argumente: Akteur, Amt. */
    NOT_OFFICE_HOLDER,
    /** Argumente: Gliedkörperschaft, Bund, dem sie schon angehört. */
    ALREADY_IN_FEDERATION,
    /** Argumente: Polity. */
    NOT_IN_FEDERATION,
    /** Argumente: Bund. */
    LAST_CONSTITUENT,
    /** Argumente: Polity. */
    NOT_A_FEDERATION,
    NOT_A_MUNICIPALITY,
    CONSTITUTION_ALREADY_PROCLAIMED,
    PREAMBLE_TOO_LONG,
    CONSTITUTION_REQUIRES_VOTE,
    ALREADY_IN_PARTY,
    NOT_IN_PARTY,
    NOT_A_PARTY,
    STATUTE_TEXT_TOO_LONG,
    /** Argumente: Gliedkörperschaft, Bund. */
    FEDERATION_CYCLE,
    /** Das Bürgermeisteramt wird nur gewählt, nicht ernannt. */
    MAYOR_NOT_APPOINTABLE,
    /** Argumente: Chunk-X, Chunk-Z. */
    CHUNK_HAS_PLOTS,
    /** Argumente: Akteur. Mit gewähltem Parlament stimmen nur Abgeordnete ab. */
    NOT_IN_PARLIAMENT,
    /** Das Recht sieht kein Parlament vor ({@code parliament_seats} ist 0). */
    NO_PARLIAMENT_PROVIDED,
    /** Mit gewähltem Parlament braucht jede Rechtsänderung seine Zustimmung. */
    PARLIAMENT_DECIDES,
    /** Das Parlament wird gewählt, nicht ernannt. */
    PARLIAMENT_NOT_APPOINTABLE,
    /** Das Recht schreibt die Stimmabgabe an einer Wahlurne vor. */
    BALLOT_BOX_REQUIRED,
    /** Nur wer bei einer laufenden Wahl kandidiert, kann etwas versprechen. */
    NOT_RUNNING,
    /** Argumente: Vertrag. */
    UNKNOWN_CONTRACT,
    /** Argumente: Vertragsnummer. */
    CONTRACT_NOT_POSSIBLE,
    /** Argumente: Spieler. */
    NOT_FRIENDS,
    /** Das Grundstück gehört nicht dem Vermieter oder ist schon vermietet. */
    PLOT_NOT_LEASABLE,
    /** Der Dorfbewohner arbeitet schon oder lehnt den Lohn ab. */
    VILLAGER_DECLINES,
    /** Argumente: Name. */
    UNKNOWN_STREET,
    NOT_ENOUGH_SHARES,
    UNKNOWN_ORDER,
    NOT_LISTED,
    INVALID_ORDER,
    NOT_AN_ORGANISATION,
    NOT_AN_EDITOR,
    PRESS_LIMIT,

    // Markt
    INVALID_COMMODITY,
    INVALID_QUANTITY,
    OUT_OF_STOCK,
    WORTHLESS_GOODS,

    // Krieg
    /** Argumente: Angreifer, Verteidiger. */
    WAR_ALREADY_ONGOING,
    /** Argumente: Krieg-ID. */
    UNKNOWN_WAR,
    /** Der Krieg ist bereits beendet. Argumente: Krieg-ID. */
    WAR_ALREADY_ENDED,
    /** Die Verteidigerin hat sich Neutralität erkauft. Argumente: Polity. */
    DEFENDER_IS_NEUTRAL,
    /** Argumente: Polity, Akteur. */
    ALREADY_ENLISTED,
    /** Argumente: Polity, Akteur. */
    NOT_ENLISTED,
    /** Neutralität lässt sich nicht während eines laufenden Kriegs erkaufen. Argumente: Polity. */
    AT_WAR,
    /** Der Feldzug ist nicht möglich (Zeitpunkt, Art oder Beteiligte). Argumente: keine. */
    INVALID_CAMPAIGN,
    /** Das Bündnis ist nicht möglich oder gibt es nicht. Argumente: keine. */
    INVALID_ALLIANCE,
    /** Verbündete können einander nicht den Krieg erklären. Argumente: Polity, Polity. */
    ALLIED_POLITIES,
    /** Der Anspruch ist nicht möglich. Argumente: keine. */
    INVALID_CLAIM,
    /** Die Truppe ist schon voll ausgebildet bzw. ausgerüstet. Argumente: keine. */
    NOTHING_TO_IMPROVE,
    /** Der Krieg hat keine offenen Siegpunkte (nicht entschieden oder schon verteilt). Argumente: keine. */
    NO_SPOILS;

    private static final String TRANSLATION_PREFIX = "eddies-civ-mod.error.";

    /** Schlüssel in den Sprachdateien, z. B. {@code eddies-civ-mod.error.already_member}. */
    public String translationKey() {
        return TRANSLATION_PREFIX + name().toLowerCase(Locale.ROOT);
    }
}

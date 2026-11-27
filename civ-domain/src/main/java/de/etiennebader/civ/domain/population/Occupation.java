package de.etiennebader.civ.domain.population;

import java.util.Optional;

/**
 * Beruf eines Dorfbewohners, wie ihn Mitbürger an seiner Kleidung erkennen (Villager-Skins nach Beruf).
 *
 * <p>Die Reihenfolge ist zugleich der Vorrang: Wer mehrere Rollen hat (etwa Bürgermeister und Firmenchef), trägt die
 * Kleidung der ersten. {@link #CIVILIAN} ist kein Beruf und steht zuletzt. Der {@link #code() Code} geht übers Netz zum
 * Client und bleibt deshalb fest, auch wenn die Reihenfolge sich ändert.
 */
public enum Occupation {
    /** Amtsträger: Bürgermeister, Schatzmeister, Bauamt, Abgeordnete, Parteivorsitzende, Minister. */
    POLITICIAN(1),
    /** Richter und Staatsanwälte. */
    JUDGE(2),
    /** Offiziere der Garnison: Unteroffizier, Offizier, Hauptmann, General. */
    OFFICER(3),
    /** Soldaten und Ausspäher. */
    SOLDIER(4),
    /** Polizisten aller Dienstgrade. */
    POLICE(5),
    /** Betreiber einer Bank. */
    BANKER(6),
    /** Chef einer Firma. */
    ENTREPRENEUR(7),
    /** Leiter einer Zeitung. */
    JOURNALIST(8),
    /** Angestellte einer Baufirma. */
    BUILDER(9),
    /** Angestellte einer Wartungsfirma. */
    MECHANIC(10),
    /** Angestellte eines Bergwerks. */
    MINER(11),
    /** Angestellte einer Forstfirma. */
    LUMBERJACK(12),
    /** Angestellte in Ackerbau, Obstbau, Schäferei und Viehzucht. */
    FARMER(13),
    /** Angestellte einer Fischerei. */
    FISHER(14),
    /** Alle übrigen Angestellten (zum Beispiel einer Ziegelei). */
    WORKER(15),
    /** Kein besonderer Beruf: bleibt im Vanilla-Gewand. */
    CIVILIAN(0);

    private final int code;

    Occupation(int code) {
        this.code = code;
    }

    /** Fester Code für die Übertragung zum Client. */
    public int code() {
        return code;
    }

    /** Ob der Dorfbewohner besondere Kleidung trägt. */
    public boolean dressed() {
        return this != CIVILIAN;
    }

    /** Ob dies der Vorrang-höhere der beiden Berufe ist. */
    public boolean outranks(Occupation other) {
        return this != CIVILIAN && (other == CIVILIAN || ordinal() < other.ordinal());
    }

    /** Beruf zu einem Code; unbekannte Codes (neuere Server) ergeben keinen Beruf. */
    public static Occupation ofCode(int code) {
        for (Occupation occupation : values()) {
            if (occupation.code == code) {
                return occupation;
            }
        }
        return CIVILIAN;
    }

    /** Beruf, den ein Angestellter einer Firma dieser Branche ausübt. */
    public static Occupation employeeOf(String branchKey) {
        return switch (Optional.ofNullable(branchKey).orElse("")) {
            case "bau" -> BUILDER;
            case "wartung" -> MECHANIC;
            case "bergwerk" -> MINER;
            case "forst" -> LUMBERJACK;
            case "acker", "obstbau", "schaeferei", "viehzucht" -> FARMER;
            case "fischerei" -> FISHER;
            default -> WORKER;
        };
    }
}

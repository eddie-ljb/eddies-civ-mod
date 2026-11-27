package de.etiennebader.civ.domain.war;

/**
 * Militärischer Rang eines Mitglieds in der Garnison einer Polity.
 *
 * <p>Die Kampfkraft ist in Hundertsteln eines einfachen Soldaten angegeben (100 = ein Soldat). Offiziere zählen
 * dreifach, Ausspäher nur halb, liefern dafür Aufklärung ({@link WarRegistry#intel}) und ermöglichen Hinterhalte.
 *
 * <p>Befehlskette (Issue #84): Soldat/Ausspäher &lt; Unteroffizier &lt; Offizier &lt; Hauptmann &lt; General. Höhere Ränge
 * befehligen niedrigere und befördern sie; Hauptmann und General führen den Feldzug, es gibt nur einen General.
 * Die Reihenfolge der ersten drei Werte ist fest (Speicherformat benennt sie, Reihenfolge nutzen Verlustregeln).
 */
public enum MilitaryRank {
    SOLDIER(100, 1),
    OFFICER(300, 3),
    SCOUT(50, 1),
    SERGEANT(200, 2),
    CAPTAIN(450, 4),
    GENERAL(700, 5);

    private final int power;
    private final int level;

    MilitaryRank(int power, int level) {
        this.power = power;
        this.level = level;
    }

    /** Grundkampfkraft in Hundertsteln eines Soldaten. */
    public int power() {
        return power;
    }

    /** Stufe in der Befehlskette, 1 (Soldat, Ausspäher) bis 5 (General). */
    public int level() {
        return level;
    }

    public boolean outranks(MilitaryRank other) {
        return level > other.level;
    }

    /** Ob dieser Rang Feldzüge planen darf (Hauptmann, General). */
    public boolean commandsCampaigns() {
        return level >= CAPTAIN.level;
    }

    /** Reihenfolge der Verluste: niedrige Werte werden zuerst verwundet. */
    public int casualtyOrder() {
        return level * 2 + (this == SCOUT ? 1 : 0);
    }
}

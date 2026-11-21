package de.etiennebader.civ.domain.war;

/**
 * Militärischer Rang eines Mitglieds in der Garnison einer Polity.
 *
 * <p>Die Kampfkraft ist in Hundertsteln eines einfachen Soldaten angegeben (100 = ein Soldat). Offiziere zählen
 * dreifach, Ausspäher nur halb, liefern dafür Aufklärung ({@link WarRegistry#intel}) und ermöglichen Hinterhalte.
 */
public enum MilitaryRank {
    SOLDIER(100),
    OFFICER(300),
    SCOUT(50);

    private final int power;

    MilitaryRank(int power) {
        this.power = power;
    }

    /** Grundkampfkraft in Hundertsteln eines Soldaten. */
    public int power() {
        return power;
    }
}

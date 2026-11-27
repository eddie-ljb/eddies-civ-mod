package de.etiennebader.civ.domain.police;

/**
 * Dienstgrad bei der Polizei einer Gemeinde. Höhere Ränge befehligen niedrigere (Befehlskette).
 *
 * <p>Befugnisse: Festnehmen darf ab {@link #OFFICER}; ein {@link #CADET} darf nur melden und streifen. Befördern und
 * Entlassen darf nur, wer höher im Rang steht als der Betroffene und als der neue Rang.
 */
public enum PoliceRank {
    CADET(1),
    OFFICER(2),
    SERGEANT(3),
    COMMISSIONER(4);

    private final int level;

    PoliceRank(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean mayArrest() {
        return level >= OFFICER.level;
    }

    public boolean outranks(PoliceRank other) {
        return level > other.level;
    }
}

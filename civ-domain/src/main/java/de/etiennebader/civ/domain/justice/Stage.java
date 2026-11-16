package de.etiennebader.civ.domain.justice;

/** Abschnitt eines Verfahrens, in dieser Reihenfolge. */
public enum Stage {
    /** Eingereicht; der Richter eröffnet oder weist ab. */
    FILED,
    /** Beweisaufnahme: Beweise, Zeugen, Aussagen. */
    EVIDENCE,
    /** Schlussvorträge (Plädoyers) beider Seiten. */
    PLEADINGS,
    /** Beratung: Geschworene stimmen ab, sonst entscheidet der Richter. */
    DELIBERATION,
    /** Urteil gesprochen; bis zum Ende der Berufungsfrist nicht rechtskräftig. */
    DECIDED,
    /** Rechtskräftig und vollstreckt. */
    FINAL,
    /** Vor der Eröffnung abgewiesen. */
    DISMISSED,
    /** In Berufung an das höhere Gericht gegangen. */
    APPEALED;

    public boolean open() {
        return this == FILED || this == EVIDENCE || this == PLEADINGS || this == DELIBERATION;
    }
}

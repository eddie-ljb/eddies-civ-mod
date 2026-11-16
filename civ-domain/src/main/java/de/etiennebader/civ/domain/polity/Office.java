package de.etiennebader.civ.domain.polity;

/**
 * Ämter einer Gemeinde. Jedes Amt hat höchstens einen Inhaber, außer {@link #PARLIAMENT}.
 */
public enum Office {
    /** Leitet die Gemeinde: Gesetze ohne Abstimmung, Sitz verlegen, auflösen, Ämter besetzen. */
    MAYOR,
    /** Verwaltet die Gemeindekasse. */
    TREASURER,
    /** Bauamt: legt Grundstücke an, bietet sie an, vergibt und löst sie auf. */
    PLANNER,
    /**
     * Das Parlament: ein Gremium mit mehreren Sitzen, kein Einzelamt. Gewählt wird es wie ein Amt (Kandidatur,
     * Stimmabgabe), besetzt aber über {@link Polity#seatParliament}; ernennen lässt es sich nicht.
     */
    PARLIAMENT;

    /** Ob das Amt einen einzelnen Inhaber hat. */
    public boolean single() {
        return this != PARLIAMENT;
    }
}

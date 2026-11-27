package de.etiennebader.civ.domain.government;

/** Staatsform einer politischen Ebene; legt nur den Standardtitel fest, alles andere ist frei konfigurierbar. */
public enum StateForm {
    REPUBLIC("Präsident"),
    MONARCHY("König"),
    EMPIRE("Kaiser"),
    PRINCIPALITY("Fürst"),
    CUSTOM("Oberhaupt");

    private final String defaultTitle;

    StateForm(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}

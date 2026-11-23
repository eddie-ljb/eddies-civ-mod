package de.etiennebader.civ.domain.land;

/**
 * Nutzungsart eines Grundstücks.
 */
public enum PlotType {
    RESIDENTIAL,
    COMMERCIAL,
    INDUSTRIAL,
    AGRICULTURAL,
    PUBLIC,
    /** Botschaft: gehört immer einer fremden Polity. */
    EMBASSY,
    /** Schule: junge Dorfbewohner, die sich hier aufhalten, gewinnen Bildung (Issue #21). */
    EDUCATION,
    /** Gotteshaus: Gebete hier stärken die Inbrunst der Religion doppelt (Issue #13). */
    RELIGIOUS
}

package de.etiennebader.civ.domain.protection;

/**
 * Warum eine Handlung verweigert wurde.
 */
public enum DenialReason {

    /** Der Akteur gehört nicht zur Gemeinde, und ihr Recht erlaubt es Fremden nicht. */
    FOREIGN_TERRITORY,
    /** Das Grundstück gehört jemand anderem. */
    FOREIGN_PLOT,
    /** Das Gemeindehaus selbst darf niemand verändern. */
    SEAT
}

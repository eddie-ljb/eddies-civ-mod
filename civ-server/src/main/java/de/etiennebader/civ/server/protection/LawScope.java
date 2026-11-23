package de.etiennebader.civ.server.protection;

/**
 * Wessen Recht für eine Handlung gilt, die ein Gesetz einer fremden Mod regelt (Third-Party-API, #51).
 */
public enum LawScope {

    /**
     * Das Recht der Gemeinde, auf deren Gebiet die Handlung stattfindet; es gilt für jeden dort,
     * Mitglieder wie Fremde. In der Wildnis gilt kein Recht, die Handlung ist erlaubt.
     */
    TERRITORY,

    /**
     * Das Recht der Gemeinde, der der Spieler angehört; es gilt für ihn überall. Wer keiner Gemeinde
     * angehört, unterliegt keinem Recht.
     */
    CITIZENSHIP
}

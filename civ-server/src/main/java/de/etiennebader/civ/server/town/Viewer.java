package de.etiennebader.civ.server.town;

/**
 * Wer eine Gemeinde betrachtet, und was er darum in Menüs tun darf.
 */
public enum Viewer {
    OUTSIDER,
    RESIDENT,
    CITIZEN,
    MAYOR;

    public boolean isMember() {
        return this != OUTSIDER;
    }

    public boolean isCitizen() {
        return this == CITIZEN || this == MAYOR;
    }
}

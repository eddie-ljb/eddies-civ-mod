package de.etiennebader.civ.server.territory;

/**
 * Wie ein Chunk zu einem Spieler steht.
 */
public enum Relation {
    /** Gebiet der eigenen Gemeinde. */
    OWN,
    /** Gebiet einer anderen Gemeinde. */
    FOREIGN,
    /** Niemandes Land. */
    WILDERNESS
}

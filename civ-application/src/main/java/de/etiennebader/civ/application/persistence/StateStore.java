package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.query.WorldQueries;

/**
 * Port zur dauerhaften Speicherung des Weltzustands, z. B. in SQLite.
 */
public interface StateStore {

    /** Lädt den vollständigen gespeicherten Zustand; bei einem neuen Speicher eine leere Welt. */
    StoredWorld load();

    /**
     * Schreibt alle Änderungen in einer Transaktion: entweder vollständig oder gar nicht.
     *
     * @param changes was sich geändert hat
     * @param current der aktuelle Arbeitsstand, aus dem die neuen Werte gelesen werden
     */
    void write(ChangeSet changes, WorldQueries current);
}

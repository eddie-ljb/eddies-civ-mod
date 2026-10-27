package de.etiennebader.civ.application.persistence;

/**
 * Port zur dauerhaften Speicherung des Weltzustands, z. B. in SQLite.
 *
 * <p>{@link #write} darf aus einem anderen Thread aufgerufen werden als {@link #load}, aber nie
 * aus mehreren gleichzeitig.
 */
public interface StateStore {

    /** Lädt den vollständigen gespeicherten Zustand; bei einem neuen Speicher eine leere Welt. */
    StoredWorld load();

    /** Schreibt alle Änderungen in einer Transaktion: entweder vollständig oder gar nicht. */
    void write(StateDelta delta);
}

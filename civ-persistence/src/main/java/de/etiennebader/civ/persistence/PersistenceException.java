package de.etiennebader.civ.persistence;

/**
 * Lesen oder Schreiben der Datenbank ist fehlgeschlagen, oder die gespeicherten Daten sind ungültig.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

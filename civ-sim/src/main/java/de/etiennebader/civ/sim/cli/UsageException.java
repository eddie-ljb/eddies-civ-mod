package de.etiennebader.civ.sim.cli;

/**
 * Die Kommandozeile ist fehlerhaft.
 */
public final class UsageException extends RuntimeException {

    public UsageException(String message) {
        super(message);
    }
}

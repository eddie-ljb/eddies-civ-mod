package de.etiennebader.civ.persistence.migration;

import de.etiennebader.civ.persistence.PersistenceException;

/**
 * Die Datenbank hat eine neuere Schemaversion, als dieser Mod kennt, z. B. nach einem Downgrade.
 * Sie wird dann nicht angefasst, damit keine Daten verloren gehen.
 */
public final class UnsupportedSchemaVersionException extends PersistenceException {

    public UnsupportedSchemaVersionException(int databaseVersion, int latestKnownVersion) {
        super("Datenbank hat Schemaversion " + databaseVersion + ", dieser Mod kennt nur bis "
                + latestKnownVersion + ". Bitte eine neuere Mod-Version verwenden.");
    }
}

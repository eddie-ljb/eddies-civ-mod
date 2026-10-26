package de.etiennebader.civ.sim;

import de.etiennebader.civ.persistence.store.SqliteStateStore;
import de.etiennebader.civ.sim.cli.CommandLineOptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Die SQLite-Datei eines Simulationslaufs. Eine bestehende Datei wird überschrieben, weil jeder
 * Lauf mit einer neuen Welt beginnt.
 */
final class SaveTarget implements AutoCloseable {

    private final Optional<SqliteStateStore> store;
    private final int intervalDays;

    private SaveTarget(Optional<SqliteStateStore> store, int intervalDays) {
        this.store = store;
        this.intervalDays = intervalDays;
    }

    static SaveTarget open(CommandLineOptions options) {
        return new SaveTarget(options.save().map(SaveTarget::freshStore), options.saveEvery());
    }

    private static SqliteStateStore freshStore(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-journal"));
        } catch (IOException e) {
            throw new UncheckedIOException("Alte Speicherdatei kann nicht entfernt werden: " + file, e);
        }
        return SqliteStateStore.open(file);
    }

    Optional<Autosave> autosave() {
        return store.map(sqlite -> new Autosave(sqlite, intervalDays));
    }

    @Override
    public void close() {
        store.ifPresent(SqliteStateStore::close);
    }
}

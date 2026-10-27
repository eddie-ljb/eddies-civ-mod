package de.etiennebader.civ.application.directory;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDirectoryTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(3);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerDirectory directory = new PlayerDirectory();

    @Test
    @DisplayName("Nur neue oder geänderte Namen gelten als Änderung")
    void tracksOnlyNewOrChangedNames() {
        directory.record(alice, "Alice");
        directory.record(bob, "Bob");
        directory.takeChanges();

        directory.record(alice, "Alice");
        assertFalse(directory.hasChanges());

        directory.record(alice, "Alicia");
        assertEquals(List.of(new KnownPlayer(alice, "Alicia")), directory.takeChanges());
        assertEquals(Optional.of("Alicia"), directory.nameOf(alice));
    }

    @Test
    @DisplayName("Geladene Einträge gelten nicht als Änderung")
    void restoredEntriesAreClean() {
        directory.restore(List.of(new KnownPlayer(alice, "Alice")));

        assertFalse(directory.hasChanges());
        assertEquals(Optional.of(alice), directory.findByName("ALICE"));
    }

    @Test
    @DisplayName("Fehlgeschlagene Einträge werden erneut gemeldet")
    void requeuesFailedEntries() {
        directory.record(alice, "Alice");
        List<KnownPlayer> failed = directory.takeChanges();

        directory.requeue(failed);

        assertTrue(directory.hasChanges());
        assertEquals(failed, directory.takeChanges());
    }
}

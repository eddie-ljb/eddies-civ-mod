package de.etiennebader.civ.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SeededIdGeneratorTest {

    @Test
    @DisplayName("Gleicher Seed → identische UUID-Folgen")
    void sameSeedProducesSameSequence() {
        assertEquals(firstIds(new SeededIdGenerator(7), 100), firstIds(new SeededIdGenerator(7), 100));
    }

    @Test
    @DisplayName("Verschiedene Seeds → verschiedene Folgen")
    void differentSeedsProduceDifferentSequences() {
        assertNotEquals(firstIds(new SeededIdGenerator(1), 10), firstIds(new SeededIdGenerator(2), 10));
    }

    @Test
    @DisplayName("Erzeugt gültige, eindeutige Version-4-UUIDs")
    void producesUniqueVersion4Uuids() {
        List<UUID> ids = firstIds(new SeededIdGenerator(3), 10_000);
        Set<UUID> unique = new HashSet<>(ids);

        assertEquals(ids.size(), unique.size());
        ids.forEach(id -> {
            assertEquals(4, id.version());
            assertEquals(2, id.variant());
        });
    }

    private static List<UUID> firstIds(IdGenerator generator, int count) {
        return Stream.generate(generator::next).limit(count).toList();
    }
}

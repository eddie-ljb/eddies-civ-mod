package de.etiennebader.civ.domain.civic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aufnahme (OGG-Hash) an einer Hymne (Issue #81). */
class AnthemAudioTest {

    private static final String HASH = "ab".repeat(32);

    @Test
    void anthemWithoutAudioHasNone() {
        Anthem anthem = new Anthem("Lied", "harp", "C4 D4");
        assertFalse(anthem.hasAudio());
        assertEquals("", anthem.audio());
    }

    @Test
    void withAudioKeepsNotesAsFallback() {
        Anthem anthem = new Anthem("Lied", "harp", "C4 D4").withAudio(HASH.toUpperCase());
        assertTrue(anthem.hasAudio());
        assertEquals(HASH, anthem.audio());
        assertEquals("C4 D4", anthem.notes());
        assertEquals("anthem." + HASH.substring(0, 16), anthem.soundName());
        assertFalse(anthem.withAudio("").hasAudio());
    }

    @Test
    void rejectsMalformedHash() {
        Anthem anthem = new Anthem("Lied", "harp", "C4");
        assertThrows(IllegalArgumentException.class, () -> anthem.withAudio("../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> anthem.withAudio("abc"));
    }
}

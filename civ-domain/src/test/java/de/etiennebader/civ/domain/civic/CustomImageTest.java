package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eigene hochgeladene Bilder (Issue #12 "Custom flags and banner"): ein neues Wappen ersetzt ein
 * altes, während Wahlplakate und Leuchtreklamen bis zu {@link CustomImage#MAX_PER_KIND} gleichzeitig
 * nebeneinander bestehen dürfen.
 */
class CustomImageTest {

    private static final Day DAY = Day.of(1);

    @Test
    void rejectsOversizedDimensions() {
        PolityId polity = new PolityId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new CustomImage("id", polity, ImageKind.WAPPEN, "hash",
                CustomImage.MAX_DIMENSION + 1, 10, DAY));
        assertThrows(IllegalArgumentException.class, () -> new CustomImage("id", polity, ImageKind.WAPPEN, "hash",
                10, 0, DAY));
    }

    @Test
    void newCrestReplacesTheOldOneAndReturnsItAsDisplaced() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());

        List<CustomImage> firstDisplaced = registry.addImage("a", polity, ImageKind.WAPPEN, "hash-a", 16, 16, DAY);
        assertTrue(firstDisplaced.isEmpty());

        List<CustomImage> secondDisplaced = registry.addImage("b", polity, ImageKind.WAPPEN, "hash-b", 16, 16, DAY);
        assertEquals(1, secondDisplaced.size());
        assertEquals("a", secondDisplaced.get(0).id());

        assertEquals(List.of("b"), registry.imagesOf(polity, ImageKind.WAPPEN).stream().map(CustomImage::id).toList());
    }

    @Test
    void campaignPostersStackUpToTheLimitThenReject() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());

        for (int i = 0; i < CustomImage.MAX_PER_KIND; i++) {
            registry.addImage("poster-" + i, polity, ImageKind.WAHLPLAKAT, "hash-" + i, 16, 16, DAY);
        }
        assertEquals(CustomImage.MAX_PER_KIND, registry.imagesOf(polity, ImageKind.WAHLPLAKAT).size());

        assertThrows(IllegalStateException.class,
                () -> registry.addImage("one-too-many", polity, ImageKind.WAHLPLAKAT, "hash-x", 16, 16, DAY));
    }

    @Test
    void removeImageReturnsItOnceThenEmpty() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        registry.addImage("a", polity, ImageKind.LEUCHTREKLAME, "hash-a", 16, 16, DAY);

        Optional<CustomImage> removed = registry.removeImage("a", DAY);
        assertTrue(removed.isPresent());
        assertEquals("a", removed.get().id());

        assertEquals(Optional.empty(), registry.removeImage("a", DAY));
    }

    @Test
    void dissolvingAPolityRemovesAllItsImages() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        registry.addImage("a", polity, ImageKind.WAPPEN, "hash-a", 16, 16, DAY);
        registry.addImage("b", polity, ImageKind.WAHLPLAKAT, "hash-b", 16, 16, DAY);

        registry.forget(polity, DAY);

        assertTrue(registry.imagesOf(polity).isEmpty());
    }
}

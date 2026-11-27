package de.etiennebader.civ.domain.law;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.etiennebader.civ.domain.shared.InvalidValueException;
import de.etiennebader.civ.domain.shared.StatuteId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LawStructureTest {

    @Test
    void pathWithThreeNamesSplitsLawPartAndTitle() {
        assertEquals(new LawPath("Baugesetz", "Teil 1", "Grundstücke"),
                LawPath.parse(" Baugesetz > Teil 1 > Grundstücke "));
    }

    @Test
    void pathWithTwoNamesHasNoPart() {
        assertEquals(new LawPath("Baugesetz", "", "Grundstücke"), LawPath.parse("Baugesetz > Grundstücke"));
    }

    @Test
    void plainTitleStaysTitleAndExtraSeparatorsStayInTitle() {
        assertEquals(new LawPath("", "", "Steuer"), LawPath.parse("Steuer"));
        assertEquals(new LawPath("A", "B", "C > D"), LawPath.parse("A > B > C > D"));
    }

    @Test
    void textIsSplitIntoSectionsSentencesAndEnumeration() {
        var sections = LawText.sections("Jeder darf bauen. Ausnahmen gelten:\n- am Markt\n- im Wald\n\nZweiter Absatz!");
        assertEquals(2, sections.size());
        assertEquals(List.of("Jeder darf bauen.", "Ausnahmen gelten:"), sections.get(0).sentences());
        assertEquals(List.of("am Markt", "im Wald"), sections.get(0).enumeration());
        assertEquals(2, sections.get(1).number());
        assertTrue(LawText.sections("   ").isEmpty());
    }

    @Test
    void statuteRejectsPartWithoutLawAndTooLongNames() {
        StatuteId id = new StatuteId(UUID.randomUUID());
        assertThrows(InvalidValueException.class, () -> new Statute(id, "", "Teil 1", "Titel", "Text", List.of()));
        assertThrows(InvalidValueException.class, () -> new Statute(id, "x".repeat(81), "", "Titel", "Text", List.of()));
        assertEquals("Baugesetz", new Statute(id, " Baugesetz ", "", "Titel", "Text", List.of()).lawName());
    }
}

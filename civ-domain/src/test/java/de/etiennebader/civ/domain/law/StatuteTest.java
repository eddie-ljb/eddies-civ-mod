package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.shared.StatuteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_PER_BLOCK;
import static de.etiennebader.civ.domain.law.StandardParameters.MAX_OUTPOSTS;
import static de.etiennebader.civ.domain.law.StandardParameters.TAX_EMBASSIES;
import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatuteTest {

    private final StatuteId id = StatuteId.generate(new SeededIdGenerator(5));

    @Test
    @DisplayName("Titel wird getrimmt")
    void trimsTitle() {
        Statute statute = Statute.of(id, "  Grundsteuerordnung ", ParameterValue.of(LAND_TAX_PER_BLOCK, 1L));

        assertEquals("Grundsteuerordnung", statute.title());
    }

    @Test
    @DisplayName("Zu kurzer oder zu langer Titel → INVALID_STATUTE_TITLE")
    void rejectsInvalidTitle() {
        ParameterValue<Long> value = ParameterValue.of(LAND_TAX_PER_BLOCK, 1L);

        assertDomainError(ErrorCode.INVALID_STATUTE_TITLE, () -> Statute.of(id, "ab", value));
        assertDomainError(ErrorCode.INVALID_STATUTE_TITLE, () -> Statute.of(id, "x".repeat(81), value));
    }

    @Test
    @DisplayName("Gesetz ohne Werte → STATUTE_WITHOUT_VALUES")
    void rejectsStatuteWithoutValues() {
        assertDomainError(ErrorCode.STATUTE_WITHOUT_VALUES, () -> new Statute(id, "Leeres Gesetz", List.of()));
    }

    @Test
    @DisplayName("Parameter doppelt gesetzt → DUPLICATE_PARAMETER")
    void rejectsDuplicateParameter() {
        assertDomainError(ErrorCode.DUPLICATE_PARAMETER, () -> Statute.of(id, "Doppelt",
                ParameterValue.of(LAND_TAX_PER_BLOCK, 1L), ParameterValue.of(LAND_TAX_PER_BLOCK, 2L)));
    }

    @Test
    @DisplayName("Ungültiger Parameterwert → INVALID_PARAMETER_VALUE")
    void rejectsInvalidParameterValue() {
        assertDomainError(ErrorCode.INVALID_PARAMETER_VALUE, () -> ParameterValue.of(LAND_TAX_PER_BLOCK, -1L));
        assertDomainError(ErrorCode.INVALID_PARAMETER_VALUE, () -> ParameterValue.of(MAX_OUTPOSTS, -3));
    }

    @Test
    @DisplayName("valueOf liefert nur gesetzte Parameter")
    void valueOfReturnsOnlySetParameters() {
        Statute statute = Statute.of(id, "Botschaftssteuer", ParameterValue.of(TAX_EMBASSIES, true));

        assertEquals(Optional.of(true), statute.valueOf(TAX_EMBASSIES));
        assertEquals(Optional.empty(), statute.valueOf(LAND_TAX_PER_BLOCK));
    }
}

package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.shared.StatuteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_PER_BLOCK;
import static de.etiennebader.civ.domain.law.StandardParameters.MAX_OUTPOSTS;
import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawBookTest {

    private final IdGenerator ids = new SeededIdGenerator(1);
    private final LawBook lawBook = new LawBook();

    @Test
    @DisplayName("Parameter ohne Gesetz → Standardwert")
    void returnsDefaultWithoutStatute() {
        assertEquals(0L, lawBook.valueOf(LAND_TAX_PER_BLOCK));
        assertEquals(0, lawBook.valueOf(MAX_OUTPOSTS));
    }

    @Test
    @DisplayName("Zwei Gesetze setzen denselben Parameter → jüngeres gewinnt, nach Aufhebung wieder das ältere")
    void youngerStatuteWinsAndOlderReturnsAfterRepeal() {
        Statute older = taxLaw(5);
        Statute younger = taxLaw(8);
        lawBook.enact(older, Day.of(1));
        lawBook.enact(younger, Day.of(2));

        assertEquals(8L, lawBook.valueOf(LAND_TAX_PER_BLOCK));

        lawBook.repeal(younger.id());

        assertEquals(5L, lawBook.valueOf(LAND_TAX_PER_BLOCK));
    }

    @Test
    @DisplayName("Aufhebung des letzten Gesetzes → wieder Standardwert")
    void fallsBackToDefaultAfterLastRepeal() {
        Statute statute = taxLaw(5);
        lawBook.enact(statute, Day.of(1));

        lawBook.repeal(statute.id());

        assertEquals(0L, lawBook.valueOf(LAND_TAX_PER_BLOCK));
        assertFalse(lawBook.isInForce(statute.id()));
    }

    @Test
    @DisplayName("Gesetze beeinflussen nur die Parameter, die sie setzen")
    void statutesOnlyAffectTheirOwnParameters() {
        lawBook.enact(Statute.of(StatuteId.generate(ids), "Außenpostengesetz", ParameterValue.of(MAX_OUTPOSTS, 3)), Day.of(1));
        lawBook.enact(taxLaw(4), Day.of(2));

        assertEquals(3, lawBook.valueOf(MAX_OUTPOSTS));
        assertEquals(4L, lawBook.valueOf(LAND_TAX_PER_BLOCK));
        assertEquals(2, lawBook.inForce().size());
    }

    @Test
    @DisplayName("Aufhebung eines nicht geltenden Gesetzes → UNKNOWN_STATUTE")
    void rejectsRepealOfUnknownStatute() {
        assertDomainError(ErrorCode.UNKNOWN_STATUTE, () -> lawBook.repeal(StatuteId.generate(ids)));
    }

    @Test
    @DisplayName("Dasselbe Gesetz zweimal erlassen → STATUTE_ALREADY_IN_FORCE")
    void rejectsDuplicateEnactment() {
        Statute statute = taxLaw(1);
        lawBook.enact(statute, Day.of(1));

        assertDomainError(ErrorCode.STATUTE_ALREADY_IN_FORCE, () -> lawBook.enact(statute, Day.of(2)));
        assertTrue(lawBook.isInForce(statute.id()));
    }

    private Statute taxLaw(long rate) {
        return Statute.of(StatuteId.generate(ids), "Grundsteuerordnung", ParameterValue.of(LAND_TAX_PER_BLOCK, rate));
    }
}

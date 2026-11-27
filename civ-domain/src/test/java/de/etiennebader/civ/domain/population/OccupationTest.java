package de.etiennebader.civ.domain.population;

import de.etiennebader.civ.domain.production.Branch;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupationTest {

    @Test
    void codesAreUniqueAndRoundTrip() {
        Set<Integer> codes = new HashSet<>();
        for (Occupation occupation : Occupation.values()) {
            assertTrue(codes.add(occupation.code()), occupation.name());
            assertEquals(occupation, Occupation.ofCode(occupation.code()));
        }
        assertEquals(Occupation.CIVILIAN, Occupation.ofCode(0));
        assertEquals(Occupation.CIVILIAN, Occupation.ofCode(999));
    }

    @Test
    void earlierRolesOutrankLaterOnesAndCivilianNeverDoes() {
        assertTrue(Occupation.POLITICIAN.outranks(Occupation.ENTREPRENEUR));
        assertTrue(Occupation.OFFICER.outranks(Occupation.SOLDIER));
        assertTrue(Occupation.WORKER.outranks(Occupation.CIVILIAN));
        assertFalse(Occupation.CIVILIAN.outranks(Occupation.WORKER));
        assertFalse(Occupation.CIVILIAN.outranks(Occupation.CIVILIAN));
        assertFalse(Occupation.CIVILIAN.dressed());
        assertTrue(Occupation.BANKER.dressed());
    }

    @Test
    void everyBranchGivesItsEmployeesAnOccupation() {
        for (Branch branch : Branch.values()) {
            assertTrue(Occupation.employeeOf(branch.id()).dressed(), branch.name());
        }
        assertEquals(Occupation.BUILDER, Occupation.employeeOf(Branch.CONSTRUCTION.id()));
        assertEquals(Occupation.MINER, Occupation.employeeOf(Branch.MINE.id()));
        assertEquals(Occupation.FARMER, Occupation.employeeOf(Branch.ORCHARD.id()));
        assertEquals(Occupation.WORKER, Occupation.employeeOf(Branch.BRICKWORKS.id()));
        assertEquals(Occupation.WORKER, Occupation.employeeOf(null));
    }
}

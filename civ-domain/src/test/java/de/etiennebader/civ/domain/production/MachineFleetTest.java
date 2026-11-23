package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineFleetTest {

    private final PolityId company = new PolityId(UUID.randomUUID());
    private final MachineFleet fleet = new MachineFleet();

    @Test
    void placedMachineRaisesOutputPerLevelAndBrokenOneStops() {
        Machine machine = fleet.buy(UUID.randomUUID(), company, MachineType.HARVESTER);
        assertEquals(1.0, fleet.factor(company, m -> m.where().isPresent()), 1e-9);
        fleet.place(machine.id(), new MachinePos("minecraft:overworld", 1, 64, 1));
        assertEquals(1.16, fleet.factor(company, m -> m.where().isPresent()), 1e-9);
        fleet.upgrade(machine.id());
        assertEquals(1.32, fleet.factor(company, m -> m.where().isPresent()), 1e-9);
        for (int day = 0; day < 30; day++) {
            fleet.wearDay(company, m -> m.where().isPresent());
        }
        assertTrue(fleet.of(company).getFirst().broken());
        assertEquals(1.0, fleet.factor(company, m -> m.where().isPresent()), 1e-9);
        fleet.maintain(machine.id());
        assertEquals(1.32, fleet.factor(company, m -> m.where().isPresent()), 1e-9);
    }

    @Test
    void limitsAndUpgradeCap() {
        Machine machine = null;
        for (int i = 0; i < MachineType.MAX_PER_COMPANY; i++) {
            machine = fleet.buy(UUID.randomUUID(), company, MachineType.HARVESTER);
        }
        assertThrows(IllegalStateException.class, () -> fleet.buy(UUID.randomUUID(), company, MachineType.HARVESTER));
        UUID id = machine.id();
        for (int i = 1; i < MachineType.MAX_LEVEL; i++) {
            fleet.upgrade(id);
        }
        assertThrows(IllegalStateException.class, () -> fleet.upgrade(id));
    }

    @Test
    void everyBranchHasSeveralMachinesAndThematicExtras() {
        for (Branch branch : Branch.values()) {
            assertTrue(MachineType.of(branch).size() >= 2, branch.name());
            assertFalse(branch.extras().isEmpty(), branch.name());
        }
        assertTrue(Branch.FARM.accepts(Commodity.of("minecraft:cactus")));
        assertTrue(Branch.FARM.accepts(Commodity.of("minecraft:wheat")));
        assertFalse(Branch.FARM.accepts(Commodity.of("minecraft:diamond")));
        assertFalse(Branch.MINE.accepts(Commodity.of("minecraft:cactus")));
    }
}

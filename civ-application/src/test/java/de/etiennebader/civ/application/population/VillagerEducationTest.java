package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.actor.PersonRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerEducationTest {

    private final PersonRef villager = new PersonRef(new PersonId(UUID.fromString("00000000-0000-0000-0000-000000000042")));

    @Test
    void schoolDaysRaiseEducationAndAbsenceLowersIntelligence() {
        VillagerEducationLedger ledger = new VillagerEducationLedger();
        int startIntelligence = ledger.of(villager).intelligence();
        ledger.recordChildDay(villager, true);
        assertTrue(ledger.of(villager).points() > 0);
        ledger.recordChildDay(villager, false);
        assertEquals(startIntelligence - 1, ledger.of(villager).intelligence());
    }

    @Test
    void valuesFreezeOnceGrownUp() {
        VillagerEducationLedger ledger = new VillagerEducationLedger();
        ledger.recordChildDay(villager, true);
        VillagerEducation before = ledger.of(villager);
        ledger.grownUp(villager);
        ledger.recordChildDay(villager, true);
        ledger.recordChildDay(villager, false);
        assertEquals(before.points(), ledger.of(villager).points());
        assertEquals(before.intelligence(), ledger.of(villager).intelligence());
        assertTrue(ledger.of(villager).frozen());
    }

    @Test
    void unknownVillagerIsNeutral() {
        assertEquals(VillagerEducationLedger.NEUTRAL_BONUS, new VillagerEducationLedger().bonusOf(villager));
    }
}

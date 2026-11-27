package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Villager lesen Plakate (Issue #82): höchstens einmal am Tag, gleiche Obergrenze wie Reden. */
class CampaignPosterReadingTest {

    private static final Day DAY = Day.of(5);

    private static PersonRef villager() {
        return new PersonRef(new PersonId(UUID.randomUUID()));
    }

    @Test
    void aVillagerReadsOnlyOncePerDay() {
        Campaign campaign = new Campaign();
        PersonRef reader = villager();
        PersonRef candidate = villager();
        assertTrue(campaign.readPoster(reader, candidate, Platform.NONE, DAY));
        assertFalse(campaign.readPoster(reader, candidate, Platform.NONE, DAY));
        assertTrue(campaign.readPoster(reader, candidate, Platform.NONE, DAY.plus(1)));
    }
}

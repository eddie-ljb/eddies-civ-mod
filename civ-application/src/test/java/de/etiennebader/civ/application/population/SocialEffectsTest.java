package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialEffectsTest {

    private final PlayerRef alice = new PlayerRef(new PlayerId(UUID.randomUUID()));
    private final PersonRef villager = new PersonRef(new PersonId(UUID.randomUUID()));

    @Test
    @DisplayName("Politische Beeinflussung wirkt und hebt die Stimmung an")
    void influenceWorks() {
        SocialEffects social = new SocialEffects();
        assertEquals(0, social.pointsOf(villager));
        assertTrue(social.influence(alice, villager, Day.of(1)));
        assertTrue(social.pointsOf(villager) > 0);
    }

    @Test
    @DisplayName("Vor Ablauf der Abklingzeit wirkt eine weitere Beeinflussung nicht noch einmal")
    void influenceHasCooldown() {
        SocialEffects social = new SocialEffects();
        assertTrue(social.influence(alice, villager, Day.of(1)));
        int afterFirst = social.pointsOf(villager);
        assertFalse(social.influence(alice, villager, Day.of(2)));
        assertEquals(afterFirst, social.pointsOf(villager));
        assertTrue(social.influence(alice, villager, Day.of(1 + SocialEffects.INFLUENCE_COOLDOWN_DAYS)));
        assertTrue(social.pointsOf(villager) > afterFirst);
    }

    @Test
    @DisplayName("Der Zuschlag klingt ohne weitere Beeinflussung Tag für Tag ab")
    void bonusDecays() {
        SocialEffects social = new SocialEffects();
        social.influence(alice, villager, Day.of(1));
        int start = social.pointsOf(villager);
        for (long day = 2; day <= start + 1; day++) {
            social.endOfDay(List.of(), Day.of(day));
        }
        assertEquals(0, social.pointsOf(villager));
    }

    @Test
    @DisplayName("Satisfaction.withSocial schlägt den Zuschlag auf die Grundstimmung auf")
    void withSocialAddsToBase() {
        Satisfaction base = new Satisfaction(java.util.Map.of(Satisfaction.Factor.BASE, 10));
        Satisfaction boosted = base.withSocial(5);
        assertEquals(15, boosted.points(Satisfaction.Factor.BASE));
        assertEquals(base.score() + 5, boosted.score());
    }
}

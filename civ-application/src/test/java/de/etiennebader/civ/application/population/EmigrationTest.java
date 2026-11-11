package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmigrationTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
    private final PolityId eichenfurt = fixture.found("Eichenfurt", bob, 20, 20);

    @Test
    @DisplayName("Zufriedene Dorfbewohner bleiben")
    void contentVillagersStay() {
        settle(nordhain, 40);
        for (long day = 1; day <= 10; day++) {
            assertTrue(Emigration.plan(towns(), Day.of(day)).isEmpty());
        }
    }

    @Test
    @DisplayName("Bei erdrückenden Steuern gehen Unzufriedene, höchstens ein Viertel am Tag, bevorzugt in eine bessere Gemeinde")
    void miseryDrivesVillagersAway() {
        List<PersonRef> villagers = settle(nordhain, 40);
        amend(nordhain, alice, StandardParameters.SALES_TAX_PERCENT, 50L);
        amend(nordhain, alice, StandardParameters.LAND_TAX_PER_BLOCK, 10L);
        amend(nordhain, alice, StandardParameters.EXPLOSIONS_ALLOWED, true);
        amend(nordhain, alice, StandardParameters.FIRE_SPREAD_ALLOWED, true);
        amend(eichenfurt, bob, StandardParameters.VILLAGER_VOTE_PERCENT, 100L);

        int atRisk = (int) villagers.stream().filter(villager ->
                Emigration.atRisk(Satisfaction.of(Personality.of(villager), town(nordhain)).score())).count();
        assertTrue(atRisk > 0, "manche sind sehr unzufrieden");
        assertEquals(atRisk, TownMood.of(town(nordhain)).atRisk());

        int left = 0;
        int toEichenfurt = 0;
        for (long day = 1; day <= 30; day++) {
            List<Emigration.Departure> departures = Emigration.plan(towns(), Day.of(day));
            assertTrue(departures.size() <= 10, "höchstens ein Viertel von 40");
            for (Emigration.Departure departure : departures) {
                assertTrue(Emigration.atRisk(Satisfaction.of(Personality.of(departure.villager()), town(nordhain))
                        .score()));
                left++;
                if (departure.to().isPresent()) {
                    assertEquals(eichenfurt, departure.to().get());
                    toEichenfurt++;
                }
            }
        }
        assertTrue(left > 0, "es wandert jemand ab");
        assertTrue(toEichenfurt > 0, "die steuerfreie Gemeinde mit Wahlrecht zieht an");
        assertEquals(Emigration.plan(towns(), Day.of(3)), Emigration.plan(towns(), Day.of(3)), "derselbe Tag, dasselbe Ergebnis");
    }

    @Test
    @DisplayName("Rastlose gehen eher als Heimatverbundene; ab -70 sicher, wenn ganz rastlos")
    void restlessnessScalesChance() {
        assertEquals(0.0, Emigration.chance(-30, new Personality(0, 0, 0)), 1e-9);
        assertEquals(1.0, Emigration.chance(-70, new Personality(0, 0, -100)), 1e-9);
        assertEquals(0.2, Emigration.chance(-90, new Personality(0, 0, 100)), 1e-9);
        assertTrue(Emigration.chance(-80, new Personality(0, 0, -50)) > Emigration.chance(-80, new Personality(0, 0, 80)));
    }

    private List<PersonRef> settle(PolityId town, int count) {
        List<PersonRef> villagers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PersonRef villager = fixture.villager();
            fixture.app.joinPolity().execute(new JoinPolity.Command(town, villager, MembershipType.RESIDENT, DAY));
            villagers.add(villager);
        }
        return villagers;
    }

    private <T> void amend(PolityId town, PlayerRef mayor, ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(town, mayor, ParameterValue.of(key, value), DAY));
    }

    private List<PolityView> towns() {
        return fixture.app.queries().polities();
    }

    private PolityView town(PolityId id) {
        return fixture.app.queries().polity(id).orElseThrow();
    }
}

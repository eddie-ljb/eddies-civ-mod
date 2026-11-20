package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.party.JoinParty;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerLoyaltyTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    @DisplayName("derive(): ganz neu zählt nur die Heimatverbundenheit, halbiert")
    void freshMembershipIsJustHalvedBelonging() {
        assertEquals(50, VillagerLoyalty.derive(100, 0, 100));
        assertEquals(-50, VillagerLoyalty.derive(-100, 0, -100));
        assertEquals(0, VillagerLoyalty.derive(0, 0, 100));
    }

    @Test
    @DisplayName("derive(): nach voller Gewöhnungszeit zählt die seitherige Stimmung mit vollem Gewicht")
    void fullTenureWeightsMoodFully() {
        int freshNeutral = VillagerLoyalty.derive(0, 0, 100);
        int tenuredNeutral = VillagerLoyalty.derive(0, VillagerLoyalty.FULL_WEIGHT_DAYS, 100);
        assertTrue(tenuredNeutral > freshNeutral, "mit Dauer zieht gute Stimmung die Loyalität hoch");
        assertEquals((int) Math.round(100 * VillagerLoyalty.DRIFT_SHARE), tenuredNeutral);
    }

    @Test
    @DisplayName("derive(): negative Mitgliedsdauer wird wie 0 behandelt, Ergebnis bleibt in [-100, 100]")
    void clampsAndTreatsNegativeTenureAsZero() {
        assertEquals(VillagerLoyalty.derive(100, 0, 100), VillagerLoyalty.derive(100, -5, 100));
        assertTrue(VillagerLoyalty.derive(100, 10_000, 100) <= 100);
        assertTrue(VillagerLoyalty.derive(-100, 10_000, -100) >= -100);
    }

    @Test
    @DisplayName("toPolity(): dauerhaft gute Stimmung macht nach einiger Zeit loyaler als am ersten Tag")
    void toPolityGrowsWithSustainedGoodMood() {
        PlayerRef mayor = fixture.player();
        PolityId home = fixture.found("Lindenau", mayor, 0, 0);
        PersonRef villager = fixture.villager();
        fixture.app.joinPolity().execute(new JoinPolity.Command(home, villager, MembershipType.RESIDENT, DAY));
        Personality personality = Personality.of(villager);
        PolityView onDayOne = fixture.app.queries().polity(home).orElseThrow();

        int loyaltyOnDayOne = VillagerLoyalty.toPolity(personality, onDayOne, villager, DAY,
                fixture.app.queries().effectiveLaws(onDayOne));
        Day muchLater = Day.of(DAY.value() + VillagerLoyalty.FULL_WEIGHT_DAYS * 2L);
        int loyaltyMuchLater = VillagerLoyalty.toPolity(personality, onDayOne, villager, muchLater,
                fixture.app.queries().effectiveLaws(onDayOne));

        int mood = Satisfaction.of(personality, onDayOne, fixture.app.queries().effectiveLaws(onDayOne)).score();
        assertEquals(VillagerLoyalty.derive(personality.belonging(), 0, mood), loyaltyOnDayOne);
        // Nach genug Mitgliedsdauer zählt die Stimmung mit vollem Gewicht (auf FULL_WEIGHT_DAYS gedeckelt),
        // unabhängig davon, wie viel weiter darüber hinaus die tatsächliche Dauer liegt.
        assertEquals(VillagerLoyalty.derive(personality.belonging(), VillagerLoyalty.FULL_WEIGHT_DAYS, mood),
                loyaltyMuchLater);
    }

    @Test
    @DisplayName("toChairperson(): 0 ohne Amtsinhaber, sonst an dessen Amtszeit gekoppelt")
    void toChairpersonZeroWithoutOfficeHolder() {
        PlayerRef founder = fixture.player();
        PolityId party = fixture.app.foundParty().execute(new FoundParty.Command(
                de.etiennebader.civ.domain.polity.PolityName.of("Fortschrittspartei"), founder,
                new PartyProfile(20, -10, PartyProfile.DEFAULT_EMBLEM), CurrencyId.EMERALD, DAY));
        PersonRef villager = fixture.villager();
        fixture.app.joinParty().execute(new JoinParty.Command(party, villager, DAY));
        Personality personality = Personality.of(villager);
        PolityView partyView = fixture.app.queries().polity(party).orElseThrow();

        // Der Gründer ist kommissarisch Vorsitz (Analogon zum Bürgermeister) ab Gründungstag.
        int loyalty = VillagerLoyalty.toChairperson(personality, partyView, Day.of(DAY.value() + 10));
        assertTrue(loyalty >= -100 && loyalty <= 100);
    }

    @Test
    @DisplayName("toEmployer(): 0 ohne Anstellungsdauer, wächst mit guter Heimatverbundenheit und Dauer")
    void toEmployerGrowsWithTenureForLoyalPersonalities() {
        Personality homebody = new Personality(0, 0, 100);
        assertEquals(VillagerLoyalty.derive(100, 0, 0), VillagerLoyalty.toEmployer(homebody, 0));
        assertTrue(VillagerLoyalty.toEmployer(homebody, VillagerLoyalty.FULL_WEIGHT_DAYS)
                >= VillagerLoyalty.toEmployer(homebody, 0));
    }
}

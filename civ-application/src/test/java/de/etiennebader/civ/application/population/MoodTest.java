package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.office.AppointOfficer;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.RunElections;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoodTest {

    private static final Personality THRIFTY = new Personality(0, 90, 0);
    private static final Personality GENEROUS = new Personality(0, -90, 0);
    private static final Personality ORDERLY = new Personality(90, 0, 0);
    private static final Personality FREE = new Personality(-90, 0, 0);

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Steuern stören Sparsame stark, Großzügige kaum")
    void taxesHurtTheThrifty() {
        assertEquals(0, Satisfaction.of(THRIFTY, town()).points(Satisfaction.Factor.TAXES));
        amend(StandardParameters.SALES_TAX_PERCENT, 50L);
        amend(StandardParameters.LAND_TAX_PER_BLOCK, 10L);

        int thrifty = Satisfaction.of(THRIFTY, town()).points(Satisfaction.Factor.TAXES);
        int generous = Satisfaction.of(GENEROUS, town()).points(Satisfaction.Factor.TAXES);
        assertTrue(thrifty <= -35, "sparsam: " + thrifty);
        assertEquals(-15, generous);
    }

    @Test
    @DisplayName("Strenge Schutzregeln freuen Ordnungsliebende und ärgern Freigeister")
    void orderSplitsOpinions() {
        assertTrue(Satisfaction.of(ORDERLY, town()).points(Satisfaction.Factor.ORDER) > 20);
        assertTrue(Satisfaction.of(FREE, town()).points(Satisfaction.Factor.ORDER) < -20);
        amend(StandardParameters.EXPLOSIONS_ALLOWED, true);
        amend(StandardParameters.FIRE_SPREAD_ALLOWED, true);
        amend(StandardParameters.OUTSIDERS_MAY_BUILD, true);
        amend(StandardParameters.OUTSIDERS_MAY_INTERACT, true);
        amend(StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS, true);
        assertTrue(Satisfaction.of(FREE, town()).points(Satisfaction.Factor.ORDER) > 20);
    }

    @Test
    @DisplayName("Mitsprache: gewählter Bürgermeister und Wahlrecht machen zufriedener; Produktivität 50 bis 150 %")
    void voiceAndProductivity() {
        int before = Satisfaction.of(THRIFTY, town()).score();
        amend(StandardParameters.VILLAGER_VOTE_PERCENT, 100L);
        amend(StandardParameters.LAWS_BY_VOTE, true);
        assertEquals(19, Satisfaction.of(THRIFTY, town()).points(Satisfaction.Factor.VOICE));
        assertTrue(Satisfaction.of(THRIFTY, town()).score() > before);
        assertEquals(150, new Satisfaction(Map.of(Satisfaction.Factor.BASE, 300)).productivityPercent());
        assertEquals(50, new Satisfaction(Map.of(Satisfaction.Factor.BASE, -300)).productivityPercent());
    }

    @Test
    @DisplayName("Sparsame wählen niedrige Steuern, Ordnungsliebende Ordnung; unzufriedene wollen Wechsel")
    void votersFollowTheirViews() {
        PersonRef villager = fixture.villager();
        PlayerRef carla = fixture.player();
        Satisfaction neutral = mood(0, 0);
        assertTrue(PersonalityVoter.utility(THRIFTY, neutral, Platform.LOW_TAXES, false, villager, carla)
                > PersonalityVoter.utility(THRIFTY, neutral, Platform.INVESTMENT, false, villager, carla) + 1);
        assertTrue(PersonalityVoter.utility(GENEROUS, neutral, Platform.INVESTMENT, false, villager, carla)
                > PersonalityVoter.utility(GENEROUS, neutral, Platform.LOW_TAXES, false, villager, carla));
        Personality average = new Personality(0, 0, 0);
        assertTrue(PersonalityVoter.utility(average, mood(0, -22), Platform.LOW_TAXES, false, villager, carla)
                > PersonalityVoter.utility(average, mood(0, -22), Platform.INVESTMENT, false, villager, carla) + 0.3,
                "Steuerlast zieht Durchschnittliche zu niedrigen Steuern");
        assertTrue(PersonalityVoter.utility(ORDERLY, neutral, Platform.ORDER, false, villager, carla)
                > PersonalityVoter.utility(ORDERLY, neutral, Platform.FREEDOM, false, villager, carla) + 1);
        assertTrue(PersonalityVoter.utility(GENEROUS, mood(-80, 0), Platform.NONE, true, villager, carla)
                < PersonalityVoter.utility(GENEROUS, mood(-80, 0), Platform.NONE, false, villager, carla));
        assertTrue(PersonalityVoter.utility(GENEROUS, mood(80, 0), Platform.NONE, true, villager, carla)
                > PersonalityVoter.utility(GENEROUS, mood(80, 0), Platform.NONE, false, villager, carla));
    }

    @Test
    @DisplayName("Bei hohen Steuern gewinnt mit Dorfbewohner-Stimmen, wer niedrige Steuern verspricht")
    void villagersDecideOnProgramme() {
        join(bob, MembershipType.CITIZEN);
        List<PersonRef> villagers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            PersonRef villager = fixture.villager();
            villagers.add(villager);
            join(villager, MembershipType.RESIDENT);
        }
        amend(StandardParameters.SALES_TAX_PERCENT, 50L);
        amend(StandardParameters.LAND_TAX_PER_BLOCK, 10L);
        amend(StandardParameters.VILLAGER_VOTE_PERCENT, 100L);
        fixture.app.appointOfficer().execute(new AppointOfficer.Command(nordhain, alice, Office.TREASURER,
                Optional.of(alice), DAY));

        ElectionId election = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice,
                Office.TREASURER, DAY));
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, alice,
                Platform.INVESTMENT, DAY));
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, bob,
                Platform.LOW_TAXES, DAY));
        Election open = town().electionFor(Office.TREASURER).orElseThrow();
        assertEquals(Platform.LOW_TAXES, open.platformOf(bob));

        RunElections.Decided decided = assertInstanceOf(RunElections.Decided.class,
                fixture.app.runElections().execute(DAY.plus(2)).getFirst());
        assertEquals(bob, decided.winner());
        assertTrue(decided.count().villagers() > 30, "fast alle wählen: " + decided.count().villagers());

        TownMood mood = TownMood.of(town());
        assertEquals(40, mood.villagers());
        assertTrue(mood.averageFactors().get(Satisfaction.Factor.TAXES) < -10);
        assertEquals(40, mood.traits().values().stream().mapToInt(Integer::intValue).sum());
    }

    private static Satisfaction mood(int base, int taxes) {
        return new Satisfaction(Map.of(Satisfaction.Factor.BASE, base, Satisfaction.Factor.TAXES, taxes));
    }

    private <T> void amend(ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice, ParameterValue.of(key, value), DAY));
    }

    private void join(ActorRef member, MembershipType type) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, member, type, DAY));
    }

    private PolityView town() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }
}

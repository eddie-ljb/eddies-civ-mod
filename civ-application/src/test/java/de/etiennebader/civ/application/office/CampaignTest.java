package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.federation.JoinFederation;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.ParliamentSession;
import de.etiennebader.civ.application.population.Campaign;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Promise;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Wahl im Bund: Ergebnis je Wahlbezirk (Gemeinde des Wählers) wird festgehalten")
    void districts() {
        PolityId suedtal = fixture.found("Suedtal", bob, 20, 20);
        PolityId land = fixture.app.foundFederation().execute(new FoundFederation.Command(PolityName.of("Westmark"),
                "land", nordhain, alice, DAY));
        fixture.app.joinFederation().execute(new JoinFederation.Command(land, suedtal, DAY));
        ElectionId election = fixture.app.callElection().execute(new CallElection.Command(land, alice, Office.MAYOR,
                DAY));
        for (PlayerRef candidate : List.of(alice, bob)) {
            fixture.app.standForElection().execute(new StandForElection.Command(land, election, candidate, DAY));
            fixture.app.castBallot().execute(new CastBallot.Command(land, election, candidate, candidate, DAY));
        }
        fixture.app.runElections().execute(DAY.plus(2));

        ElectionResult result = polity(land).results().getLast();
        assertEquals(List.of(alice), result.winners());
        Map<Optional<PolityId>, ElectionResult.District> byTown = new HashMap<>();
        result.districts().forEach(district -> byTown.put(district.district(), district));
        assertEquals(Optional.of(alice), byTown.get(Optional.of(nordhain)).leader());
        assertEquals(Optional.of(bob), byTown.get(Optional.of(suedtal)).leader());
        assertEquals(Map.of(alice, 100L, bob, 100L), result.totals());
    }

    @Test
    @DisplayName("Urnenpflicht: Stimmen zählen nur an der Wahlurne")
    void ballotBox() {
        join(bob, MembershipType.CITIZEN);
        amend(StandardParameters.BALLOT_BOX_REQUIRED, true);
        ElectionId election = call(Office.TREASURER);
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, bob, DAY));
        assertEquals(ErrorCode.BALLOT_BOX_REQUIRED, assertThrows(DomainException.class, () -> fixture.app.castBallot()
                .execute(new CastBallot.Command(nordhain, election, alice, bob, DAY))).code());
        fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, alice, bob, DAY, true));
        assertEquals(1, polity(nordhain).electionFor(Office.TREASURER).orElseThrow().ballots().size());
    }

    @Test
    @DisplayName("Versprechen werden erkannt und an der Rechtslage gemessen; nur Kandidaten können versprechen")
    void promises() {
        join(bob, MembershipType.CITIZEN);
        assertEquals(ErrorCode.NOT_RUNNING, assertThrows(DomainException.class, () -> fixture.app.makePromise()
                .execute(new MakePromise.Command(nordhain, bob, "Ich senke die Steuern.", DAY))).code());
        ElectionId election = call(Office.MAYOR);
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, bob, DAY));
        Promise promise = fixture.app.makePromise().execute(new MakePromise.Command(nordhain, bob,
                "Die Umsatzsteuer beträgt 5 %.", DAY));
        assertEquals(List.of(ParameterValue.of(StandardParameters.SALES_TAX_PERCENT, 5L)), promise.values());
        Campaign campaign = fixture.app.campaign();
        assertEquals(Optional.of(0.0), campaign.balance(bob, Office.MAYOR, polity(nordhain)));
        amend(StandardParameters.SALES_TAX_PERCENT, 5L);
        assertEquals(Optional.of(1.0), campaign.balance(bob, Office.MAYOR, polity(nordhain)));
    }

    @Test
    @DisplayName("Reden und Plakate ziehen Dorfbewohner zum Kandidaten; Umfrage fragt alle Dorfbewohner")
    void speechesPostersPolls() {
        join(bob, MembershipType.CITIZEN);
        PersonRef orderly = villagerWith(personality -> personality.order() >= 60);
        PersonRef free = villagerWith(personality -> personality.order() <= -60);
        join(orderly, MembershipType.RESIDENT);
        join(free, MembershipType.RESIDENT);
        ElectionId id = call(Office.MAYOR);
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, id, bob, DAY));
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, id, alice, DAY));
        Election election = polity(nordhain).electionFor(Office.MAYOR).orElseThrow();
        Campaign campaign = fixture.app.campaign();

        Campaign.Reception reception = campaign.speak(bob, Campaign.topicOf("Mehr Sicherheit und Ordnung!"),
                List.of(orderly, free), DAY).orElseThrow();
        assertEquals(Platform.ORDER, reception.topic());
        assertEquals(1, reception.convinced());
        assertEquals(1, reception.repelled());
        assertTrue(campaign.speak(bob, Platform.ORDER, List.of(orderly), DAY).isEmpty(), "eine Rede am Tag");
        assertTrue(campaign.bonus(orderly, bob, election, polity(nordhain)) > 0);
        assertTrue(campaign.bonus(free, bob, election, polity(nordhain)) < 0);
        campaign.endOfDay(DAY.plus(3));
        assertEquals(0.0, campaign.bonus(orderly, bob, election, polity(nordhain)), 1e-9);

        PutUpPoster.Result poster = fixture.app.putUpPoster().execute(ApplicationFixture.seat(0, 0), bob, DAY)
                .orElseThrow();
        assertEquals(bob, poster.poster().subject());
        assertEquals(0.05, campaign.bonus(orderly, bob, election, polity(nordhain)), 1e-9);
        assertTrue(fixture.app.putUpPoster().takeDown(ApplicationFixture.seat(0, 0)));

        ElectionPoll poll = ElectionPoll.of(polity(nordhain), election, fixture.app.villagerVoter(),
                fixture.app.queries()::partyOf);
        assertEquals(2, poll.sample());
    }

    @Test
    @DisplayName("Das Parlament tagt nur an Sitzungstagen")
    void sessionDays() {
        amend(StandardParameters.SESSION_DAYS, 2L);
        amend(StandardParameters.PARLIAMENT_SEATS, 1L);
        ElectionId election = call(Office.PARLIAMENT);
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, alice, DAY));
        fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, alice, alice, DAY));
        fixture.app.runElections().execute(DAY.plus(2));
        assertTrue(polity(nordhain).parliament().seated());
        assertTrue(polity(nordhain).parliament().reserves().isEmpty());

        assertTrue(fixture.app.parliamentSession().execute(DAY.plus(2)).isEmpty(), "Tag 3 ist kein Sitzungstag");
        assertInstanceOf(ParliamentSession.Session.class, fixture.app.parliamentSession().execute(DAY.plus(3)).getFirst());
    }

    private PersonRef villagerWith(Predicate<Personality> wanted) {
        while (true) {
            PersonRef villager = fixture.villager();
            if (wanted.test(Personality.of(villager))) {
                return villager;
            }
        }
    }

    private ElectionId call(Office office) {
        return fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, office, DAY));
    }

    private <T> void amend(ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice, ParameterValue.of(key, value), DAY));
    }

    private void join(ActorRef member, MembershipType type) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, member, type, DAY));
    }

    private PolityView polity(PolityId id) {
        return fixture.app.queries().polity(id).orElseThrow();
    }
}

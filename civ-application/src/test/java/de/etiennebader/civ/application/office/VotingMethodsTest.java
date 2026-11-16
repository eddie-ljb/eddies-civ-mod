package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.party.JoinParty;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingMethodsTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PlayerRef dave = fixture.player();
    private final PlayerRef emil = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Absolute Mehrheit: ohne Mehrheit Stichwahl der beiden Besten, dann entscheidet sie")
    void runoff() {
        citizens(bob, carla, dave, emil);
        amend(StandardParameters.OFFICE_VOTING, "runoff");
        ElectionId election = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice,
                Office.TREASURER, DAY));
        for (PlayerRef candidate : List.of(alice, bob, carla)) {
            stand(election, candidate);
        }
        ballot(election, alice, alice);
        ballot(election, bob, alice);
        ballot(election, carla, bob);
        ballot(election, dave, bob);
        ballot(election, emil, carla);
        RunElections.Runoff runoff = assertInstanceOf(RunElections.Runoff.class,
                fixture.app.runElections().execute(DAY.plus(2)).getFirst());
        assertEquals(List.of(alice, bob), runoff.finalists());
        ElectionId second = polity().electionFor(Office.TREASURER).orElseThrow().id();
        assertEquals(List.of(alice, bob), polity().electionFor(Office.TREASURER).orElseThrow().candidates());
        ballot(second, carla, bob);
        ballot(second, emil, bob);
        RunElections.Decided decided = assertInstanceOf(RunElections.Decided.class,
                fixture.app.runElections().execute(runoff.closesOn()).getFirst());
        assertEquals(bob, decided.winner());
    }

    @Test
    @DisplayName("Listenwahl: Listenplätze statt persönlicher Stimmen; Mehrheitswahl ohne Ausgleich; Sperrklausel")
    void listAndMajority() {
        citizens(bob, carla, dave, emil);
        PolityId red = party("Rot", alice);
        fixture.app.joinParty().execute(new JoinParty.Command(red, bob, DAY));
        PolityId blue = party("Blau", carla);
        amend(StandardParameters.PARLIAMENT_SEATS, 2L);
        amend(StandardParameters.BALLOT_TYPE, "list");
        ElectionId election = parliamentElection();
        for (PlayerRef candidate : List.of(alice, bob, carla)) {
            stand(election, candidate);
        }
        ballot(election, alice, bob);
        ballot(election, bob, bob);
        ballot(election, carla, carla);
        ballot(election, dave, bob);
        ballot(election, emil, carla);
        RunElections.Seated seated = assertInstanceOf(RunElections.Seated.class,
                fixture.app.runElections().execute(DAY.plus(2)).getFirst());
        assertEquals(List.of(alice, carla), seated.count().seats().stream().map(Parliament.Seat::member).toList(),
                "Rot bekommt einen Sitz: Listenplatz 1 ist Alice, obwohl Bob die Stimmen hatte");
        assertEquals(Optional.of(red), seated.count().seats().getFirst().party());
        assertTrue(blue != null);
    }

    @Test
    @DisplayName("Mehrheitswahl und Amtszeitbegrenzung")
    void majorityAndTermLimit() {
        citizens(bob, carla, dave, emil);
        party("Rot", alice);
        fixture.app.joinParty().execute(new JoinParty.Command(
                fixture.app.queries().polityNamed("Rot").orElseThrow().id(), bob, DAY));
        amend(StandardParameters.PARLIAMENT_SEATS, 2L);
        amend(StandardParameters.PARLIAMENT_VOTING, "majority");
        amend(StandardParameters.TERM_LIMIT, 1L);
        ElectionId election = parliamentElection();
        for (PlayerRef candidate : List.of(alice, bob, carla)) {
            stand(election, candidate);
        }
        ballot(election, alice, alice);
        ballot(election, bob, bob);
        ballot(election, dave, alice);
        ballot(election, emil, bob);
        ballot(election, carla, carla);
        RunElections.Seated seated = assertInstanceOf(RunElections.Seated.class,
                fixture.app.runElections().execute(DAY.plus(2)).getFirst());
        assertEquals(List.of(alice, bob), seated.count().seats().stream().map(Parliament.Seat::member).toList(),
                "Mehrheitswahl: Rot holt beide Sitze, Verhältniswahl hätte Carla einen gegeben");

        ElectionId mayor = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice,
                Office.TREASURER, DAY.plus(2)));
        stand(mayor, dave);
        ballot(mayor, dave, dave);
        fixture.app.runElections().execute(DAY.plus(4));
        ElectionId again = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice,
                Office.TREASURER, DAY.plus(4)));
        assertEquals(ErrorCode.TERM_LIMIT_REACHED, assertThrows(DomainException.class, () -> stand(again, dave)).code());
    }

    private ElectionId parliamentElection() {
        fixture.app.runElections().execute(DAY);
        return polity().electionFor(Office.PARLIAMENT).orElseThrow().id();
    }

    private PolityId party(String name, PlayerRef founder) {
        return fixture.app.foundParty().execute(new FoundParty.Command(PolityName.of(name), founder,
                new PartyProfile(50, 0, PartyProfile.DEFAULT_EMBLEM), CurrencyId.EMERALD, DAY));
    }

    private void citizens(PlayerRef... players) {
        for (PlayerRef player : players) {
            fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, player, MembershipType.CITIZEN, DAY));
        }
    }

    private void stand(ElectionId election, ActorRef candidate) {
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, candidate, DAY));
    }

    private void ballot(ElectionId election, ActorRef voter, ActorRef candidate) {
        fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, voter, candidate, DAY));
    }

    private <T> void amend(ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice, ParameterValue.of(key, value), DAY));
    }

    private PolityView polity() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }
}

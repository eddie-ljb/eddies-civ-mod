package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectionsTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Ohne Wahl ist der Gründer kommissarisch Bürgermeister")
    void founderIsActingMayor() {
        join(bob, MembershipType.CITIZEN);

        assertEquals(Optional.of(alice), polity().mayor());
        assertTrue(polity().holds(alice, Office.MAYOR));
        assertFalse(polity().holds(bob, Office.MAYOR));
        assertTrue(polity().offices().isEmpty());
    }

    @Test
    @DisplayName("Wahl: kandidieren, abstimmen, am letzten Tag auszählen; der Gewinner wird Bürgermeister")
    void electionDecidesMayor() {
        join(bob, MembershipType.CITIZEN);
        join(carla, MembershipType.CITIZEN);
        amend(StandardParameters.TERM_DAYS, 10L);

        ElectionId election = call(alice, Office.MAYOR);
        stand(alice, election);
        stand(bob, election);
        ballot(alice, election, alice);
        ballot(bob, election, bob);
        ballot(carla, election, bob);

        assertTrue(fixture.app.runElections().execute(DAY.plus(1)).isEmpty(), "läuft noch");
        List<RunElections.Outcome> outcomes = fixture.app.runElections().execute(DAY.plus(2));

        RunElections.Decided decided = assertInstanceOf(RunElections.Decided.class, outcomes.getFirst());
        assertEquals(bob, decided.winner());
        assertEquals(200L, decided.count().totals().get(bob));
        OfficeTerm term = polity().officeTerm(Office.MAYOR).orElseThrow();
        assertEquals(bob, term.holder());
        assertEquals(Optional.of(DAY.plus(12)), term.until());
        assertTrue(polity().elections().isEmpty());
        assertEquals(Optional.of(bob), polity().mayor());
    }

    @Test
    @DisplayName("Gleichstand: der Amtsinhaber bleibt, sonst gewinnt wer zuerst kandidiert hat")
    void tieBreak() {
        join(bob, MembershipType.CITIZEN);
        ElectionId first = call(alice, Office.TREASURER);
        stand(bob, first);
        stand(alice, first);
        ballot(alice, first, alice);
        ballot(bob, first, bob);
        fixture.app.runElections().execute(DAY.plus(2));
        assertEquals(bob, polity().officeTerm(Office.TREASURER).orElseThrow().holder());

        ElectionId second = call(alice, Office.TREASURER);
        stand(alice, second);
        stand(bob, second);
        ballot(alice, second, alice);
        ballot(bob, second, bob);
        fixture.app.runElections().execute(DAY.plus(4));
        assertEquals(bob, polity().officeTerm(Office.TREASURER).orElseThrow().holder(), "Amtsinhaber gewinnt Gleichstand");
    }

    @Test
    @DisplayName("Dorfbewohner wählen nur mit Wahlrecht, gewichtet nach villager_vote_percent")
    void villagersVoteWithWeight() {
        join(bob, MembershipType.CITIZEN);
        List<PersonRef> villagers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            PersonRef villager = fixture.villager();
            villagers.add(villager);
            join(villager, MembershipType.RESIDENT);
        }
        ElectionId withoutSuffrage = call(alice, Office.PLANNER);
        stand(alice, withoutSuffrage);
        stand(bob, withoutSuffrage);
        RunElections.Decided noVillagers = decided(fixture.app.runElections().execute(DAY.plus(2)));
        assertEquals(0, noVillagers.count().villagers());
        assertEquals(0L, noVillagers.count().totals().get(alice) + noVillagers.count().totals().get(bob));

        amend(StandardParameters.VILLAGER_VOTE_PERCENT, 25L);
        ElectionId withSuffrage = call(alice, Office.TREASURER);
        stand(alice, withSuffrage);
        stand(bob, withSuffrage);
        RunElections.Decided counted = decided(fixture.app.runElections().execute(DAY.plus(4)));
        assertEquals(8, counted.count().villagers());
        assertEquals(8 * 25L, counted.count().totals().get(alice) + counted.count().totals().get(bob));
    }

    @Test
    @DisplayName("Dorfbewohner können nicht kandidieren; Fremde nicht wählen")
    void onlyCitizensStandAndVote() {
        PersonRef villager = fixture.villager();
        join(villager, MembershipType.RESIDENT);
        ElectionId election = call(alice, Office.MAYOR);
        stand(alice, election);

        assertEquals(ErrorCode.NOT_A_CITIZEN, assertThrows(DomainException.class, () -> stand(villager, election)).code());
        assertEquals(ErrorCode.NOT_A_CITIZEN, assertThrows(DomainException.class, () -> ballot(bob, election, alice)).code());
        assertEquals(ErrorCode.NOT_A_CANDIDATE, assertThrows(DomainException.class, () -> ballot(alice, election, bob)).code());
        assertEquals(ErrorCode.ALREADY_CANDIDATE, assertThrows(DomainException.class, () -> stand(alice, election)).code());
        assertEquals(ErrorCode.ELECTION_ALREADY_OPEN,
                assertThrows(DomainException.class, () -> call(alice, Office.MAYOR)).code());
    }

    @Test
    @DisplayName("Ausrufen: Bürgermeister jederzeit, andere Bürger nur bei freiem oder abgelaufenem Amt")
    void whoMayCallAnElection() {
        join(bob, MembershipType.CITIZEN);
        appoint(carlaAsCitizen(), Office.TREASURER);

        assertEquals(ErrorCode.NOT_OFFICE_HOLDER,
                assertThrows(DomainException.class, () -> call(bob, Office.TREASURER)).code());
        call(bob, Office.PLANNER);
        call(alice, Office.TREASURER);
    }

    @Test
    @DisplayName("Der Bürgermeister ernennt und entlässt; den Bürgermeister selbst kann niemand ernennen")
    void appointAndDismiss() {
        join(bob, MembershipType.CITIZEN);

        appoint(bob, Office.PLANNER);
        assertTrue(polity().holds(bob, Office.PLANNER));
        assertEquals(Optional.empty(), polity().officeTerm(Office.PLANNER).orElseThrow().until());

        assertEquals(ErrorCode.NOT_OFFICE_HOLDER, assertThrows(DomainException.class, () -> fixture.app.appointOfficer()
                .execute(new AppointOfficer.Command(nordhain, bob, Office.TREASURER, Optional.of(bob), DAY))).code());
        assertEquals(ErrorCode.MAYOR_NOT_APPOINTABLE, assertThrows(DomainException.class, () -> appoint(bob, Office.MAYOR))
                .code());

        assertTrue(fixture.app.appointOfficer().execute(new AppointOfficer.Command(nordhain, alice, Office.PLANNER,
                Optional.empty(), DAY)));
        assertTrue(polity().officeTerm(Office.PLANNER).isEmpty());
    }

    @Test
    @DisplayName("Wer austritt, verliert seine Ämter, seine Kandidatur und seine Stimme")
    void leavingVacatesOffices() {
        join(bob, MembershipType.CITIZEN);
        appoint(bob, Office.TREASURER);
        ElectionId election = call(alice, Office.PLANNER);
        stand(bob, election);
        stand(alice, election);
        ballot(bob, election, bob);

        fixture.app.leavePolity().execute(new LeavePolity.Command(nordhain, bob, DAY));

        assertTrue(polity().officeTerm(Office.TREASURER).isEmpty());
        Election open = polity().electionFor(Office.PLANNER).orElseThrow();
        assertEquals(List.of(alice), open.candidates());
        assertTrue(open.ballots().isEmpty());
    }

    @Test
    @DisplayName("Amtszeit: abgelaufene Ämter werden neu gewählt; ohne Kandidaten verlängert sich die Amtszeit")
    void termsTriggerElections() {
        amend(StandardParameters.TERM_DAYS, 3L);

        List<RunElections.Outcome> first = fixture.app.runElections().execute(DAY);
        assertInstanceOf(RunElections.Called.class, first.getFirst(), "Bürgermeister wurde noch nie gewählt");
        assertEquals(Office.MAYOR, first.getFirst().office());

        ElectionId election = polity().electionFor(Office.MAYOR).orElseThrow().id();
        stand(alice, election);
        fixture.app.runElections().execute(DAY.plus(2));
        assertEquals(Optional.of(DAY.plus(5)), polity().officeTerm(Office.MAYOR).orElseThrow().until());

        assertTrue(fixture.app.runElections().execute(DAY.plus(4)).isEmpty());
        List<RunElections.Outcome> expired = fixture.app.runElections().execute(DAY.plus(5));
        assertInstanceOf(RunElections.Called.class, expired.getFirst());

        List<RunElections.Outcome> empty = fixture.app.runElections().execute(DAY.plus(7));
        assertInstanceOf(RunElections.NoCandidates.class, empty.getFirst());
        assertEquals(1, empty.size(), "keine neue Wahl am selben Tag");
        assertEquals(Optional.of(DAY.plus(10)), polity().officeTerm(Office.MAYOR).orElseThrow().until());
        assertEquals(alice, polity().officeTerm(Office.MAYOR).orElseThrow().holder());
    }

    private PlayerRef carlaAsCitizen() {
        join(carla, MembershipType.CITIZEN);
        return carla;
    }

    private static RunElections.Decided decided(List<RunElections.Outcome> outcomes) {
        return assertInstanceOf(RunElections.Decided.class, outcomes.getFirst());
    }

    private ElectionId call(ActorRef citizen, Office office) {
        return fixture.app.callElection().execute(new CallElection.Command(nordhain, citizen, office, DAY));
    }

    private void stand(ActorRef citizen, ElectionId election) {
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, citizen, DAY));
    }

    private void ballot(ActorRef citizen, ElectionId election, ActorRef candidate) {
        fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, citizen, candidate, DAY));
    }

    private void appoint(ActorRef appointee, Office office) {
        fixture.app.appointOfficer().execute(new AppointOfficer.Command(nordhain, alice, office, Optional.of(appointee),
                DAY));
    }

    private <T> void amend(ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice, ParameterValue.of(key, value), DAY));
    }

    private void join(ActorRef member, MembershipType type) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, member, type, DAY));
    }

    private PolityView polity() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }
}

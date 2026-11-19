package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.party.JoinParty;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.CastVote;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.Legislator;
import de.etiennebader.civ.application.polity.ParliamentSession;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.polity.VoteOutcome;
import de.etiennebader.civ.application.population.PersonalityLegislator;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParliamentTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PlayerRef dave = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Parlamentswahl nach Sainte-Laguë, Präsidium, danach stimmen nur Abgeordnete; ohne Sitze aufgelöst")
    void electAndLegislate() {
        for (PlayerRef citizen : List.of(bob, carla, dave)) {
            join(citizen, MembershipType.CITIZEN);
        }
        PolityId red = fixture.app.foundParty().execute(new FoundParty.Command(PolityName.of("Rot"), alice,
                new PartyProfile(50, 0, PartyProfile.DEFAULT_EMBLEM), CurrencyId.EMERALD, DAY));
        fixture.app.joinParty().execute(new JoinParty.Command(red, bob, DAY));
        assertEquals(ErrorCode.NO_PARLIAMENT_PROVIDED, code(() -> call(alice)));
        amend(StandardParameters.PARLIAMENT_SEATS, 3L);

        List<RunElections.Outcome> called = fixture.app.runElections().execute(DAY);
        assertEquals(new RunElections.Called(nordhain, Office.PARLIAMENT, DAY.plus(2)), called.getFirst());
        ElectionId election = polity().electionFor(Office.PARLIAMENT).orElseThrow().id();
        for (PlayerRef candidate : List.of(alice, bob, carla)) {
            fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, candidate, DAY));
        }
        ballot(alice, election, alice);
        ballot(bob, election, bob);
        ballot(carla, election, carla);
        ballot(dave, election, alice);

        RunElections.Seated seated = assertInstanceOf(RunElections.Seated.class,
                fixture.app.runElections().execute(DAY.plus(2)).getFirst());
        assertEquals(300L, seated.count().lists().getFirst().votes());
        assertEquals(2, seated.count().lists().getFirst().seats());
        Parliament parliament = polity().parliament();
        assertEquals(List.of(alice, bob, carla), parliament.seats().stream().map(Parliament.Seat::member).toList());
        assertEquals(Optional.of(alice), parliament.president());
        assertEquals(Optional.of(red), parliament.seats().getFirst().party());
        assertEquals(Optional.empty(), parliament.until());
        assertEquals(ErrorCode.PARLIAMENT_NOT_APPOINTABLE, code(() -> fixture.app.appointOfficer().execute(
                new AppointOfficer.Command(nordhain, alice, Office.PARLIAMENT, Optional.of(bob), DAY))));

        assertEquals(ErrorCode.PARLIAMENT_DECIDES, code(() -> amend(StandardParameters.SALES_TAX_PERCENT, 5L)));
        ProposalId proposal = fixture.app.proposeMotion().execute(new ProposeMotion.Command(nordhain, dave,
                change(StandardParameters.PARLIAMENT_SEATS, 0L), DAY)).proposal();
        assertEquals(0, polity().proposals().iterator().next().votes().size(), "Nicht-Abgeordnete stimmen nicht mit");
        assertEquals(ErrorCode.NOT_IN_PARLIAMENT, code(() -> vote(dave, proposal)));
        assertEquals(VoteOutcome.OPEN, vote(alice, proposal));
        assertEquals(VoteOutcome.ACCEPTED, vote(bob, proposal), "2 von 3 Sitzen");

        assertEquals(List.of(new RunElections.Dissolved(nordhain, Office.PARLIAMENT)),
                fixture.app.runElections().execute(DAY.plus(3)));
        assertFalse(polity().parliament().seated());
    }

    @Test
    @DisplayName("Dorfbewohner kandidieren für ihre Partei, ziehen ein und stimmen ab, sobald die Mindestoffenzeit erreicht ist")
    void villagersInParliament() {
        List<PersonRef> villagers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            PersonRef villager = fixture.villager();
            villagers.add(villager);
            join(villager, MembershipType.RESIDENT);
        }
        fixture.app.foundParty().execute(new FoundParty.Command(PolityName.of("Dorfpartei"), villagers.getFirst(),
                new PartyProfile(0, 0, PartyProfile.DEFAULT_EMBLEM), CurrencyId.EMERALD, villagers.subList(1, 4), DAY));
        amend(StandardParameters.VILLAGER_VOTE_PERCENT, 100L);
        amend(StandardParameters.PARLIAMENT_SEATS, 2L);

        List<RunElections.Outcome> first = fixture.app.runElections().execute(DAY);
        RunElections.Nominated nominated = assertInstanceOf(RunElections.Nominated.class, first.getLast());
        assertEquals(villagers.subList(0, 2), nominated.candidates(), "je Partei höchstens so viele wie Sitze");
        fixture.app.runElections().execute(DAY.plus(2));
        assertTrue(polity().parliament().seated());
        assertTrue(polity().parliament().seats().stream().allMatch(seat -> seat.member() instanceof PersonRef));

        ProposalId proposal = fixture.app.proposeMotion().execute(new ProposeMotion.Command(nordhain, alice,
                change(StandardParameters.SALES_TAX_PERCENT, 5L), DAY.plus(3))).proposal();
        Legislator yes = new Legislator() {
            @Override
            public Optional<Boolean> decide(PersonRef member, Proposal open, PolityView polity) {
                return Optional.of(true);
            }

            @Override
            public Optional<Motion> propose(PersonRef member, PolityView polity) {
                return Optional.empty();
            }
        };
        ParliamentSession session = new ParliamentSession(
                new Aggregates(fixture.repositories, fixture.events), fixture.ids, yes);
        List<ParliamentSession.Outcome> sameDay = session.execute(DAY.plus(3));
        assertTrue(sameDay.stream().noneMatch(ParliamentSession.Decided.class::isInstance),
                "Antrag braucht erst parliament_motion_min_open_days, bevor im Parlament darüber abgestimmt wird");
        ParliamentSession.AgendaAnnounced announced = sameDay.stream()
                .filter(ParliamentSession.AgendaAnnounced.class::isInstance)
                .map(ParliamentSession.AgendaAnnounced.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("die Tagesordnung für morgen wird schon heute bekanntgegeben"));
        assertEquals(1, announced.agenda());
        List<ParliamentSession.Outcome> outcomes = session.execute(DAY.plus(4));
        ParliamentSession.Decided decided = assertInstanceOf(ParliamentSession.Decided.class, outcomes.getLast());
        assertEquals(proposal, decided.proposal().id());
        assertEquals(VoteOutcome.ACCEPTED, decided.outcome());
        assertEquals(5L, polity().laws().valueOf(StandardParameters.SALES_TAX_PERCENT));
    }

    @Test
    @DisplayName("full_turnout_required: Ergebnis erst, wenn alle Abgeordneten abgestimmt haben, nicht schon bei rechnerisch sicherer Mehrheit")
    void fullTurnoutRequired() {
        for (PlayerRef citizen : List.of(bob, carla, dave)) {
            join(citizen, MembershipType.CITIZEN);
        }
        amend(StandardParameters.PARLIAMENT_SEATS, 3L);
        amend(StandardParameters.FULL_TURNOUT_REQUIRED, true);
        fixture.app.runElections().execute(DAY);
        ElectionId election = polity().electionFor(Office.PARLIAMENT).orElseThrow().id();
        for (PlayerRef candidate : List.of(alice, bob, carla)) {
            fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, candidate, DAY));
        }
        ballot(alice, election, alice);
        ballot(bob, election, bob);
        ballot(carla, election, carla);
        fixture.app.runElections().execute(DAY.plus(2));
        assertEquals(3, polity().parliament().seats().size(), "je 100 Stimmen auf 3 Sitze: ein Sitz je Kandidat");

        ProposalId proposal = fixture.app.proposeMotion().execute(new ProposeMotion.Command(nordhain, alice,
                change(StandardParameters.SALES_TAX_PERCENT, 7L), DAY.plus(2))).proposal();
        assertEquals(VoteOutcome.OPEN, vote(bob, proposal, true),
                "2 von 3 wären rechnerisch sicher, aber carla hat noch nicht abgestimmt");
        assertEquals(VoteOutcome.ACCEPTED, vote(carla, proposal, false),
                "jetzt haben alle drei abgestimmt: 2 dafür, 1 dagegen bleibt eine Mehrheit");
        assertEquals(7L, polity().laws().valueOf(StandardParameters.SALES_TAX_PERCENT));
    }

    @Test
    @DisplayName("full_turnout_required: ohne volle Beteiligung zählt trotzdem die Frist (vote_days) aus")
    void fullTurnoutRequiredStillHitsDeadline() {
        for (PlayerRef citizen : List.of(bob, carla)) {
            join(citizen, MembershipType.CITIZEN);
        }
        amend(StandardParameters.PARLIAMENT_SEATS, 3L);
        amend(StandardParameters.FULL_TURNOUT_REQUIRED, true);
        amend(StandardParameters.VOTE_DAYS, 2L);
        fixture.app.runElections().execute(DAY);
        ElectionId election = polity().electionFor(Office.PARLIAMENT).orElseThrow().id();
        for (PlayerRef candidate : List.of(alice, bob, carla)) {
            fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, candidate, DAY));
        }
        ballot(alice, election, alice);
        ballot(bob, election, bob);
        ballot(carla, election, carla);
        fixture.app.runElections().execute(DAY.plus(2));

        ProposalId proposal = fixture.app.proposeMotion().execute(new ProposeMotion.Command(nordhain, alice,
                change(StandardParameters.SALES_TAX_PERCENT, 9L), DAY.plus(2))).proposal();
        assertEquals(VoteOutcome.OPEN, vote(bob, proposal, true), "carla stimmt nicht ab, aber die Mehrheit stünde fest");

        List<de.etiennebader.civ.application.polity.TallyProposals.Tallied> tallied = fixture.app.tallyProposals()
                .execute(DAY.plus(2).plus(2));
        assertEquals(1, tallied.size(), "die Frist zählt trotz fehlender voller Beteiligung aus");
        assertEquals(VoteOutcome.ACCEPTED, tallied.getFirst().outcome());
    }

    @Test
    @DisplayName("Abgeordnete nach Persönlichkeit: Freigeister beantragen Lockerungen, Ordnungsliebende lehnen sie ab")
    void personalityLegislator() {
        PersonRef free = villagerWith(personality -> personality.order() <= -70);
        PersonRef strict = villagerWith(personality -> personality.order() >= 70);
        join(free, MembershipType.RESIDENT);
        join(strict, MembershipType.RESIDENT);
        PersonalityLegislator legislator = new PersonalityLegislator();

        Motion motion = legislator.propose(free, polity()).orElseThrow();
        Motion.Change change = assertInstanceOf(Motion.Change.class, motion);
        assertEquals(Boolean.TRUE, change.change().value(), "lockert eine Schutzregel");
        Proposal proposal = new Proposal(new ProposalId(java.util.UUID.randomUUID()), motion, free, DAY, List.of());
        assertEquals(Optional.of(false), legislator.decide(strict, proposal, polity()));
        assertEquals(Optional.of(true), legislator.decide(free, proposal, polity()));
    }

    private PersonRef villagerWith(java.util.function.Predicate<Personality> wanted) {
        while (true) {
            PersonRef villager = fixture.villager();
            if (wanted.test(Personality.of(villager))) {
                return villager;
            }
        }
    }

    private static Motion change(ParameterKey<Long> key, long value) {
        return new Motion.Change(ParameterValue.of(key, value));
    }

    private VoteOutcome vote(ActorRef voter, ProposalId proposal) {
        return vote(voter, proposal, true);
    }

    private VoteOutcome vote(ActorRef voter, ProposalId proposal, boolean inFavour) {
        return fixture.app.castVote().execute(new CastVote.Command(nordhain, proposal, voter, inFavour, DAY));
    }

    private ElectionId call(ActorRef citizen) {
        return fixture.app.callElection().execute(new CallElection.Command(nordhain, citizen, Office.PARLIAMENT, DAY));
    }

    private void ballot(ActorRef voter, ElectionId election, ActorRef candidate) {
        fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, voter, candidate, DAY));
    }

    private <T> void amend(ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice, ParameterValue.of(key, value), DAY));
    }

    private void join(ActorRef member, MembershipType type) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, member, type, DAY));
    }

    private static ErrorCode code(Executable action) {
        return assertThrows(DomainException.class, action).code();
    }

    private PolityView polity() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }
}

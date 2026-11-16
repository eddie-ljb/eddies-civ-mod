package de.etiennebader.civ.application.party;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.population.PartyAffiliation;
import de.etiennebader.civ.application.population.PersonalityVoter;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;
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

class PartyTest {

    private static final PartyProfile ORDER = new PartyProfile(70, 0, "minecraft:blue_banner");

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Partei gründen, beitreten, nur eine Partei je Person, Vorsitz ändert Richtung, letztes Mitglied löst auf")
    void lifecycle() {
        PolityId party = found("Ordnungspartei", alice);
        PolityView view = polity(party);
        assertEquals(PolityKind.PARTY, view.kind());
        assertEquals(Optional.of(ORDER), view.partyProfile());
        assertTrue(view.holds(alice, Office.MAYOR));
        assertEquals(Platform.ORDER, ORDER.platform());

        fixture.app.joinParty().execute(new JoinParty.Command(party, bob, DAY));
        assertEquals(ErrorCode.ALREADY_IN_PARTY, code(() -> found("Zweite", bob)));
        assertEquals(ErrorCode.ALREADY_IN_PARTY, code(() -> fixture.app.joinParty()
                .execute(new JoinParty.Command(party, bob, DAY))));
        assertEquals(ErrorCode.NOT_A_PARTY, code(() -> fixture.app.joinParty()
                .execute(new JoinParty.Command(nordhain, bob, DAY))));
        assertEquals(ErrorCode.NOT_A_MUNICIPALITY, code(() -> fixture.app.joinPolity()
                .execute(new JoinPolity.Command(party, fixture.player(), MembershipType.CITIZEN, DAY))));
        assertEquals(ErrorCode.NOT_A_MUNICIPALITY, code(() -> fixture.app.foundFederation()
                .execute(new FoundFederation.Command(PolityName.of("Parteienbund"), "land", party, alice, DAY))));

        assertEquals(ErrorCode.NOT_OFFICE_HOLDER, code(() -> fixture.app.changePartyProfile().execute(
                new ChangePartyProfile.Command(party, bob, ORDER.withDirection(-50, 0), DAY))));
        fixture.app.changePartyProfile().execute(new ChangePartyProfile.Command(party, alice,
                ORDER.withDirection(-50, 10), DAY));
        assertEquals(Platform.FREEDOM, polity(party).partyProfile().orElseThrow().platform());

        assertFalse(fixture.app.leaveParty().execute(new LeaveParty.Command(alice, DAY)).dissolved());
        assertEquals(Optional.of(polity(party)), fixture.app.queries().partyOf(bob));
        assertTrue(fixture.app.leaveParty().execute(new LeaveParty.Command(bob, DAY)).dissolved());
        assertTrue(fixture.app.queries().polity(party).isEmpty());
        assertEquals(ErrorCode.NOT_IN_PARTY, code(() -> fixture.app.leaveParty()
                .execute(new LeaveParty.Command(bob, DAY))));
    }

    @Test
    @DisplayName("Dorfbewohner: Gleichgesinnte gründen eine Partei, weitere treten ihr bei")
    void villagersOrganise() {
        List<PersonRef> orderly = villagers(Platform.ORDER, 6);
        for (PersonRef villager : orderly) {
            fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, villager, MembershipType.RESIDENT, DAY));
        }
        List<PartyAffiliation.Step> steps = PartyAffiliation.plan(fixture.app.queries().polities(), DAY, 5);
        PartyAffiliation.Found found = assertInstanceOf(PartyAffiliation.Found.class, steps.getFirst());
        assertEquals(Platform.ORDER, found.direction());
        assertEquals(6, found.members().size());
        assertTrue(PartyAffiliation.plan(fixture.app.queries().polities(), DAY, 7).isEmpty(), "zu wenige");

        PolityId party = fixture.app.foundParty().execute(new FoundParty.Command(PolityName.of("Ordnung Nordhain"),
                found.members().getFirst(), PartyAffiliation.profileFor(found), CurrencyId.EMERALD,
                found.members().subList(1, found.members().size()), DAY));
        assertEquals(6, polity(party).memberCount());
        assertTrue(polity(party).holds(found.members().getFirst(), Office.MAYOR), "auch ein Dorfbewohner kann führen");

        PersonRef newcomer = villagers(Platform.ORDER, 1).getFirst();
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, newcomer, MembershipType.RESIDENT, DAY));
        boolean joined = false;
        for (int day = 1; day < 60 && !joined; day++) {
            joined = PartyAffiliation.plan(fixture.app.queries().polities(), Day.of(day), 5).stream()
                    .anyMatch(step -> step instanceof PartyAffiliation.Join join && join.villager().equals(newcomer));
        }
        assertTrue(joined, "tritt binnen einiger Tage bei");
    }

    @Test
    @DisplayName("Dorfbewohner bevorzugen Kandidaten ihrer Partei")
    void partyVoting() {
        PersonRef villager = villagers(Platform.ORDER, 1).getFirst();
        PolityId party = found("Ordnungspartei", alice);
        PolityView ownParty = polity(party);
        Personality personality = Personality.of(villager);
        double same = PersonalityVoter.partyAffinity(personality, Optional.of(party), Optional.of(ownParty));
        PolityView freedom = polity(fixture.app.foundParty().execute(new FoundParty.Command(
                PolityName.of("Freiheitsbund"), bob, new PartyProfile(-80, 0, PartyProfile.DEFAULT_EMBLEM),
                CurrencyId.EMERALD, DAY)));
        double other = PersonalityVoter.partyAffinity(personality, Optional.of(party), Optional.of(freedom));
        assertTrue(same > other);
        assertTrue(PersonalityVoter.partyAffinity(personality, Optional.empty(), Optional.of(ownParty))
                > PersonalityVoter.partyAffinity(personality, Optional.empty(), Optional.of(freedom)));
    }

    @Test
    @DisplayName("Bundesumlage: Glieder zahlen täglich einen Anteil ihrer Kasse an den Bund")
    void levy() {
        PolityId land = fixture.app.foundFederation().execute(new FoundFederation.Command(PolityName.of("Westmark"),
                "land", nordhain, alice, DAY));
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(new PolityRef(nordhain),
                Money.of(10_000, CurrencyId.EMERALD), DAY));
        fixture.app.amendLaw().execute(new AmendLaw.Command(land, alice,
                new ParameterValue<>(StandardParameters.FEDERAL_LEVY_PERCENT, 5L), DAY));
        fixture.app.endOfDay().execute(DAY);
        assertEquals(500, fixture.app.queries().ledger().balanceOf(new PolityRef(land), CurrencyId.EMERALD).amount());
        assertEquals(9_500, fixture.app.queries().ledger().balanceOf(new PolityRef(nordhain), CurrencyId.EMERALD)
                .amount());
    }

    private List<PersonRef> villagers(Platform direction, int count) {
        List<PersonRef> found = new ArrayList<>();
        while (found.size() < count) {
            PersonRef villager = fixture.villager();
            int[] position = PartyAffiliation.position(Personality.of(villager));
            boolean fits = switch (direction) {
                case ORDER -> position[0] >= 50 && Math.abs(position[1]) <= 25;
                default -> false;
            };
            if (fits) {
                found.add(villager);
            }
        }
        return found;
    }

    private PolityId found(String name, ActorRef founder) {
        return fixture.app.foundParty().execute(new FoundParty.Command(PolityName.of(name), founder, ORDER,
                CurrencyId.EMERALD, DAY));
    }

    private PolityView polity(PolityId id) {
        return fixture.app.queries().polity(id).orElseThrow();
    }

    private static ErrorCode code(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }
}

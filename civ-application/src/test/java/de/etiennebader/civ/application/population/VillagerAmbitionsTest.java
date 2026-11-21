package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerAmbitionsTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    private List<PersonRef> settle(int ambitious, int calm, long savings) {
        List<PersonRef> villagers = new ArrayList<>();
        int ambitiousLeft = ambitious;
        int calmLeft = calm;
        while (ambitiousLeft > 0 || calmLeft > 0) {
            PersonRef villager = fixture.villager();
            boolean isAmbitious = VillagerAmbitions.isAmbitious(villager);
            if (isAmbitious ? ambitiousLeft-- <= 0 : calmLeft-- <= 0) {
                continue;
            }
            fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, villager, MembershipType.RESIDENT, DAY));
            if (savings > 0) {
                fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(villager, emeralds(savings), DAY));
            }
            villagers.add(villager);
        }
        return villagers;
    }

    @Test
    @DisplayName("Ehrgeiz ist fest und selten: nur ein Teil der Dorfbewohner nimmt teil")
    void ambitionIsRare() {
        int ambitious = 0;
        for (int i = 0; i < 400; i++) {
            PersonRef villager = fixture.villager();
            assertEquals(VillagerAmbitions.drive(villager), VillagerAmbitions.drive(villager));
            if (VillagerAmbitions.isAmbitious(villager)) {
                ambitious++;
            }
        }
        assertTrue(ambitious > 20 && ambitious < 130, "ambitious=" + ambitious);
    }

    @Test
    @DisplayName("Aggressivität folgt der Friedfertigkeit; die Parteirichtung der Persönlichkeit")
    void traits() {
        Personality warlike = new Personality(80, -70, 0);
        PersonRef villager = fixture.villager();
        double aggression = VillagerAmbitions.aggression(Personality.of(villager));
        assertTrue(aggression >= 0 && aggression <= 1);
        assertEquals(80, VillagerAmbitions.profileFor(warlike).order());
        assertEquals(70, VillagerAmbitions.profileFor(warlike).economy());
    }

    @Test
    @DisplayName("Ruhige Dorfbewohner tun nichts, auch mit Geld und laufender Wahl")
    void calmVillagersStayOut() {
        settle(0, 15, 5_000);
        fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, Office.MAYOR, DAY));
        for (int day = 0; day < 30; day++) {
            VillagerAmbitions.Report report = fixture.app.villagerAmbitions().execute(Day.of(DAY.value() + day));
            assertEquals(0, report.companies() + report.parties() + report.candidacies());
        }
    }

    @Test
    @DisplayName("Ehrgeizige kandidieren in der Wahl einer Spielergemeinde, höchstens drei Handlungen je Tag")
    void candidacy() {
        List<PersonRef> villagers = settle(20, 0, 0);
        amendVillagerVote(25L);
        fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, Office.MAYOR, DAY));
        VillagerAmbitions.Report report = fixture.app.villagerAmbitions().execute(DAY);
        assertTrue(report.candidacies() >= 1 && report.candidacies() <= VillagerAmbitions.MAX_ACTIONS);
        PolityView town = fixture.app.queries().polity(nordhain).orElseThrow();
        Election election = town.electionFor(Office.MAYOR).orElseThrow();
        long candidates = villagers.stream().filter(election::isCandidate).count();
        assertEquals(report.candidacies(), candidates);
    }

    @Test
    @DisplayName("Ohne Wahlrecht für Dorfbewohner kandidiert niemand")
    void noCandidacyWithoutVillagerSuffrage() {
        settle(20, 0, 0);
        fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, Office.MAYOR, DAY));
        assertEquals(0, fixture.app.villagerAmbitions().execute(DAY).candidacies());
    }

    private void amendVillagerVote(long percent) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                ParameterValue.of(StandardParameters.VILLAGER_VOTE_PERCENT, percent), DAY));
    }

    @Test
    @DisplayName("Ehrgeizige mit Ersparnissen gründen Firmen und Parteien, begrenzt nach Einwohnerzahl")
    void foundCompaniesAndParties() {
        settle(25, 0, 8_000);
        for (int day = 0; day < 200; day++) {
            fixture.app.villagerAmbitions().execute(Day.of(DAY.value() + day));
        }
        int companies = fixture.app.queries().companies().size();
        int parties = fixture.app.queries().parties().size();
        assertTrue(companies >= 1 && companies <= 3, "companies=" + companies);
        assertTrue(parties >= 1 && parties <= 2, "parties=" + parties);
        assertFalse(fixture.app.queries().companies().stream().anyMatch(company -> company.members().isEmpty()));
    }

    @Test
    @DisplayName("Derselbe Ausgangszustand ergibt denselben Tag")
    void deterministic() {
        settle(10, 0, 8_000);
        VillagerAmbitions.Report first = fixture.app.villagerAmbitions().execute(DAY);
        assertEquals(first, new VillagerAmbitions.Report(first.companies(), first.parties(), first.candidacies(), first.takeovers(), first.claims()));
    }

    @Test
    @DisplayName("Übernahme: Wer mehr als die Hälfte der Aktien hält, übernimmt die Firmenleitung; vorher nicht")
    void takeOver() {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(5_000), DAY));
        PolityId firm = fixture.app.companies().foundCompany(alice, PolityName.of("Holz AG"), emeralds(2_000), DAY);
        PersonRef raider = settle(1, 0, 0).getFirst();
        fixture.app.companies().admit(alice, firm, PolityKind.COMPANY, raider, DAY);
        assertFalse(fixture.app.takeOverCompany().execute(firm, raider, DAY), "noch keine Mehrheit");

        fixture.app.trading().place(alice, firm, Side.SELL, 600, 1, DAY);
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(raider, emeralds(5_000), DAY));
        fixture.app.trading().place(raider, firm, Side.BUY, 600, 5, DAY);
        assertEquals(600L, fixture.app.queries().exchange().sharesOf(firm, raider));

        VillagerAmbitions.Report report = fixture.app.villagerAmbitions().execute(DAY);
        assertEquals(1, report.takeovers());
        PolityView company = fixture.app.queries().polity(firm).orElseThrow();
        assertTrue(company.holds(raider, Office.MAYOR));
        assertFalse(company.holds(alice, Office.MAYOR));
        assertTrue(company.isMember(alice));
        assertFalse(fixture.app.takeOverCompany().execute(firm, raider, DAY), "schon Leiter");
    }

    @Test
    @DisplayName("Ein ehrgeiziger Villager-Bürgermeister beansprucht angrenzende Chunks, ein Spieler-Bürgermeister nicht")
    void mayorClaims() {
        int before = fixture.app.queries().territory().territoryOf(nordhain).size();
        for (int day = 0; day < 150; day++) {
            assertEquals(0, fixture.app.villagerAmbitions().execute(Day.of(DAY.value() + day)).claims());
        }
        assertEquals(before, fixture.app.queries().territory().territoryOf(nordhain).size());

        PersonRef villager = settle(1, 0, 0).getFirst();
        var election = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, Office.MAYOR, DAY));
        amendVillagerVote(100L);
        fixture.app.standForElection().execute(new de.etiennebader.civ.application.office.StandForElection.Command(
                nordhain, election, villager, DAY));
        fixture.app.castBallot().execute(new de.etiennebader.civ.application.office.CastBallot.Command(
                nordhain, election, alice, villager, DAY));
        fixture.app.runElections().execute(DAY.plus(5));
        assertEquals(java.util.Optional.of(villager), fixture.app.queries().polity(nordhain).orElseThrow().mayor());

        int claimed = 0;
        for (int day = 0; day < 300; day++) {
            claimed += fixture.app.villagerAmbitions().execute(Day.of(DAY.value() + 10 + day)).claims();
        }
        assertTrue(claimed >= 1, "claimed=" + claimed);
        assertEquals(before + claimed, fixture.app.queries().territory().territoryOf(nordhain).size());
    }
}

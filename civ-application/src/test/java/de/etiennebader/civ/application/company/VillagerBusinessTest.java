package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.OfferPlot;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerBusinessTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    private PersonRef resident(long savings) {
        PersonRef villager = fixture.villager();
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, villager, MembershipType.RESIDENT, DAY));
        if (savings > 0) {
            fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(villager, emeralds(savings), DAY));
        }
        return villager;
    }

    private PolityId firmOf(PersonRef founder, long capital) {
        return fixture.app.companies().foundCompany(founder, PolityName.of("Dorf AG"), emeralds(capital), DAY);
    }

    private long treasury(PolityId firm) {
        return fixture.app.queries().ledger().balanceOf(fixture.app.queries().polity(firm).orElseThrow().asActor(),
                EMERALD).amount();
    }

    @Test
    @DisplayName("Firmen von Spielern lässt der Dorfbewohner-Chef unberührt")
    void ignoresPlayerFirms() {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(9_000), DAY));
        PolityId firm = fixture.app.companies().foundCompany(alice, PolityName.of("Alice AG"), emeralds(5_000), DAY);
        for (int day = 0; day < 40; day++) {
            VillagerBusiness.Report report = fixture.app.villagerBusiness().execute(Day.of(DAY.value() + day));
            assertEquals(0, report.branches() + report.investments() + report.hires() + report.dividends());
        }
        assertTrue(fixture.app.queries().production().enterpriseOf(firm).isEmpty());
    }

    @Test
    @DisplayName("Villager-Chef wählt eine Branche, baut aus, stellt ein und schüttet Dividenden aus")
    void growsTheFirm() {
        PersonRef boss = resident(40_000);
        for (int i = 0; i < 6; i++) {
            resident(0);
        }
        PolityId firm = firmOf(boss, 30_000);
        int branches = 0;
        int investments = 0;
        int hires = 0;
        int dividends = 0;
        for (int day = 0; day < 120; day++) {
            fixture.app.production().endOfDay(code -> java.util.OptionalLong.empty(),
                    de.etiennebader.civ.domain.market.PriceLevel.NEUTRAL, Day.of(DAY.value() + day));
            VillagerBusiness.Report report = fixture.app.villagerBusiness().execute(Day.of(DAY.value() + day));
            branches += report.branches();
            investments += report.investments();
            hires += report.hires();
            dividends += report.dividends();
        }
        assertEquals(1, branches);
        assertTrue(fixture.app.queries().production().enterpriseOf(firm).orElseThrow().level() >= 1);
        assertTrue(investments >= 1, "investments=" + investments);
        assertTrue(hires >= 1, "hires=" + hires);
        assertTrue(dividends >= 1, "dividends=" + dividends);
        assertEquals(hires, fixture.app.production().employees(firm).size());
        assertTrue(treasury(firm) >= 0);
    }

    @Test
    @DisplayName("Ist die Kasse leer, kündigt der Chef dem zuletzt Eingestellten")
    void firesWhenBroke() {
        PersonRef boss = resident(40_000);
        resident(0);
        resident(0);
        PolityId firm = firmOf(boss, 30_000);
        fixture.app.production().chooseBranch(boss, firm, de.etiennebader.civ.domain.production.Branch.FARM, DAY);
        for (int i = 0; i < 2; i++) {
            fixture.app.contracts().offerEmployment(fixture.app.queries().polity(firm).orElseThrow().asActor(),
                    fixture.villager(), emeralds(30), 0, DAY);
        }
        long before = fixture.app.production().employees(firm).size();
        assertEquals(2, before);
        fixture.app.production().invest(boss, firm, emeralds(treasury(firm)), DAY);
        assertEquals(0, treasury(firm));
        int fired = 0;
        for (int day = 0; day < 60; day++) {
            fired += fixture.app.villagerBusiness().execute(Day.of(DAY.value() + day)).fired();
        }
        assertTrue(fired >= 1, "fired=" + fired);
    }

    @Test
    @DisplayName("Die Firma kauft ein angebotenes Gewerbegrundstück der Gemeinde")
    void buysPlot() {
        PersonRef boss = resident(40_000);
        PolityId firm = firmOf(boss, 30_000);
        PlotId plot = fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, 0, 0, 7, 7), PlotType.COMMERCIAL, DAY));
        fixture.app.offerPlot().execute(new OfferPlot.Command(nordhain, plot,
                new PolityRef(nordhain), Optional.of(emeralds(50)), DAY));
        PolityView company = fixture.app.queries().polity(firm).orElseThrow();
        for (int day = 0; day < 120 && fixture.app.queries().land(nordhain).plotsHeldBy(company.asActor()).isEmpty();
             day++) {
            fixture.app.villagerBusiness().execute(Day.of(DAY.value() + day));
        }
        assertFalse(fixture.app.queries().land(nordhain).plotsHeldBy(company.asActor()).isEmpty());
        assertTrue(Personality.of(boss).thrift() >= -100 && VillagerAmbitions.drive(boss) >= 0);
    }
}

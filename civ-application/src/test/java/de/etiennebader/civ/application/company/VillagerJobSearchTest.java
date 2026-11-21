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
import de.etiennebader.civ.domain.production.Branch;
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

class VillagerJobSearchTest {

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

    @Test
    @DisplayName("Arbeitslose Dorfbewohner finden eine Stelle, bis die Firma voll besetzt ist")
    void unemployedFindJobs() {
        PersonRef boss = resident(40_000);
        for (int i = 0; i < 8; i++) {
            resident(0);
        }
        PolityId firm = fixture.app.companies().foundCompany(boss, PolityName.of("Dorf AG"), emeralds(30_000), DAY);
        fixture.app.production().chooseBranch(boss, firm, Branch.FARM, DAY);
        int placed = 0;
        for (int day = 0; day < 30; day++) {
            placed += fixture.app.villagerJobSearch().execute(Day.of(DAY.value() + day), villager -> false);
        }
        int cap = VillagerBusiness.BASE_STAFF + 2 * fixture.app.queries().production().enterpriseOf(firm).orElseThrow().level();
        assertEquals(cap, placed, "Die Stellen werden besetzt, aber nicht überbesetzt");
        assertEquals(cap, fixture.app.production().employees(firm).size());
    }

    @Test
    @DisplayName("Wer ausgeschlossen ist (z. B. im Dienst), sucht nicht")
    void excludedDoNotSearch() {
        PersonRef boss = resident(40_000);
        resident(0);
        PolityId firm = fixture.app.companies().foundCompany(boss, PolityName.of("Dorf AG"), emeralds(30_000), DAY);
        fixture.app.production().chooseBranch(boss, firm, Branch.FARM, DAY);
        for (int day = 0; day < 30; day++) {
            assertEquals(0, fixture.app.villagerJobSearch().execute(Day.of(DAY.value() + day), villager -> true));
        }
    }

    @Test
    @DisplayName("Passung und Lohnforderung folgen der Persönlichkeit")
    void fitAndWage() {
        Personality green = new Personality(0, 0, 0) ;
        assertEquals(Math.round(green.nature()), VillagerJobSearch.fit(Branch.FARM, green));
        assertEquals(green.order(), VillagerJobSearch.fit(Branch.MINE, green));
        assertTrue(VillagerJobSearch.wageOf(new Personality(0, 100, 0)) > VillagerJobSearch.wageOf(new Personality(0, -100, 0)));
    }
}

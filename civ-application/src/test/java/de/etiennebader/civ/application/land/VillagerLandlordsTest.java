package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerLandlordsTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    private PersonRef resident(long savings) {
        PersonRef villager = fixture.villager();
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, villager, MembershipType.RESIDENT, DAY));
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(villager, emeralds(savings), DAY));
        return villager;
    }

    @Test
    @DisplayName("Ein Dorfbewohner kauft ein Wohngrundstück und vermietet es an einen Mitbürger")
    void buysAndLeases() {
        resident(20_000);
        resident(20_000);
        PlotId plot = fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, 0, 0, 7, 7), PlotType.RESIDENTIAL, DAY));
        fixture.app.offerPlot().execute(new OfferPlot.Command(nordhain, plot,
                new PolityRef(nordhain), Optional.of(emeralds(50)), DAY));
        for (int day = 0; day < 200; day++) {
            fixture.app.villagerLandlords().execute(Day.of(DAY.value() + day));
        }
        assertFalse(fixture.app.queries().land(nordhain).plot(plot).orElseThrow().isForSale());
        assertTrue(fixture.app.queries().contracts().active().stream()
                .anyMatch(contract -> contract.kind() == ContractKind.LEASE));
    }

    @Test
    @DisplayName("Ohne Geld wird nichts gekauft")
    void needsMoney() {
        PersonRef broke = fixture.villager();
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, broke, MembershipType.RESIDENT, DAY));
        PlotId plot = fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, 0, 0, 7, 7), PlotType.RESIDENTIAL, DAY));
        fixture.app.offerPlot().execute(new OfferPlot.Command(nordhain, plot,
                new PolityRef(nordhain), Optional.of(emeralds(50)), DAY));
        for (int day = 0; day < 50; day++) {
            fixture.app.villagerLandlords().execute(Day.of(DAY.value() + day));
        }
        assertTrue(fixture.app.queries().land(nordhain).plot(plot).orElseThrow().isForSale());
    }

    @Test
    @DisplayName("Sparsame kaufen mehr Häuser, Großzügige verlangen weniger Miete")
    void personalityShapesLandlords() {
        assertEquals(3, VillagerLandlords.maxHouses(new Personality(0, 80, 0)));
        assertEquals(1, VillagerLandlords.maxHouses(new Personality(0, -60, 0)));
        assertTrue(VillagerLandlords.rentPerDay(new Personality(0, 80, 0))
                > VillagerLandlords.rentPerDay(new Personality(0, -80, 0)));
    }

    @Test
    @DisplayName("Fortschrittliche Chefs können die Baufirma wählen; die Branche liefert Baumaterial")
    void constructionBranch() {
        assertEquals(Optional.of(Branch.CONSTRUCTION), Branch.of("bau"));
        assertFalse(Branch.CONSTRUCTION.yields().isEmpty());
    }
}

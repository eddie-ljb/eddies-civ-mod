package de.etiennebader.civ.application.day;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EndOfDayTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    @DisplayName("Tageswechsel zieht die Grundsteuer aller Gemeinden ein, in Gründungsreihenfolge")
    void collectsLandTaxOfAllPolities() {
        PlayerRef alice = fixture.player();
        PlayerRef bob = fixture.player();
        PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
        PolityId eichenfurt = fixture.found("Eichenfurt", bob, 10, 10);
        sellPlotTo(nordhain, alice, new Area(DimensionId.OVERWORLD, 0, 0, 4, 4), 2);
        sellPlotTo(eichenfurt, bob, new Area(DimensionId.OVERWORLD, 160, 160, 162, 162), 1);
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(60), DAY));

        DayReport report = fixture.app.endOfDay().execute(Day.of(2));

        assertEquals(List.of(nordhain, eichenfurt), report.landTaxReports().stream().map(r -> r.polity()).toList());
        assertEquals(emeralds(50), report.landTaxCollected(EMERALD));
        assertEquals(emeralds(9), report.landTaxArrears(EMERALD));
        assertEquals(emeralds(50), fixture.app.queries().ledger().balanceOf(new PolityRef(nordhain), EMERALD));
    }

    private void sellPlotTo(PolityId polity, PlayerRef buyer, Area area, long taxPerBlock) {
        PlotId plot = fixture.app.createPlot().execute(new CreatePlot.Command(polity, area, PlotType.RESIDENTIAL, DAY));
        fixture.app.sellPlot().execute(new SellPlot.Command(polity, plot, new PolityRef(polity), buyer, emeralds(0), DAY));
        fixture.app.enactStatute().execute(new EnactStatute.Command(polity, "Grundsteuerordnung",
                List.of(ParameterValue.of(StandardParameters.LAND_TAX_PER_BLOCK, taxPerBlock)), DAY));
    }
}

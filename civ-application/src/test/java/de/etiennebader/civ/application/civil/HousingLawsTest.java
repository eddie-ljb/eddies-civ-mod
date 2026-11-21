package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Mehrfamilienhäuser und Einkommensteuer auf Mieten (Issue #19). */
class HousingLawsTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carol = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    private PlotId alicesPlot() {
        PlotId plot = fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, 0, 0, 3, 3), PlotType.RESIDENTIAL, DAY));
        fixture.app.assignPlot().execute(new AssignPlot.Command(nordhain, plot, alice, DAY));
        return plot;
    }

    private <T> void amend(ParameterKey<T> key, T value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice, ParameterValue.of(key, value), DAY));
    }

    private long balance(ActorRef actor) {
        return fixture.app.queries().ledger().balanceOf(actor, EMERALD).amount();
    }

    private void fund(PlayerRef player, long amount) {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(player, emeralds(amount), DAY));
    }

    @Test
    @DisplayName("Ohne Gesetz hat ein Grundstück nur einen Mieter, mit max_tenants_per_plot mehrere")
    void multiFamilyHouse() {
        PlotId plot = alicesPlot();
        fund(bob, 50);
        fund(carol, 50);
        Contract first = fixture.app.contracts().offerLease(alice, nordhain, plot, bob, emeralds(1), emeralds(0), 0, DAY);
        fixture.app.contracts().sign(first.id(), bob, DAY);
        assertThrows(PlotNotLeasableException.class, () -> fixture.app.contracts()
                .offerLease(alice, nordhain, plot, carol, emeralds(1), emeralds(0), 0, DAY));

        amend(StandardParameters.MAX_TENANTS_PER_PLOT, 3);
        Contract second = fixture.app.contracts().offerLease(alice, nordhain, plot, carol, emeralds(1), emeralds(0), 0, DAY);
        fixture.app.contracts().sign(second.id(), carol, DAY);
        assertEquals(2, fixture.app.queries().leasesOf(nordhain, plot).size());
    }

    @Test
    @DisplayName("Einkommensteuer: ein Teil der Miete geht an die Gemeindekasse")
    void incomeTaxOnRent() {
        PlotId plot = alicesPlot();
        fund(bob, 100);
        amend(StandardParameters.INCOME_TAX_PERCENT, 20L);
        Contract lease = fixture.app.contracts().offerLease(alice, nordhain, plot, bob, emeralds(10), emeralds(0), 0, DAY);
        fixture.app.contracts().sign(lease.id(), bob, DAY);
        long town = balance(fixture.app.queries().polity(nordhain).orElseThrow().asActor());
        long landlord = balance(alice);
        fixture.app.runContracts().execute(DAY.plus(1));
        assertEquals(town + 2, balance(fixture.app.queries().polity(nordhain).orElseThrow().asActor()));
        assertEquals(landlord + 8, balance(alice));
        assertEquals(90, balance(bob));
    }
}

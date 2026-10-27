package de.etiennebader.civ.domain.fiscal;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.economy.FundsTransferred;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimContext;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_PER_BLOCK;
import static de.etiennebader.civ.domain.law.StandardParameters.MAX_OUTPOSTS;
import static de.etiennebader.civ.domain.law.StandardParameters.TAX_EMBASSIES;
import static de.etiennebader.civ.domain.territory.DimensionId.OVERWORLD;
import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandTaxCollectorTest {

    private static final CurrencyId EMERALD = CurrencyId.EMERALD;
    private static final Day FOUNDING = Day.of(1);
    private static final Day TAX_DAY = Day.of(10);

    private final TestActors actors = new TestActors();
    private final LandTaxCollector collector = new LandTaxCollector();
    private final TerritoryRegistry territory = TerritoryRegistry.empty();
    private final Ledger ledger = Ledger.empty();

    private PlayerRef alice;
    private PersonRef bertram;
    private Polity nordhain;
    private Polity eichenfurt;
    private LandRegistry land;

    @BeforeEach
    void foundNordhainWithFourChunks() {
        alice = actors.player();
        bertram = actors.villager();
        nordhain = Polity.found(actors.polityId(), PolityName.of("Nordhain"), alice, EMERALD, seatIn(0, 0), FOUNDING);
        nordhain.admit(bertram, MembershipType.CITIZEN, FOUNDING);
        eichenfurt = Polity.found(actors.polityId(), PolityName.of("Eichenfurt"), actors.player(), EMERALD, seatIn(20, 20),
                FOUNDING);

        CompositeClaimPolicy rules = CompositeClaimPolicy.standard(4, 2);
        for (ChunkCoord chunk : List.of(chunk(0, 0), chunk(1, 0), chunk(0, 1), chunk(1, 1))) {
            ClaimContext context = new ClaimContext(nordhain.id(), ClaimType.CORE, chunk,
                    nordhain.memberCount(), nordhain.laws().valueOf(MAX_OUTPOSTS));
            territory.claim(context, rules, FOUNDING);
        }
        land = LandRegistry.open(nordhain.id());
    }

    @Nested
    @DisplayName("Abnahmeszenario Nordhain")
    class AcceptanceScenario {

        private PlotId alicePlot;
        private PlotId bertramPlot;
        private PlotId embassy;

        @BeforeEach
        void setUpScenario() {
            alicePlot = createPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
            bertramPlot = createPlot(new Area(OVERWORLD, 10, 0, 14, 9), PlotType.RESIDENTIAL);
            embassy = createPlot(new Area(OVERWORLD, 20, 20, 23, 23), PlotType.EMBASSY);
            land.assign(alicePlot, alice, FOUNDING);
            land.assign(bertramPlot, bertram, FOUNDING);
            land.assign(embassy, eichenfurt.asActor(), FOUNDING);

            enactTax(1);

            ledger.mint(alice, emeralds(1_000), SupplyChange.COMMODITY_DEPOSIT, FOUNDING);
            ledger.mint(bertram, emeralds(200), SupplyChange.COMMODITY_DEPOSIT, FOUNDING);
            ledger.withdrawCash(bertram, emeralds(200), FOUNDING);
            ledger.pullEvents();
        }

        @Test
        @DisplayName("A zahlt 100, B schuldet 50, Botschaft steuerfrei, Geldmenge erhalten")
        void collectsLandTax() {
            LandTaxReport report = collector.collect(nordhain, land, ledger, TAX_DAY);

            assertEquals(emeralds(100), report.collected());
            assertEquals(List.of(new TaxArrear(bertram, emeralds(50))), report.arrears());
            assertEquals(emeralds(900), ledger.balanceOf(alice, EMERALD));
            assertEquals(emeralds(100), ledger.balanceOf(nordhain.asActor(), EMERALD));
            assertEquals(Money.zero(EMERALD), ledger.balanceOf(eichenfurt.asActor(), EMERALD));
            assertEquals(emeralds(1_200), ledger.totalSupply(EMERALD));
            assertEquals(emeralds(200), ledger.cashInCirculation(EMERALD));
            assertEquals(List.of(new FundsTransferred(alice, nordhain.asActor(), emeralds(100), TransferReason.LAND_TAX, TAX_DAY)),
                    ledger.pullEvents());
        }

        @Test
        @DisplayName("Nach Einzahlung des Bargelds zahlt auch B")
        void villagerPaysAfterDepositingCash() {
            ledger.depositCash(bertram, emeralds(200), TAX_DAY);

            LandTaxReport report = collector.collect(nordhain, land, ledger, TAX_DAY);

            assertEquals(emeralds(150), report.collected());
            assertTrue(report.arrears().isEmpty());
            assertEquals(emeralds(150), ledger.balanceOf(bertram, EMERALD));
        }

        @Test
        @DisplayName("Mit TAX_EMBASSIES zahlt auch die Botschaft")
        void taxesEmbassiesWhenEnabled() {
            nordhain.enact(Statute.of(StatuteId.generate(actors.ids()), "Botschaftssteuergesetz",
                    ParameterValue.of(TAX_EMBASSIES, true)), TAX_DAY);

            LandTaxReport report = collector.collect(nordhain, land, ledger, TAX_DAY);

            assertTrue(report.arrears().contains(new TaxArrear(eichenfurt.asActor(), emeralds(16))));
            assertEquals(emeralds(66), report.totalArrears());
        }
    }

    @Test
    @DisplayName("Steuersatz 0 → leerer Report, keine Buchung")
    void zeroRateCollectsNothing() {
        PlotId plot = createPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        land.assign(plot, alice, FOUNDING);
        ledger.mint(alice, emeralds(1_000), SupplyChange.ADMIN, FOUNDING);
        ledger.pullEvents();

        LandTaxReport report = collector.collect(nordhain, land, ledger, TAX_DAY);

        assertEquals(LandTaxReport.empty(nordhain.id(), EMERALD, TAX_DAY), report);
        assertTrue(ledger.pullEvents().isEmpty());
    }

    @Test
    @DisplayName("Gemeindeland und nicht vergebene Grundstücke sind steuerfrei")
    void municipalLandIsTaxFree() {
        createPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.PUBLIC);
        enactTax(5);

        LandTaxReport report = collector.collect(nordhain, land, ledger, TAX_DAY);

        assertEquals(Money.zero(EMERALD), report.collected());
        assertTrue(report.arrears().isEmpty());
    }

    @Test
    @DisplayName("Flächen mehrerer Grundstücke eines Inhabers werden zusammengezählt")
    void sumsAreaPerHolder() {
        land.assign(createPlot(new Area(OVERWORLD, 0, 0, 1, 1), PlotType.RESIDENTIAL), alice, FOUNDING);
        land.assign(createPlot(new Area(OVERWORLD, 2, 0, 4, 0), PlotType.COMMERCIAL), alice, FOUNDING);
        enactTax(10);
        ledger.mint(alice, emeralds(1_000), SupplyChange.ADMIN, FOUNDING);
        ledger.pullEvents();

        LandTaxReport report = collector.collect(nordhain, land, ledger, TAX_DAY);

        assertEquals(emeralds(70), report.collected());
        assertEquals(1, ledger.pullEvents().size());
    }

    @Test
    @DisplayName("Fremdes Grundbuch wird abgelehnt")
    void rejectsForeignLandRegistry() {
        assertThrows(IllegalArgumentException.class,
                () -> collector.collect(nordhain, LandRegistry.open(eichenfurt.id()), ledger, TAX_DAY));
    }

    private PlotId createPlot(Area area, PlotType type) {
        PlotId plotId = PlotId.generate(actors.ids());
        land.createPlot(plotId, area, type, territory, FOUNDING);
        return plotId;
    }

    private void enactTax(long ratePerBlock) {
        nordhain.enact(Statute.of(StatuteId.generate(actors.ids()), "Grundsteuerordnung",
                ParameterValue.of(LAND_TAX_PER_BLOCK, ratePerBlock)), FOUNDING);
    }

    private static ChunkCoord chunk(int x, int z) {
        return new ChunkCoord(OVERWORLD, x, z);
    }

    private static Money emeralds(long amount) {
        return Money.of(amount, EMERALD);
    }
}

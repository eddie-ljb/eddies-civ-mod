package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimContext;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.domain.territory.DimensionId.OVERWORLD;
import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandRegistryTest {

    private static final Day DAY = Day.of(1);

    private final TestActors actors = new TestActors();
    private final PolityId nordhain = actors.polityId();
    private final PolityRef nordhainActor = new PolityRef(nordhain);
    private final PolityRef eichenfurt = actors.polity();
    private final PlayerRef alice = actors.player();
    private final PlayerRef bob = actors.player();
    private final TerritoryRegistry territory = TerritoryRegistry.empty();
    private final LandRegistry land = LandRegistry.open(nordhain);

    @BeforeEach
    void claimTwoChunks() {
        claim(new ChunkCoord(OVERWORLD, 0, 0));
        claim(new ChunkCoord(OVERWORLD, 1, 0));
    }

    @Test
    @DisplayName("Neues Grundstück gehört der Polity, Event PlotCreated")
    void createdPlotBelongsToPolity() {
        PlotId plotId = newPlot(new Area(OVERWORLD, 10, 0, 25, 9), PlotType.RESIDENTIAL);

        Plot plot = land.plot(plotId).orElseThrow();
        assertEquals(nordhainActor, plot.holder());
        assertEquals(Optional.of(plot), land.plotAt(OVERWORLD, 20, 5));
        assertTrue(land.hasPlotsIn(new ChunkCoord(OVERWORLD, 1, 0)));
        assertEquals(List.of(new PlotCreated(nordhain, plotId, plot.area(), PlotType.RESIDENTIAL, DAY)), land.pullEvents());
    }

    @Test
    @DisplayName("Grundstück ragt über eigenes Gebiet hinaus → PLOT_OUTSIDE_TERRITORY")
    void rejectsPlotOutsideTerritory() {
        var exception = assertDomainError(ErrorCode.PLOT_OUTSIDE_TERRITORY,
                () -> newPlot(new Area(OVERWORLD, 20, 0, 40, 9), PlotType.RESIDENTIAL));

        assertEquals(List.of(2, 0), exception.arguments());
        assertTrue(land.plots().isEmpty());
    }

    @Test
    @DisplayName("Grundstück auf fremdem Gebiet → PLOT_OUTSIDE_TERRITORY")
    void rejectsPlotOnForeignTerritory() {
        territory.claim(new ClaimContext(eichenfurt.id(), ClaimType.CORE, new ChunkCoord(OVERWORLD, 5, 5), 1, 0),
                ClaimPolicy.ALLOW_ALL, DAY);

        assertDomainError(ErrorCode.PLOT_OUTSIDE_TERRITORY,
                () -> newPlot(new Area(OVERWORLD, 80, 80, 85, 85), PlotType.RESIDENTIAL));
    }

    @Test
    @DisplayName("Überlappende Grundstücke → PLOT_OVERLAP")
    void rejectsOverlappingPlots() {
        PlotId existing = newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);

        var exception = assertDomainError(ErrorCode.PLOT_OVERLAP,
                () -> newPlot(new Area(OVERWORLD, 9, 9, 12, 12), PlotType.COMMERCIAL));

        assertEquals(List.of(existing), exception.arguments());
    }

    @Test
    @DisplayName("Angrenzende Grundstücke sind erlaubt")
    void allowsAdjacentPlots() {
        newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        newPlot(new Area(OVERWORLD, 10, 0, 19, 9), PlotType.RESIDENTIAL);

        assertEquals(2, land.plots().size());
    }

    @Test
    @DisplayName("Vergabe an einen Bürger, Event PlotAssigned")
    void assignsPlotToCitizen() {
        PlotId plotId = newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        land.pullEvents();

        land.assign(plotId, alice, Day.of(2));

        assertEquals(List.copyOf(land.plotsHeldBy(alice)), List.of(land.plot(plotId).orElseThrow()));
        assertEquals(Day.of(2), land.plot(plotId).orElseThrow().holderSince());
        assertEquals(List.of(new PlotAssigned(nordhain, plotId, alice, Day.of(2))), land.pullEvents());
    }

    @Test
    @DisplayName("Bereits vergebenes Grundstück erneut vergeben → NOT_PLOT_HOLDER")
    void rejectsAssignmentOfAssignedPlot() {
        PlotId plotId = newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        land.assign(plotId, alice, Day.of(2));

        assertDomainError(ErrorCode.NOT_PLOT_HOLDER, () -> land.assign(plotId, bob, Day.of(3)));
    }

    @Test
    @DisplayName("Botschaft an einen Spieler oder an die eigene Polity → INVALID_EMBASSY_HOLDER")
    void rejectsInvalidEmbassyHolder() {
        PlotId embassy = newPlot(new Area(OVERWORLD, 0, 0, 3, 3), PlotType.EMBASSY);

        assertDomainError(ErrorCode.INVALID_EMBASSY_HOLDER, () -> land.assign(embassy, alice, Day.of(2)));
        assertDomainError(ErrorCode.INVALID_EMBASSY_HOLDER, () -> land.assign(embassy, nordhainActor, Day.of(2)));
    }

    @Test
    @DisplayName("Botschaft an eine fremde Polity ist erlaubt")
    void assignsEmbassyToForeignPolity() {
        PlotId embassy = newPlot(new Area(OVERWORLD, 0, 0, 3, 3), PlotType.EMBASSY);

        land.assign(embassy, eichenfurt, Day.of(2));

        assertTrue(land.plot(embassy).orElseThrow().isHeldBy(eichenfurt));
    }

    @Test
    @DisplayName("Verkauf zwischen Bürgern, Event PlotTransferred")
    void transfersPlotBetweenCitizens() {
        PlotId plotId = newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        land.assign(plotId, alice, Day.of(2));
        land.pullEvents();

        land.transfer(plotId, alice, bob, Day.of(3));

        assertTrue(land.plot(plotId).orElseThrow().isHeldBy(bob));
        assertEquals(List.of(new PlotTransferred(nordhain, plotId, alice, bob, Day.of(3))), land.pullEvents());
    }

    @Test
    @DisplayName("Verkauf durch Nicht-Inhaber → NOT_PLOT_HOLDER")
    void rejectsTransferByNonHolder() {
        PlotId plotId = newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        land.assign(plotId, alice, Day.of(2));

        assertDomainError(ErrorCode.NOT_PLOT_HOLDER, () -> land.transfer(plotId, bob, bob, Day.of(3)));
    }

    @Test
    @DisplayName("Auflösung macht die Fläche wieder zu Gemeindeland")
    void dissolveFreesArea() {
        PlotId plotId = newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.RESIDENTIAL);
        land.assign(plotId, alice, Day.of(2));
        land.pullEvents();

        land.dissolve(plotId, Day.of(3));

        assertFalse(land.hasPlotsIn(new ChunkCoord(OVERWORLD, 0, 0)));
        assertEquals(List.of(new PlotDissolved(nordhain, plotId, alice, Day.of(3))), land.pullEvents());
        newPlot(new Area(OVERWORLD, 0, 0, 9, 9), PlotType.PUBLIC);
    }

    @Test
    @DisplayName("Unbekanntes Grundstück → UNKNOWN_PLOT")
    void rejectsUnknownPlot() {
        PlotId unknown = PlotId.generate(actors.ids());

        assertDomainError(ErrorCode.UNKNOWN_PLOT, () -> land.dissolve(unknown, DAY));
        assertDomainError(ErrorCode.UNKNOWN_PLOT, () -> land.assign(unknown, alice, DAY));
        assertDomainError(ErrorCode.UNKNOWN_PLOT, () -> land.transfer(unknown, alice, bob, DAY));
    }

    private void claim(ChunkCoord chunk) {
        territory.claim(new ClaimContext(nordhain, ClaimType.CORE, chunk, 1, 0), ClaimPolicy.ALLOW_ALL, DAY);
    }

    private PlotId newPlot(Area area, PlotType type) {
        PlotId plotId = PlotId.generate(actors.ids());
        land.createPlot(plotId, area, type, territory, DAY);
        return plotId;
    }
}

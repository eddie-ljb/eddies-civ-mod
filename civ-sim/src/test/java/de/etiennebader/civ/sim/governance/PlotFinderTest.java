package de.etiennebader.civ.sim.governance;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimContext;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlotFinderTest {

    private static final DimensionId OVERWORLD = DimensionId.OVERWORLD;

    private final SeededIdGenerator ids = new SeededIdGenerator(3);
    private final PolityId polity = PolityId.generate(ids);
    private final TerritoryRegistry territory = TerritoryRegistry.empty();
    private final LandRegistry land = LandRegistry.open(polity);
    private final PlotFinder finder = new PlotFinder();

    @Test
    @DisplayName("Leeres Gebiet: erstes Grundstück in der Ecke des ersten Chunks")
    void placesFirstPlotInCorner() {
        List<ChunkCoord> chunks = claim(new ChunkCoord(OVERWORLD, 1, 0), new ChunkCoord(OVERWORLD, 0, 0));

        assertEquals(Optional.of(new Area(OVERWORLD, 0, 0, 7, 5)), finder.find(land, chunks, 8, 6));
    }

    @Test
    @DisplayName("Belegte Flächen werden übersprungen, Grundstücke liegen dicht beieinander")
    void skipsOccupiedArea() {
        List<ChunkCoord> chunks = claim(new ChunkCoord(OVERWORLD, 0, 0));
        createPlot(new Area(OVERWORLD, 0, 0, 7, 7));

        assertEquals(Optional.of(new Area(OVERWORLD, 8, 0, 15, 7)), finder.find(land, chunks, 8, 8));
    }

    @Test
    @DisplayName("Voller Chunk → keine Fläche")
    void returnsEmptyWhenFull() {
        List<ChunkCoord> chunks = claim(new ChunkCoord(OVERWORLD, 0, 0));
        createPlot(new Area(OVERWORLD, 0, 0, 15, 15));

        assertEquals(Optional.empty(), finder.find(land, chunks, 2, 2));
    }

    @Test
    @DisplayName("Ungültige Größe wird abgelehnt")
    void rejectsInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> finder.find(land, List.of(), 17, 4));
    }

    private List<ChunkCoord> claim(ChunkCoord... chunks) {
        for (ChunkCoord chunk : chunks) {
            territory.claim(new ClaimContext(polity, ClaimType.CORE, chunk, 1, 0), ClaimPolicy.ALLOW_ALL, Day.ZERO);
        }
        return List.of(chunks);
    }

    private void createPlot(Area area) {
        land.createPlot(PlotId.generate(ids), area, PlotType.RESIDENTIAL, territory, Day.ZERO);
    }
}

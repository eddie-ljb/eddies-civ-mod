package de.etiennebader.civ.server.road;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadJobTest {

    private static RoadJob job(Area area) {
        return new RoadJob(UUID.randomUUID(), new PolityId(UUID.randomUUID()), "Hauptstraße", area,
                RoadJob.Material.PATH, 0, 0, false);
    }

    @Test
    void cellsRunAlongTheLongSide() {
        RoadJob along = job(new Area(DimensionId.OVERWORLD, 10, 20, 15, 21));
        assertEquals(12, along.total());
        assertArrayEquals(new int[] {10, 20}, along.cell(0));
        assertArrayEquals(new int[] {15, 20}, along.cell(5));
        assertArrayEquals(new int[] {10, 21}, along.cell(6));
        RoadJob across = job(new Area(DimensionId.OVERWORLD, 10, 20, 11, 25));
        assertArrayEquals(new int[] {10, 25}, across.cell(5));
        assertArrayEquals(new int[] {11, 20}, across.cell(6));
    }

    @Test
    void progressFinishesTheJob() {
        RoadJob road = job(new Area(DimensionId.OVERWORLD, 0, 0, 3, 1));
        RoadJob half = road.progressed(4, 80);
        assertEquals(4, half.done());
        assertEquals(false, half.finished());
        assertEquals(true, half.progressed(100, 0).finished());
        assertEquals(8, half.progressed(100, 0).done());
    }
}

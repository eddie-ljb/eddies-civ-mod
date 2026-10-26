package de.etiennebader.civ.application.territory;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.Objects;

/**
 * Eine Gemeinde gibt einen Chunk auf. Liegt dort noch ein Grundstück, wird das abgelehnt,
 * sonst läge es außerhalb des Gebiets. Diese Regel betrifft Grundbuch und Hoheitsregister.
 */
public final class ReleaseChunk {

    public record Command(PolityId polity, ChunkCoord chunk, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public ReleaseChunk(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws ChunkHasPlotsException wenn auf dem Chunk noch ein Grundstück liegt
     * @throws de.etiennebader.civ.domain.territory.NotSovereignException wenn der Chunk nicht der Gemeinde gehört
     */
    public void execute(Command command) {
        aggregates.polity(command.polity());
        LandRegistry land = aggregates.land(command.polity());
        if (land.hasPlotsIn(command.chunk())) {
            throw new ChunkHasPlotsException(command.chunk());
        }
        TerritoryRegistry territory = aggregates.territory();
        territory.release(command.polity(), command.chunk(), command.day());
        aggregates.commit(territory);
    }
}

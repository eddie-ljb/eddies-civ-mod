package de.etiennebader.civ.application.territory;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Der Sieger eines Kriegs erobert Grenz-Chunks des Verlierers: nur Chunks, die an das Gebiet des Siegers grenzen,
 * keine mit Grundstücken (die blieben sonst außerhalb des Gebiets ihres Grundbuchs) und nicht der Chunk mit dem
 * Rathaus des Verlierers. Der Verlierer behält mindestens einen Chunk.
 */
public final class ConquerChunks {

    public record Command(PolityId winner, PolityId loser, int count, Day day) {

        public Command {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(loser, "loser");
            Objects.requireNonNull(day, "day");
            if (count < 0) {
                throw new IllegalArgumentException("Anzahl darf nicht negativ sein");
            }
        }
    }

    private final Aggregates aggregates;

    public ConquerChunks(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /** @return die tatsächlich eroberten Chunks (kann weniger als gefordert oder keiner sein) */
    public List<ChunkCoord> execute(Command command) {
        Polity loser = aggregates.polity(command.loser());
        aggregates.polity(command.winner());
        LandRegistry land = aggregates.land(command.loser());
        TerritoryRegistry territory = aggregates.territory();
        Optional<ChunkCoord> seatChunk = loser.seat().map(seat -> seat.chunk());
        List<ChunkCoord> conquered = new java.util.ArrayList<>();
        for (int i = 0; i < command.count(); i++) {
            java.util.Set<ChunkCoord> winnerLand = territory.territoryOf(command.winner());
            java.util.Set<ChunkCoord> loserLand = territory.territoryOf(command.loser());
            if (loserLand.size() <= 1) {
                break;
            }
            Optional<ChunkCoord> target = loserLand.stream()
                    .filter(chunk -> winnerLand.stream().anyMatch(chunk::isAdjacentTo))
                    .filter(chunk -> !land.hasPlotsIn(chunk))
                    .filter(chunk -> !seatChunk.equals(Optional.of(chunk)))
                    .min(Comparator.comparingInt(ChunkCoord::x).thenComparingInt(ChunkCoord::z));
            if (target.isEmpty()) {
                break;
            }
            territory.transfer(command.loser(), command.winner(), target.get(), command.day());
            conquered.add(target.get());
        }
        if (!conquered.isEmpty()) {
            aggregates.commit(territory);
        }
        return conquered;
    }
}

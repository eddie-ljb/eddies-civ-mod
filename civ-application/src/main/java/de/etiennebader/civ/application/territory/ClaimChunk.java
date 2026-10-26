package de.etiennebader.civ.application.territory;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.application.support.ClaimContexts;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.Objects;

/**
 * Eine Gemeinde beansprucht einen Chunk als Kerngebiet oder Außenposten. Die Regeln kommen
 * aus der Serverkonfiguration ({@link ClaimPolicy}) und dem geltenden Recht der Gemeinde.
 */
public final class ClaimChunk {

    public record Command(PolityId polity, ChunkCoord chunk, ClaimType type, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final ClaimPolicy claimPolicy;

    public ClaimChunk(Aggregates aggregates, ClaimPolicy claimPolicy) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.claimPolicy = Objects.requireNonNull(claimPolicy, "claimPolicy");
    }

    /**
     * @throws de.etiennebader.civ.domain.territory.ClaimRejectedException wenn der Claim abgelehnt wird
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        TerritoryRegistry territory = aggregates.territory();
        territory.claim(ClaimContexts.forPolity(polity, command.chunk(), command.type()), claimPolicy, command.day());
        aggregates.commit(territory);
    }
}

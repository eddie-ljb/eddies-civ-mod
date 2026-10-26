package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.application.support.ClaimContexts;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.Objects;

/**
 * Gründet eine Gemeinde mitsamt erstem Kern-Chunk (dort steht das Gemeindehaus) und leerem Grundbuch.
 * Schlägt der Claim fehl, entsteht keine Gemeinde.
 */
public final class FoundPolity {

    public record Command(PolityName name, ActorRef founder, CurrencyId currency, ChunkCoord firstChunk, Day day) {

        public Command {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(founder, "founder");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(firstChunk, "firstChunk");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;
    private final ClaimPolicy claimPolicy;
    private final MunicipalMembershipRule membershipRule;

    public FoundPolity(Aggregates aggregates, IdGenerator ids, ClaimPolicy claimPolicy) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.claimPolicy = Objects.requireNonNull(claimPolicy, "claimPolicy");
        this.membershipRule = new MunicipalMembershipRule(aggregates.repositories().polities());
    }

    /**
     * @return die ID der neuen Gemeinde
     * @throws MemberOfOtherPolityException wenn der Gründer bereits einer Gemeinde angehört
     * @throws de.etiennebader.civ.domain.territory.ClaimRejectedException wenn der Chunk nicht beansprucht werden kann
     */
    public PolityId execute(Command command) {
        membershipRule.requireNoMunicipality(command.founder());
        PolityId id = PolityId.generate(ids);
        Polity polity = Polity.found(id, command.name(), command.founder(), command.currency(), command.day());
        TerritoryRegistry territory = aggregates.territory();
        territory.claim(ClaimContexts.forPolity(polity, command.firstChunk(), ClaimType.CORE), claimPolicy, command.day());

        aggregates.commit(polity);
        aggregates.commit(territory);
        aggregates.commit(LandRegistry.open(id));
        return id;
    }
}

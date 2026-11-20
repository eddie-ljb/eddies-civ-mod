package de.etiennebader.civ.application.territory;

import de.etiennebader.civ.application.federation.Federations;
import de.etiennebader.civ.application.law.EffectiveLaws;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.application.support.ClaimContexts;
import de.etiennebader.civ.application.support.ClaimWeighting;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimRejectedException;
import de.etiennebader.civ.domain.territory.ClaimRejection;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.Objects;

/**
 * Eine Gemeinde beansprucht einen Chunk als Kerngebiet oder Außenposten. Die Regeln kommen
 * aus der Serverkonfiguration ({@link ClaimPolicy}) und dem geltenden Recht der Gemeinde.
 *
 * <p>Ist {@link StandardParameters#CLAIM_COST_PER_CHUNK} größer 0, kostet der Claim diesen Betrag
 * aus der Gemeindekasse ({@link Polity#asActor()}); reicht die Kasse nicht, wird er abgelehnt, bevor
 * die Hoheit über den Chunk übergeht.
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
    private final ClaimWeighting weighting;

    public ClaimChunk(Aggregates aggregates, ClaimPolicy claimPolicy) {
        this(aggregates, claimPolicy, ClaimWeighting.ALL_MEMBERS);
    }

    public ClaimChunk(Aggregates aggregates, ClaimPolicy claimPolicy, ClaimWeighting weighting) {
        this.weighting = Objects.requireNonNull(weighting, "weighting");
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.claimPolicy = Objects.requireNonNull(claimPolicy, "claimPolicy");
    }

    /**
     * @throws de.etiennebader.civ.domain.territory.ClaimRejectedException wenn der Claim abgelehnt wird
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        TerritoryRegistry territory = aggregates.territory();
        LawView laws = new EffectiveLaws(polity, Federations.ancestorsOf(aggregates.repositories().polities().findAll(),
                polity.id()));

        Money cost = Money.of(laws.valueOf(StandardParameters.CLAIM_COST_PER_CHUNK), polity.currency());
        Ledger ledger = aggregates.ledger();
        if (cost.isPositive()) {
            Money available = ledger.balanceOf(polity.asActor(), cost.currency());
            if (available.compareTo(cost) < 0) {
                throw new ClaimRejectedException(ClaimRejection.INSUFFICIENT_FUNDS, command.chunk(), cost.minus(available));
            }
        }

        territory.claim(ClaimContexts.forPolity(polity, laws, command.chunk(), command.type(), weighting), claimPolicy,
                command.day());
        aggregates.commit(territory);

        if (cost.isPositive()) {
            ledger.burn(polity.asActor(), cost, SupplyChange.TERRITORY_CLAIM, command.day());
            aggregates.commit(ledger);
        }
    }
}

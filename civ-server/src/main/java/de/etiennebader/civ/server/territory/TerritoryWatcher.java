package de.etiennebader.civ.server.territory;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Texts;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Merkt sich, in wessen Gebiet jeder Spieler zuletzt stand, und meldet Grenzübertritte.
 * Läuft auf dem Spielthread.
 */
public final class TerritoryWatcher {

    private final WorldQueries queries;
    private final Texts texts;
    private final Map<PlayerRef, Optional<PolityId>> lastOwner = new HashMap<>();

    public TerritoryWatcher(WorldQueries queries, Texts texts) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    /**
     * @return der Hinweis, wenn der Spieler in ein anderes Gebiet gewechselt ist; beim ersten Aufruf nach dem
     *         Betreten des Servers nur, wenn er nicht in der Wildnis steht
     */
    public Optional<TerritoryChange> moved(PlayerRef player, ChunkCoord chunk) {
        Objects.requireNonNull(player, "player");
        Optional<Claim> claim = queries.territory().claimAt(chunk);
        Optional<PolityId> owner = claim.map(Claim::polity);
        Optional<PolityId> previous = lastOwner.put(player, owner);
        boolean firstCheck = previous == null;
        if (owner.equals(previous) || (firstCheck && owner.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(describe(player, claim));
    }

    /** Vergisst den Spieler, z. B. wenn er den Server verlässt. */
    public void forget(PlayerRef player) {
        lastOwner.remove(player);
    }

    /** Wie ein Chunk zu einem Spieler steht. */
    public Relation relation(PlayerRef player, Optional<PolityId> owner) {
        if (owner.isEmpty()) {
            return Relation.WILDERNESS;
        }
        return queries.polity(owner.get()).filter(polity -> polity.isMember(player)).isPresent()
                ? Relation.OWN : Relation.FOREIGN;
    }

    private TerritoryChange describe(PlayerRef player, Optional<Claim> claim) {
        if (claim.isEmpty()) {
            return new TerritoryChange(Message.of(MessageKey.TERRITORY_WILDERNESS),
                    Message.of(MessageKey.TERRITORY_WILDERNESS_HINT), Relation.WILDERNESS);
        }
        PolityView polity = queries.polity(claim.get().polity()).orElseThrow();
        Relation relation = relation(player, claim.map(Claim::polity));
        Message subtitle = Message.of(relation == Relation.OWN ? MessageKey.TERRITORY_HOME : MessageKey.TERRITORY_FOREIGN,
                texts.tier(polity.tier()), texts.claimType(claim.get().type()));
        return new TerritoryChange(Message.of(MessageKey.TERRITORY_NAME, polity.name().value()), subtitle, relation);
    }
}

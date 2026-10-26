package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Weltweites Aggregat der Hoheit über Chunks.
 *
 * <p>Invariante: Jeder Chunk hat höchstens einen Hoheitsträger. Weil diese Regel alle
 * Polities zugleich betrifft, liegt die Hoheit nicht im {@code Polity}-Aggregat.
 */
public final class TerritoryRegistry extends AggregateRoot<TerritoryEvent> implements TerritoryView {

    private final Map<ChunkCoord, Claim> claims = new HashMap<>();

    private TerritoryRegistry() {
    }

    public static TerritoryRegistry empty() {
        return new TerritoryRegistry();
    }

    /**
     * Übernimmt die Hoheit über einen Chunk.
     *
     * <p>Dass ein Chunk nicht doppelt vergeben wird, prüft das Aggregat immer selbst; diese Regel
     * ist nicht konfigurierbar. Alle weiteren Regeln kommen über die {@code policy}.
     *
     * @throws ClaimRejectedException wenn der Chunk vergeben ist oder die Policy ablehnt
     */
    public void claim(ClaimContext context, ClaimPolicy policy, Day day) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(day, "day");
        ChunkCoord target = context.target();
        if (claims.containsKey(target)) {
            throw new ClaimRejectedException(ClaimRejection.ALREADY_CLAIMED, target);
        }
        Optional<ClaimRejection> rejection = policy.check(context, this);
        if (rejection.isPresent()) {
            throw new ClaimRejectedException(rejection.get(), target);
        }
        claims.put(target, new Claim(context.claimant(), context.type(), day));
        raise(new ChunkClaimed(context.claimant(), target, context.type(), day));
    }

    /**
     * Gibt die Hoheit über einen Chunk auf. Ob dort noch Grundstücke liegen, prüft der
     * Use Case, der beide Aggregate kennt.
     *
     * @throws NotSovereignException wenn die Polity dort keine Hoheit hat
     */
    public void release(PolityId polity, ChunkCoord chunk, Day day) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(day, "day");
        if (sovereignOf(chunk).filter(polity::equals).isEmpty()) {
            throw new NotSovereignException(chunk);
        }
        claims.remove(chunk);
        raise(new ChunkReleased(polity, chunk, day));
    }

    @Override
    public Optional<Claim> claimAt(ChunkCoord chunk) {
        return Optional.ofNullable(claims.get(Objects.requireNonNull(chunk, "chunk")));
    }

    @Override
    public Set<ChunkCoord> territoryOf(PolityId polity) {
        Objects.requireNonNull(polity, "polity");
        return claims.entrySet().stream()
                .filter(entry -> entry.getValue().polity().equals(polity))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int countOf(PolityId polity, ClaimType type) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(type, "type");
        return (int) claims.values().stream()
                .filter(claim -> claim.polity().equals(polity) && claim.type() == type)
                .count();
    }
}

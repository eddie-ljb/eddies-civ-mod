package de.etiennebader.civ.domain.territory;

import java.util.Optional;

/**
 * Kerngebiet muss zusammenhängend sein: Ein {@link ClaimType#CORE}-Claim muss an einen
 * bestehenden Kern-Chunk derselben Polity grenzen. Der erste Kern-Claim ist frei,
 * Außenposten sind ausgenommen.
 */
public final class AdjacencyClaimPolicy implements ClaimPolicy {

    @Override
    public Optional<ClaimRejection> check(ClaimContext context, TerritoryView territory) {
        if (context.type() != ClaimType.CORE || territory.countOf(context.claimant(), ClaimType.CORE) == 0) {
            return Optional.empty();
        }
        boolean bordersOwnCore = context.target().neighbours().stream()
                .flatMap(neighbour -> territory.claimAt(neighbour).stream())
                .anyMatch(claim -> claim.polity().equals(context.claimant()) && claim.type() == ClaimType.CORE);
        return bordersOwnCore ? Optional.empty() : Optional.of(ClaimRejection.NOT_ADJACENT);
    }
}

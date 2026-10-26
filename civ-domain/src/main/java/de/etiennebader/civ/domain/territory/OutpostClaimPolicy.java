package de.etiennebader.civ.domain.territory;

import java.util.Optional;

/**
 * Begrenzt die Zahl der Außenposten auf {@link ClaimContext#maxOutposts()}.
 */
public final class OutpostClaimPolicy implements ClaimPolicy {

    @Override
    public Optional<ClaimRejection> check(ClaimContext context, TerritoryView territory) {
        if (context.type() != ClaimType.OUTPOST) {
            return Optional.empty();
        }
        int outposts = territory.countOf(context.claimant(), ClaimType.OUTPOST);
        return outposts < context.maxOutposts()
                ? Optional.empty()
                : Optional.of(ClaimRejection.OUTPOST_LIMIT_REACHED);
    }
}

package de.etiennebader.civ.domain.territory;

import java.util.Optional;

/**
 * Begrenzt die Gebietsgröße nach Mitgliederzahl:
 * höchstens {@code base + perMember × memberCount} Chunks, Außenposten eingeschlossen.
 */
public final class ClaimLimitPolicy implements ClaimPolicy {

    private final int base;
    private final int perMember;

    public ClaimLimitPolicy(int base, int perMember) {
        if (base < 0 || perMember < 0) {
            throw new IllegalArgumentException("base und perMember dürfen nicht negativ sein");
        }
        this.base = base;
        this.perMember = perMember;
    }

    /** Wie viele Chunks eine Polity mit {@code memberCount} Mitgliedern höchstens besitzen darf. */
    public long limitFor(int memberCount) {
        return base + (long) perMember * memberCount;
    }

    @Override
    public Optional<ClaimRejection> check(ClaimContext context, TerritoryView territory) {
        int claimed = territory.territoryOf(context.claimant()).size();
        return claimed < limitFor(context.memberCount())
                ? Optional.empty()
                : Optional.of(ClaimRejection.LIMIT_REACHED);
    }
}

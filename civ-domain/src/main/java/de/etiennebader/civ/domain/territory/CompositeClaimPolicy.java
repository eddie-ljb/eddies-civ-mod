package de.etiennebader.civ.domain.territory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Kombiniert mehrere Policies. Sie werden in der angegebenen Reihenfolge geprüft,
 * die erste Ablehnung gewinnt.
 */
public final class CompositeClaimPolicy implements ClaimPolicy {

    private final List<ClaimPolicy> policies;

    public CompositeClaimPolicy(List<ClaimPolicy> policies) {
        this.policies = List.copyOf(Objects.requireNonNull(policies, "policies"));
    }

    public static CompositeClaimPolicy of(ClaimPolicy... policies) {
        return new CompositeClaimPolicy(List.of(policies));
    }

    /**
     * Die Standardregeln: zusammenhängendes Kerngebiet, begrenzte Außenposten und
     * Gebietsgröße nach Mitgliederzahl.
     */
    public static CompositeClaimPolicy standard(int baseLimit, int limitPerMember) {
        return of(new AdjacencyClaimPolicy(), new OutpostClaimPolicy(), new ClaimLimitPolicy(baseLimit, limitPerMember));
    }

    @Override
    public Optional<ClaimRejection> check(ClaimContext context, TerritoryView territory) {
        return policies.stream()
                .map(policy -> policy.check(context, territory))
                .flatMap(Optional::stream)
                .findFirst();
    }
}

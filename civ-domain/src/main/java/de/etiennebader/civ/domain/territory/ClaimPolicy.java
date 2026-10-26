package de.etiennebader.civ.domain.territory;

import java.util.Optional;

/**
 * Austauschbare Regel, ob ein Claim erlaubt ist. Welche Regeln gelten, legt die
 * Serverkonfiguration fest, z. B. über eine {@link CompositeClaimPolicy}.
 */
@FunctionalInterface
public interface ClaimPolicy {

    /** Eine Policy, die jeden Claim erlaubt. */
    ClaimPolicy ALLOW_ALL = (context, territory) -> Optional.empty();

    /**
     * @return der Ablehnungsgrund, oder leer, wenn der Claim erlaubt ist
     */
    Optional<ClaimRejection> check(ClaimContext context, TerritoryView territory);
}

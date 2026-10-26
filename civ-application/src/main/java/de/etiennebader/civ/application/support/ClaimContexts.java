package de.etiennebader.civ.application.support;

import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimContext;
import de.etiennebader.civ.domain.territory.ClaimType;

/**
 * Baut den {@link ClaimContext} aus Mitgliederzahl und geltendem Recht einer Polity.
 * Das Paket {@code territory} des Kerns kennt beides nicht, deshalb geschieht das hier.
 */
public final class ClaimContexts {

    private ClaimContexts() {
    }

    public static ClaimContext forPolity(Polity polity, ChunkCoord target, ClaimType type) {
        return new ClaimContext(polity.id(), type, target, polity.memberCount(),
                polity.laws().valueOf(StandardParameters.MAX_OUTPOSTS));
    }
}

package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.Optional;

/**
 * Lesender Zugriff auf alles, was die Schutzregeln brauchen.
 */
public interface ProtectionContext {

    TerritoryView territory();

    Optional<? extends PolityView> polity(PolityId id);

    Optional<? extends LandView> land(PolityId polity);
}

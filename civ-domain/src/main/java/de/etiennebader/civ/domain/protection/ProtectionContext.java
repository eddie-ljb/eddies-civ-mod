package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.law.LawView;
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

    /** Ob der Akteur ein Grundstück nutzen darf, ohne Inhaber zu sein, z. B. als Mieter. */
    default boolean mayUsePlot(ActorRef actor, PolityId polity, PlotId plot) {
        return false;
    }

    /** Ob der Akteur in dieser Polity nicht als Fremder gilt, obwohl er kein Mitglied ist, z. B. über ein Bündnis. */
    default boolean isAlly(ActorRef actor, PolityView polity) {
        return false;
    }

    /** Das Recht, das in dieser Polity gilt; übergeordnetes Bundesrecht kann es ergänzen. */
    default LawView laws(PolityView polity) {
        return polity.laws();
    }
}

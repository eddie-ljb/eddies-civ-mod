package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotOutsideTerritoryException;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Street;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;

import java.util.Objects;
import java.util.Optional;

/**
 * Straßen anlegen und aufheben: Bürgermeister oder Bauamt, nur im eigenen Gebiet.
 */
public final class StreetUseCases {

    private final Aggregates aggregates;

    public StreetUseCases(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return ob die Straße neu ist (sonst neu vermessen); {@code false} auch, wenn die Höchstzahl erreicht ist
     * @throws NotOfficeHolderException      wenn der Akteur weder Bürgermeister noch Bauamt ist
     * @throws PlotOutsideTerritoryException wenn die Fläche über das Gebiet hinausreicht
     */
    public boolean lay(ActorRef actor, PolityId town, String name, Area area, Day day) {
        Polity polity = aggregates.polity(town);
        requirePlanner(polity, actor);
        for (ChunkCoord chunk : area.chunks()) {
            Optional<PolityId> owner = aggregates.territory().claimAt(chunk).map(Claim::polity);
            if (owner.filter(town::equals).isEmpty()) {
                throw new PlotOutsideTerritoryException(chunk);
            }
        }
        boolean added = polity.layStreet(new Street(name, area), day);
        aggregates.commit(polity);
        return added;
    }

    /** @return ob es die Straße gab */
    public boolean remove(ActorRef actor, PolityId town, String name, Day day) {
        Polity polity = aggregates.polity(town);
        requirePlanner(polity, actor);
        boolean removed = polity.removeStreet(name, day);
        aggregates.commit(polity);
        return removed;
    }

    private static void requirePlanner(Polity polity, ActorRef actor) {
        if (!polity.holds(actor, Office.MAYOR) && !polity.holds(actor, Office.PLANNER)) {
            throw new NotOfficeHolderException(actor, Office.PLANNER);
        }
    }
}

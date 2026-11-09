package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.List;
import java.util.Objects;

/**
 * Löst eine Gemeinde auf.
 *
 * <ol>
 *     <li>Alle Grundstücke in ihrem Gebiet werden aufgelöst.</li>
 *     <li>Botschaften, die sie in anderen Gemeinden hält, fallen an die jeweilige Gemeinde zurück.</li>
 *     <li>Ihr Gebiet wird Wildnis.</li>
 *     <li>Das Geld der Gemeindekasse geht an den Erben, z. B. den Bürgermeister.</li>
 *     <li>Die Gemeinde verschwindet; ihre Mitglieder sind danach ohne Gemeinde.</li>
 * </ol>
 */
public final class DissolvePolity {

    public record Command(PolityId polity, ActorRef heir, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(heir, "heir");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public DissolvePolity(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return das Geld, das der Erbe aus der Kasse erhalten hat
     */
    public Money execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        PolityRef asActor = polity.asActor();
        Day day = command.day();

        LandRegistry land = aggregates.land(polity.id());
        for (Plot plot : List.copyOf(land.plots())) {
            land.dissolve(plot.id(), day);
        }
        aggregates.commit(land);

        for (Polity host : aggregates.repositories().polities().findAll()) {
            if (host.id().equals(polity.id())) {
                continue;
            }
            LandRegistry hostLand = aggregates.land(host.id());
            List<Plot> embassies = List.copyOf(hostLand.plotsHeldBy(asActor));
            for (Plot embassy : embassies) {
                hostLand.dissolve(embassy.id(), day);
            }
            if (!embassies.isEmpty()) {
                aggregates.commit(hostLand);
            }
        }

        TerritoryRegistry territory = aggregates.territory();
        for (ChunkCoord chunk : List.copyOf(territory.territoryOf(polity.id()))) {
            territory.release(polity.id(), chunk, day);
        }
        aggregates.commit(territory);

        Ledger ledger = aggregates.ledger();
        Money treasury = ledger.balanceOf(asActor, polity.currency());
        if (treasury.isPositive()) {
            ledger.transfer(asActor, command.heir(), treasury, TransferReason.GRANT, day);
            aggregates.commit(ledger);
        }

        polity.dissolve(day);
        aggregates.commit(polity);
        aggregates.repositories().polities().remove(polity.id());
        aggregates.repositories().land().remove(polity.id());
        return treasury;
    }
}

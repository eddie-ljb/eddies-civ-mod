package de.etiennebader.civ.application.civic;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.civic.Anthem;
import de.etiennebader.civ.domain.civic.Area;
import de.etiennebader.civ.domain.civic.CivicRegistry;
import de.etiennebader.civ.domain.civic.Constituency;
import de.etiennebader.civ.domain.civic.ConstituencyId;
import de.etiennebader.civ.domain.civic.Flag;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Wahlkreise anlegen und zuschneiden, Flagge und Hymne setzen. Das darf nur die Spitze der Polity
 * (Bürgermeister bzw. Vorsitz); Wahlkreise nicht, während eine Parlamentswahl läuft.
 */
public final class CivicUseCases {

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public CivicUseCases(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public Constituency define(ActorRef head, PolityId polity, String name, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        Constituency constituency = registry.define(ConstituencyId.generate(ids), polity, name, day);
        aggregates.commit(registry);
        return constituency;
    }

    public Constituency addArea(ActorRef head, PolityId polity, String name, Area area, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        Constituency updated = registry.addArea(require(registry, polity, name).id(), area, day);
        aggregates.commit(registry);
        return updated;
    }

    public Constituency addTown(ActorRef head, PolityId polity, String name, PolityId town, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        Constituency updated = registry.addTown(require(registry, polity, name).id(), town, day);
        aggregates.commit(registry);
        return updated;
    }

    public Constituency clear(ActorRef head, PolityId polity, String name, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        Constituency updated = registry.clear(require(registry, polity, name).id(), day);
        aggregates.commit(registry);
        return updated;
    }

    public void remove(ActorRef head, PolityId polity, String name, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        registry.remove(require(registry, polity, name).id(), day);
        aggregates.commit(registry);
    }

    public void setFlag(ActorRef head, PolityId polity, Optional<Flag> flag, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        registry.setFlag(polity, flag, day);
        aggregates.commit(registry);
    }

    public void setAnthem(ActorRef head, PolityId polity, Optional<Anthem> anthem, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        registry.setAnthem(polity, anthem, day);
        aggregates.commit(registry);
    }

    /** Aufgelöste Polity: ihre Wahlkreise und Hoheitszeichen verschwinden. */
    public void forget(PolityId polity, Day day) {
        CivicRegistry registry = aggregates.civic();
        registry.forget(polity, day);
        aggregates.commit(registry);
    }

    /** Ob der Akteur die Polity führt: Bürgermeister bzw. Vorsitz. */
    public static boolean isHead(PolityView polity, ActorRef actor) {
        return polity.holds(actor, Office.MAYOR) || polity.mayor().filter(actor::equals).isPresent();
    }

    private void requireReshapable(ActorRef head, PolityId id) {
        Polity polity = aggregates.polity(id);
        requireHead(head, polity);
        if (polity.electionFor(Office.PARLIAMENT).isPresent()) {
            throw new IllegalStateException("Während einer Parlamentswahl bleiben die Wahlkreise, wie sie sind");
        }
    }

    private static void requireHead(ActorRef head, PolityView polity) {
        if (!isHead(polity, head)) {
            throw new NotOfficeHolderException(head, Office.MAYOR);
        }
    }

    private static Constituency require(CivicRegistry registry, PolityId polity, String name) {
        return registry.constituencyNamed(polity, name)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Wahlkreis: " + name));
    }
}

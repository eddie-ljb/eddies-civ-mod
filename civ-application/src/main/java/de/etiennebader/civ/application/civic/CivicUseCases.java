package de.etiennebader.civ.application.civic;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.civic.Anthem;
import de.etiennebader.civ.domain.civic.Area;
import de.etiennebader.civ.domain.civic.CivicRegistry;
import de.etiennebader.civ.domain.civic.Constituency;
import de.etiennebader.civ.domain.civic.ConstituencyId;
import de.etiennebader.civ.domain.civic.CustomImage;
import de.etiennebader.civ.domain.civic.Flag;
import de.etiennebader.civ.domain.civic.HomePort;
import de.etiennebader.civ.domain.civic.PortAccess;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.civic.ImageKind;
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

    public Constituency removeArea(ActorRef head, PolityId polity, String name, Area area, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        Constituency updated = registry.removeArea(require(registry, polity, name).id(), area, day);
        aggregates.commit(registry);
        return updated;
    }

    public Constituency removeTown(ActorRef head, PolityId polity, String name, PolityId town, Day day) {
        requireReshapable(head, polity);
        CivicRegistry registry = aggregates.civic();
        Constituency updated = registry.removeTown(require(registry, polity, name).id(), town, day);
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

    /** Wer sich zu den Heimat-Häfen der Polity porten darf. Nur die Spitze der Polity. */
    public void setPortAccess(ActorRef head, PolityId polity, PortAccess access, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        registry.setPortSettings(polity, registry.portSettingsOf(polity).withAccess(access), day);
        aggregates.commit(registry);
    }

    /** Preis je Teleport zu den Heimat-Häfen der Polity. Nur die Spitze der Polity. */
    public void setPortFee(ActorRef head, PolityId polity, long fee, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        registry.setPortSettings(polity, registry.portSettingsOf(polity).withFee(fee), day);
        aggregates.commit(registry);
    }

    /** Setzt oder verlegt einen zusätzlichen Hafen (Slot 2 oder 3). Nur die Spitze der Polity. */
    public HomePort setPort(ActorRef head, PolityId polity, int slot, BlockPosition position, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        HomePort port = registry.setPort(polity, slot, position, day);
        aggregates.commit(registry);
        return port;
    }

    /** Entfernt einen zusätzlichen Hafen. Nur die Spitze der Polity. */
    public boolean removePort(ActorRef head, PolityId polity, int slot, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        boolean removed = registry.removePort(polity, slot, day).isPresent();
        aggregates.commit(registry);
        return removed;
    }

    /** Räumt einen Hafen ab, der nicht mehr auf dem Gebiet der Polity liegt (kein Rechtecheck, vom Server veranlasst). */
    public void dropPort(PolityId polity, int slot, Day day) {
        CivicRegistry registry = aggregates.civic();
        if (registry.removePort(polity, slot, day).isPresent()) {
            aggregates.commit(registry);
        }
    }

    public void setAnthem(ActorRef head, PolityId polity, Optional<Anthem> anthem, Day day) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        registry.setAnthem(polity, anthem, day);
        aggregates.commit(registry);
    }

    /**
     * Hinterlegt ein hochgeladenes Bild. Nur die Spitze der Polity darf das.
     *
     * @return die Bilder, die dadurch verdrängt wurden (bei einem Wappen-Wechsel das alte); der Aufrufer
     *         löscht deren Dateien
     */
    public CustomImage uploadImage(ActorRef head, PolityId polity, ImageKind kind, String hash, int width, int height,
                                   Day day, java.util.function.Consumer<CustomImage> onDisplaced) {
        requireHead(head, aggregates.polity(polity));
        CivicRegistry registry = aggregates.civic();
        String id = ids.next().toString();
        java.util.List<CustomImage> displaced = registry.addImage(id, polity, kind, hash, width, height, day);
        aggregates.commit(registry);
        displaced.forEach(onDisplaced);
        return registry.image(id).orElseThrow();
    }

    /**
     * Entfernt ein hochgeladenes Bild. Nur die Spitze seiner Polity darf das.
     *
     * @return das entfernte Bild, dessen Datei der Aufrufer löscht
     */
    public Optional<CustomImage> removeImage(ActorRef head, String id, Day day) {
        CivicRegistry registry = aggregates.civic();
        Optional<CustomImage> found = registry.image(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        requireHead(head, aggregates.polity(found.get().polity()));
        Optional<CustomImage> removed = registry.removeImage(id, day);
        aggregates.commit(registry);
        return removed;
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

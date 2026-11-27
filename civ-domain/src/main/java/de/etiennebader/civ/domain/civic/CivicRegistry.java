package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Register aller Wahlkreise und Hoheitszeichen (Flaggen, Hymnen). Klein genug, um als Ganzes gespeichert zu werden.
 */
public final class CivicRegistry extends AggregateRoot<CivicEvent> implements CivicView {

    public static final int MAX_CONSTITUENCIES = 30;

    private final List<Constituency> constituencies = new ArrayList<>();
    private final Map<PolityId, Flag> flags = new LinkedHashMap<>();
    private final Map<PolityId, Anthem> anthems = new LinkedHashMap<>();
    private final Map<String, CustomImage> images = new LinkedHashMap<>();
    private final Map<PolityId, PortSettings> portSettings = new LinkedHashMap<>();
    private final List<HomePort> ports = new ArrayList<>();
    private final Map<BlockPosition, Emblem> emblems = new LinkedHashMap<>();

    private CivicRegistry() {
    }

    public static CivicRegistry empty() {
        return new CivicRegistry();
    }

    public static CivicRegistry restore(Collection<Constituency> constituencies, Map<PolityId, Flag> flags,
                                        Map<PolityId, Anthem> anthems) {
        return restore(constituencies, flags, anthems, Map.of());
    }

    public static CivicRegistry restore(Collection<Constituency> constituencies, Map<PolityId, Flag> flags,
                                        Map<PolityId, Anthem> anthems, Map<String, CustomImage> images) {
        return restore(constituencies, flags, anthems, images, Map.of(), List.of());
    }

    public static CivicRegistry restore(Collection<Constituency> constituencies, Map<PolityId, Flag> flags,
                                        Map<PolityId, Anthem> anthems, Map<String, CustomImage> images,
                                        Map<PolityId, PortSettings> portSettings, Collection<HomePort> ports) {
        return restore(constituencies, flags, anthems, images, portSettings, ports, List.of());
    }

    public static CivicRegistry restore(Collection<Constituency> constituencies, Map<PolityId, Flag> flags,
                                        Map<PolityId, Anthem> anthems, Map<String, CustomImage> images,
                                        Map<PolityId, PortSettings> portSettings, Collection<HomePort> ports,
                                        Collection<Emblem> emblems) {
        CivicRegistry registry = new CivicRegistry();
        emblems.forEach(emblem -> registry.emblems.put(emblem.position(), emblem));
        registry.portSettings.putAll(portSettings);
        registry.ports.addAll(ports);
        registry.constituencies.addAll(constituencies);
        registry.flags.putAll(flags);
        registry.anthems.putAll(anthems);
        registry.images.putAll(images);
        return registry;
    }

    // ---- Wahlkreise ----

    /** Legt einen leeren Wahlkreis an. */
    public Constituency define(ConstituencyId id, PolityId polity, String name, Day day) {
        Objects.requireNonNull(day, "day");
        if (constituencyNamed(polity, name).isPresent()) {
            throw new IllegalArgumentException("Wahlkreis gibt es schon: " + name);
        }
        if (constituenciesOf(polity).size() >= MAX_CONSTITUENCIES) {
            throw new IllegalStateException("Höchstens " + MAX_CONSTITUENCIES + " Wahlkreise");
        }
        Constituency constituency = new Constituency(id, polity, name, List.of(), List.of());
        constituencies.add(constituency);
        raise(new CivicEvent.ConstituencyChanged(polity, id, false, day));
        return constituency;
    }

    /** Fügt einem Wahlkreis ein Gebiet hinzu. */
    public Constituency addArea(ConstituencyId id, Area area, Day day) {
        Constituency found = require(id);
        if (found.areas().size() >= Constituency.MAX_AREAS) {
            throw new IllegalStateException("Höchstens " + Constituency.MAX_AREAS + " Gebiete je Wahlkreis");
        }
        return replace(found.withArea(Objects.requireNonNull(area, "area")), day);
    }

    /** Ordnet einem Wahlkreis eine ganze Gemeinde zu und nimmt sie aus den anderen Wahlkreisen der Polity. */
    public Constituency addTown(ConstituencyId id, PolityId town, Day day) {
        Constituency found = require(id);
        for (Constituency other : constituenciesOf(found.polity())) {
            if (!other.id().equals(id) && other.includes(town)) {
                List<PolityId> rest = other.towns().stream().filter(member -> !member.equals(town)).toList();
                replace(new Constituency(other.id(), other.polity(), other.name(), other.areas(), rest), day);
            }
        }
        return replace(found.withTown(Objects.requireNonNull(town, "town")), day);
    }

    /** Entfernt Gebiete und Gemeinden eines Wahlkreises. */
    public Constituency clear(ConstituencyId id, Day day) {
        return replace(require(id).cleared(), day);
    }

    /**
     * Nimmt ein einzelnes Gebiet aus einem Wahlkreis heraus, ohne die übrigen Gebiete oder Gemeinden
     * anzutasten (anders als {@link #clear}). So lassen sich Wahlkreise gezielt neu zuschneiden.
     */
    public Constituency removeArea(ConstituencyId id, Area area, Day day) {
        Objects.requireNonNull(area, "area");
        Constituency found = require(id);
        if (!found.hasArea(area)) {
            throw new IllegalArgumentException("Dieses Gebiet gehört nicht zum Wahlkreis: " + found.name());
        }
        return replace(found.withoutArea(area), day);
    }

    /** Nimmt eine einzelne Gemeinde aus einem Wahlkreis heraus, ohne die übrigen Gebiete/Gemeinden anzutasten. */
    public Constituency removeTown(ConstituencyId id, PolityId town, Day day) {
        Objects.requireNonNull(town, "town");
        Constituency found = require(id);
        if (!found.includes(town)) {
            throw new IllegalArgumentException("Diese Gemeinde gehört nicht zum Wahlkreis: " + found.name());
        }
        return replace(found.withoutTown(town), day);
    }

    /** Löst einen Wahlkreis auf. @return ob es ihn gab */
    public boolean remove(ConstituencyId id, Day day) {
        Optional<Constituency> found = constituency(id);
        if (found.isEmpty()) {
            return false;
        }
        constituencies.remove(found.get());
        raise(new CivicEvent.ConstituencyChanged(found.get().polity(), id, true, day));
        return true;
    }

    /** Vergisst alles, was zu einer aufgelösten Polity gehört. */
    public void forget(PolityId polity, Day day) {
        List<Constituency> own = constituenciesOf(polity);
        own.forEach(constituency -> remove(constituency.id(), day));
        boolean symbols = flags.remove(polity) != null;
        symbols |= anthems.remove(polity) != null;
        List<String> ownImages = imagesOf(polity).stream().map(CustomImage::id).toList();
        ownImages.forEach(images::remove);
        symbols |= !ownImages.isEmpty();
        symbols |= portSettings.remove(polity) != null;
        symbols |= ports.removeIf(port -> port.polity().equals(polity));
        if (symbols) {
            raise(new CivicEvent.SymbolChanged(polity, "all", day));
        }
    }

    private Constituency require(ConstituencyId id) {
        return constituency(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Wahlkreis: " + id));
    }

    private Constituency replace(Constituency updated, Day day) {
        Objects.requireNonNull(day, "day");
        for (int index = 0; index < constituencies.size(); index++) {
            if (constituencies.get(index).id().equals(updated.id())) {
                constituencies.set(index, updated);
            }
        }
        raise(new CivicEvent.ConstituencyChanged(updated.polity(), updated.id(), false, day));
        return updated;
    }

    // ---- Hoheitszeichen ----

    public void setFlag(PolityId polity, Optional<Flag> flag, Day day) {
        Objects.requireNonNull(polity, "polity");
        flag.ifPresentOrElse(set -> flags.put(polity, set), () -> flags.remove(polity));
        raise(new CivicEvent.SymbolChanged(polity, "flag", day));
    }

    public void setAnthem(PolityId polity, Optional<Anthem> anthem, Day day) {
        Objects.requireNonNull(polity, "polity");
        anthem.ifPresentOrElse(set -> anthems.put(polity, set), () -> anthems.remove(polity));
        raise(new CivicEvent.SymbolChanged(polity, "anthem", day));
    }

    /**
     * Hinterlegt ein hochgeladenes Bild. Ein neues Wappen ersetzt ein etwaiges altes; von anderen Arten
     * (Wahlplakat, Leuchtreklame) sind bis zu {@link CustomImage#MAX_PER_KIND} gleichzeitig erlaubt.
     *
     * @return die bei einem Wappen-Wechsel verdrängten Bilder, deren Dateien der Aufrufer aufräumen sollte
     */
    public List<CustomImage> addImage(String id, PolityId polity, ImageKind kind, String hash, int width, int height,
                                      Day day) {
        Objects.requireNonNull(day, "day");
        List<CustomImage> displaced;
        if (kind == ImageKind.WAPPEN) {
            displaced = imagesOf(polity, kind);
            displaced.forEach(image -> images.remove(image.id()));
        } else {
            List<CustomImage> existing = imagesOf(polity, kind);
            if (existing.size() >= CustomImage.MAX_PER_KIND) {
                throw new IllegalStateException(
                        "Höchstens " + CustomImage.MAX_PER_KIND + " Bilder der Art " + kind + " je Polity");
            }
            displaced = List.of();
        }
        images.put(id, new CustomImage(id, polity, kind, hash, width, height, day));
        raise(new CivicEvent.SymbolChanged(polity, "image:" + kind.name().toLowerCase(java.util.Locale.ROOT), day));
        return displaced;
    }

    /** @return das entfernte Bild, falls es die angegebene Kennung gab */
    public Optional<CustomImage> removeImage(String id, Day day) {
        CustomImage removed = images.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        raise(new CivicEvent.SymbolChanged(removed.polity(),
                "image:" + removed.kind().name().toLowerCase(java.util.Locale.ROOT), day));
        return Optional.of(removed);
    }

    // ---- Heimat-Häfen ----

    public void setPortSettings(PolityId polity, PortSettings settings, Day day) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(settings, "settings");
        if (settings.equals(PortSettings.DEFAULT)) {
            portSettings.remove(polity);
        } else {
            portSettings.put(polity, settings);
        }
        raise(new CivicEvent.SymbolChanged(polity, "port-settings", day));
    }

    /** Setzt (oder verlegt) einen zusätzlichen Hafen; ein belegter Slot wird überschrieben. */
    public HomePort setPort(PolityId polity, int slot, BlockPosition position, Day day) {
        HomePort port = new HomePort(polity, slot, position);
        ports.removeIf(existing -> existing.polity().equals(polity) && existing.slot() == slot);
        ports.add(port);
        raise(new CivicEvent.SymbolChanged(polity, "port:" + slot, day));
        return port;
    }

    public Optional<HomePort> removePort(PolityId polity, int slot, Day day) {
        Optional<HomePort> found = port(polity, slot);
        found.ifPresent(port -> {
            ports.remove(port);
            raise(new CivicEvent.SymbolChanged(polity, "port:" + slot, day));
        });
        return found;
    }

    @Override
    public Map<PolityId, PortSettings> portSettings() {
        return Map.copyOf(portSettings);
    }

    @Override
    public List<HomePort> ports() {
        return List.copyOf(ports);
    }

    @Override
    public List<Constituency> constituencies() {
        return List.copyOf(constituencies);
    }

    @Override
    public Map<PolityId, Flag> flags() {
        return Map.copyOf(flags);
    }

    @Override
    public Map<PolityId, Anthem> anthems() {
        return Map.copyOf(anthems);
    }

    @Override
    public Map<String, CustomImage> images() {
        return Map.copyOf(images);
    }

    // ---- Hoheitszeichen (Flaggen, Wappen als Blöcke) ----

    /**
     * Stellt ein Hoheitszeichen auf; eines am selben Ort wird ersetzt.
     *
     * @return ob es aufgestellt wurde; nicht, wenn die Polity schon {@link Emblem#MAX_PER_POLITY} hat
     */
    public boolean placeEmblem(Emblem emblem) {
        Objects.requireNonNull(emblem, "emblem");
        boolean replaces = emblems.containsKey(emblem.position());
        if (!replaces && emblemsOf(emblem.polity()).size() >= Emblem.MAX_PER_POLITY) {
            return false;
        }
        emblems.put(emblem.position(), emblem);
        return true;
    }

    /** Entfernt das Hoheitszeichen an dieser Stelle. @return ob dort eines stand */
    public boolean removeEmblem(BlockPosition position) {
        return emblems.remove(Objects.requireNonNull(position, "position")) != null;
    }

    @Override
    public List<Emblem> emblems() {
        return List.copyOf(emblems.values());
    }
}

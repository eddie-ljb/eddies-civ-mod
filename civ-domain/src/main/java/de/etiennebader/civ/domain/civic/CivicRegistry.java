package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

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
        CivicRegistry registry = new CivicRegistry();
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
}

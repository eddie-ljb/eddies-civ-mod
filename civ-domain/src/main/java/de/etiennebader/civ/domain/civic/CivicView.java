package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lesender Zugriff auf Wahlkreise und Hoheitszeichen.
 */
public interface CivicView {

    /** Alle Wahlkreise, nach Polity und Anlage geordnet. */
    List<Constituency> constituencies();

    default List<Constituency> constituenciesOf(PolityId polity) {
        return constituencies().stream().filter(constituency -> constituency.polity().equals(polity)).toList();
    }

    default Optional<Constituency> constituency(ConstituencyId id) {
        return constituencies().stream().filter(constituency -> constituency.id().equals(id)).findFirst();
    }

    /** Ein Wahlkreis über seinen Bezirksschlüssel aus einem Wahlergebnis. */
    default Optional<Constituency> byDistrict(PolityId district) {
        return constituency(new ConstituencyId(district.value()));
    }

    default Optional<Constituency> constituencyNamed(PolityId polity, String name) {
        return constituenciesOf(polity).stream().filter(constituency -> constituency.name().equalsIgnoreCase(name.strip()))
                .findFirst();
    }

    /**
     * Der Wahlkreis eines Wählers bei Wahlen der Polity: zuerst nach Wohnsitz im Gebiet, dann nach seiner Gemeinde.
     */
    default Optional<Constituency> constituencyFor(PolityId polity, Optional<BlockPosition> residence,
                                                   Optional<PolityId> town) {
        List<Constituency> own = constituenciesOf(polity);
        if (residence.isPresent()) {
            Optional<Constituency> byArea = own.stream().filter(found -> found.contains(residence.get())).findFirst();
            if (byArea.isPresent()) {
                return byArea;
            }
        }
        return town.flatMap(home -> own.stream().filter(found -> found.includes(home)).findFirst());
    }

    Map<PolityId, Flag> flags();

    Map<PolityId, Anthem> anthems();

    /** Hochgeladene Bilder (Wappen, Wahlplakat, Leuchtreklame), nach Kennung. */
    Map<String, CustomImage> images();

    /** Aufgestellte Flaggen- und Wappen-Blöcke. */
    default List<Emblem> emblems() {
        return List.of();
    }

    default List<Emblem> emblemsOf(PolityId polity) {
        return emblems().stream().filter(emblem -> emblem.polity().equals(polity)).toList();
    }

    /** Wie stark Flaggen und Wappen die Treue der Dorfbewohner zu dieser Polity heben (0 bis {@link Emblem#LOYALTY_CAP}). */
    default int emblemLoyalty(PolityId polity) {
        return Emblem.loyaltyBonus(emblemsOf(polity));
    }

    default Optional<Flag> flagOf(PolityId polity) {
        return Optional.ofNullable(flags().get(polity));
    }

    default Optional<Anthem> anthemOf(PolityId polity) {
        return Optional.ofNullable(anthems().get(polity));
    }

    default Optional<CustomImage> image(String id) {
        return Optional.ofNullable(images().get(id));
    }

    default List<CustomImage> imagesOf(PolityId polity) {
        return images().values().stream().filter(image -> image.polity().equals(polity)).toList();
    }

    default List<CustomImage> imagesOf(PolityId polity, ImageKind kind) {
        return imagesOf(polity).stream().filter(image -> image.kind() == kind).toList();
    }

    /** Zugangsregeln der Heimat-Häfen; Polities ohne Eintrag haben {@link PortSettings#DEFAULT}. */
    Map<PolityId, PortSettings> portSettings();

    /** Alle zusätzlichen Heimat-Häfen (Slot 2 und 3) aller Polities. */
    List<HomePort> ports();

    default PortSettings portSettingsOf(PolityId polity) {
        return portSettings().getOrDefault(polity, PortSettings.DEFAULT);
    }

    default List<HomePort> portsOf(PolityId polity) {
        return ports().stream().filter(port -> port.polity().equals(polity))
                .sorted(java.util.Comparator.comparingInt(HomePort::slot)).toList();
    }

    default Optional<HomePort> port(PolityId polity, int slot) {
        return ports().stream().filter(port -> port.polity().equals(polity) && port.slot() == slot).findFirst();
    }
}

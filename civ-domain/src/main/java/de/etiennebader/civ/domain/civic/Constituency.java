package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.List;
import java.util.Objects;

/**
 * Ein Wahlkreis einer Polity, unabhängig von Gemeindegrenzen: aus Gebieten (Rechtecken) und/oder ganzen Gemeinden.
 *
 * <p>Ein Wähler gehört zu dem Wahlkreis, in dessen Gebiet sein Wohnsitz liegt; sonst zu dem, der seine Gemeinde
 * aufzählt.
 *
 * @param polity die Polity, deren Wahlen der Wahlkreis gliedert
 * @param towns  Gemeinden, die ganz zum Wahlkreis gehören
 */
public record Constituency(ConstituencyId id, PolityId polity, String name, List<Area> areas, List<PolityId> towns) {

    public static final int MAX_NAME = 32;
    public static final int MAX_AREAS = 16;

    public Constituency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(polity, "polity");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Name des Wahlkreises leer oder zu lang: " + name);
        }
        areas = List.copyOf(areas);
        towns = List.copyOf(towns);
    }

    public boolean contains(BlockPosition position) {
        return areas.stream().anyMatch(area -> area.contains(position));
    }

    public boolean includes(PolityId town) {
        return towns.contains(town);
    }

    Constituency withArea(Area area) {
        List<Area> updated = new java.util.ArrayList<>(areas);
        updated.add(area);
        return new Constituency(id, polity, name, updated, towns);
    }

    Constituency withTown(PolityId town) {
        List<PolityId> updated = new java.util.ArrayList<>(towns);
        if (!updated.contains(town)) {
            updated.add(town);
        }
        return new Constituency(id, polity, name, areas, updated);
    }

    /** Ob dieses Gebiet (exakt, gleiche Grenzen) Teil des Wahlkreises ist. */
    boolean hasArea(Area area) {
        return areas.contains(area);
    }

    Constituency withoutArea(Area area) {
        List<Area> updated = areas.stream().filter(existing -> !existing.equals(area)).toList();
        return new Constituency(id, polity, name, updated, towns);
    }

    Constituency withoutTown(PolityId town) {
        List<PolityId> updated = towns.stream().filter(existing -> !existing.equals(town)).toList();
        return new Constituency(id, polity, name, areas, updated);
    }

    Constituency cleared() {
        return new Constituency(id, polity, name, List.of(), List.of());
    }
}

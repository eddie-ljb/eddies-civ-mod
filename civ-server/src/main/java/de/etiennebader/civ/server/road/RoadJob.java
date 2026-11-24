package de.etiennebader.civ.server.road;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Ein Wegebau auf einer Straße.
 *
 * @param material womit gebaut wird: {@link Material#PATH} mit der Schaufel (kostenlos, langsam) oder
 *                 {@link Material#PAVING} mit Pflastersteinen (kostet die Gemeindekasse, schnell)
 * @param done     Zahl der bereits gelegten Wegfelder, in der Reihenfolge von {@link #cell}
 * @param paid     bisher bezahlt
 */
public record RoadJob(UUID id, PolityId town, String street, Area area, Material material, int done, long paid,
                      boolean finished) {

    /** Der Belag. */
    public enum Material {
        /** Mit der Schaufel zum Trampelpfad geplättet: kostet nichts, geht langsam. */
        PATH,
        /** Gepflastert mit Steinen: kostet Geld aus der Kasse, geht schnell. */
        PAVING
    }

    public RoadJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(material, "material");
    }

    /** Zahl der Wegfelder. */
    public int total() {
        return (int) Math.min(Integer.MAX_VALUE, area.width() * area.depth());
    }

    /** Das Feld Nummer {@code index}: die Straße wird Reihe für Reihe entlang ihrer langen Seite gelegt. */
    public int[] cell(int index) {
        boolean alongX = area.width() >= area.depth();
        int length = (int) (alongX ? area.width() : area.depth());
        int along = index % length;
        int across = index / length;
        return alongX ? new int[] {area.minX() + along, area.minZ() + across}
                : new int[] {area.minX() + across, area.minZ() + along};
    }

    RoadJob progressed(int count, long payment) {
        int next = Math.min(total(), done + count);
        return new RoadJob(id, town, street, area, material, next, paid + payment, next >= total());
    }
}

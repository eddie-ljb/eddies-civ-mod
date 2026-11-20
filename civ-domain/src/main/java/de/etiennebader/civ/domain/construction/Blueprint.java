package de.etiennebader.civ.domain.construction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ein Bauplan: eine Liste von Blöcken relativ zu einem Ursprung, den ein Bauarbeiter-Dorfbewohner nachbaut (#19).
 * Eigene Baupläne lassen sich derzeit nur serverseitig als weiterer Eintrag hinzufügen (siehe
 * {@link PredefinedBlueprints}); ein Editor über Webseite oder Ingame ist bewusst nicht Teil dieser Umsetzung
 * (siehe docs/issue-19-bauarbeiter-villager.md).
 *
 * @param id     Kennung, z. B. {@code huette}
 * @param name   Anzeigename
 * @param blocks Blöcke relativ zum Ursprung; Platzierungen auf derselben Position überschreiben sich in Reihenfolge
 */
public record Blueprint(String id, String name, List<BlockPlacement> blocks) {

    public static final int MAX_BLOCKS = 2000;

    public Blueprint {
        id = Objects.requireNonNull(id, "id").strip();
        name = Objects.requireNonNull(name, "name").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Bauplan-Kennung darf nicht leer sein");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Bauplan-Name darf nicht leer sein");
        }
        Objects.requireNonNull(blocks, "blocks");
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Bauplan ohne Blöcke: " + id);
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Bauplan zu groß (" + blocks.size() + " > " + MAX_BLOCKS + "): " + id);
        }
        blocks = List.copyOf(blocks);
    }

    /** Wie viele Blöcke dieser Bauplan insgesamt braucht. */
    public int totalBlocks() {
        return blocks.size();
    }

    /** Materialbedarf: je Block-Kennung die benötigte Menge, in der Reihenfolge des ersten Auftretens. */
    public Map<String, Long> materials() {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (BlockPlacement placement : blocks) {
            totals.merge(placement.block(), 1L, Long::sum);
        }
        return totals;
    }
}

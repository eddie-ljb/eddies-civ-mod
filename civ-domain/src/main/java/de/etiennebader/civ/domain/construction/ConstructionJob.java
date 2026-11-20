package de.etiennebader.civ.domain.construction;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.List;
import java.util.Objects;

/**
 * Ein laufender oder abgeschlossener Bauauftrag: ein Bauarbeiter-Dorfbewohner baut einen {@link Blueprint} an einem
 * Ursprung nach, einen festen Vorrat an Blöcken pro Tag (#19).
 *
 * @param placed wie viele Blöcke des Bauplans schon gesetzt sind, in dessen Reihenfolge
 */
public record ConstructionJob(ConstructionJobId id, PolityId company, ActorRef worker, String blueprintId,
                              BlockPosition origin, int placed, Day startedOn) {

    public ConstructionJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(worker, "worker");
        blueprintId = Objects.requireNonNull(blueprintId, "blueprintId").strip();
        if (blueprintId.isEmpty()) {
            throw new IllegalArgumentException("blueprintId darf nicht leer sein");
        }
        Objects.requireNonNull(origin, "origin");
        if (placed < 0) {
            throw new IllegalArgumentException("placed darf nicht negativ sein: " + placed);
        }
        Objects.requireNonNull(startedOn, "startedOn");
    }

    public static ConstructionJob start(ConstructionJobId id, PolityId company, ActorRef worker, Blueprint blueprint,
                                        BlockPosition origin, Day today) {
        return new ConstructionJob(id, company, worker, blueprint.id(), origin, 0, today);
    }

    public boolean done(Blueprint blueprint) {
        return placed >= blueprint.totalBlocks();
    }

    /** Wie viele Blöcke noch fehlen. */
    public int remaining(Blueprint blueprint) {
        return Math.max(0, blueprint.totalBlocks() - placed);
    }

    /**
     * Die nächsten Blöcke, absolut in der Welt, die heute gesetzt werden sollen (höchstens {@code capacity} viele,
     * weniger falls der Bauplan vorher fertig ist). Setzt den Fortschritt nicht selbst fort, siehe {@link #advanced}.
     */
    public List<AbsolutePlacement> next(Blueprint blueprint, int capacity) {
        if (!blueprint.id().equals(blueprintId)) {
            throw new IllegalArgumentException("Bauplan passt nicht zum Auftrag: " + blueprint.id() + " != " + blueprintId);
        }
        int upTo = Math.min(blueprint.totalBlocks(), placed + Math.max(0, capacity));
        return blueprint.blocks().subList(placed, upTo).stream()
                .map(block -> new AbsolutePlacement(
                        new BlockPosition(origin.dimension(), origin.x() + block.dx(), origin.y() + block.dy(),
                                origin.z() + block.dz()),
                        block.block()))
                .toList();
    }

    /** Fortschritt um {@code count} weitere Blöcke, höchstens bis der Bauplan fertig ist. */
    public ConstructionJob advanced(Blueprint blueprint, int count) {
        int next = Math.min(blueprint.totalBlocks(), placed + Math.max(0, count));
        return new ConstructionJob(id, company, worker, blueprintId, origin, next, startedOn);
    }

    /** Ein Block eines Bauauftrags an seiner tatsächlichen Position in der Welt. */
    public record AbsolutePlacement(BlockPosition position, String block) {
    }
}

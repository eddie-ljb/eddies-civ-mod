package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;

import java.util.Collection;
import java.util.Optional;

/**
 * Lesender Zugriff auf das Grundbuch einer Polity.
 */
public interface LandView {

    /** Die Polity, deren Grundbuch dies ist. */
    PolityId owner();

    Optional<Plot> plotAt(DimensionId dimension, int x, int z);

    /** Alle Grundstücke in Anlagereihenfolge. */
    Collection<Plot> plots();

    Collection<Plot> plotsHeldBy(ActorRef holder);

    /** Ob mindestens ein Grundstück den Chunk berührt. */
    boolean hasPlotsIn(ChunkCoord chunk);
}

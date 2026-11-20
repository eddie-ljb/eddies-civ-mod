package de.etiennebader.civ.domain.religion;

import de.etiennebader.civ.domain.actor.ActorRef;

import java.util.List;
import java.util.Optional;

/**
 * Lesender Zugriff auf alle Religionen.
 */
public interface ReligionView {

    /** Alle Religionen, nach Gründung geordnet. */
    List<Religion> religions();

    default Optional<Religion> religion(ReligionId id) {
        return religions().stream().filter(religion -> religion.id().equals(id)).findFirst();
    }

    default Optional<Religion> religionNamed(String name) {
        return religions().stream().filter(religion -> religion.name().equalsIgnoreCase(name.strip())).findFirst();
    }

    /** Alle Religionen, deren Klerus-Oberhaupt dieser Akteur ist. */
    default List<Religion> religionsLedBy(ActorRef actor) {
        return religions().stream().filter(religion -> religion.clergyHead().filter(actor::equals).isPresent()).toList();
    }
}

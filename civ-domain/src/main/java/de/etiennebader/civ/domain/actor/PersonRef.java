package de.etiennebader.civ.domain.actor;

import de.etiennebader.civ.domain.shared.PersonId;

import java.util.Objects;
import java.util.UUID;

/**
 * Ein Villager-Individuum. In Phase 0 existiert ein Villager nur als {@link PersonId};
 * das Aggregat {@code Person} kommt mit der Bevölkerungssimulation.
 */
public record PersonRef(PersonId id) implements ActorRef {

    public PersonRef {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public UUID uuid() {
        return id.value();
    }
}

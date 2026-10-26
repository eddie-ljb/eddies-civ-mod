package de.etiennebader.civ.domain.actor;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Eine Polity, die als Ganzes handelt, z. B. als Steuerempfänger oder Grundbesitzer.
 */
public record PolityRef(PolityId id) implements ActorRef {

    public PolityRef {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public UUID uuid() {
        return id.value();
    }
}

package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId generate(IdGenerator ids) {
        return new OrderId(ids.next());
    }
}

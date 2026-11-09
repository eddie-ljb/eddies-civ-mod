package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.InvalidValueException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Eine handelbare Ware, bezeichnet wie in Minecraft, z. B. {@code minecraft:oak_log}.
 */
public record Commodity(String id) implements Comparable<Commodity> {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public Commodity {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new InvalidValueException(ErrorCode.INVALID_COMMODITY, id);
        }
    }

    public static Commodity of(String id) {
        return new Commodity(id);
    }

    @Override
    public int compareTo(Commodity other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id;
    }
}

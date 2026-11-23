package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Eine Maschine einer Firma.
 *
 * @param level Ausbaustufe, 1 bis {@link MachineType#MAX_LEVEL}
 * @param wear  Verschleiß, 0 bis {@link MachineType#BROKEN}; bei {@link MachineType#BROKEN} steht sie still
 * @param where wo der Block steht; leer, solange er noch nicht aufgestellt ist
 */
public record Machine(UUID id, PolityId company, MachineType type, int level, int wear, Optional<MachinePos> where) {

    public Machine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(where, "where");
        level = Math.max(1, Math.min(MachineType.MAX_LEVEL, level));
        wear = Math.max(0, Math.min(MachineType.BROKEN, wear));
    }

    public static Machine bought(UUID id, PolityId company, MachineType type) {
        return new Machine(id, company, type, 1, 0, Optional.empty());
    }

    public boolean broken() {
        return wear >= MachineType.BROKEN;
    }

    public Machine upgraded() {
        return new Machine(id, company, type, level + 1, wear, where);
    }

    public Machine worn(int by) {
        return new Machine(id, company, type, level, wear + by, where);
    }

    public Machine maintained() {
        return new Machine(id, company, type, level, 0, where);
    }

    public Machine placedAt(Optional<MachinePos> position) {
        return new Machine(id, company, type, level, wear, position);
    }

    /** Ertragsbeitrag in Prozent: 0 bei Stillstand. */
    public int contributionPercent(boolean running) {
        return running && !broken() ? type.outputPercent() * level : 0;
    }
}

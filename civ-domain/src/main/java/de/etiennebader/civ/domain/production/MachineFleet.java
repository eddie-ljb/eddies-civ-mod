package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Alle Maschinen aller Firmen. */
public final class MachineFleet {

    private final Map<UUID, Machine> machines = new LinkedHashMap<>();

    public static MachineFleet restore(Collection<Machine> stored) {
        MachineFleet fleet = new MachineFleet();
        stored.forEach(machine -> fleet.machines.put(machine.id(), machine));
        return fleet;
    }

    public Collection<Machine> all() {
        return List.copyOf(machines.values());
    }

    /** Die Maschinen einer Firma in fester Reihenfolge (Nummer = Index + 1). */
    public List<Machine> of(PolityId company) {
        return machines.values().stream().filter(machine -> machine.company().equals(company))
                .sorted(Comparator.comparing((Machine machine) -> machine.type().ordinal())
                        .thenComparing(machine -> machine.id())).toList();
    }

    public Optional<Machine> find(UUID id) {
        return Optional.ofNullable(machines.get(id));
    }

    public Optional<Machine> at(MachinePos position) {
        return machines.values().stream().filter(machine -> machine.where().filter(position::equals).isPresent())
                .findFirst();
    }

    /** @throws IllegalStateException wenn die Firma schon {@link MachineType#MAX_PER_COMPANY} Maschinen hat */
    public Machine buy(UUID id, PolityId company, MachineType type) {
        if (of(company).size() >= MachineType.MAX_PER_COMPANY) {
            throw new IllegalStateException("Höchstens " + MachineType.MAX_PER_COMPANY + " Maschinen je Firma");
        }
        Machine machine = Machine.bought(id, company, type);
        machines.put(id, machine);
        return machine;
    }

    public Machine upgrade(UUID id) {
        Machine machine = require(id);
        if (machine.level() >= MachineType.MAX_LEVEL) {
            throw new IllegalStateException("Höchste Ausbaustufe erreicht");
        }
        return put(machine.upgraded());
    }

    public Machine maintain(UUID id) {
        return put(require(id).maintained());
    }

    public Machine place(UUID id, MachinePos position) {
        return put(require(id).placedAt(Optional.of(Objects.requireNonNull(position, "position"))));
    }

    public Optional<Machine> unplaceAt(MachinePos position) {
        return at(position).map(machine -> put(machine.placedAt(Optional.empty())));
    }

    public void removeCompany(PolityId company) {
        machines.values().removeIf(machine -> machine.company().equals(company));
    }

    /** Täglicher Verschleiß der Maschinen, die laufen. @return Maschinen, die dabei ausgefallen sind */
    public List<Machine> wearDay(PolityId company, Predicate<Machine> running) {
        List<Machine> broken = new ArrayList<>();
        for (Machine machine : of(company)) {
            if (!machine.broken() && running.test(machine)) {
                Machine worn = put(machine.worn(machine.type().wearPerDay()));
                if (worn.broken()) {
                    broken.add(worn);
                }
            }
        }
        return broken;
    }

    /** Ertragsfaktor der Firma: 1 + Summe der Beiträge laufender, intakter Maschinen. */
    public double factor(PolityId company, Predicate<Machine> running) {
        int percent = of(company).stream().mapToInt(machine -> machine.contributionPercent(running.test(machine))).sum();
        return 1 + percent / 100.0;
    }

    private Machine require(UUID id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unbekannte Maschine"));
    }

    private Machine put(Machine machine) {
        machines.put(machine.id(), machine);
        return machine;
    }
}

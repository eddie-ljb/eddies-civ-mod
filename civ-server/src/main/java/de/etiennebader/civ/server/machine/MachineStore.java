package de.etiennebader.civ.server.machine;

import de.etiennebader.civ.domain.production.Machine;
import de.etiennebader.civ.domain.production.MachineFleet;
import de.etiennebader.civ.domain.production.MachinePos;
import de.etiennebader.civ.domain.production.MachineType;
import de.etiennebader.civ.domain.shared.PolityId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Speichert die Maschinen der Firmen als Textdatei (tabulatorgetrennt), atomar ersetzt. */
final class MachineStore {

    private final Optional<Path> file;

    MachineStore(Optional<Path> file) {
        this.file = file;
    }

    MachineFleet load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return new MachineFleet();
        }
        List<Machine> machines = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length < 5) {
                    continue;
                }
                Optional<MachinePos> where = f.length >= 9 && !f[5].isEmpty()
                        ? Optional.of(new MachinePos(f[5], Integer.parseInt(f[6]), Integer.parseInt(f[7]),
                        Integer.parseInt(f[8]))) : Optional.empty();
                machines.add(new Machine(UUID.fromString(f[0]), new PolityId(UUID.fromString(f[1])),
                        MachineType.valueOf(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), where));
            }
        } catch (IOException | RuntimeException e) {
            return new MachineFleet();
        }
        return MachineFleet.restore(machines);
    }

    void save(MachineFleet fleet) {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Machine m : fleet.all()) {
            String where = m.where().map(p -> String.join("\t", p.dimension(), Integer.toString(p.x()),
                    Integer.toString(p.y()), Integer.toString(p.z()))).orElse("\t\t\t");
            lines.add(String.join("\t", m.id().toString(), m.company().value().toString(), m.type().name(),
                    Integer.toString(m.level()), Integer.toString(m.wear()), where));
        }
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // best effort; der nächste Tagesabschluss versucht es erneut
        }
    }
}

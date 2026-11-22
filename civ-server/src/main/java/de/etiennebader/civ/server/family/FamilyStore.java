package de.etiennebader.civ.server.family;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.family.FamilyRegistry;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Speichert Ehen, Beziehungen, Kinder und Scheidungen als Textdatei (tabulatorgetrennt), atomar ersetzt. */
final class FamilyStore {

    private final Optional<Path> file;

    FamilyStore(Optional<Path> file) {
        this.file = file;
    }

    FamilyRegistry load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return FamilyRegistry.empty();
        }
        List<FamilyRegistry.Marriage> marriages = new ArrayList<>();
        Map<String, Integer> bonds = new LinkedHashMap<>();
        List<FamilyRegistry.Child> children = new ArrayList<>();
        List<FamilyRegistry.Divorce> divorces = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                switch (f[0]) {
                    case "MAR" -> marriages.add(new FamilyRegistry.Marriage(person(f[1]), person(f[2]), day(f[3]),
                            day(f[4]), Integer.parseInt(f[5])));
                    case "BOND" -> bonds.put(f[1], Integer.parseInt(f[2]));
                    case "KID" -> children.add(new FamilyRegistry.Child(new PersonId(UUID.fromString(f[1])),
                            person(f[2]), person(f[3]), day(f[4])));
                    case "DIV" -> divorces.add(new FamilyRegistry.Divorce(person(f[1]), person(f[2]), day(f[3]),
                            day(f[4])));
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return FamilyRegistry.empty();
        }
        return FamilyRegistry.restore(new FamilyRegistry.Snapshot(marriages, bonds, children, divorces));
    }

    void save(FamilyRegistry registry) {
        if (file.isEmpty()) {
            return;
        }
        FamilyRegistry.Snapshot s = registry.snapshot();
        List<String> lines = new ArrayList<>();
        for (FamilyRegistry.Marriage m : s.marriages()) {
            lines.add(String.join("\t", "MAR", m.first().uuid().toString(), m.second().uuid().toString(),
                    Long.toString(m.since().value()), Long.toString(m.lastBirth().value()),
                    Integer.toString(m.children())));
        }
        s.bonds().forEach((key, value) -> lines.add("BOND\t" + key + "\t" + value));
        for (FamilyRegistry.Child c : s.children()) {
            lines.add(String.join("\t", "KID", c.id().value().toString(), c.parent1().uuid().toString(),
                    c.parent2().uuid().toString(), Long.toString(c.born().value())));
        }
        for (FamilyRegistry.Divorce d : s.divorces()) {
            lines.add(String.join("\t", "DIV", d.first().uuid().toString(), d.second().uuid().toString(),
                    Long.toString(d.married().value()), Long.toString(d.divorced().value())));
        }
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Speichern ist best effort; beim nächsten Tagesende wird erneut versucht.
        }
    }

    private static PersonRef person(String uuid) {
        return new PersonRef(new PersonId(UUID.fromString(uuid)));
    }

    private static Day day(String value) {
        return Day.of(Long.parseLong(value));
    }
}

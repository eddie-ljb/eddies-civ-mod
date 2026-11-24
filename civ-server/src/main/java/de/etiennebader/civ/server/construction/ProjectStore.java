package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Speichert Bauaufträge als Textdatei ({@code construction.tsv}) neben der Datenbank: eine Zeile je Auftrag,
 * Felder durch Tabulator getrennt. Geschrieben wird über eine Zwischendatei, damit ein Absturz die Datei nie halb
 * zurücklässt.
 */
final class ProjectStore {

    private final Optional<Path> file;

    ProjectStore(Optional<Path> file) {
        this.file = file;
    }

    List<ConstructionProject> load() {
        List<ConstructionProject> projects = new ArrayList<>();
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return projects;
        }
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    projects.add(parse(line));
                } catch (RuntimeException ignored) {
                    // eine kaputte Zeile darf den Rest nicht verlieren
                }
            }
        } catch (IOException ignored) {
            // Datei nicht lesbar: ohne Aufträge starten
        }
        return projects;
    }

    void save(List<ConstructionProject> projects) {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = projects.stream().map(ProjectStore::format).toList();
        try {
            Path target = file.get();
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String format(ConstructionProject p) {
        return String.join("\t", p.id().toString(), Integer.toString(p.number()), p.town().value().toString(),
                p.plot().value().toString(), ActorCodec.encode(p.client()), p.builder().value().toString(),
                p.blueprint(), p.dimension().value(), Integer.toString(p.originX()),
                p.originY().isPresent() ? Integer.toString(p.originY().getAsInt()) : "-",
                Integer.toString(p.originZ()), p.status().name(), Integer.toString(p.placed()),
                Integer.toString(p.total()), Long.toString(p.price()), Long.toString(p.paid()),
                Long.toString(p.orderedOn().value()), Integer.toString(p.rotation()),
                p.selfSupplied() ? "1" : "0", depotText(p.depot()),
                p.contractor().map(c -> ActorCodec.encode(c)).orElse("-"), p.contractorState().name());
    }

    private static String depotText(java.util.Map<String, Integer> depot) {
        return depot.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private static java.util.Map<String, Integer> depotOf(String text) {
        java.util.Map<String, Integer> depot = new java.util.TreeMap<>();
        for (String entry : text.split(";")) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                depot.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
            }
        }
        return depot;
    }

    private static ConstructionProject parse(String line) {
        String[] f = line.split("\t", -1);
        return new ConstructionProject(UUID.fromString(f[0]), Integer.parseInt(f[1]),
                new PolityId(UUID.fromString(f[2])), new PlotId(UUID.fromString(f[3])), ActorCodec.decode(f[4]),
                new PolityId(UUID.fromString(f[5])), f[6], new DimensionId(f[7]), Integer.parseInt(f[8]),
                f[9].equals("-") ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(f[9])),
                Integer.parseInt(f[10]), ConstructionProject.Status.valueOf(f[11]), Integer.parseInt(f[12]),
                Integer.parseInt(f[13]), Long.parseLong(f[14]), Long.parseLong(f[15]), Day.of(Long.parseLong(f[16])),
                f.length > 17 ? Integer.parseInt(f[17]) : 0, f.length > 18 && f[18].equals("1"),
                f.length > 19 ? depotOf(f[19]) : java.util.Map.of(),
                f.length > 20 && !f[20].equals("-")
                        ? java.util.Optional.of((de.etiennebader.civ.domain.actor.PlayerRef) ActorCodec.decode(f[20]))
                        : java.util.Optional.empty(),
                f.length > 21 ? ConstructionProject.Contractor.valueOf(f[21]) : ConstructionProject.Contractor.NONE);
    }
}

package de.etiennebader.civ.server.police;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.police.CrimeKind;
import de.etiennebader.civ.domain.police.Detention;
import de.etiennebader.civ.domain.police.Offense;
import de.etiennebader.civ.domain.police.PoliceRank;
import de.etiennebader.civ.domain.police.PoliceRegistry;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.persistence.ActorCodec;

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

/** Speichert den Polizeizustand als Textdatei (tabulatorgetrennt), atomar ersetzt. */
final class PoliceStore {

    private final Optional<Path> file;

    PoliceStore(Optional<Path> file) {
        this.file = file;
    }

    PoliceRegistry load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return PoliceRegistry.empty();
        }
        Map<PolityId, Map<ActorRef, PoliceRank>> ranks = new LinkedHashMap<>();
        Map<PolityId, List<Offense>> offenses = new LinkedHashMap<>();
        Map<PolityId, Integer> cells = new LinkedHashMap<>();
        Map<PolityId, Map<ActorRef, Detention>> detained = new LinkedHashMap<>();
        Map<ActorRef, Day> cuffed = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                switch (f[0]) {
                    case "RANK" -> ranks.computeIfAbsent(pid(f[1]), k -> new LinkedHashMap<>())
                            .put(ActorCodec.decode(f[2]), PoliceRank.valueOf(f[3]));
                    case "OFF" -> offenses.computeIfAbsent(pid(f[1]), k -> new ArrayList<>())
                            .add(new Offense(Integer.parseInt(f[2]), ActorCodec.decode(f[3]), CrimeKind.valueOf(f[4]),
                                    Day.of(Long.parseLong(f[5])), Boolean.parseBoolean(f[6])));
                    case "CELLS" -> cells.put(pid(f[1]), Integer.parseInt(f[2]));
                    case "JAIL" -> {
                        ActorRef person = ActorCodec.decode(f[2]);
                        detained.computeIfAbsent(pid(f[1]), k -> new LinkedHashMap<>()).put(person,
                                new Detention(person, Day.of(Long.parseLong(f[3])), Day.of(Long.parseLong(f[4])),
                                        Boolean.parseBoolean(f[5])));
                    }
                    case "CUFF" -> cuffed.put(ActorCodec.decode(f[1]), Day.of(Long.parseLong(f[2])));
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return PoliceRegistry.empty();
        }
        return PoliceRegistry.restore(new PoliceRegistry.Snapshot(ranks, offenses, cells, detained, cuffed));
    }

    void save(PoliceRegistry registry) {
        if (file.isEmpty()) {
            return;
        }
        PoliceRegistry.Snapshot s = registry.snapshot();
        List<String> lines = new ArrayList<>();
        s.ranks().forEach((polity, map) -> map.forEach((m, rank) -> lines.add(String.join("\t", "RANK",
                polity.value().toString(), ActorCodec.encode(m), rank.name()))));
        s.offenses().forEach((polity, list) -> list.forEach(o -> lines.add(String.join("\t", "OFF",
                polity.value().toString(), Integer.toString(o.number()), ActorCodec.encode(o.suspect()),
                o.kind().name(), Long.toString(o.on().value()), Boolean.toString(o.handled())))));
        s.cells().forEach((polity, n) -> lines.add("CELLS\t" + polity.value() + "\t" + n));
        s.detained().forEach((polity, map) -> map.values().forEach(d -> lines.add(String.join("\t", "JAIL",
                polity.value().toString(), ActorCodec.encode(d.person()), Long.toString(d.since().value()),
                Long.toString(d.until().value()), Boolean.toString(d.remand())))));
        s.cuffed().forEach((person, until) -> lines.add("CUFF\t" + ActorCodec.encode(person) + "\t" + until.value()));
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // best effort; beim nächsten Tagesende erneut
        }
    }

    private static PolityId pid(String s) {
        return new PolityId(UUID.fromString(s));
    }
}

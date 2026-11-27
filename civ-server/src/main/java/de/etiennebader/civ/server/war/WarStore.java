package de.etiennebader.civ.server.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.war.BattleKind;
import de.etiennebader.civ.domain.war.Campaign;
import de.etiennebader.civ.domain.war.MilitaryRank;
import de.etiennebader.civ.domain.war.PeaceTerms;
import de.etiennebader.civ.domain.war.War;
import de.etiennebader.civ.domain.war.WarId;
import de.etiennebader.civ.domain.war.WarRegistry;
import de.etiennebader.civ.domain.war.WarView;
import de.etiennebader.civ.persistence.ActorCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Speichert den Kriegszustand als Textdatei (eine Zeile je Eintrag, tabulatorgetrennt), atomar ersetzt. */
final class WarStore {

    private final Optional<Path> file;

    WarStore(Optional<Path> file) {
        this.file = file;
    }

    WarRegistry load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return WarRegistry.empty();
        }
        List<War> wars = new ArrayList<>();
        Map<PolityId, Map<ActorRef, WarView.Service>> services = new LinkedHashMap<>();
        Map<PolityId, Map<ActorRef, Day>> wounded = new LinkedHashMap<>();
        Map<PolityId, Integer> beds = new LinkedHashMap<>();
        Map<WarId, Integer> fronts = new LinkedHashMap<>();
        List<Campaign> campaigns = new ArrayList<>();
        Map<PolityId, Set<PolityId>> allies = new LinkedHashMap<>();
        Map<PolityId, Set<PolityId>> proposals = new LinkedHashMap<>();
        Map<PolityId, Set<PolityId>> claims = new LinkedHashMap<>();
        Map<PolityId, Day> neutral = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                switch (f[0]) {
                    case "WAR" -> wars.add(new War(wid(f[1]), pid(f[2]), pid(f[3]), day(f[4]),
                            f[5].isEmpty() ? Optional.empty() : Optional.of(day(f[5])),
                            f[6].isEmpty() ? Optional.empty() : Optional.of(new PeaceTerms(pid(f[6]), pid(f[7]),
                                    Long.parseLong(f[8]), Integer.parseInt(f[9])))));
                    case "SVC" -> services.computeIfAbsent(pid(f[1]), k -> new LinkedHashMap<>())
                            .put(ActorCodec.decode(f[2]), new WarView.Service(MilitaryRank.valueOf(f[3]),
                                    Integer.parseInt(f[4]), Integer.parseInt(f[5])));
                    case "WND" -> wounded.computeIfAbsent(pid(f[1]), k -> new LinkedHashMap<>())
                            .put(ActorCodec.decode(f[2]), day(f[3]));
                    case "BED" -> beds.put(pid(f[1]), Integer.parseInt(f[2]));
                    case "FRONT" -> fronts.put(wid(f[1]), Integer.parseInt(f[2]));
                    case "CAMP" -> campaigns.add(new Campaign(wid(f[1]), pid(f[2]), BattleKind.valueOf(f[3]), day(f[4])));
                    case "ALLY" -> allies.computeIfAbsent(pid(f[1]), k -> new LinkedHashSet<>()).add(pid(f[2]));
                    case "PROP" -> proposals.computeIfAbsent(pid(f[1]), k -> new LinkedHashSet<>()).add(pid(f[2]));
                    case "CLAIM" -> claims.computeIfAbsent(pid(f[1]), k -> new LinkedHashSet<>()).add(pid(f[2]));
                    case "NEUT" -> neutral.put(pid(f[1]), day(f[2]));
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return WarRegistry.empty();
        }
        return WarRegistry.restore(new WarRegistry.Snapshot(wars, services, wounded, beds, fronts, campaigns, allies,
                proposals, claims, neutral));
    }

    List<WarService.LogEntry> loadLog() {
        List<WarService.LogEntry> entries = new ArrayList<>();
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f[0].equals("LOG")) {
                    entries.add(new WarService.LogEntry(wid(f[1]), Long.parseLong(f[2]), BattleKind.valueOf(f[3]),
                            Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                            Integer.parseInt(f[7])));
                }
            }
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
        return entries;
    }

    void save(WarRegistry registry, List<WarService.LogEntry> log) {
        if (file.isEmpty()) {
            return;
        }
        WarRegistry.Snapshot s = registry.snapshot();
        List<String> lines = new ArrayList<>();
        for (War war : s.wars()) {
            Optional<PeaceTerms> t = war.outcome();
            lines.add(String.join("\t", "WAR", war.id().value().toString(), war.aggressor().value().toString(),
                    war.defender().value().toString(), Long.toString(war.declaredOn().value()),
                    war.endedOn().map(d -> Long.toString(d.value())).orElse(""),
                    t.map(x -> x.winner().value().toString()).orElse(""),
                    t.map(x -> x.loser().value().toString()).orElse(""),
                    t.map(x -> Long.toString(x.reparations())).orElse("0"),
                    t.map(x -> Integer.toString(x.conquest())).orElse("0")));
        }
        s.services().forEach((polity, map) -> map.forEach((member, svc) -> lines.add(String.join("\t", "SVC",
                polity.value().toString(), ActorCodec.encode(member), svc.rank().name(),
                Integer.toString(svc.training()), Integer.toString(svc.equipment())))));
        s.wounded().forEach((polity, map) -> map.forEach((member, until) -> lines.add(String.join("\t", "WND",
                polity.value().toString(), ActorCodec.encode(member), Long.toString(until.value())))));
        s.hospitalBeds().forEach((polity, n) -> lines.add("BED\t" + polity.value() + "\t" + n));
        s.fronts().forEach((war, v) -> lines.add("FRONT\t" + war.value() + "\t" + v));
        for (Campaign c : s.campaigns()) {
            lines.add(String.join("\t", "CAMP", c.war().value().toString(), c.initiator().value().toString(),
                    c.kind().name(), Long.toString(c.on().value())));
        }
        for (WarService.LogEntry e : log) {
            lines.add(String.join("\t", "LOG", e.war().value().toString(), Long.toString(e.day()), e.kind().name(),
                    Integer.toString(e.front()), Integer.toString(e.shift()), Integer.toString(e.woundedAggressor()),
                    Integer.toString(e.woundedDefender())));
        }
        pairs(lines, "ALLY", s.alliances());
        pairs(lines, "PROP", s.proposals());
        pairs(lines, "CLAIM", s.claims());
        s.neutralUntil().forEach((polity, until) -> lines.add("NEUT\t" + polity.value() + "\t" + until.value()));
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

    private static void pairs(List<String> lines, String tag, Map<PolityId, Set<PolityId>> map) {
        map.forEach((a, set) -> set.forEach(b -> lines.add(tag + "\t" + a.value() + "\t" + b.value())));
    }

    private static PolityId pid(String s) {
        return new PolityId(UUID.fromString(s));
    }

    private static WarId wid(String s) {
        return new WarId(UUID.fromString(s));
    }

    private static Day day(String s) {
        return Day.of(Long.parseLong(s));
    }
}

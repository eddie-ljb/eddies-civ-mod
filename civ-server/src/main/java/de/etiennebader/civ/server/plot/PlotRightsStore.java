package de.etiennebader.civ.server.plot;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.protection.PlotFlag;
import de.etiennebader.civ.domain.protection.PlotRights;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.protection.Stance;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Speichert die Rechte der Grundstücke als Textdatei (tabulatorgetrennt), atomar ersetzt. Eine Zeile je Grundstück:
 * {@code Grundstück, Gemeinde, Inhaber, Mitglieder, Fremde, Gäste, Regeln}.
 */
final class PlotRightsStore {

    /** Die gespeicherten Rechte eines Grundstücks samt dem Inhaber, der sie eingestellt hat. */
    record Entry(PolityId polity, ActorRef holder, PlotRights rights) {
    }

    private final Optional<Path> file;

    PlotRightsStore(Optional<Path> file) {
        this.file = file;
    }

    Map<PlotId, Entry> load() {
        Map<PlotId, Entry> entries = new LinkedHashMap<>();
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length < 7) {
                    continue;
                }
                Map<ActorRef, Set<ProtectedAction>> guests = new LinkedHashMap<>();
                for (String guest : f[5].split(";")) {
                    String[] parts = guest.split("=", 2);
                    if (parts.length == 2 && !parts[0].isEmpty()) {
                        guests.put(actor(parts[0]), actions(parts[1]));
                    }
                }
                Map<PlotFlag, Stance> flags = new EnumMap<>(PlotFlag.class);
                for (String flag : f[6].split(";")) {
                    String[] parts = flag.split("=", 2);
                    if (parts.length == 2) {
                        flags.put(PlotFlag.valueOf(parts[0]), Stance.valueOf(parts[1]));
                    }
                }
                entries.put(new PlotId(UUID.fromString(f[0])), new Entry(new PolityId(UUID.fromString(f[1])),
                        actor(f[2]), new PlotRights(actions(f[3]), actions(f[4]), guests, flags)));
            }
        } catch (IOException | RuntimeException e) {
            return new LinkedHashMap<>();
        }
        return entries;
    }

    void save(Map<PlotId, Entry> entries) {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        entries.forEach((plot, entry) -> {
            PlotRights rights = entry.rights();
            List<String> guests = new ArrayList<>();
            rights.guests().forEach((guest, actions) -> guests.add(code(guest) + "=" + join(actions)));
            List<String> flags = new ArrayList<>();
            rights.flags().forEach((flag, stance) -> flags.add(flag.name() + "=" + stance.name()));
            lines.add(String.join("\t", plot.value().toString(), entry.polity().value().toString(), code(entry.holder()),
                    join(rights.members()), join(rights.outsiders()), String.join(";", guests),
                    String.join(";", flags)));
        });
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Grundstücksrechte nicht speicherbar: " + file.get(), e);
        }
    }

    private static String join(Set<ProtectedAction> actions) {
        return String.join(",", actions.stream().map(Enum::name).toList());
    }

    private static Set<ProtectedAction> actions(String text) {
        Set<ProtectedAction> actions = EnumSet.noneOf(ProtectedAction.class);
        for (String name : text.split(",")) {
            if (!name.isEmpty()) {
                actions.add(ProtectedAction.valueOf(name));
            }
        }
        return actions;
    }

    private static String code(ActorRef actor) {
        return switch (actor) {
            case PlayerRef player -> "P" + player.uuid();
            case PersonRef person -> "V" + person.uuid();
            case PolityRef polity -> "G" + polity.uuid();
        };
    }

    private static ActorRef actor(String code) {
        UUID id = UUID.fromString(code.substring(1));
        return switch (code.charAt(0)) {
            case 'P' -> new PlayerRef(new PlayerId(id));
            case 'V' -> new PersonRef(new PersonId(id));
            case 'G' -> new PolityRef(new PolityId(id));
            default -> throw new IllegalArgumentException(code);
        };
    }
}

package de.etiennebader.civ.server.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Wer bei {@code court_staffing = election} für ein Richter- oder Staatsanwaltsamt kandidiert (Spieler); als Textdatei gespeichert. */
final class CourtCandidacies {

    private record Entry(PolityId court, ActorRef candidate) {
    }

    private final Optional<Path> file;
    private final Set<Entry> entries = new LinkedHashSet<>();

    CourtCandidacies(Optional<Path> file) {
        this.file = file;
        if (file.isPresent() && Files.isRegularFile(file.get())) {
            try {
                for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                    String[] f = line.split("\t", -1);
                    entries.add(new Entry(new PolityId(UUID.fromString(f[0])), ActorCodec.decode(f[1])));
                }
            } catch (IOException | RuntimeException e) {
                entries.clear();
            }
        }
    }

    synchronized boolean run(PolityId court, ActorRef candidate) {
        boolean added = entries.add(new Entry(court, candidate));
        if (added) {
            save();
        }
        return added;
    }

    synchronized boolean withdraw(PolityId court, ActorRef candidate) {
        boolean removed = entries.remove(new Entry(court, candidate));
        if (removed) {
            save();
        }
        return removed;
    }

    synchronized List<ActorRef> of(PolityId court) {
        return entries.stream().filter(e -> e.court().equals(court)).map(Entry::candidate).toList();
    }

    private void save() {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        entries.forEach(e -> lines.add(e.court().value() + "\t" + ActorCodec.encode(e.candidate())));
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // best effort
        }
    }
}

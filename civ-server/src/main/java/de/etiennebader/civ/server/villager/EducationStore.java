package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.population.VillagerEducation;
import de.etiennebader.civ.application.population.VillagerEducationLedger;
import de.etiennebader.civ.domain.actor.PersonRef;
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

/** Speichert Bildung und Intelligenz der Dorfbewohner (Issue #21) als Textdatei, atomar ersetzt. */
public final class EducationStore {

    private final Optional<Path> file;

    public EducationStore(Optional<Path> file) {
        this.file = file;
    }

    public void load(VillagerEducationLedger ledger) {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return;
        }
        Map<PersonRef, VillagerEducation> stored = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length == 4) {
                    stored.put(new PersonRef(new PersonId(UUID.fromString(f[0]))), new VillagerEducation(
                            Integer.parseInt(f[1]), Integer.parseInt(f[2]), Boolean.parseBoolean(f[3])));
                }
            }
        } catch (IOException | RuntimeException e) {
            return;
        }
        ledger.restore(stored);
    }

    public void save(VillagerEducationLedger ledger) {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        ledger.snapshot().forEach((villager, e) -> lines.add(String.join("\t", villager.uuid().toString(),
                Integer.toString(e.points()), Integer.toString(e.intelligence()), Boolean.toString(e.frozen()))));
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // best effort; beim nächsten Tagesabschluss erneut
        }
    }
}

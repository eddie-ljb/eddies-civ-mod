package de.etiennebader.civ.server.culture;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Culture;
import de.etiennebader.civ.domain.population.Race;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.TranslationKeys;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Kultur und Rasse der Gemeinden. Beides wählt der Bürgermeister bei der Gründung und kann es später ändern; es
 * bestimmt das Aussehen der Dorfbewohner (Kleidung nach Kultur, Größe und mit dem Mod Hautton nach Rasse). Gemeinden
 * ohne Eintrag sind Menschen der Ebene. Von Dorfbewohnern gegründete Gemeinden erben die Art ihrer Heimatgemeinde.
 * Gespeichert in {@code culture.tsv}.
 */
public final class CultureService {

    /** Kultur und Rasse einer Gemeinde. */
    public record Character(Culture culture, Race race) {

        public static final Character DEFAULT = new Character(Culture.DEFAULT, Race.DEFAULT);

        public Character {
            Objects.requireNonNull(culture, "culture");
            Objects.requireNonNull(race, "race");
        }
    }

    private final WorldQueries queries;
    private final Announcements announcements;
    private final Optional<Path> file;
    private final Map<PolityId, Character> characters = new LinkedHashMap<>();

    public CultureService(WorldQueries queries, Announcements announcements, Optional<Path> file) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    /** Kultur und Rasse der Gemeinde. */
    public synchronized Character of(PolityId polity) {
        return characters.getOrDefault(polity, Character.DEFAULT);
    }

    /** Kultur und Rasse der Gemeinde, in der der Dorfbewohner lebt; leer, wenn er keine hat. */
    public synchronized Optional<Character> ofVillager(PersonRef villager) {
        return queries.municipalityOf(villager).map(town -> of(town.id()));
    }

    /** Der Bürgermeister legt Kultur und/oder Rasse fest; was leer bleibt, bleibt wie es ist. */
    public synchronized Reply set(Caller caller, Optional<Culture> culture, Optional<Race> race) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        if (!town.get().holds(caller.player(), Office.MAYOR)) {
            return Reply.failure(Message.of(MessageKey.CULTURE_NOT_MAYOR));
        }
        Character current = of(town.get().id());
        Character next = new Character(culture.orElse(current.culture()), race.orElse(current.race()));
        if (next.equals(current)) {
            return Reply.failure(Message.of(MessageKey.CULTURE_UNCHANGED, name(current.culture()), name(current.race())));
        }
        characters.put(town.get().id(), next);
        save();
        Message line = Message.of(MessageKey.CULTURE_SET, town.get().name().value(), name(next.culture()),
                name(next.race()));
        announcements.add(Announcement.toTown(town.get().id(), line));
        return Reply.success(line);
    }

    /** Wie {@link #set(Caller, Optional, Optional)}, aus Befehlswörtern; Unbekanntes wird abgelehnt. */
    public synchronized Reply set(Caller caller, String cultureWord, String raceWord) {
        Optional<Culture> culture = Culture.parse(cultureWord);
        if (culture.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CULTURE_UNKNOWN_CULTURE, cultureWord.strip(),
                    String.join(", ", Culture.words())));
        }
        Optional<Race> race = Race.parse(raceWord);
        if (race.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CULTURE_UNKNOWN_RACE, raceWord.strip(),
                    String.join(", ", Race.words())));
        }
        return set(caller, culture, race);
    }

    /** Auskunft über Kultur und Rasse der eigenen Gemeinde. */
    public synchronized Reply describe(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        Character character = of(town.get().id());
        return Reply.success(Message.of(MessageKey.CULTURE_INFO, town.get().name().value(), name(character.culture()),
                name(character.race())));
    }

    /** Eine neu gegründete Gemeinde übernimmt Kultur und Rasse ihrer Heimatgemeinde. */
    public synchronized void inherit(PolityId home, PolityId created) {
        Character parent = of(home);
        if (!parent.equals(Character.DEFAULT)) {
            characters.put(created, parent);
            save();
        }
    }

    /** Alle Gemeinden mit eigener Kultur oder Rasse, für Anzeigen. */
    public synchronized Map<PolityId, Character> all() {
        return Map.copyOf(characters);
    }

    private static Message name(Culture culture) {
        return Message.of(TranslationKeys.culture(culture));
    }

    private static Message name(Race race) {
        return Message.of(TranslationKeys.race(race));
    }

    // ------------------------------------------------------------------ Speichern

    private void load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length >= 3) {
                    characters.put(new PolityId(UUID.fromString(f[0])), new Character(Culture.valueOf(f[1]),
                            Race.valueOf(f[2])));
                }
            }
        } catch (IOException | RuntimeException e) {
            characters.clear(); // beschädigte Datei: alle Gemeinden zurück auf Standard
        }
    }

    private void save() {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        characters.forEach((polity, character) -> lines.add(String.join("\t", polity.value().toString(),
                character.culture().name(), character.race().name())));
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // beim nächsten Speichern erneut versuchen
        }
    }

    /** Ob die Polity eine Gemeinde ist, für die Kultur und Rasse gelten. */
    public static boolean applies(PolityView polity) {
        return polity.kind() == PolityKind.TERRITORIAL;
    }
}

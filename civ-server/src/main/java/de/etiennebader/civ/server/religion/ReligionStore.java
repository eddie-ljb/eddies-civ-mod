package de.etiennebader.civ.server.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.religion.ClergyElection;
import de.etiennebader.civ.domain.religion.Denomination;
import de.etiennebader.civ.domain.religion.Religion;
import de.etiennebader.civ.domain.religion.ReligionId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;

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

/** Speichert Religionen, Mitgliedschaften und Riten als Textdatei (tabulatorgetrennt), atomar ersetzt. */
final class ReligionStore {

    /** Alles, was gespeichert wird. */
    record State(List<Religion> religions, Map<ActorRef, ReligionId> members, Map<ActorRef, String> denominations,
                 List<String> rites, Map<ReligionId, Integer> fervor, Map<ReligionId, String> logos,
                 Map<ReligionId, String> texts, List<ClergyRole> roles, Map<ActorRef, Long> blessed) {
        State(List<Religion> religions, Map<ActorRef, ReligionId> members, Map<ActorRef, String> denominations,
              List<String> rites, Map<ReligionId, Integer> fervor, Map<ReligionId, String> logos) {
            this(religions, members, denominations, rites, fervor, logos, new LinkedHashMap<>(), new ArrayList<>(),
                    new LinkedHashMap<>());
        }

        static State empty() {
            return new State(List.of(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(),
                    new LinkedHashMap<>());
        }
    }

    private final Optional<Path> file;

    ReligionStore(Optional<Path> file) {
        this.file = file;
    }

    State load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return State.empty();
        }
        List<Religion> religions = new ArrayList<>();
        Map<ActorRef, ReligionId> members = new LinkedHashMap<>();
        Map<ActorRef, String> denominations = new LinkedHashMap<>();
        List<String> rites = new ArrayList<>();
        Map<ReligionId, Integer> fervor = new LinkedHashMap<>();
        Map<ReligionId, String> logos = new LinkedHashMap<>();
        Map<ReligionId, String> texts = new LinkedHashMap<>();
        List<ClergyRole> roles = new ArrayList<>();
        Map<ActorRef, Long> blessed = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                switch (f[0]) {
                    case "REL" -> {
                        List<Denomination> dens = new ArrayList<>();
                        if (!f[8].isEmpty()) {
                            for (String d : f[8].split("\\|")) {
                                dens.add(new Denomination(d));
                            }
                        }
                        Optional<ClergyElection> election = Optional.empty();
                        if (!f[11].isEmpty()) {
                            List<ActorRef> candidates = new ArrayList<>();
                            if (!f[12].isEmpty()) {
                                for (String c : f[12].split("\\|")) {
                                    candidates.add(actor(c));
                                }
                            }
                            Map<ActorRef, ActorRef> ballots = new LinkedHashMap<>();
                            if (!f[13].isEmpty()) {
                                for (String b : f[13].split("\\|")) {
                                    String[] pair = b.split(">");
                                    ballots.put(actor(pair[0]), actor(pair[1]));
                                }
                            }
                            election = Optional.of(new ClergyElection(candidates, ballots, Day.of(Long.parseLong(f[11]))));
                        }
                        religions.add(new Religion(new ReligionId(UUID.fromString(f[1])), f[2], actor(f[3]),
                                Boolean.parseBoolean(f[4]), f[5], f[6], f[7], dens,
                                f[9].isEmpty() ? Optional.empty() : Optional.of(actor(f[9])),
                                Integer.parseInt(f[10]), election, Day.of(Long.parseLong(f[14]))));
                    }
                    case "MEM" -> {
                        members.put(actor(f[1]), new ReligionId(UUID.fromString(f[2])));
                        if (f.length > 3 && !f[3].isEmpty()) {
                            denominations.put(actor(f[1]), f[3]);
                        }
                    }
                    case "RITE" -> rites.add(f[1]);
                    case "FER" -> fervor.put(new ReligionId(UUID.fromString(f[1])), Integer.parseInt(f[2]));
                    case "LOGO" -> logos.put(new ReligionId(UUID.fromString(f[1])), f[2]);
                    case "TEXT" -> texts.put(new ReligionId(UUID.fromString(f[1])), unescape(f[2]));
                    case "ROLE" -> {
                        java.util.Set<ClergyRole.Right> rights = new java.util.LinkedHashSet<>();
                        if (!f[3].isEmpty()) {
                            for (String right : f[3].split(",")) {
                                rights.add(ClergyRole.Right.valueOf(right));
                            }
                        }
                        java.util.Set<ActorRef> holders = new java.util.LinkedHashSet<>();
                        if (!f[4].isEmpty()) {
                            for (String holder : f[4].split("\\|")) {
                                holders.add(actor(holder));
                            }
                        }
                        roles.add(new ClergyRole(new ReligionId(UUID.fromString(f[1])), f[2], rights, holders));
                    }
                    case "BLESS" -> blessed.put(actor(f[1]), Long.parseLong(f[2]));
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return State.empty();
        }
        return new State(religions, members, denominations, rites, fervor, logos, texts, roles, blessed);
    }

    void save(State state) {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Religion r : state.religions()) {
            String election = "";
            String candidates = "";
            String ballots = "";
            if (r.election().isPresent()) {
                ClergyElection e = r.election().get();
                election = Long.toString(e.closesOn().value());
                candidates = String.join("|", e.candidates().stream().map(ReligionStore::text).toList());
                ballots = String.join("|", e.ballots().entrySet().stream()
                        .map(b -> text(b.getKey()) + ">" + text(b.getValue())).toList());
            }
            lines.add(String.join("\t", "REL", r.id().value().toString(), clean(r.name()), text(r.founder()),
                    Boolean.toString(r.monotheistic()), clean(r.scriptureName()), clean(r.templeName()),
                    clean(r.language()),
                    String.join("|", r.denominations().stream().map(d -> clean(d.name())).toList()),
                    r.clergyHead().map(ReligionStore::text).orElse(""), Integer.toString(r.clergyPower()), election,
                    candidates, ballots, Long.toString(r.foundedOn().value())));
        }
        state.members().forEach((actor, religion) -> lines.add(String.join("\t", "MEM", text(actor),
                religion.value().toString(), clean(state.denominations().getOrDefault(actor, "")))));
        state.rites().forEach(rite -> lines.add("RITE\t" + clean(rite)));
        state.fervor().forEach((id, value) -> lines.add("FER\t" + id.value() + "\t" + value));
        state.logos().forEach((id, logo) -> lines.add("LOGO\t" + id.value() + "\t" + clean(logo)));
        state.texts().forEach((id, text) -> lines.add("TEXT\t" + id.value() + "\t" + escape(text)));
        for (ClergyRole role : state.roles()) {
            lines.add(String.join("\t", "ROLE", role.religion().value().toString(), clean(role.name()),
                    String.join(",", role.rights().stream().map(Enum::name).toList()),
                    String.join("|", role.holders().stream().map(ReligionStore::text).toList())));
        }
        state.blessed().forEach((actor, until) -> lines.add("BLESS\t" + text(actor) + "\t" + until));
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // best effort, nächster Tagesabschluss versucht es erneut
        }
    }

    /** Zeilenumbrüche und Tabs in Texten schützen. */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Trennzeichen aus Freitext entfernen. */
    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('|', '/').replace('>', ' ');
    }

    static String text(ActorRef actor) {
        if (actor instanceof PersonRef person) {
            return "V" + person.uuid();
        }
        return "P" + ((PlayerRef) actor).id().value();
    }

    static ActorRef actor(String text) {
        UUID uuid = UUID.fromString(text.substring(1));
        return text.charAt(0) == 'V' ? new PersonRef(new PersonId(uuid)) : new PlayerRef(new PlayerId(uuid));
    }
}

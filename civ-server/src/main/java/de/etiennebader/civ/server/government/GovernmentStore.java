package de.etiennebader.civ.server.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.government.Budget;
import de.etiennebader.civ.domain.government.Coalition;
import de.etiennebader.civ.domain.government.MinistryElection;
import de.etiennebader.civ.domain.government.PendingLaw;
import de.etiennebader.civ.domain.government.Portfolio;
import de.etiennebader.civ.domain.government.Subsidy;
import de.etiennebader.civ.domain.government.Government;
import de.etiennebader.civ.domain.government.GovernmentRegistry;
import de.etiennebader.civ.domain.government.HeadOfState;
import de.etiennebader.civ.domain.government.Ministry;
import de.etiennebader.civ.domain.government.Selection;
import de.etiennebader.civ.domain.government.StateForm;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Speichert Ministerien, Haushalte und Oberhäupter als Textdatei (tabulatorgetrennt), atomar ersetzt. */
final class GovernmentStore {

    private final Optional<Path> file;

    GovernmentStore(Optional<Path> file) {
        this.file = file;
    }

    /** Mutable Zwischenstand je Ebene beim Einlesen. */
    private static final class Draft {
        HeadOfState head = HeadOfState.DEFAULT;
        int max = Government.DEFAULT_MAX_MINISTRIES;
        int interval = Government.DEFAULT_BUDGET_INTERVAL;
        int approval = Government.DEFAULT_APPROVAL_PERCENT;
        String preparer = Government.DEFAULT_PREPARER;
        Optional<Day> last = Optional.empty();
        Optional<Day> adoptedOn = Optional.empty();
        final Map<String, Ministry> ministries = new LinkedHashMap<>();
        final Map<String, Long> adopted = new LinkedHashMap<>();
        boolean hasDraft;
        final Map<String, Long> draft = new LinkedHashMap<>();
        final Map<ActorRef, Boolean> votes = new LinkedHashMap<>();
        int tolerance = 100;
        boolean minority = true;
        Optional<Coalition> coalition = Optional.empty();
        List<PolityId> coalitionParties = new ArrayList<>();
        boolean coalitionMinority;
        Day coalitionDay = Day.of(0);
        final List<Coalition.Item> agreement = new ArrayList<>();
        final List<Subsidy> subsidies = new ArrayList<>();
        final Map<String, Map<ActorRef, Boolean>> subsidyVotes = new LinkedHashMap<>();
        final Map<String, Day> electionCloses = new LinkedHashMap<>();
        final Map<String, List<ActorRef>> candidates = new LinkedHashMap<>();
        final Map<String, Map<ActorRef, ActorRef>> ballots = new LinkedHashMap<>();
        int vetoDays = 3;
        final List<PendingLaw> pending = new ArrayList<>();
    }

    GovernmentRegistry load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return GovernmentRegistry.empty();
        }
        Map<PolityId, Draft> drafts = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                Draft d = drafts.computeIfAbsent(pid(f[1]), k -> new Draft());
                switch (f[0]) {
                    case "GOV" -> {
                        d.head = new HeadOfState(StateForm.valueOf(f[2]), f[3], Selection.valueOf(f[4]),
                                Long.parseLong(f[5]), Boolean.parseBoolean(f[6]), Boolean.parseBoolean(f[7]),
                                Boolean.parseBoolean(f[8]));
                        d.max = Integer.parseInt(f[9]);
                        d.interval = Integer.parseInt(f[10]);
                        d.approval = Integer.parseInt(f[11]);
                        d.preparer = f[12];
                        d.last = f[13].isEmpty() ? Optional.empty() : Optional.of(Day.of(Long.parseLong(f[13])));
                        d.adoptedOn = f[14].isEmpty() ? Optional.empty() : Optional.of(Day.of(Long.parseLong(f[14])));
                        if (f.length > 16) {
                            d.tolerance = Integer.parseInt(f[15]);
                            d.minority = Boolean.parseBoolean(f[16]);
                        }
                    }
                    case "MIN" -> d.ministries.put(f[2], new Ministry(f[2], f[3], f[4], Selection.valueOf(f[5]),
                            f[6].isEmpty() ? Optional.empty() : Optional.of(ActorCodec.decode(f[6])), List.of(),
                            Long.parseLong(f[7]), Long.parseLong(f[8]), Long.parseLong(f[9]),
                            f.length > 11 ? Portfolio.valueOf(f[10]) : Portfolio.guess(f[3]),
                            f.length > 11 && Boolean.parseBoolean(f[11])));
                    case "STAFF" -> d.ministries.computeIfPresent(f[2], (k, m) -> m.hire(ActorCodec.decode(f[3])));
                    case "ADOPT" -> d.adopted.put(f[2], Long.parseLong(f[3]));
                    case "DRAFT" -> {
                        d.hasDraft = true;
                        if (!f[2].isEmpty()) {
                            d.draft.put(f[2], Long.parseLong(f[3]));
                        }
                    }
                    case "COAL" -> {
                        d.coalitionParties = new ArrayList<>();
                        for (String party : f[2].split(",")) {
                            if (!party.isEmpty()) {
                                d.coalitionParties.add(pid(party));
                            }
                        }
                        d.coalitionMinority = Boolean.parseBoolean(f[3]);
                        d.coalitionDay = Day.of(Long.parseLong(f[4]));
                    }
                    case "AGR" -> d.agreement.add(new Coalition.Item(f[2], f[3], Boolean.parseBoolean(f[4])));
                    case "SUB" -> d.subsidies.add(new Subsidy(f[2], f[3],
                            f[4].isEmpty() ? Optional.empty() : Optional.of(ActorCodec.decode(f[4])), f[5],
                            Long.parseLong(f[6]),
                            f[7].isEmpty() ? Optional.empty() : Optional.of(Day.of(Long.parseLong(f[7]))),
                            Long.parseLong(f[8]), Long.parseLong(f[9]), Boolean.parseBoolean(f[10])));
                    case "SUBVOTE" -> d.subsidyVotes.computeIfAbsent(f[2], k -> new LinkedHashMap<>())
                            .put(ActorCodec.decode(f[3]), Boolean.parseBoolean(f[4]));
                    case "ELEC" -> d.electionCloses.put(f[2], Day.of(Long.parseLong(f[3])));
                    case "CAND" -> d.candidates.computeIfAbsent(f[2], k -> new ArrayList<>())
                            .add(ActorCodec.decode(f[3]));
                    case "BALLOT" -> d.ballots.computeIfAbsent(f[2], k -> new LinkedHashMap<>())
                            .put(ActorCodec.decode(f[3]), ActorCodec.decode(f[4]));
                    case "VETO" -> d.vetoDays = Integer.parseInt(f[2]);
                    case "PEND" -> d.pending.add(new PendingLaw(f[2], dec(f[3]), dec(f[4]),
                            f[5].isEmpty() ? List.of() : java.util.Arrays.stream(f[5].split(",")).map(GovernmentStore::dec).toList(),
                            f[6], ActorCodec.decode(f[7]), Day.of(Long.parseLong(f[8])), Day.of(Long.parseLong(f[9]))));
                    case "VOTE" -> d.votes.put(ActorCodec.decode(f[2]), Boolean.parseBoolean(f[3]));
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return GovernmentRegistry.empty();
        }
        Map<PolityId, Government.Snapshot> snapshots = new LinkedHashMap<>();
        drafts.forEach((id, d) -> {
            Optional<Coalition> coalition = d.coalitionParties.isEmpty() ? Optional.empty()
                    : Optional.of(new Coalition(d.coalitionParties, d.coalitionMinority, d.coalitionDay, d.agreement));
            List<MinistryElection> elections = new ArrayList<>();
            d.electionCloses.forEach((key, closes) -> elections.add(new MinistryElection(key, closes,
                    d.candidates.getOrDefault(key, List.of()), d.ballots.getOrDefault(key, Map.of()))));
            snapshots.put(id, new Government.Snapshot(d.head, List.copyOf(d.ministries.values()), d.max, d.interval,
                    d.approval, d.preparer, new Budget(d.adopted, d.adoptedOn),
                    d.hasDraft ? Optional.of(new Budget(d.draft, Optional.empty())) : Optional.empty(), d.votes,
                    d.last, coalition, d.tolerance, d.minority, d.subsidies, d.subsidyVotes, elections, d.vetoDays,
                    d.pending));
        });
        return GovernmentRegistry.restore(snapshots);
    }

    void save(GovernmentRegistry registry) {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        registry.snapshot().forEach((id, g) -> {
            String p = id.value().toString();
            HeadOfState h = g.head();
            lines.add(String.join("\t", "GOV", p, h.form().name(), clean(h.title()), h.selection().name(),
                    Long.toString(h.salary()), Boolean.toString(h.veto()), Boolean.toString(h.appointsGovernment()),
                    Boolean.toString(h.callsElections()), Integer.toString(g.maxMinistries()),
                    Integer.toString(g.budgetInterval()), Integer.toString(g.approvalPercent()), g.preparer(),
                    g.lastBudgetDay().map(d -> Long.toString(d.value())).orElse(""),
                    g.adopted().adoptedOn().map(d -> Long.toString(d.value())).orElse(""),
                    Integer.toString(g.coalitionTolerance()), Boolean.toString(g.allowMinority())));
            for (Ministry m : g.ministries()) {
                lines.add(String.join("\t", "MIN", p, m.key(), clean(m.name()), clean(m.mandate()),
                        m.selection().name(), m.minister().map(ActorCodec::encode).orElse(""),
                        Long.toString(m.ministerWage()), Long.toString(m.staffWage()), Long.toString(m.etat()),
                        m.portfolio().name(), Boolean.toString(m.decrees())));
                for (ActorRef employee : m.staff()) {
                    lines.add(String.join("\t", "STAFF", p, m.key(), ActorCodec.encode(employee)));
                }
            }
            g.adopted().allocations().forEach((key, amount) -> lines.add("ADOPT\t" + p + "\t" + key + "\t" + amount));
            g.draft().ifPresent(draft -> {
                lines.add("DRAFT\t" + p + "\t\t0");
                draft.allocations().forEach((key, amount) -> lines.add("DRAFT\t" + p + "\t" + key + "\t" + amount));
            });
            g.coalition().ifPresent(c -> {
                lines.add(String.join("\t", "COAL", p, String.join(",", c.parties().stream()
                        .map(party -> party.value().toString()).toList()), Boolean.toString(c.minority()),
                        Long.toString(c.formedOn().value())));
                c.agreement().forEach(item -> lines.add(String.join("\t", "AGR", p, clean(item.parameter()),
                        clean(item.encodedValue()), Boolean.toString(item.done()))));
            });
            for (Subsidy sub : g.subsidies()) {
                lines.add(String.join("\t", "SUB", p, sub.key(), clean(sub.name()),
                        sub.recipient().map(ActorCodec::encode).orElse(""), sub.ministry(),
                        Long.toString(sub.amount()), sub.until().map(d -> Long.toString(d.value())).orElse(""),
                        Long.toString(sub.fullUpTo()), Long.toString(sub.taperUntil()),
                        Boolean.toString(sub.active())));
            }
            g.subsidyVotes().forEach((key, votes) -> votes.forEach((voter, yes) -> lines.add(String.join("\t",
                    "SUBVOTE", p, key, ActorCodec.encode(voter), Boolean.toString(yes)))));
            for (MinistryElection e : g.elections()) {
                lines.add(String.join("\t", "ELEC", p, e.ministry(), Long.toString(e.closesOn().value())));
                e.candidates().forEach(c -> lines.add(String.join("\t", "CAND", p, e.ministry(), ActorCodec.encode(c))));
                e.ballots().forEach((voter, c) -> lines.add(String.join("\t", "BALLOT", p, e.ministry(),
                        ActorCodec.encode(voter), ActorCodec.encode(c))));
            }
            lines.add("VETO\t" + p + "\t" + g.vetoDays());
            for (PendingLaw law : g.pendingLaws()) {
                lines.add(String.join("\t", "PEND", p, law.kind(), enc(law.title()), enc(law.text()),
                        String.join(",", law.values().stream().map(GovernmentStore::enc).toList()), law.statute(),
                        ActorCodec.encode(law.proposer()), Long.toString(law.acceptedOn().value()),
                        Long.toString(law.deadline().value())));
            }
            g.votes().forEach((voter, yes) -> lines.add(String.join("\t", "VOTE", p, ActorCodec.encode(voter),
                    Boolean.toString(yes))));
        });
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Speichern ist best effort; bei der nächsten Änderung wird erneut versucht.
        }
    }

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String dec(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static String clean(String text) {
        return text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static PolityId pid(String s) {
        return new PolityId(UUID.fromString(s));
    }
}

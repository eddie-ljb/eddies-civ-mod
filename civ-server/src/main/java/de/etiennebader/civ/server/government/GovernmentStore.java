package de.etiennebader.civ.server.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.government.Budget;
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
                    }
                    case "MIN" -> d.ministries.put(f[2], new Ministry(f[2], f[3], f[4], Selection.valueOf(f[5]),
                            f[6].isEmpty() ? Optional.empty() : Optional.of(ActorCodec.decode(f[6])), List.of(),
                            Long.parseLong(f[7]), Long.parseLong(f[8]), Long.parseLong(f[9])));
                    case "STAFF" -> d.ministries.computeIfPresent(f[2], (k, m) -> m.hire(ActorCodec.decode(f[3])));
                    case "ADOPT" -> d.adopted.put(f[2], Long.parseLong(f[3]));
                    case "DRAFT" -> {
                        d.hasDraft = true;
                        if (!f[2].isEmpty()) {
                            d.draft.put(f[2], Long.parseLong(f[3]));
                        }
                    }
                    case "VOTE" -> d.votes.put(ActorCodec.decode(f[2]), Boolean.parseBoolean(f[3]));
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return GovernmentRegistry.empty();
        }
        Map<PolityId, Government.Snapshot> snapshots = new LinkedHashMap<>();
        drafts.forEach((id, d) -> snapshots.put(id, new Government.Snapshot(d.head,
                List.copyOf(d.ministries.values()), d.max, d.interval, d.approval, d.preparer,
                new Budget(d.adopted, d.adoptedOn), d.hasDraft ? Optional.of(new Budget(d.draft, Optional.empty()))
                : Optional.empty(), d.votes, d.last)));
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
                    g.adopted().adoptedOn().map(d -> Long.toString(d.value())).orElse("")));
            for (Ministry m : g.ministries()) {
                lines.add(String.join("\t", "MIN", p, m.key(), clean(m.name()), clean(m.mandate()),
                        m.selection().name(), m.minister().map(ActorCodec::encode).orElse(""),
                        Long.toString(m.ministerWage()), Long.toString(m.staffWage()), Long.toString(m.etat())));
                for (ActorRef employee : m.staff()) {
                    lines.add(String.join("\t", "STAFF", p, m.key(), ActorCodec.encode(employee)));
                }
            }
            g.adopted().allocations().forEach((key, amount) -> lines.add("ADOPT\t" + p + "\t" + key + "\t" + amount));
            g.draft().ifPresent(draft -> {
                lines.add("DRAFT\t" + p + "\t\t0");
                draft.allocations().forEach((key, amount) -> lines.add("DRAFT\t" + p + "\t" + key + "\t" + amount));
            });
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

    private static String clean(String text) {
        return text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static PolityId pid(String s) {
        return new PolityId(UUID.fromString(s));
    }
}

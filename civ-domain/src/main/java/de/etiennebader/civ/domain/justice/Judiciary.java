package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Alle Gerichte und Verfahren. Regeln zum Ablauf:
 *
 * <ul>
 *   <li>Eingereicht ({@link Stage#FILED}): Der Richter eröffnet oder weist ab; geschieht bis zum Fristende nichts,
 *       eröffnet das Gericht von selbst.</li>
 *   <li>Beweisaufnahme ({@link #EVIDENCE_DAYS} Tage), Plädoyers und Beratung (je {@link #STAGE_DAYS} Tag) laufen nach
 *       Frist weiter; Richter können vorzeitig weiterschalten.</li>
 *   <li>Nach dem Urteil läuft die Berufungsfrist ({@link #APPEAL_DAYS} Tage); danach ist es rechtskräftig.</li>
 * </ul>
 */
public final class Judiciary extends AggregateRoot<JusticeEvent> implements JudiciaryView {

    public static final int FILING_DAYS = 1;
    public static final int EVIDENCE_DAYS = 2;
    public static final int STAGE_DAYS = 1;
    public static final int APPEAL_DAYS = 2;
    public static final int KEPT_CASES = 300;

    private final List<CourtCase> cases = new ArrayList<>();
    private final Map<PolityId, CourtStaff> staff = new LinkedHashMap<>();
    private final List<Exile> exiles = new ArrayList<>();
    private int lastNumber;

    private Judiciary() {
    }

    public static Judiciary empty() {
        return new Judiciary();
    }

    public static Judiciary restore(Collection<CourtCase> stored, Map<PolityId, CourtStaff> staff,
                                    Collection<Exile> exiles) {
        Judiciary judiciary = new Judiciary();
        stored.stream().sorted(java.util.Comparator.comparingInt(CourtCase::number)).forEach(judiciary.cases::add);
        judiciary.staff.putAll(staff);
        judiciary.exiles.addAll(exiles);
        judiciary.lastNumber = judiciary.cases.stream().mapToInt(CourtCase::number).max().orElse(0);
        return judiciary;
    }

    // ---- Personal ----

    public void appoint(PolityId court, Role role, ActorRef person, boolean appointed, Day day) {
        CourtStaff current = staffOf(court);
        List<ActorRef> judges = new ArrayList<>(current.judges());
        List<ActorRef> prosecutors = new ArrayList<>(current.prosecutors());
        List<ActorRef> list = switch (role) {
            case JUDGE -> judges;
            case PROSECUTOR -> prosecutors;
            default -> throw new IllegalArgumentException("Nicht ernennbar: " + role);
        };
        if (appointed && !list.contains(person)) {
            if (list.size() >= CourtStaff.MAX) {
                throw new IllegalStateException("Höchstens " + CourtStaff.MAX);
            }
            list.add(person);
        } else if (!appointed) {
            list.remove(person);
        }
        staff.put(court, new CourtStaff(judges, prosecutors));
        raise(new JusticeEvent.CourtChanged(court, day));
    }

    // ---- Verfahren ----

    /** Reicht ein Verfahren ein; die Nummer vergibt die Justiz. */
    public CourtCase file(CaseId id, PolityId court, CaseKind kind, ActorRef plaintiff, ActorRef defendant,
                          String charge, String target, Optional<CaseId> appealOf, Day day) {
        if (plaintiff.equals(defendant)) {
            throw new IllegalArgumentException("Niemand verklagt sich selbst");
        }
        CourtCase filed = new CourtCase(id, ++lastNumber, court, kind, plaintiff, defendant, charge, target,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), Stage.FILED, day, day.plus(FILING_DAYS), Optional.empty(), appealOf);
        cases.add(filed);
        while (cases.size() > KEPT_CASES) {
            cases.removeFirst();
        }
        raise(new JusticeEvent.CaseChanged(id, filed.number(), filed.stage(), day));
        return filed;
    }

    /** Eröffnet das Hauptverfahren mit Richter, Staatsanwalt und Geschworenen. */
    public CourtCase open(CaseId id, ActorRef judge, Optional<ActorRef> prosecutor, List<ActorRef> jurors, Day day) {
        CourtCase found = require(id, Stage.FILED);
        CourtCase staffed = found.staffed(Optional.of(judge), prosecutor, found.defense(), jurors)
                .at(Stage.EVIDENCE, day.plus(EVIDENCE_DAYS));
        return replace(staffed, day);
    }

    public CourtCase dismiss(CaseId id, ActorRef judge, String reason, Day day) {
        CourtCase found = requireOpen(id);
        if (found.stage() == Stage.DECIDED) {
            throw new IllegalStateException("Ein Urteil wird nicht eingestellt, nur angefochten");
        }
        CourtCase dismissed = found.staffed(Optional.of(judge), found.prosecutor(), found.defense(), found.jurors())
                .withStatement(new Statement(judge, Role.JUDGE, reason, 0, day)).at(Stage.DISMISSED, day);
        return replace(dismissed, day);
    }

    /** Bestellt einen Verteidiger. */
    public CourtCase assignDefense(CaseId id, ActorRef lawyer, Day day) {
        CourtCase found = requireOpen(id);
        if (found.roleOf(lawyer).filter(role -> role != Role.DEFENSE).isPresent()) {
            throw new IllegalArgumentException("Schon anders beteiligt");
        }
        return replace(found.staffed(found.judge(), found.prosecutor(), Optional.of(lawyer), found.jurors()), day);
    }

    public CourtCase summon(CaseId id, ActorRef witness, Day day) {
        CourtCase found = requireOpen(id);
        if (found.witnesses().size() >= CourtCase.MAX_WITNESSES) {
            throw new IllegalStateException("Höchstens " + CourtCase.MAX_WITNESSES + " Zeugen");
        }
        if (found.roleOf(witness).filter(role -> role != Role.WITNESS).isPresent()) {
            throw new IllegalArgumentException("Schon anders beteiligt");
        }
        return replace(found.withWitness(witness), day);
    }

    public CourtCase submit(CaseId id, ActorRef by, Side side, String kind, String title, String content, int weight,
                            Day day) {
        CourtCase found = requireOpen(id);
        if (found.stage() != Stage.FILED && found.stage() != Stage.EVIDENCE && found.stage() != Stage.PLEADINGS) {
            throw new IllegalStateException("Beweise nur bis zu den Plädoyers");
        }
        if (found.evidence().size() >= CourtCase.MAX_EVIDENCE) {
            throw new IllegalStateException("Höchstens " + CourtCase.MAX_EVIDENCE + " Beweise");
        }
        Evidence evidence = new Evidence(found.evidence().size() + 1, by, side, kind, title, content, weight, day);
        return replace(found.withEvidence(evidence), day);
    }

    public CourtCase state(CaseId id, Statement statement, Day day) {
        CourtCase found = requireOpen(id);
        if (found.statements().size() >= CourtCase.MAX_STATEMENTS) {
            throw new IllegalStateException("Höchstens " + CourtCase.MAX_STATEMENTS + " Aussagen");
        }
        return replace(found.withStatement(statement), day);
    }

    public CourtCase vote(CaseId id, ActorRef juror, boolean guilty, Day day) {
        CourtCase found = requireOpen(id);
        if (found.stage() != Stage.PLEADINGS && found.stage() != Stage.DELIBERATION) {
            throw new IllegalStateException("Geschworene stimmen nach der Beweisaufnahme ab");
        }
        if (!found.jurors().contains(juror)) {
            throw new IllegalArgumentException("Kein Geschworener");
        }
        return replace(found.withVote(juror, guilty), day);
    }

    /** Schaltet in den nächsten Abschnitt; aus der Beratung heraus nur über {@link #decide}. */
    public CourtCase advance(CaseId id, Day day) {
        CourtCase found = requireOpen(id);
        return switch (found.stage()) {
            case EVIDENCE -> replace(found.at(Stage.PLEADINGS, day.plus(STAGE_DAYS)), day);
            case PLEADINGS -> replace(found.at(Stage.DELIBERATION, day.plus(STAGE_DAYS)), day);
            default -> throw new IllegalStateException("Nicht weiterschaltbar: " + found.stage());
        };
    }

    public CourtCase decide(CaseId id, Verdict verdict, Day day) {
        CourtCase found = requireOpen(id);
        if (found.stage() == Stage.FILED) {
            throw new IllegalStateException("Erst eröffnen");
        }
        return replace(found.decided(verdict, day.plus(APPEAL_DAYS)), day);
    }

    /** Das Urteil wird rechtskräftig. */
    public CourtCase finalise(CaseId id, Day day) {
        CourtCase found = require(id, Stage.DECIDED);
        return replace(found.at(Stage.FINAL, day), day);
    }

    /** Die Vorinstanz geht in Berufung; das neue Verfahren legt der Aufrufer mit {@link #file} an. */
    public CourtCase markAppealed(CaseId id, Day day) {
        CourtCase found = require(id, Stage.DECIDED);
        return replace(found.at(Stage.APPEALED, day), day);
    }

    public void exile(Exile exile, Day day) {
        exiles.removeIf(old -> old.polity().equals(exile.polity()) && old.actor().equals(exile.actor()));
        exiles.add(exile);
        exiles.removeIf(old -> old.until().isBefore(day));
        raise(new JusticeEvent.CourtChanged(exile.polity(), day));
    }

    /** Vergisst Personal und Verbannungen einer aufgelösten Polity; ihre Verfahren bleiben als Archiv. */
    public void forget(PolityId court, Day day) {
        boolean changed = staff.remove(court) != null;
        changed |= exiles.removeIf(exile -> exile.polity().equals(court));
        if (changed) {
            raise(new JusticeEvent.CourtChanged(court, day));
        }
    }

    private CourtCase require(CaseId id, Stage stage) {
        CourtCase found = courtCase(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Verfahren"));
        if (found.stage() != stage) {
            throw new IllegalStateException("Verfahren ist " + found.stage() + ", nicht " + stage);
        }
        return found;
    }

    private CourtCase requireOpen(CaseId id) {
        CourtCase found = courtCase(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Verfahren"));
        if (!found.stage().open()) {
            throw new IllegalStateException("Verfahren ist abgeschlossen");
        }
        return found;
    }

    private CourtCase replace(CourtCase updated, Day day) {
        for (int index = 0; index < cases.size(); index++) {
            if (cases.get(index).id().equals(updated.id())) {
                cases.set(index, updated);
            }
        }
        raise(new JusticeEvent.CaseChanged(updated.id(), updated.number(), updated.stage(), day));
        return updated;
    }

    @Override
    public List<CourtCase> cases() {
        return List.copyOf(cases);
    }

    @Override
    public Map<PolityId, CourtStaff> staff() {
        return Map.copyOf(staff);
    }

    @Override
    public List<Exile> exiles() {
        return List.copyOf(exiles);
    }
}

package de.etiennebader.civ.application.justice;

import de.etiennebader.civ.application.civic.CivicUseCases;
import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.justice.CaseId;
import de.etiennebader.civ.domain.justice.CaseKind;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.justice.CourtStaff;
import de.etiennebader.civ.domain.justice.Evidence;
import de.etiennebader.civ.domain.justice.Exile;
import de.etiennebader.civ.domain.justice.Judiciary;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.justice.Side;
import de.etiennebader.civ.domain.justice.Stage;
import de.etiennebader.civ.domain.justice.Statement;
import de.etiennebader.civ.domain.justice.Verdict;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Gerichtsverfahren von der Klage bis zur Vollstreckung.
 *
 * <ul>
 *   <li>Jede Gemeinde und jeder Bund hat ein Gericht. Richter und Staatsanwälte ernennt die Spitze; ohne Richter
 *       verhandelt die Spitze selbst, ist sie Partei, ein Dorfbewohner.</li>
 *   <li>Wer klagt, zahlt die Gerichtsgebühr ({@link StandardParameters#COURT_FEE}); gewinnt er, erstattet sie der
 *       Beklagte.</li>
 *   <li>Strafverfahren haben Geschworene ({@link StandardParameters#JURY_SIZE}), gezogen aus Bürgern und
 *       Dorfbewohnern; Dorfbewohner stimmen nach der Beweislage ({@link CourtReasoning}).</li>
 *   <li>Rechtskräftige Urteile werden vollstreckt: Geldstrafe an die Kasse, Schadenersatz an den Kläger, Verbannung
 *       ({@link StandardParameters#EXILE_ALLOWED}), Amtsenthebung, Nichtigkeit eines Gesetzes.</li>
 * </ul>
 */
public final class JusticeUseCases {

    public static final long EXILE_DAYS = 30;

    /** Was am Tagesende in einem Verfahren geschah. */
    public record Outcome(CourtCase courtCase, String event) {
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public JusticeUseCases(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    private WorldQueries queries() {
        return new WorldQueries(aggregates.repositories());
    }

    // ---- Personal ----

    public void appoint(ActorRef head, PolityId court, Role role, ActorRef person, boolean appointed, Day day) {
        requireHead(head, aggregates.polity(court));
        Judiciary judiciary = aggregates.justice();
        judiciary.appoint(court, role, person, appointed, day);
        aggregates.commit(judiciary);
    }

    // ---- Klage ----

    public CourtCase file(ActorRef plaintiff, PolityId court, CaseKind kind, ActorRef defendant, String charge,
                          String target, Day day) {
        Polity polity = aggregates.polity(court);
        if (polity.kind() != PolityKind.TERRITORIAL && polity.kind() != PolityKind.FEDERATION) {
            throw new IllegalArgumentException("Nur Gemeinden und Bünde haben Gerichte");
        }
        if (kind == CaseKind.CONSTITUTIONAL) {
            defendant = polity.asActor();
            statuteFor(polity, target);
        } else if (!belongs(defendant, polity)) {
            throw new IllegalArgumentException("Der Beklagte gehört nicht zum Gerichtsbezirk");
        }
        if (kind == CaseKind.CIVIL) {
            parseAmount(target);
        }
        long fee = polity.laws().valueOf(StandardParameters.COURT_FEE);
        if (fee > 0) {
            Ledger ledger = aggregates.ledger();
            ledger.transfer(plaintiff, polity.asActor(), Money.of(fee, polity.currency()), TransferReason.FEE, day);
            aggregates.commit(ledger);
        }
        Judiciary judiciary = aggregates.justice();
        CourtCase filed = judiciary.file(CaseId.generate(ids), court, kind, plaintiff, defendant, charge, target,
                Optional.empty(), day);
        aggregates.commit(judiciary);
        return filed;
    }

    // ---- Verhandlung ----

    /** Eröffnet das Hauptverfahren; der Eröffnende ist Richter. */
    public CourtCase open(ActorRef judge, CaseId id, Day day) {
        CourtCase found = require(id);
        requireJudge(judge, found);
        return openWith(found, judge, day);
    }

    public CourtCase dismiss(ActorRef judge, CaseId id, String reason, Day day) {
        CourtCase found = require(id);
        requireJudge(judge, found);
        Judiciary judiciary = aggregates.justice();
        CourtCase dismissed = judiciary.dismiss(id, judge, reason.isBlank() ? "Abgewiesen." : reason, day);
        aggregates.commit(judiciary);
        return dismissed;
    }

    /** Der Angeklagte bestellt einen Verteidiger (Spieler oder Dorfbewohner). */
    public CourtCase assignDefense(ActorRef defendant, CaseId id, ActorRef lawyer, Day day) {
        CourtCase found = require(id);
        if (!found.defendant().equals(defendant) && !isHead(defendant, found)) {
            throw new IllegalArgumentException("Nur der Angeklagte bestellt seinen Verteidiger");
        }
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.assignDefense(id, lawyer, day);
        aggregates.commit(judiciary);
        return updated;
    }

    /** Lädt einen Zeugen; Dorfbewohner sagen sofort aus. */
    public CourtCase summon(ActorRef by, CaseId id, ActorRef witness, Day day) {
        CourtCase found = require(id);
        requireParty(by, found);
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.summon(id, witness, day);
        if (witness instanceof PersonRef villager) {
            updated = judiciary.state(id, CourtReasoning.villagerTestimony(villager, updated, day), day);
        }
        aggregates.commit(judiciary);
        return updated;
    }

    public CourtCase submit(ActorRef by, CaseId id, String kind, String title, String content, int weight, Day day) {
        CourtCase found = require(id);
        Side side = found.sideOf(by).orElseThrow(() -> new IllegalArgumentException(
                "Beweise reichen nur Kläger, Anklage, Angeklagter und Verteidigung ein"));
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.submit(id, by, side, kind, title, content, weight, day);
        aggregates.commit(judiciary);
        return updated;
    }

    /** Aussage eines Zeugen oder Einlassung einer Partei während der Beweisaufnahme. */
    public CourtCase testify(ActorRef speaker, CaseId id, String text, Day day) {
        CourtCase found = require(id);
        Role role = found.roleOf(speaker).filter(candidate -> candidate != Role.JUROR && candidate != Role.JUDGE)
                .orElseThrow(() -> new IllegalArgumentException("Nur Zeugen und Parteien sagen aus"));
        if (found.stage() != Stage.EVIDENCE && found.stage() != Stage.FILED) {
            throw new IllegalStateException("Aussagen nur während der Beweisaufnahme");
        }
        return state(id, new Statement(speaker, role, text, CourtReasoning.tendency(text, role), day), day);
    }

    /** Schlussvortrag einer Seite. */
    public CourtCase plead(ActorRef speaker, CaseId id, String text, Day day) {
        CourtCase found = require(id);
        if (found.stage() != Stage.PLEADINGS) {
            throw new IllegalStateException("Plädoyers erst nach der Beweisaufnahme");
        }
        Role role = found.roleOf(speaker).filter(candidate -> found.sideOf(speaker).isPresent())
                .orElseThrow(() -> new IllegalArgumentException("Plädieren dürfen nur die Parteien"));
        return state(id, new Statement(speaker, role, text, CourtReasoning.tendency(text, role), day), day);
    }

    public CourtCase vote(ActorRef juror, CaseId id, boolean guilty, Day day) {
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.vote(id, juror, guilty, day);
        aggregates.commit(judiciary);
        return updated;
    }

    /** Der Richter schaltet vorzeitig in den nächsten Abschnitt. */
    public CourtCase advance(ActorRef judge, CaseId id, Day day) {
        CourtCase found = require(id);
        requireJudge(judge, found);
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.advance(id, day);
        if (updated.stage() == Stage.DELIBERATION) {
            updated = villagerVotes(judiciary, updated, day);
        }
        aggregates.commit(judiciary);
        return updated;
    }

    /**
     * Urteil des Richters. Mit Jury entscheidet die Mehrheit der Geschworenen über schuldig oder nicht; der Richter
     * bestimmt nur die Strafe.
     */
    public CourtCase decide(ActorRef judge, CaseId id, boolean upheld, long fine, long damages, boolean exile,
                            boolean dismissal, String reasoning, Day day) {
        CourtCase found = require(id);
        requireJudge(judge, found);
        if (found.stage() != Stage.DELIBERATION && found.stage() != Stage.PLEADINGS) {
            throw new IllegalStateException("Ein Urteil erst nach der Beweisaufnahme");
        }
        return decideNow(found, judge, upheld, fine, damages, exile, dismissal, reasoning, day);
    }

    /** Berufung der unterlegenen Partei an das Gericht der nächsthöheren Ebene. */
    public CourtCase appeal(ActorRef by, CaseId id, Day day) {
        CourtCase found = require(id);
        if (found.stage() != Stage.DECIDED || found.verdict().isEmpty()) {
            throw new IllegalStateException("Berufung nur gegen ein noch nicht rechtskräftiges Urteil");
        }
        boolean upheld = found.verdict().get().upheld();
        ActorRef loser = upheld ? found.defendant() : found.plaintiff();
        if (!loser.equals(by) && !(upheld && found.defense().filter(by::equals).isPresent())) {
            throw new IllegalArgumentException("Berufung legt nur die unterlegene Partei ein");
        }
        PolityId higher = queries().parentOf(found.court()).map(PolityView::id)
                .orElseThrow(() -> new IllegalStateException("Es gibt kein höheres Gericht"));
        Judiciary judiciary = aggregates.justice();
        judiciary.markAppealed(id, day);
        CourtCase appeal = judiciary.file(CaseId.generate(ids), higher, found.kind(), found.plaintiff(),
                found.defendant(), found.charge(), found.target(), Optional.of(found.id()), day);
        for (Evidence evidence : found.evidence()) {
            appeal = judiciary.submit(appeal.id(), evidence.submittedBy(), evidence.side(), evidence.kind(),
                    evidence.title(), evidence.content(), evidence.weight(), day);
        }
        aggregates.commit(judiciary);
        return appeal;
    }

    // ---- Tagesende ----

    /** Fristen: eröffnen, weiterschalten, nach Aktenlage entscheiden, rechtskräftig werden und vollstrecken. */
    public List<Outcome> endOfDay(Day day) {
        List<Outcome> outcomes = new ArrayList<>();
        Judiciary judiciary = aggregates.justice();
        for (CourtCase found : judiciary.cases()) {
            if (day.isBefore(found.stageUntil()) || aggregates.repositories().polities().findById(found.court()).isEmpty()) {
                continue;
            }
            switch (found.stage()) {
                case FILED -> {
                    ActorRef judge = defaultJudge(found);
                    outcomes.add(new Outcome(openWith(found, judge, day), "opened"));
                }
                case EVIDENCE -> outcomes.add(new Outcome(autoAdvance(found.id(), day), "pleadings"));
                case PLEADINGS -> outcomes.add(new Outcome(autoAdvance(found.id(), day), "deliberation"));
                case DELIBERATION -> {
                    CourtCase current = aggregates.justice().courtCase(found.id()).orElseThrow();
                    boolean upheld = current.hasJury() ? juryVerdict(current) : CourtReasoning.judgeUpholds(current);
                    outcomes.add(new Outcome(decideNow(current, current.judge().orElse(defaultJudge(current)), upheld,
                            suggestedFine(current), suggestedDamages(current), false, false,
                            "Das Gericht entschied nach Aktenlage.", day), "decided"));
                }
                case DECIDED -> outcomes.add(new Outcome(finalise(found.id(), day), "final"));
                default -> {
                }
            }
        }
        return outcomes;
    }

    // ---- intern ----

    private CourtCase openWith(CourtCase found, ActorRef judge, Day day) {
        Polity polity = aggregates.polity(found.court());
        CourtStaff staff = aggregates.justice().staffOf(found.court());
        Optional<ActorRef> prosecutor = found.kind() == CaseKind.CRIMINAL
                ? staff.prosecutors().stream().filter(candidate -> found.roleOf(candidate).isEmpty()).findFirst()
                : Optional.empty();
        List<ActorRef> jurors = found.kind() == CaseKind.CRIMINAL
                ? drawJurors(polity, found, (int) (long) polity.laws().valueOf(StandardParameters.JURY_SIZE), judge)
                : List.of();
        Judiciary judiciary = aggregates.justice();
        CourtCase opened = judiciary.open(found.id(), judge, prosecutor, jurors, day);
        aggregates.commit(judiciary);
        return opened;
    }

    private CourtCase autoAdvance(CaseId id, Day day) {
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.advance(id, day);
        if (updated.stage() == Stage.DELIBERATION) {
            updated = villagerVotes(judiciary, updated, day);
        }
        aggregates.commit(judiciary);
        return updated;
    }

    private CourtCase villagerVotes(Judiciary judiciary, CourtCase found, Day day) {
        CourtCase current = found;
        for (ActorRef juror : found.jurors()) {
            if (juror instanceof PersonRef villager) {
                current = judiciary.vote(found.id(), juror, CourtReasoning.jurorFindsGuilty(villager, current), day);
            }
        }
        return current;
    }

    private static boolean juryVerdict(CourtCase found) {
        long guilty = found.votes().values().stream().filter(Boolean::booleanValue).count();
        long innocent = found.votes().size() - guilty;
        return guilty > innocent;
    }

    private CourtCase decideNow(CourtCase found, ActorRef judge, boolean upheld, long fine, long damages,
                                boolean exile, boolean dismissal, String reasoning, Day day) {
        boolean decision = found.hasJury() ? juryVerdict(found) : upheld;
        Polity polity = aggregates.polity(found.court());
        long maxFine = polity.laws().valueOf(StandardParameters.MAX_FINE);
        int guilty = (int) found.votes().values().stream().filter(Boolean::booleanValue).count();
        Verdict verdict = new Verdict(decision,
                decision && found.kind() == CaseKind.CRIMINAL ? Math.min(maxFine, Math.max(0, fine)) : 0,
                decision && found.kind() == CaseKind.CIVIL ? Math.min(claimed(found), Math.max(0, damages)) : 0,
                decision && exile && found.kind() == CaseKind.CRIMINAL
                        && polity.laws().valueOf(StandardParameters.EXILE_ALLOWED),
                decision && dismissal && found.kind() == CaseKind.CRIMINAL,
                reasoning, judge, guilty, found.votes().size() - guilty, day);
        Judiciary judiciary = aggregates.justice();
        CourtCase decided = judiciary.decide(found.id(), verdict, day);
        aggregates.commit(judiciary);
        return decided;
    }

    /** Rechtskraft und Vollstreckung. */
    private CourtCase finalise(CaseId id, Day day) {
        Judiciary judiciary = aggregates.justice();
        CourtCase found = judiciary.finalise(id, day);
        aggregates.commit(judiciary);
        Verdict verdict = found.verdict().orElseThrow();
        Polity polity = aggregates.polity(found.court());
        Ledger ledger = aggregates.ledger();
        long fee = polity.laws().valueOf(StandardParameters.COURT_FEE);
        if (verdict.upheld()) {
            pay(ledger, found.defendant(), polity.asActor(), verdict.fine(), polity, TransferReason.FINE, day);
            pay(ledger, found.defendant(), found.plaintiff(), verdict.damages(), polity, TransferReason.FINE, day);
            if (found.kind() != CaseKind.CONSTITUTIONAL) {
                pay(ledger, found.defendant(), found.plaintiff(), fee, polity, TransferReason.FEE, day);
            }
        }
        aggregates.commit(ledger);
        if (verdict.upheld() && verdict.dismissal()) {
            for (Office office : Office.values()) {
                if (office.single() && polity.holds(found.defendant(), office)) {
                    polity.vacateOffice(office, day);
                }
            }
        }
        if (verdict.upheld() && verdict.exile() && polity.isMember(found.defendant())) {
            polity.leave(found.defendant(), day);
            Judiciary exiles = aggregates.justice();
            exiles.exile(new Exile(polity.id(), found.defendant(), day.plus(EXILE_DAYS)), day);
            aggregates.commit(exiles);
        }
        if (verdict.upheld() && found.kind() == CaseKind.CONSTITUTIONAL) {
            Optional<EnactedStatute> statute = statuteFor(polity, found.target());
            statute.ifPresent(law -> polity.repeal(law.statute().id(), day));
        }
        aggregates.commit(polity);
        return found;
    }

    private void pay(Ledger ledger, ActorRef from, ActorRef to, long amount, Polity polity, TransferReason reason,
                     Day day) {
        long due = Math.min(amount, ledger.balanceOf(from, polity.currency()).amount());
        if (due > 0) {
            ledger.transfer(from, to, Money.of(due, polity.currency()), reason, day);
        }
    }

    /** Das Gesetz, das eine Verfassungsklage angreift: {@code §n} oder die ID einer Regelung. */
    private static Optional<EnactedStatute> statuteFor(PolityView polity, String target) {
        List<EnactedStatute> inForce = polity.laws().inForce();
        String clean = target.strip().replace("§", "").strip();
        if (clean.matches("\\d+")) {
            int number = Integer.parseInt(clean);
            if (number < 1 || number > inForce.size()) {
                throw new IllegalArgumentException("Kein geltender Paragraph " + target);
            }
            return Optional.of(inForce.get(number - 1));
        }
        for (int index = inForce.size() - 1; index >= 0; index--) {
            EnactedStatute statute = inForce.get(index);
            if (statute.statute().values().stream().anyMatch(value -> value.key().id().equalsIgnoreCase(clean))) {
                return Optional.of(statute);
            }
        }
        throw new IllegalArgumentException("Keine geltende Regel " + target);
    }

    private static long parseAmount(String target) {
        try {
            long amount = Long.parseLong(target.strip());
            if (amount < 0) {
                throw new IllegalArgumentException("Negative Forderung");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Forderung ist keine Zahl: " + target, e);
        }
    }

    private static long claimed(CourtCase found) {
        return found.kind() == CaseKind.CIVIL ? parseAmount(found.target()) : 0;
    }

    /** Vorschlag für die Geldstrafe nach Aktenlage: je nach Beweislage 1 bis 10 Smaragde. */
    /** Strafvorschlag nach Beweislage (in Hundertstel Smaragd). */
    public static long suggestedFine(CourtCase found) {
        return found.kind() == CaseKind.CRIMINAL
                ? 100L * Math.max(1, Math.min(10, Math.round(CourtReasoning.weight(found) * 2))) : 0;
    }

    private static long suggestedDamages(CourtCase found) {
        return claimed(found);
    }

    private List<ActorRef> drawJurors(Polity polity, CourtCase found, int size, ActorRef judge) {
        if (size <= 0) {
            return List.of();
        }
        List<ActorRef> pool = new ArrayList<>();
        for (Membership membership : polity.members()) {
            ActorRef member = membership.member();
            if (found.roleOf(member).isEmpty() && !member.equals(judge)
                    && (member instanceof PersonRef || membership.type() == MembershipType.CITIZEN)
                    && !(member instanceof de.etiennebader.civ.domain.actor.PolityRef)) {
                pool.add(member);
            }
        }
        if (pool.isEmpty()) {
            for (PolityView town : queries().polities()) {
                if (queries().ancestorsOf(town.id()).stream().anyMatch(parent -> parent.id().equals(polity.id()))) {
                    town.members().stream().map(Membership::member)
                            .filter(member -> member instanceof PersonRef && found.roleOf(member).isEmpty())
                            .forEach(pool::add);
                }
            }
        }
        pool.sort(Comparator.comparingLong(member -> member.uuid().getLeastSignificantBits()
                ^ found.id().value().getMostSignificantBits()));
        return List.copyOf(pool.subList(0, Math.min(size, pool.size())));
    }

    /** Richter für einen Fall ohne Zutun: ein ernannter, sonst die Spitze, sonst ein Dorfbewohner. */
    private ActorRef defaultJudge(CourtCase found) {
        Polity polity = aggregates.polity(found.court());
        CourtStaff staff = aggregates.justice().staffOf(found.court());
        Optional<ActorRef> appointed = staff.judges().stream().filter(judge -> found.roleOf(judge).isEmpty()
                || found.judge().filter(judge::equals).isPresent()).findFirst();
        if (appointed.isPresent()) {
            return appointed.get();
        }
        Optional<ActorRef> head = polity.mayor().filter(mayor -> found.roleOf(mayor).isEmpty()
                || found.judge().filter(mayor::equals).isPresent());
        if (head.isPresent()) {
            return head.get();
        }
        return polity.members().stream().map(Membership::member).filter(member -> member instanceof PersonRef)
                .filter(member -> found.roleOf(member).isEmpty()).findFirst().orElse(polity.asActor());
    }

    private void requireJudge(ActorRef actor, CourtCase found) {
        CourtStaff staff = aggregates.justice().staffOf(found.court());
        boolean appointed = staff.judges().contains(actor);
        boolean head = staff.judges().isEmpty() && isHead(actor, found);
        boolean assigned = found.judge().filter(actor::equals).isPresent();
        if (!(appointed || head || assigned) || (found.sideOf(actor).isPresent())) {
            throw new IllegalArgumentException("Nur der Richter dieses Gerichts");
        }
    }

    private static void requireParty(ActorRef actor, CourtCase found) {
        if (found.roleOf(actor).filter(role -> role != Role.JUROR && role != Role.WITNESS).isEmpty()) {
            throw new IllegalArgumentException("Nur Beteiligte laden Zeugen");
        }
    }

    private boolean isHead(ActorRef actor, CourtCase found) {
        return CivicUseCases.isHead(aggregates.polity(found.court()), actor);
    }

    private static void requireHead(ActorRef head, PolityView polity) {
        if (!CivicUseCases.isHead(polity, head)) {
            throw new NotOfficeHolderException(head, Office.MAYOR);
        }
    }

    private CourtCase state(CaseId id, Statement statement, Day day) {
        Judiciary judiciary = aggregates.justice();
        CourtCase updated = judiciary.state(id, statement, day);
        aggregates.commit(judiciary);
        return updated;
    }

    private CourtCase require(CaseId id) {
        return aggregates.justice().courtCase(id).orElseThrow(() -> new IllegalArgumentException("Unbekanntes Verfahren"));
    }

    /** Ob der Akteur zum Gerichtsbezirk gehört: Mitglied der Polity oder einer Gemeinde darunter. */
    private boolean belongs(ActorRef actor, PolityView court) {
        if (court.isMember(actor)) {
            return true;
        }
        return queries().municipalityOf(actor).map(town -> town.id().equals(court.id())
                        || queries().ancestorsOf(town.id()).stream().anyMatch(parent -> parent.id().equals(court.id())))
                .orElse(false);
    }

    /** Ob ein Spieler verbannt ist und der Polity nicht beitreten darf. */
    public boolean isExiled(PolityId polity, PlayerRef player, Day today) {
        return aggregates.justice().isExiled(polity, player, today);
    }
}

package de.etiennebader.civ.server.justice;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.justice.JusticeUseCases;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.justice.CaseKind;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.justice.Stage;
import de.etiennebader.civ.domain.justice.Verdict;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Gerichte im Spiel: Klagen einreichen, Verfahren führen, Urteile sprechen und anzeigen.
 */
public final class JusticeService {

    /** Längste Haftstrafe, die ein Richter verhängen darf. */
    public static final int MAX_JAIL_DAYS = 60;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;

    private final java.util.List<CaseListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private CourtStaffing staffing;

    public void addListener(CaseListener listener) {
        listeners.add(listener);
    }

    public JusticeService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts,
                          Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    private JusticeUseCases justice() {
        return app.justice();
    }

    // ---- Klage ----

    /**
     * {@code /civ [Ebene] gericht klage straf|zivil|verfassung …}.
     *
     * <ul>
     *   <li>straf: {@code <Beklagter> | <Vorwurf>}</li>
     *   <li>zivil: {@code <Beklagter> <Forderung in Smaragden> | <Klagegrund>}</li>
     *   <li>verfassung: {@code <§n oder Regel> | <Begründung>}</li>
     * </ul>
     */
    public Reply file(Caller caller, PolityView court, String kindWord, String input) {
        Optional<CaseKind> kind = kind(kindWord);
        if (kind.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CASE_KIND_UNKNOWN, kindWord));
        }
        int bar = input.indexOf('|');
        if (bar < 0) {
            return Reply.failure(Message.of(MessageKey.CASE_FORMAT));
        }
        String head = input.substring(0, bar).strip();
        String charge = input.substring(bar + 1).strip();
        String[] words = head.split("\\s+");
        return attempt(() -> {
            ActorRef defendant = court.asActor();
            String target = "";
            if (kind.get() == CaseKind.CONSTITUTIONAL) {
                target = head;
            } else {
                Optional<PlayerRef> named = players.findByName(words[0]);
                if (named.isEmpty()) {
                    return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, words[0]));
                }
                defendant = named.get();
                if (kind.get() == CaseKind.CIVIL) {
                    if (words.length < 2) {
                        return Reply.failure(Message.of(MessageKey.CASE_FORMAT));
                    }
                    target = Long.toString(MoneyFormat.parse(words[1]));
                }
            }
            CourtCase filed = justice().file(caller.player(), court.id(), kind.get(), defendant, charge, target,
                    calendar.today());
            notifyParties(filed, Message.of(MessageKey.CASE_FILED_NOTICE, filed.number(), texts.actor(filed.plaintiff()),
                    texts.polityName(filed.court()), filed.charge()));
            return Reply.success(Message.of(MessageKey.CASE_FILED, filed.number(), texts.polityName(filed.court())));
        });
    }

    // ---- Verfahren ----

    public Reply open(Caller caller, int number) {
        return withCase(number, found -> {
            CourtCase opened = justice().open(caller.player(), found.id(), calendar.today());
            notifyParties(opened, Message.of(MessageKey.CASE_OPENED, opened.number(), texts.actor(caller.player()),
                    opened.jurors().size()));
            return Reply.success(Message.of(MessageKey.CASE_OPENED, opened.number(), caller.name(),
                    opened.jurors().size()));
        });
    }

    public Reply dismiss(Caller caller, int number, String reason) {
        return withCase(number, found -> {
            CourtCase dismissed = justice().dismiss(caller.player(), found.id(), reason, calendar.today());
            notifyParties(dismissed, Message.of(MessageKey.CASE_DISMISSED, dismissed.number(),
                    reason.isBlank() ? "-" : reason));
            return Reply.success(Message.of(MessageKey.CASE_DISMISSED, dismissed.number(), reason.isBlank() ? "-" : reason));
        });
    }

    /** {@code verteidiger <Nr> <Spieler|dorfbewohner>}; Dorfbewohner kommen aus {@code villager}. */
    public Reply defense(Caller caller, int number, String name, Optional<PersonRef> villager) {
        return withCase(number, found -> {
            ActorRef lawyer = actorNamed(name, villager);
            CourtCase updated = justice().assignDefense(caller.player(), found.id(), lawyer, calendar.today());
            notifyParties(updated, Message.of(MessageKey.CASE_DEFENSE, updated.number(), texts.actor(lawyer)));
            return Reply.success(Message.of(MessageKey.CASE_DEFENSE, updated.number(), texts.actor(lawyer)));
        });
    }

    public Reply summon(Caller caller, int number, String name, Optional<PersonRef> villager) {
        return withCase(number, found -> {
            ActorRef witness = actorNamed(name, villager);
            CourtCase updated = justice().summon(caller.player(), found.id(), witness, calendar.today());
            if (witness instanceof PlayerRef player) {
                announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.CASE_SUMMONED_YOU,
                        updated.number(), texts.polityName(updated.court()))));
            }
            return Reply.success(Message.of(MessageKey.CASE_SUMMONED, texts.actor(witness), updated.number()));
        });
    }

    /** Beweis: Text eines Buchs, Name eines Gegenstands oder freier Text; Gewicht nach Art. */
    public Reply evidence(Caller caller, int number, String kind, String title, String content) {
        int weight = switch (kind) {
            case "vertrag" -> 3;
            case "buch", "artikel", "brief" -> 2;
            default -> 1;
        };
        return withCase(number, found -> {
            CourtCase updated = justice().submit(caller.player(), found.id(), kind, title, content, weight,
                    calendar.today());
            return Reply.success(Message.of(MessageKey.CASE_EVIDENCE, updated.evidence().size(), updated.number(), title));
        });
    }

    /** Beweis aus einem Vertrag: {@code beweis <Nr> vertrag <Vertragsnummer>}. */
    public Reply contractEvidence(Caller caller, int number, int contract) {
        return queries.contracts().contracts().stream().filter(found -> found.number() == contract).findFirst()
                .map(found -> evidence(caller, number, "vertrag", "Vertrag #" + contract + ": " + found.title(),
                        found.kind() + " " + texts.actor(found.offeror()) + " / " + texts.actor(found.counterparty())
                                + ": " + found.text()))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.CASE_UNKNOWN_EVIDENCE, "Vertrag #" + contract)));
    }

    /** Beweis aus einem Zeitungsartikel: {@code beweis <Nr> artikel <Artikelnummer>}. */
    public Reply articleEvidence(Caller caller, int number, int article) {
        return queries.press().articles().stream().filter(found -> found.number() == article).findFirst()
                .map(found -> evidence(caller, number, "artikel", "Artikel #" + article + ": " + found.title(),
                        found.body()))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.CASE_UNKNOWN_EVIDENCE, "Artikel #" + article)));
    }

    public Reply testify(Caller caller, int number, String text) {
        return withCase(number, found -> {
            CourtCase updated = justice().testify(caller.player(), found.id(), text, calendar.today());
            int tendency = updated.statements().getLast().tendency();
            notifyParties(updated, Message.of(MessageKey.CASE_STATEMENT, updated.number(), caller.name(), text));
            return Reply.success(Message.of(MessageKey.CASE_STATEMENT_RECORDED, tendencyWord(tendency)));
        });
    }

    public Reply plead(Caller caller, int number, String text) {
        return withCase(number, found -> {
            CourtCase updated = justice().plead(caller.player(), found.id(), text, calendar.today());
            notifyParties(updated, Message.of(MessageKey.CASE_PLEA, updated.number(), caller.name(), text));
            return Reply.success(Message.of(MessageKey.CASE_PLEA_RECORDED, updated.number()));
        });
    }

    public Reply vote(Caller caller, int number, boolean guilty) {
        return withCase(number, found -> {
            justice().vote(caller.player(), found.id(), guilty, calendar.today());
            return Reply.success(Message.of(MessageKey.CASE_VOTED, number));
        });
    }

    public Reply advance(Caller caller, int number) {
        return withCase(number, found -> {
            CourtCase updated = justice().advance(caller.player(), found.id(), calendar.today());
            notifyParties(updated, Message.of(MessageKey.CASE_STAGE, updated.number(), stageName(updated.stage()),
                    updated.stageUntil().value()));
            return Reply.success(Message.of(MessageKey.CASE_STAGE, updated.number(), stageName(updated.stage()),
                    updated.stageUntil().value()));
        });
    }

    /**
     * {@code urteil <Nr> schuldig|freispruch [Strafe in Smaragden] [verbannen] [absetzen] | Begründung}. Ohne
     * Strafangabe schlägt das Gericht nach Beweislage eine vor.
     */
    public Reply decide(Caller caller, int number, String input) {
        int bar = input.indexOf('|');
        String head = (bar < 0 ? input : input.substring(0, bar)).strip().toLowerCase(Locale.ROOT);
        String reasoning = bar < 0 ? "" : input.substring(bar + 1).strip();
        String[] words = head.split("\\s+");
        int jail = 0;
        List<String> kept = new java.util.ArrayList<>();
        for (int index = 0; index < words.length; index++) {
            if ((words[index].equals("haft") || words[index].equals("jail")) && index + 1 < words.length) {
                try {
                    jail = Math.max(0, Math.min(MAX_JAIL_DAYS, Integer.parseInt(words[++index])));
                } catch (NumberFormatException e) {
                    return Reply.failure(Message.of(MessageKey.CASE_VERDICT_FORMAT));
                }
            } else {
                kept.add(words[index]);
            }
        }
        words = kept.toArray(new String[0]);
        int jailDays = jail;
        if (words.length == 0 || words[0].isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CASE_VERDICT_FORMAT));
        }
        boolean upheld = switch (words[0]) {
            case "schuldig", "stattgegeben", "nichtig", "guilty", "upheld" -> true;
            case "freispruch", "unschuldig", "abgewiesen", "innocent", "dismissed" -> false;
            default -> false;
        };
        if (!List.of("schuldig", "stattgegeben", "nichtig", "guilty", "upheld", "freispruch", "unschuldig",
                "abgewiesen", "innocent", "dismissed").contains(words[0])) {
            return Reply.failure(Message.of(MessageKey.CASE_VERDICT_FORMAT));
        }
        long amount = -1;
        boolean exile = false;
        boolean dismissal = false;
        for (int index = 1; index < words.length; index++) {
            switch (words[index]) {
                case "verbannen", "verbannung", "exile" -> exile = true;
                case "absetzen", "amtsenthebung", "dismiss" -> dismissal = true;
                default -> {
                    try {
                        amount = MoneyFormat.parse(words[index]);
                    } catch (IllegalArgumentException ignored) {
                        return Reply.failure(Message.of(MessageKey.CASE_VERDICT_FORMAT));
                    }
                }
            }
        }
        long sentence = amount;
        boolean banish = exile;
        boolean remove = dismissal;
        return withCase(number, found -> {
            long fine = sentence >= 0 ? sentence : JusticeUseCases.suggestedFine(found);
            long damages = sentence >= 0 ? sentence : found.kind() == CaseKind.CIVIL ? Long.parseLong(found.target()) : 0;
            CourtCase decided = justice().decide(caller.player(), found.id(), upheld, fine, damages, banish, remove,
                    reasoning.isEmpty() ? "-" : reasoning, calendar.today());
            Message line = verdictMessage(decided);
            announcements.add(Announcement.toTown(decided.court(), line));
            if (jailDays > 0 && decided.verdict().filter(Verdict::upheld).isPresent()) {
                listeners.forEach(listener -> listener.sentenced(decided, jailDays));
            }
            return Reply.success(line);
        });
    }

    public Reply appeal(Caller caller, int number) {
        return withCase(number, found -> {
            CourtCase appeal = justice().appeal(caller.player(), found.id(), calendar.today());
            notifyParties(appeal, Message.of(MessageKey.CASE_APPEALED, number, appeal.number(),
                    texts.polityName(appeal.court())));
            return Reply.success(Message.of(MessageKey.CASE_APPEALED, number, appeal.number(),
                    texts.polityName(appeal.court())));
        });
    }

    /** {@code richter|staatsanwalt ernennen|entlassen <Spieler|dorfbewohner>} (nur die Spitze). */
    public Reply appoint(Caller caller, PolityView court, Role role, String name, Optional<PersonRef> villager,
                         boolean appointed) {
        return attempt(() -> {
            ActorRef person = actorNamed(name, villager);
            justice().appoint(caller.player(), court.id(), role, person, appointed, calendar.today());
            return Reply.success(Message.of(appointed ? MessageKey.COURT_APPOINTED : MessageKey.COURT_RELEASED,
                    texts.actor(person), roleName(role), court.name().value()));
        });
    }

    // ---- Anzeigen ----

    /** {@code /civ gericht} bzw. {@code /civ <Ebene> gericht}: Personal und Verfahren eines Gerichts. */
    public Reply court(Caller caller, PolityView court) {
        List<Message> lines = new ArrayList<>();
        var staff = queries.justice().staffOf(court.id());
        lines.add(Message.of(MessageKey.COURT_HEADER, court.name().value(),
                staff.judges().isEmpty() ? court.mayor().map(texts::actor).orElse("-")
                        : String.join(", ", staff.judges().stream().map(texts::actor).toList()),
                staff.prosecutors().isEmpty() ? "-" : String.join(", ", staff.prosecutors().stream().map(texts::actor).toList())));
        List<CourtCase> cases = queries.justice().casesOf(court.id());
        if (cases.isEmpty()) {
            lines.add(Message.of(MessageKey.COURT_NO_CASES));
        }
        for (CourtCase found : cases.reversed().subList(0, Math.min(10, cases.size()))) {
            lines.add(caseLine(found));
        }
        return Reply.success(lines);
    }

    /** {@code /civ gericht faelle}: Verfahren, an denen man beteiligt ist. */
    public Reply mine(Caller caller) {
        List<CourtCase> cases = queries.justice().casesInvolving(caller.player());
        if (cases.isEmpty()) {
            return Reply.success(Message.of(MessageKey.COURT_NO_CASES));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.CASES_MINE));
        cases.reversed().stream().limit(15).forEach(found -> lines.add(caseLine(found)));
        return Reply.success(lines);
    }

    /** {@code /civ gericht fall <Nr>}: alles zu einem Verfahren. */
    public Reply show(Caller caller, int number) {
        return withCase(number, found -> {
            CaseView view = view(found, Optional.of(caller.player()));
            List<Message> lines = new ArrayList<>();
            lines.add(caseLine(found));
            lines.add(Message.of(MessageKey.CASE_PARTIES, view.plaintiff(), view.defendant(),
                    view.judge().orElse("-"), view.prosecutor().orElse("-"), view.defense().orElse("-")));
            if (!view.target().isEmpty()) {
                lines.add(Message.of(MessageKey.CASE_TARGET, view.target()));
            }
            if (!view.witnesses().isEmpty()) {
                lines.add(Message.of(MessageKey.CASE_WITNESSES, String.join(", ", view.witnesses())));
            }
            if (!view.jurors().isEmpty()) {
                lines.add(Message.of(MessageKey.CASE_JURORS, String.join(", ", view.jurors())));
            }
            view.evidence().forEach(evidence -> lines.add(Message.of(MessageKey.CASE_EVIDENCE_LINE, evidence.number(),
                    evidence.title(), evidence.by(), evidence.side())));
            view.statements().forEach(statement -> lines.add(Message.of(MessageKey.CASE_STATEMENT_LINE,
                    statement.speaker(), statement.role(), statement.text())));
            found.verdict().ifPresent(verdict -> lines.add(verdictMessage(found)));
            view.myRole().ifPresent(role -> lines.add(Message.of(MessageKey.CASE_YOUR_ROLE, role)));
            return Reply.success(lines);
        });
    }

    public List<CaseView> casesOf(PolityView court, ActorRef viewer) {
        return queries.justice().casesOf(court.id()).reversed().stream()
                .map(found -> view(found, Optional.of(viewer))).toList();
    }

    public List<CaseView> casesOf(ActorRef viewer) {
        return queries.justice().casesInvolving(viewer).reversed().stream()
                .map(found -> view(found, Optional.of(viewer))).toList();
    }

    public List<CaseView> allCases(Optional<ActorRef> viewer) {
        return queries.justice().cases().reversed().stream().limit(100).map(found -> view(found, viewer)).toList();
    }

    public Optional<CaseView> caseView(int number, Optional<ActorRef> viewer) {
        return queries.justice().caseNumbered(number).map(found -> view(found, viewer));
    }

    public Optional<CourtCase> courtCase(int number) {
        return queries.justice().caseNumbered(number);
    }

    // ---- Tagesende ----

    public void endOfDay(de.etiennebader.civ.domain.shared.Day day) {
        if (staffing == null) {
            staffing = new CourtStaffing(app, texts, announcements);
        }
        staffing.endOfDay(day);
        for (JusticeUseCases.Outcome outcome : justice().endOfDay(day)) {
            CourtCase found = outcome.courtCase();
            Message line = switch (outcome.event()) {
                case "opened" -> Message.of(MessageKey.CASE_OPENED, found.number(),
                        found.judge().map(texts::actor).orElse("-"), found.jurors().size());
                case "decided" -> verdictMessage(found);
                case "final" -> Message.of(MessageKey.CASE_FINAL, found.number(), enforcement(found));
                default -> Message.of(MessageKey.CASE_STAGE, found.number(), stageName(found.stage()),
                        found.stageUntil().value());
            };
            notifyParties(found, line);
            for (CaseListener listener : listeners) {
                if (outcome.event().equals("opened")) {
                    listener.opened(found);
                } else if (outcome.event().equals("final")) {
                    listener.finalised(found);
                }
            }
            if (outcome.event().equals("decided") || outcome.event().equals("final")) {
                announcements.add(Announcement.toTown(found.court(), line));
            }
        }
    }

    // ---- intern ----

    private Message caseLine(CourtCase found) {
        return Message.of(MessageKey.CASE_LINE, found.number(), kindName(found.kind()), texts.actor(found.plaintiff()),
                texts.actor(found.defendant()), found.charge(), stageName(found.stage()));
    }

    private Message verdictMessage(CourtCase found) {
        Verdict verdict = found.verdict().orElseThrow();
        Object fine = verdict.fine() > 0 ? Message.of(MessageKey.PENALTY_FINE, money(verdict.fine())) : "";
        Object damages = verdict.damages() > 0 ? Message.of(MessageKey.PENALTY_DAMAGES, money(verdict.damages())) : "";
        Object exile = verdict.exile() ? Message.of(MessageKey.PENALTY_EXILE, JusticeUseCases.EXILE_DAYS) : "";
        Object dismissal = verdict.dismissal() ? Message.of(MessageKey.PENALTY_DISMISSAL) : "";
        Message penalties = Message.of(MessageKey.PENALTY_LIST, fine, damages, exile, dismissal);
        Message outcome = Message.of(verdict.upheld()
                ? (found.kind() == CaseKind.CRIMINAL ? MessageKey.VERDICT_GUILTY
                : found.kind() == CaseKind.CIVIL ? MessageKey.VERDICT_UPHELD : MessageKey.VERDICT_VOID)
                : (found.kind() == CaseKind.CRIMINAL ? MessageKey.VERDICT_ACQUITTED : MessageKey.VERDICT_REJECTED));
        String jury = found.hasJury() ? verdict.guiltyVotes() + ":" + verdict.innocentVotes() : "-";
        return Message.of(MessageKey.CASE_VERDICT, found.number(), texts.actor(found.defendant()), outcome,
                penalties, jury, verdict.reasoning());
    }

    private Message enforcement(CourtCase found) {
        Verdict verdict = found.verdict().orElseThrow();
        if (!verdict.upheld()) {
            return Message.of(MessageKey.ENFORCED_NOTHING);
        }
        Object paid = verdict.fine() > 0 || verdict.damages() > 0 ? Message.of(MessageKey.ENFORCED_PAID) : "";
        Object exile = verdict.exile() ? Message.of(MessageKey.PENALTY_EXILE, JusticeUseCases.EXILE_DAYS) : "";
        Object dismissal = verdict.dismissal() ? Message.of(MessageKey.PENALTY_DISMISSAL) : "";
        Object repeal = found.kind() == CaseKind.CONSTITUTIONAL ? Message.of(MessageKey.ENFORCED_VOID, found.target()) : "";
        return Message.of(MessageKey.PENALTY_LIST, paid, exile, dismissal, repeal);
    }

    private static String money(long units) {
        return MoneyFormat.whole(units) + "," + MoneyFormat.cents(units);
    }

    CaseView view(CourtCase found, Optional<ActorRef> viewer) {
        return new CaseView(found.number(), texts.polityName(found.court()), found.kind().name(),
                texts.actor(found.plaintiff()), texts.actor(found.defendant()), found.charge(),
                found.kind() == CaseKind.CIVIL ? money(Long.parseLong(found.target())) : found.target(),
                found.stage().name(), found.stageUntil().value(), found.judge().map(texts::actor),
                found.prosecutor().map(texts::actor), found.defense().map(texts::actor),
                found.witnesses().stream().map(texts::actor).toList(),
                found.jurors().stream().map(juror -> texts.actor(juror) + " " + Optional.ofNullable(found.votes().get(juror))
                        .map(guilty -> guilty ? "✔" : "✘").orElse("?")).toList(),
                found.evidence().stream().map(evidence -> new CaseView.EvidenceLine(evidence.number(),
                        texts.actor(evidence.submittedBy()), evidence.side().name(), evidence.kind(), evidence.title(),
                        evidence.content(), evidence.weight())).toList(),
                found.statements().stream().map(statement -> new CaseView.StatementLine(texts.actor(statement.speaker()),
                        statement.role().name(), statement.text(), statement.tendency())).toList(),
                found.verdict().map(verdict -> verdictMessage(found)),
                viewer.flatMap(found::roleOf).map(Role::name), found.verdict().map(Verdict::upheld).orElse(false),
                found.appealOf().flatMap(id -> queries.justice().courtCase(id)).map(CourtCase::number));
    }

    private void notifyParties(CourtCase found, Message message) {
        List<ActorRef> involved = new ArrayList<>(List.of(found.plaintiff(), found.defendant()));
        found.judge().ifPresent(involved::add);
        found.prosecutor().ifPresent(involved::add);
        found.defense().ifPresent(involved::add);
        involved.addAll(found.witnesses());
        involved.addAll(found.jurors());
        involved.stream().distinct().filter(actor -> actor instanceof PlayerRef)
                .forEach(actor -> announcements.add(Announcement.toPlayer((PlayerRef) actor, message)));
    }

    private ActorRef actorNamed(String name, Optional<PersonRef> villager) {
        if (name.equalsIgnoreCase("dorfbewohner") || name.equalsIgnoreCase("villager")) {
            return villager.orElseThrow(() -> new IllegalArgumentException("Schau einen Dorfbewohner an"));
        }
        return players.findByName(name.strip())
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Spieler: " + name));
    }

    private Reply withCase(int number, java.util.function.Function<CourtCase, Reply> action) {
        Optional<CourtCase> found = queries.justice().caseNumbered(number);
        if (found.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CASE_UNKNOWN, number));
        }
        return attempt(() -> action.apply(found.get()));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Reply.failure(Message.of(MessageKey.CIVIC_REFUSED, e.getMessage()));
        }
    }

    public static Optional<CaseKind> kind(String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "straf", "strafe", "anzeige", "criminal" -> Optional.of(CaseKind.CRIMINAL);
            case "zivil", "civil" -> Optional.of(CaseKind.CIVIL);
            case "verfassung", "verfassungsklage", "constitutional" -> Optional.of(CaseKind.CONSTITUTIONAL);
            default -> Optional.empty();
        };
    }

    public static Message kindName(CaseKind kind) {
        return Message.of("eddies-civ-mod.case_kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    public static Message stageName(Stage stage) {
        return Message.of("eddies-civ-mod.case_stage." + stage.name().toLowerCase(Locale.ROOT));
    }

    public static Message roleName(Role role) {
        return Message.of("eddies-civ-mod.case_role." + role.name().toLowerCase(Locale.ROOT));
    }

    private Message tendencyWord(int tendency) {
        return Message.of("eddies-civ-mod.case_tendency." + (tendency + 2));
    }
}

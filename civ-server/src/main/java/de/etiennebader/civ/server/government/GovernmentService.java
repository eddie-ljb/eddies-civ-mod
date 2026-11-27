package de.etiennebader.civ.server.government;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.government.Budget;
import de.etiennebader.civ.domain.government.Government;
import de.etiennebader.civ.domain.government.GovernmentException;
import de.etiennebader.civ.domain.government.GovernmentRegistry;
import de.etiennebader.civ.domain.government.HeadOfState;
import de.etiennebader.civ.domain.government.Ministry;
import de.etiennebader.civ.domain.government.Selection;
import de.etiennebader.civ.domain.government.StateForm;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Ministerien, Etat, Haushalt und Oberhaupt (Issue #79).
 *
 * <ul>
 *     <li>Das Oberhaupt ist der Bürgermeister der Ebene; Titel, Staatsform, Gehalt, Befugnisse und die Regeln
 *         (Zahl der Ministerien, Haushaltsrhythmus, Mehrheit) legt es als „Verfassung“ der Regierung fest.</li>
 *     <li>Ministerien haben einen Minister und Angestellte; Löhne werden täglich aus der Gemeindekasse gezahlt, aber
 *         nur so weit der Etat des Ministeriums reicht.</li>
 *     <li>Der Haushalt wird vom vorbereitenden Ministerium (oder dem Oberhaupt) entworfen, vom Parlament – ohne
 *         Parlament vom Oberhaupt – abgestimmt und bestimmt dann die Etats der Ministerien.</li>
 * </ul>
 */
public final class GovernmentService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;
    private final boolean villagerPolitics;
    private final GovernmentStore store;
    private final GovernmentRegistry registry;
    private final GovernmentPolitics politics;
    private final GovernmentVeto veto;

    public GovernmentService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts,
                             Announcements announcements, boolean villagerPolitics, Optional<Path> file) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.villagerPolitics = villagerPolitics;
        this.store = new GovernmentStore(file);
        this.registry = store.load();
        this.politics = new GovernmentPolitics(this, app, calendar, players, texts, announcements, villagerPolitics);
        this.veto = new GovernmentVeto(this, app, announcements, villagerPolitics);
        app.motionGate(veto);
    }

    /** Koalitionen, Gesetzesvorschläge der Minister, Förderungen und Ministerwahlen. */
    public GovernmentPolitics politics() {
        return politics;
    }

    /** Veto-Recht des Oberhaupts über beschlossene Gesetze. */
    public GovernmentVeto veto() {
        return veto;
    }

    Day today() {
        return calendar.today();
    }

    void store() {
        store.save(registry);
    }

    boolean villagerPolitics() {
        return villagerPolitics;
    }

    public GovernmentRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------ Anzeige

    /** Übersicht über Oberhaupt, Ministerien und Haushalt der eigenen Gemeinde. */
    public synchronized Reply overview(Caller caller) {
        Optional<PolityView> town = townOf(caller);
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_NO_TOWN));
        }
        Government g = registry.of(town.get().id());
        HeadOfState head = g.head();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.GOV_HEAD_LINE, town.get().name().toString(), head.title(),
                head.selection().name(), head.salary(), yesNo(head.veto()), yesNo(head.appointsGovernment()),
                yesNo(head.callsElections())));
        lines.add(Message.of(MessageKey.GOV_RULES_LINE, g.maxMinistries(), g.budgetInterval(), g.approvalPercent(),
                g.preparer()));
        if (g.ministries().isEmpty()) {
            lines.add(Message.of(MessageKey.GOV_NO_MINISTRIES, String.join(", ", Government.SUGGESTIONS)));
        }
        for (Ministry m : g.ministries()) {
            lines.add(Message.of(MessageKey.GOV_MINISTRY_LINE, m.name(), m.mandate().isEmpty() ? "-" : m.mandate(),
                    m.minister().map(this::nameOf).orElse("-"), m.staff().size(), m.etat(),
                    g.adopted().allocationOf(m.key())));
        }
        lines.addAll(politics.overviewLines(town.get(), g));
        lines.add(g.draft().map(draft -> Message.of(MessageKey.GOV_DRAFT_LINE, draft.total(),
                        g.votes().values().stream().filter(Boolean::booleanValue).count(), g.votes().size()))
                .orElseGet(() -> Message.of(MessageKey.GOV_BUDGET_LINE, g.adopted().total(),
                        g.lastBudgetDay().map(d -> d.value()).orElse(-1L), g.budgetDue(calendar.today())
                                ? yesNo(true) : yesNo(false))));
        return Reply.success(lines);
    }

    // ------------------------------------------------------------------ Oberhaupt und Regeln

    public synchronized Reply configureHead(Caller caller, UnaryOperator<HeadOfState> change) {
        return asHead(caller, (town, g) -> {
            g.configureHead(change.apply(g.head()));
            return Message.of(MessageKey.GOV_HEAD_CONFIGURED, town.name().toString(), g.head().title());
        });
    }

    public synchronized Reply setForm(Caller caller, StateForm form) {
        return configureHead(caller, head -> head.withForm(form));
    }

    public synchronized Reply setTitle(Caller caller, String title) {
        return configureHead(caller, head -> head.withTitle(title));
    }

    public synchronized Reply setHeadSelection(Caller caller, Selection selection) {
        return configureHead(caller, head -> head.withSelection(selection));
    }

    public synchronized Reply setHeadSalary(Caller caller, long salary) {
        return configureHead(caller, head -> head.withSalary(salary));
    }

    public synchronized Reply setPowers(Caller caller, boolean veto, boolean appoints, boolean calls) {
        return configureHead(caller, head -> head.withPowers(veto, appoints, calls));
    }

    public synchronized Reply setRules(Caller caller, int maxMinistries, int intervalDays, int approvalPercent) {
        return asHead(caller, (town, g) -> {
            g.configureRules(maxMinistries, intervalDays, approvalPercent);
            return Message.of(MessageKey.GOV_RULES_LINE, g.maxMinistries(), g.budgetInterval(), g.approvalPercent(),
                    g.preparer());
        });
    }

    public synchronized Reply setPreparer(Caller caller, String ministry) {
        return asHead(caller, (town, g) -> {
            g.configurePreparer(ministry);
            return Message.of(MessageKey.GOV_RULES_LINE, g.maxMinistries(), g.budgetInterval(),
                    g.approvalPercent(), g.preparer());
        });
    }

    // ------------------------------------------------------------------ Ministerien

    public synchronized Reply createMinistry(Caller caller, String name, String mandate, Selection selection) {
        return asHead(caller, (town, g) -> {
            Ministry ministry = g.create(name, mandate, selection);
            return Message.of(MessageKey.GOV_MINISTRY_CREATED, ministry.name(), ministry.key());
        });
    }

    public synchronized Reply dissolveMinistry(Caller caller, String ministry) {
        return asHead(caller, (town, g) -> {
            String name = g.require(ministry).name();
            g.dissolve(ministry);
            return Message.of(MessageKey.GOV_MINISTRY_DISSOLVED, name);
        });
    }

    public synchronized Reply setMandate(Caller caller, String ministry, String mandate) {
        return asHead(caller, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.withMandate(mandate));
            return Message.of(MessageKey.GOV_MINISTRY_UPDATED, next.name());
        });
    }

    public synchronized Reply setMinistrySelection(Caller caller, String ministry, Selection selection) {
        return asHead(caller, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.withSelection(selection));
            return Message.of(MessageKey.GOV_MINISTRY_UPDATED, next.name());
        });
    }

    public synchronized Reply setWages(Caller caller, String ministry, long ministerWage, long staffWage) {
        return asHead(caller, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.withWages(ministerWage, staffWage));
            return Message.of(MessageKey.GOV_MINISTRY_UPDATED, next.name());
        });
    }

    public synchronized Reply appoint(Caller caller, String ministry, String playerName) {
        Optional<PlayerRef> player = players.findByName(playerName.strip());
        if (player.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_UNKNOWN_PLAYER, playerName));
        }
        return asHead(caller, (town, g) -> {
            Ministry next = g.appoint(ministry, player.get());
            announcements.add(Announcement.toTown(town.id(),
                    Message.of(MessageKey.GOV_APPOINTED, nameOf(player.get()), next.name())));
            return Message.of(MessageKey.GOV_APPOINTED, nameOf(player.get()), next.name());
        });
    }

    public synchronized Reply dismiss(Caller caller, String ministry) {
        return asHead(caller, (town, g) -> {
            Ministry next = g.dismissMinister(ministry);
            return Message.of(MessageKey.GOV_DISMISSED, next.name());
        });
    }

    /** Anstellen: der Minister des Ministeriums oder das Oberhaupt. */
    public synchronized Reply hire(Caller caller, String ministry, String playerName) {
        Optional<PlayerRef> player = players.findByName(playerName.strip());
        if (player.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_UNKNOWN_PLAYER, playerName));
        }
        return asHeadOrMinister(caller, ministry, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.hire(player.get()));
            return Message.of(MessageKey.GOV_HIRED, nameOf(player.get()), next.name());
        });
    }

    public synchronized Reply fire(Caller caller, String ministry, String playerName) {
        Optional<PlayerRef> player = players.findByName(playerName.strip());
        if (player.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_UNKNOWN_PLAYER, playerName));
        }
        return asHeadOrMinister(caller, ministry, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.fire(player.get()));
            return Message.of(MessageKey.GOV_FIRED, nameOf(player.get()), next.name());
        });
    }

    // ------------------------------------------------------------------ Haushalt

    /** Setzt den Etat eines Ministeriums im Haushaltsentwurf (Oberhaupt oder vorbereitendes Ministerium). */
    public synchronized Reply draft(Caller caller, String ministry, long amount) {
        return as(caller, (town, g) -> canPrepare(caller, town, g), (town, g) -> {
            Budget draft = g.draftAllocation(ministry, amount);
            return Message.of(MessageKey.GOV_BUDGET_DRAFTED, g.require(ministry).name(), amount, draft.total());
        });
    }

    /** Stimme zum Entwurf: Parlamentsmitglieder, ohne Parlament nur das Oberhaupt. */
    public synchronized Reply vote(Caller caller, boolean inFavour) {
        return as(caller, (town, g) -> voters(town).contains(caller.player()), (town, g) -> {
            g.vote(caller.player(), inFavour);
            return Message.of(MessageKey.GOV_VOTED, yesNo(inFavour));
        });
    }

    /** Beendet die Abstimmung (Oberhaupt oder vorbereitendes Ministerium). */
    public synchronized Reply closeBudget(Caller caller) {
        Optional<PolityView> town = townOf(caller);
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_NO_TOWN));
        }
        Government g = registry.of(town.get().id());
        if (!canPrepare(caller, town.get(), g)) {
            return Reply.failure(Message.of(MessageKey.GOV_NOT_AUTHORIZED));
        }
        try {
            Message result = close(town.get(), g, calendar.today());
            store.save(registry);
            return Reply.success(result);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    private Message close(PolityView town, Government g, Day day) {
        long available = balance(town);
        boolean accepted = g.closeBudget(voters(town).size(), available, day);
        Message result = Message.of(accepted ? MessageKey.GOV_BUDGET_ADOPTED : MessageKey.GOV_BUDGET_REJECTED,
                town.name().toString(), g.adopted().total());
        g.expireSubsidies(day, true);
        announcements.add(Announcement.toTown(town.id(), result));
        return result;
    }

    // ------------------------------------------------------------------ Tagesende

    /**
     * Zahlt Gehalt des Oberhaupts und die Löhne der Ministerien aus der Gemeindekasse, schließt offene
     * Haushaltsabstimmungen, wenn ein neuer Haushalt fällig ist, und lässt Dorfbewohner als Oberhaupt selbst planen.
     */
    public synchronized void endOfDay(Day day) {
        boolean changed = false;
        for (PolityId id : new LinkedHashSet<>(registry.all().keySet())) {
            Optional<PolityView> town = queries.polity(id);
            if (town.isEmpty()) {
                registry.remove(id);
                changed = true;
                continue;
            }
            Government g = registry.of(id);
            veto.endOfDay(town.get(), g, day);
            politics.beforeWages(town.get(), g, day);
            villagerHead(town.get(), g, day);
            if (g.draft().isPresent() && g.budgetDue(day)) {
                try {
                    close(town.get(), g, day);
                } catch (GovernmentException e) {
                    // Entwurf übersteigt die Kasse: bleibt offen und wird am nächsten Tag erneut geprüft.
                }
            }
            payHead(town.get(), g, day);
            for (Government.Wage wage : g.dueWages()) {
                if (pay(town.get(), wage.employee(), wage.amount(), day)) {
                    g.spend(wage.ministry(), wage.amount());
                }
            }
            politics.paySubsidies(town.get(), g, day);
            changed = true;
        }
        if (changed) {
            store.save(registry);
        }
    }

    /** Ein Dorfbewohner als Oberhaupt entwirft und beschließt den Haushalt selbst: gleiche Teile je besetztem Amt. */
    private void villagerHead(PolityView town, Government g, Day day) {
        Optional<ActorRef> mayor = town.mayor(villagerPolitics);
        if (mayor.isEmpty() || mayor.get() instanceof PlayerRef || !g.budgetDue(day) || g.draft().isPresent()) {
            return;
        }
        List<Ministry> staffed = g.ministries().stream().filter(m -> m.minister().isPresent()).toList();
        long share = staffed.isEmpty() ? 0 : balance(town) / 5 / staffed.size();
        if (share <= 0) {
            return;
        }
        staffed.forEach(m -> g.draftAllocation(m.key(), share));
        g.vote(mayor.get(), true);
        for (ActorRef voter : voters(town)) {
            g.vote(voter, true);
        }
    }

    private void payHead(PolityView town, Government g, Day day) {
        long salary = g.head().salary();
        if (salary > 0) {
            town.mayor(villagerPolitics).ifPresent(mayor -> pay(town, mayor, salary, day));
        }
    }

    boolean pay(PolityView town, ActorRef to, long amount, Day day) {
        return pay(town, to, amount, TransferReason.SALARY, day);
    }

    boolean pay(PolityView town, ActorRef to, long amount, TransferReason reason, Day day) {
        try {
            app.transferFunds().execute(new TransferFunds.Command(town.asActor(), to,
                    Money.of(amount, town.currency()), reason, day));
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Hilfen

    interface Action {
        Message run(PolityView town, Government government);
    }

    interface Permission {
        boolean allows(PolityView town, Government government);
    }

    Reply asHead(Caller caller, Action action) {
        return as(caller, (town, g) -> town.holds(caller.player(), Office.MAYOR), action);
    }

    Reply asHeadOrMinister(Caller caller, String ministry, Action action) {
        return as(caller, (town, g) -> town.holds(caller.player(), Office.MAYOR)
                || g.isMinister(caller.player(), ministry), action);
    }

    Reply as(Caller caller, Permission permission, Action action) {
        Optional<PolityView> town = townOf(caller);
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_NO_TOWN));
        }
        Government g = registry.of(town.get().id());
        if (!permission.allows(town.get(), g)) {
            return Reply.failure(Message.of(MessageKey.GOV_NOT_AUTHORIZED));
        }
        try {
            Message result = action.run(town.get(), g);
            store.save(registry);
            return Reply.success(result);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    boolean canPrepare(Caller caller, PolityView town, Government g) {
        return town.holds(caller.player(), Office.MAYOR) || g.isMinister(caller.player(), g.preparer());
    }

    /** Stimmberechtigt: die Mitglieder des Parlaments; gibt es keines, das Oberhaupt. */
    Set<ActorRef> voters(PolityView town) {
        Set<ActorRef> voters = new LinkedHashSet<>();
        Parliament parliament = town.parliament();
        if (parliament.seated()) {
            parliament.seats().forEach(seat -> voters.add(seat.member()));
        } else {
            town.mayor(villagerPolitics).ifPresent(voters::add);
        }
        return voters;
    }

    long balance(PolityView town) {
        return queries.ledger().balanceOf(town.asActor(), town.currency()).amount();
    }

    Optional<PolityView> townOf(Caller caller) {
        return queries.municipalityOf(caller.player());
    }

    String nameOf(ActorRef actor) {
        if (actor instanceof PlayerRef player) {
            return players.nameOf(player).orElse("?");
        }
        if (actor instanceof de.etiennebader.civ.domain.actor.PolityRef polity) {
            return queries.polity(polity.id()).map(found -> found.name().toString()).orElse("?");
        }
        return "Dorfbewohner";
    }

    static Message yesNo(boolean value) {
        return Message.of(value ? MessageKey.GOV_YES : MessageKey.GOV_NO);
    }
}

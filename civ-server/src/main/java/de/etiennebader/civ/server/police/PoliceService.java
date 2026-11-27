package de.etiennebader.civ.server.police;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.justice.CaseKind;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.police.CaseLink;
import de.etiennebader.civ.domain.police.CrimeKind;
import de.etiennebader.civ.domain.police.Criminality;
import de.etiennebader.civ.domain.police.Offense;
import de.etiennebader.civ.domain.police.PoliceRank;
import de.etiennebader.civ.domain.police.PoliceRegistry;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.justice.CaseListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Polizei (Issue #84): Dienstgrade mit Befehlskette, Anzeigen, Festnahme, Handschellen und Zellen.
 *
 * <ul>
 *     <li><b>Besetzung:</b> Spieler dienen selbst oder werden vom Bürgermeister bzw. einem Vorgesetzten ernannt;
 *         ohne Spieler stellt die Gemeinde Dorfbewohner ein und befördert sie (siehe {@link #endOfDay}).</li>
 *     <li><b>Gesetze:</b> Sold ({@code police_wage}), Streife ({@code police_patrol}) und Festnahme ohne Anzeige
 *         ({@code police_arrest_without_report}) regelt das Recht der Gemeinde.</li>
 *     <li><b>Kriminalität:</b> Dorfbewohner begehen je nach {@link Criminality} selten Straftaten; Zeugen oder
 *         Streifen bringen sie zur Anzeige, die Streife nimmt Täter fest, sofern eine Zelle frei ist.</li>
 *     <li><b>Handschellen:</b> Gefesselte und Inhaftierte können nicht schlagen, abbauen oder kämpfen
 *         ({@link #restrained}).</li>
 * </ul>
 */
public final class PoliceService implements CaseListener {

    static final int WITNESS_PERCENT = 60;
    static final int STALE_DAYS = 7;
    static final int MAX_FORCE = 12;

    private final CivApplication app;
    private final WorldQueries queries;
    private final de.etiennebader.civ.application.day.Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;
    private final ServerSettings settings;
    private final PoliceStore store;
    private final PoliceRegistry registry;

    public PoliceService(CivApplication app, de.etiennebader.civ.application.day.Calendar calendar,
                         PlayerDirectory players, Texts texts, Announcements announcements, ServerSettings settings,
                         Optional<Path> file) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = new PoliceStore(file);
        this.registry = store.load();
    }

    public PoliceRegistry registry() {
        return registry;
    }

    /** Ob die Person gefesselt oder inhaftiert ist und deshalb nicht schlagen, abbauen oder kämpfen darf. */
    public synchronized boolean restrained(ActorRef person) {
        return registry.restrained(person, calendar.today());
    }

    /** Kennungen der Dorfbewohner, die gerade gefesselt oder inhaftiert sind (das Spiel hält sie dann fest). */
    public synchronized java.util.Set<java.util.UUID> restrainedVillagers() {
        Day today = calendar.today();
        return registry.restrainedActors(today).stream().filter(PersonRef.class::isInstance)
                .map(ActorRef::uuid).collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------------ Dienst

    /** Ein Spieler tritt als Kadett in die Polizei seiner Gemeinde ein. */
    public synchronized Reply join(Caller caller) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        PolityView t = town.get();
        if (registry.rankOf(t.id(), caller.player()).isPresent()) {
            return fail(MessageKey.POLICE_ALREADY_SERVING);
        }
        if (registry.strength(t.id()) >= MAX_FORCE && !t.holds(caller.player(), Office.MAYOR)) {
            return fail(MessageKey.POLICE_FORCE_FULL);
        }
        PoliceRank rank = t.holds(caller.player(), Office.MAYOR) ? PoliceRank.COMMISSIONER : PoliceRank.CADET;
        registry.appoint(t.id(), caller.player(), rank);
        save();
        return Reply.success(Message.of(MessageKey.POLICE_JOINED, t.name().toString(), rankName(rank)));
    }

    public synchronized Reply leave(Caller caller) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        if (!registry.dismiss(town.get().id(), caller.player())) {
            return fail(MessageKey.POLICE_NOT_ON_DUTY);
        }
        save();
        return Reply.success(Message.of(MessageKey.POLICE_LEFT, town.get().name().toString()));
    }

    /**
     * Ernennt, befördert oder degradiert einen Spieler. Der Bürgermeister darf jeden Rang vergeben; sonst nur ein
     * Vorgesetzter, und zwar nur Ränge unter dem eigenen und nur für Untergebene (oder Neue).
     */
    public synchronized Reply appoint(Caller caller, String name, PoliceRank rank) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        Optional<PlayerRef> target = players.findByName(name.strip());
        if (target.isEmpty()) {
            return fail(MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        PolityView t = town.get();
        if (!t.isMember(target.get())) {
            return fail(MessageKey.POLICE_TARGET_NOT_MEMBER);
        }
        if (!mayCommand(t, caller.player(), target.get(), rank)) {
            return fail(MessageKey.POLICE_NOT_COMMANDER);
        }
        if (registry.rankOf(t.id(), target.get()).isEmpty() && registry.strength(t.id()) >= MAX_FORCE) {
            return fail(MessageKey.POLICE_FORCE_FULL);
        }
        registry.appoint(t.id(), target.get(), rank);
        save();
        return Reply.success(Message.of(MessageKey.POLICE_APPOINTED, texts.actor(target.get()), rankName(rank)));
    }

    public synchronized Reply dismiss(Caller caller, String name) {
        Optional<PolityView> town = ownTown(caller);
        Optional<PlayerRef> target = players.findByName(name.strip());
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        if (target.isEmpty()) {
            return fail(MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        PolityView t = town.get();
        Optional<PoliceRank> current = registry.rankOf(t.id(), target.get());
        if (current.isEmpty()) {
            return fail(MessageKey.POLICE_NOT_ON_DUTY);
        }
        if (!mayCommand(t, caller.player(), target.get(), PoliceRank.CADET)) {
            return fail(MessageKey.POLICE_NOT_COMMANDER);
        }
        registry.dismiss(t.id(), target.get());
        save();
        return Reply.success(Message.of(MessageKey.POLICE_DISMISSED, texts.actor(target.get())));
    }

    /** Nur der Bürgermeister baut Zellen (Anzahl 0 bis {@value PoliceRegistry#MAX_CELLS}). */
    public synchronized Reply cells(Caller caller, int count) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        if (!town.get().holds(caller.player(), Office.MAYOR)) {
            return fail(MessageKey.POLICE_NOT_MAYOR);
        }
        if (count < 0 || count > PoliceRegistry.MAX_CELLS) {
            return fail(MessageKey.POLICE_BAD_CELLS);
        }
        registry.setCells(town.get().id(), count);
        save();
        return Reply.success(Message.of(MessageKey.POLICE_CELLS_SET, count));
    }

    // ------------------------------------------------------------------ Anzeige und Festnahme

    /** Jedes Mitglied kann eine Straftat anzeigen. */
    public synchronized Reply report(Caller caller, String name, CrimeKind kind) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        Optional<PlayerRef> suspect = players.findByName(name.strip());
        if (suspect.isEmpty()) {
            return fail(MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        if (suspect.get().equals(caller.player())) {
            return fail(MessageKey.POLICE_SELF);
        }
        Offense offense = registry.report(town.get().id(), suspect.get(), kind, calendar.today());
        save();
        return Reply.success(Message.of(MessageKey.POLICE_REPORTED, offense.number()));
    }

    public synchronized Reply reports(Caller caller) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        List<Offense> open = registry.openOffenses(town.get().id());
        if (open.isEmpty()) {
            return Reply.success(Message.of(MessageKey.POLICE_NO_REPORTS));
        }
        List<Message> lines = new ArrayList<>();
        for (Offense o : open) {
            lines.add(Message.of(MessageKey.POLICE_REPORT_LINE, o.number(), texts.actor(o.suspect()),
                    crimeName(o.kind()), Long.toString(o.on().value())));
        }
        return Reply.success(lines);
    }

    /**
     * Nimmt fest: Handschellen und, wenn eine Zelle frei ist, Untersuchungshaft. Vorgesetzte sind tabu,
     * ohne Anzeige nur, wenn das Gesetz es erlaubt.
     */
    public synchronized Reply arrest(Caller caller, String name) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        PolityView t = town.get();
        Optional<PoliceRank> own = registry.rankOf(t.id(), caller.player());
        if (own.isEmpty() || !own.get().mayArrest()) {
            return fail(MessageKey.POLICE_RANK_TOO_LOW);
        }
        Optional<PlayerRef> target = players.findByName(name.strip());
        if (target.isEmpty()) {
            return fail(MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        Optional<Reply> refusal = arrestRefusal(t, caller.player(), own.get(), target.get());
        if (refusal.isPresent()) {
            return refusal.get();
        }
        Day today = calendar.today();
        Optional<Offense> offense = registry.openOffenses(t.id()).stream()
                .filter(o -> o.suspect().equals(target.get())).findFirst();
        if (offense.isEmpty() && !allowedWithoutReport(t)) {
            return fail(MessageKey.POLICE_NO_REPORT);
        }
        CrimeKind kind = offense.map(Offense::kind).orElse(CrimeKind.TRESPASS);
        offense.ifPresent(o -> registry.handle(t.id(), o.number()));
        Offense charged = offense.orElseGet(() -> new Offense(0, target.get(), kind, today, true, List.of()));
        fileCase(t, charged, today);
        if (!registry.detain(t.id(), target.get(), today, kind.detentionDays(), true)) {
            registry.cuff(target.get(), today);
            save();
            return Reply.success(Message.of(MessageKey.POLICE_CUFFED_NO_CELL, texts.actor(target.get())));
        }
        save();
        return Reply.success(Message.of(MessageKey.POLICE_ARRESTED, texts.actor(target.get()), kind.detentionDays()));
    }

    /** Legt Handschellen an (ohne Zelle); sie halten bis zum nächsten Tag oder bis zum Lösen. */
    public synchronized Reply cuff(Caller caller, String name) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        PolityView t = town.get();
        Optional<PoliceRank> own = registry.rankOf(t.id(), caller.player());
        if (own.isEmpty() || !own.get().mayArrest()) {
            return fail(MessageKey.POLICE_RANK_TOO_LOW);
        }
        Optional<PlayerRef> target = players.findByName(name.strip());
        if (target.isEmpty()) {
            return fail(MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        Optional<Reply> refusal = arrestRefusal(t, caller.player(), own.get(), target.get());
        if (refusal.isPresent()) {
            return refusal.get();
        }
        registry.cuff(target.get(), calendar.today());
        save();
        return Reply.success(Message.of(MessageKey.POLICE_CUFFED, texts.actor(target.get())));
    }

    /**
     * Handschellen als Gegenstand: Rechtsklick auf einen Spieler oder Dorfbewohner fesselt ihn, bei bereits Gefesselten
     * löst derselbe Griff die Handschellen. Es gelten dieselben Rang- und Befehlskettenregeln wie bei den Befehlen.
     */
    public synchronized Reply handcuff(Caller caller, ActorRef target) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        PolityView t = town.get();
        Optional<PoliceRank> own = registry.rankOf(t.id(), caller.player());
        if (own.isEmpty() || !own.get().mayArrest()) {
            return fail(MessageKey.POLICE_RANK_TOO_LOW);
        }
        Optional<Reply> refusal = arrestRefusal(t, caller.player(), own.get(), target);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        Day today = calendar.today();
        if (registry.restrained(target, today)) {
            if (registry.jailOf(target).isPresent() || !registry.uncuff(target)) {
                return fail(MessageKey.POLICE_NOT_CUFFED);
            }
            save();
            return Reply.success(Message.of(MessageKey.POLICE_UNCUFFED, texts.actor(target)));
        }
        registry.cuff(target, today);
        save();
        return Reply.success(Message.of(MessageKey.POLICE_CUFFED, texts.actor(target)));
    }

    public synchronized Reply uncuff(Caller caller, String name) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        Optional<PoliceRank> own = registry.rankOf(town.get().id(), caller.player());
        Optional<PlayerRef> target = players.findByName(name.strip());
        if (own.isEmpty() || !own.get().mayArrest() || target.isEmpty()) {
            return fail(own.isEmpty() || !own.get().mayArrest() ? MessageKey.POLICE_RANK_TOO_LOW
                    : MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        if (target.get().equals(caller.player())) {
            return fail(MessageKey.POLICE_SELF);
        }
        if (!registry.uncuff(target.get())) {
            return fail(MessageKey.POLICE_NOT_CUFFED);
        }
        save();
        return Reply.success(Message.of(MessageKey.POLICE_UNCUFFED, texts.actor(target.get())));
    }

    /** Entlässt aus der Zelle; Bürgermeister oder ab Wachtmeister. */
    public synchronized Reply release(Caller caller, String name) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        PolityView t = town.get();
        boolean mayor = t.holds(caller.player(), Office.MAYOR);
        boolean sergeant = registry.rankOf(t.id(), caller.player())
                .filter(r -> r.level() >= PoliceRank.SERGEANT.level()).isPresent();
        if (!mayor && !sergeant) {
            return fail(MessageKey.POLICE_RANK_TOO_LOW);
        }
        Optional<PlayerRef> target = players.findByName(name.strip());
        if (target.isEmpty()) {
            return fail(MessageKey.POLICE_UNKNOWN_PLAYER);
        }
        if (!registry.release(t.id(), target.get())) {
            return fail(MessageKey.POLICE_NOT_DETAINED);
        }
        registry.uncuff(target.get());
        save();
        return Reply.success(Message.of(MessageKey.POLICE_RELEASED, texts.actor(target.get())));
    }

    public synchronized Reply status(Caller caller) {
        Optional<PolityView> town = ownTown(caller);
        if (town.isEmpty()) {
            return fail(MessageKey.POLICE_NO_TOWN);
        }
        PolityView t = town.get();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.POLICE_STATUS, t.name().toString(), registry.strength(t.id()),
                registry.freeCells(t.id()), registry.cells(t.id()), registry.openOffenses(t.id()).size()));
        registry.forceOf(t.id()).entrySet().stream()
                .sorted(Map.Entry.<ActorRef, PoliceRank>comparingByValue(Comparator.comparingInt(PoliceRank::level))
                        .reversed())
                .forEach(e -> lines.add(Message.of(MessageKey.POLICE_ROSTER_LINE, texts.actor(e.getKey()),
                        rankName(e.getValue()))));
        return Reply.success(lines);
    }

    // ------------------------------------------------------------------ Tagesablauf

    /**
     * Täglich: Handschellen verfallen, Haft endet, die Polizei wird (ohne Spieler) mit Dorfbewohnern besetzt und
     * befördert, Dorfbewohner begehen Straftaten, die Streife nimmt Täter fest, dann wird Sold gezahlt.
     */
    public synchronized void endOfDay(Day day) {
        registry.expireCuffs(day);
        registry.releaseDue(day);
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            pruneForce(town);
            if (settings.ambitions()) {
                villagersJoin(town);
                promote(town);
                crimes(town, day);
            }
            patrol(town, day);
            payWages(town, day);
        }
        save();
    }

    private void pruneForce(PolityView town) {
        for (ActorRef member : registry.forceOf(town.id()).keySet()) {
            if (!town.isMember(member)) {
                registry.dismiss(town.id(), member);
            }
        }
    }

    /** Die Polizei wird auf etwa ein Achtel der Einwohner aufgefüllt; Ordnungsliebende und Wehrhafte melden sich. */
    private void villagersJoin(PolityView town) {
        List<PersonRef> villagers = town.members().stream().map(Membership::member)
                .filter(PersonRef.class::isInstance).map(PersonRef.class::cast).toList();
        int wanted = Math.min(MAX_FORCE, villagers.size() / 8 + (villagers.isEmpty() ? 0 : 1));
        if (registry.strength(town.id()) >= wanted) {
            return;
        }
        villagers.stream()
                .filter(v -> registry.rankOf(town.id(), v).isEmpty() && !registry.restrained(v, calendar.today()))
                .filter(v -> Personality.of(v).order() >= 0)
                .sorted(Comparator.comparingInt((PersonRef v) -> -(Personality.of(v).order()
                        - Personality.of(v).peace() / 2)).thenComparing(v -> v.uuid().toString()))
                .limit(wanted - registry.strength(town.id()))
                .forEach(v -> registry.appoint(town.id(), v, PoliceRank.CADET));
    }

    /** Hierarchie auffüllen: ein Kommissar, je vier Mann ein Wachtmeister, je zwei Mann ein Polizist. */
    private void promote(PolityView town) {
        int size = registry.strength(town.id());
        if (size == 0) {
            return;
        }
        if (registry.count(town.id(), PoliceRank.COMMISSIONER) == 0) {
            promoteOne(town, PoliceRank.COMMISSIONER, PoliceRank.SERGEANT, PoliceRank.OFFICER, PoliceRank.CADET);
        }
        if (size >= 4 && registry.count(town.id(), PoliceRank.SERGEANT) < size / 4) {
            promoteOne(town, PoliceRank.SERGEANT, PoliceRank.OFFICER);
        }
        if (size >= 2 && registry.count(town.id(), PoliceRank.OFFICER) < size / 2) {
            promoteOne(town, PoliceRank.OFFICER, PoliceRank.CADET);
        }
    }

    /** Befördert den dienstältesten Dorfbewohner aus den Quellrängen (Spieler werden nie automatisch befördert). */
    private void promoteOne(PolityView town, PoliceRank to, PoliceRank... from) {
        for (PoliceRank source : from) {
            Optional<ActorRef> pick = registry.forceOf(town.id()).entrySet().stream()
                    .filter(e -> e.getValue() == source && e.getKey() instanceof PersonRef)
                    .map(Map.Entry::getKey).min(Comparator.comparing(a -> ((PersonRef) a).uuid().toString()));
            if (pick.isPresent()) {
                registry.appoint(town.id(), pick.get(), to);
                return;
            }
        }
    }

    /** Dorfbewohner begehen mit ihrer Kriminalität selten Straftaten; Zeugen oder Streifen bringen sie zur Anzeige. */
    private void crimes(PolityView town, Day day) {
        boolean patrolled = patrolling(town);
        for (Membership membership : town.members()) {
            if (!(membership.member() instanceof PersonRef person)
                    || registry.rankOf(town.id(), person).isPresent() || registry.jailOf(person).isPresent()) {
                continue;
            }
            Personality p = Personality.of(person);
            int value = Criminality.of(app.villagerEducation().bonusOf(person), p.order(), p.peace(), p.belonging());
            Random dice = new Random(day.value() * 7919L + person.uuid().getLeastSignificantBits());
            if (dice.nextInt(1000) >= Criminality.dailyChancePerMille(value)) {
                continue;
            }
            CrimeKind kind = Criminality.kindFor(value, dice.nextInt(100));
            List<ActorRef> witnesses = witnessesOf(town, person, dice);
            if (patrolled || !witnesses.isEmpty()) {
                registry.report(town.id(), person, kind, day, witnesses);
            }
        }
    }

    /** Mitbewohner, die die Tat sehen (je 60 %, höchstens {@value Offense#MAX_WITNESSES}) und sie anzeigen. */
    private List<ActorRef> witnessesOf(PolityView town, PersonRef culprit, Random dice) {
        List<ActorRef> seen = new ArrayList<>();
        List<ActorRef> neighbours = town.members().stream().map(Membership::member)
                .filter(m -> m instanceof PersonRef && !m.equals(culprit)).toList();
        for (int i = 0; i < Offense.MAX_WITNESSES && !neighbours.isEmpty(); i++) {
            ActorRef candidate = neighbours.get(dice.nextInt(neighbours.size()));
            if (dice.nextInt(100) < WITNESS_PERCENT && !seen.contains(candidate)) {
                seen.add(candidate);
            }
        }
        return seen;
    }

    private boolean patrolling(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.POLICE_PATROL)
                && registry.count(town.id(), PoliceRank.OFFICER) > 0;
    }

    /**
     * Die Streife bearbeitet täglich so viele Anzeigen gegen Dorfbewohner, wie Polizisten (ab Rang Polizist) im
     * Dienst sind; ohne freie Zelle bleibt die Anzeige offen und verfällt nach {@value #STALE_DAYS} Tagen.
     */
    private void patrol(PolityView town, Day day) {
        int capacity = patrolling(town) ? registry.count(town.id(), PoliceRank.OFFICER) : 0;
        for (Offense offense : registry.openOffenses(town.id())) {
            if (day.value() - offense.on().value() > STALE_DAYS) {
                registry.handle(town.id(), offense.number());
                continue;
            }
            if (capacity == 0 || !(offense.suspect() instanceof PersonRef)) {
                continue;
            }
            if (registry.detain(town.id(), offense.suspect(), day, offense.kind().detentionDays(), false)) {
                registry.handle(town.id(), offense.number());
                fileCase(town, offense, day);
                capacity--;
                announcements.add(Announcement.toAll(Message.of(MessageKey.POLICE_VILLAGER_ARRESTED,
                        texts.actor(offense.suspect()), town.name().toString(), crimeName(offense.kind()))));
            }
        }
    }

    private void payWages(PolityView town, Day day) {
        long wage = queries.effectiveLaws(town).valueOf(StandardParameters.POLICE_WAGE);
        if (wage == 0) {
            return;
        }
        for (Map.Entry<ActorRef, PoliceRank> member : registry.forceOf(town.id()).entrySet()) {
            try {
                app.transferFunds().execute(new TransferFunds.Command(town.asActor(), member.getKey(),
                        Money.of(wage * member.getValue().level(), town.currency()), TransferReason.WAGE, day));
            } catch (DomainException e) {
                if (member.getKey() instanceof PersonRef) {
                    registry.dismiss(town.id(), member.getKey()); // unbezahlte Dorfbewohner kündigen
                }
            }
        }
    }

    // ------------------------------------------------------------------ Gericht (Issue #84)

    /**
     * Die Gemeinde klagt als Staatsanwaltschaft den Festgenommenen an (Strafverfahren) und legt den Polizeibericht
     * als Beweis bei; die Zeugen werden nach Eröffnung geladen. Scheitert das (z. B. kein Gerichtsbezirk, Gebühr
     * nicht zahlbar), bleibt es bei der Untersuchungshaft.
     */
    private void fileCase(PolityView town, Offense offense, Day day) {
        try {
            CourtCase filed = app.justice().file(town.asActor(), town.id(), CaseKind.CRIMINAL, offense.suspect(),
                    chargeOf(offense.kind()), "", day);
            registry.link(new CaseLink(filed.number(), town.id(), offense.suspect(), offense.kind(), 0,
                    offense.witnesses(), false));
            app.justice().submit(town.asActor(), filed.id(), "police_report", "Polizeibericht",
                    "Festnahme wegen " + chargeOf(offense.kind()) + " am Tag " + offense.on().value() + "; "
                            + offense.witnesses().size() + " Zeugen.", 2, day);
        } catch (RuntimeException e) {
            // Haft ohne Verfahren; der Täter kommt nach der Untersuchungshaft frei
        }
    }

    private static String chargeOf(CrimeKind kind) {
        return switch (kind) {
            case THEFT -> "Diebstahl";
            case TRESPASS -> "Hausfriedensbruch";
            case ASSAULT -> "Körperverletzung";
            case MURDER -> "Mord";
        };
    }

    /** Das Verfahren ist eröffnet: Zeugen werden geladen; Dorfbewohner sagen sofort aus. */
    @Override
    public synchronized void opened(CourtCase courtCase) {
        registry.linkOf(courtCase.number()).filter(link -> !link.prepared()).ifPresent(link -> {
            PolityView town = queries.polity(link.polity()).orElse(null);
            if (town != null) {
                for (ActorRef witness : link.witnesses()) {
                    try {
                        app.justice().summon(town.asActor(), courtCase.id(), witness, calendar.today());
                    } catch (RuntimeException e) {
                        // schon beteiligt oder zu viele Zeugen
                    }
                }
            }
            registry.prepared(courtCase.number());
            save();
        });
    }

    /** Berufung: das neue Verfahren übernimmt die Verknüpfung; das Urteil der Vorinstanz vollstreckt dann nichts mehr. */
    @Override
    public synchronized void appealed(CourtCase original, CourtCase appeal) {
        if (registry.moveLink(original.number(), appeal.number())) {
            save();
        }
    }

    /** Der Richter hat Haft verhängt. */
    @Override
    public synchronized void sentenced(CourtCase courtCase, int days) {
        if (registry.sentence(courtCase.number(), days)) {
            save();
        }
    }

    /** Rechtskräftig: bei Schuldspruch sitzt der Täter die Haft ab, sonst wird er freigelassen. */
    @Override
    public synchronized void finalised(CourtCase courtCase) {
        Optional<CaseLink> link = registry.unlink(courtCase.number());
        if (link.isEmpty()) {
            return;
        }
        ActorRef suspect = link.get().suspect();
        Day today = calendar.today();
        PolityId jail = link.get().polity();
        registry.release(jail, suspect);
        registry.uncuff(suspect);
        boolean guilty = courtCase.verdict().filter(v -> v.upheld()).isPresent();
        if (guilty && registry.detain(jail, suspect, today, link.get().effectiveDays(), false)) {
            announcements.add(Announcement.toTown(jail, Message.of(MessageKey.POLICE_SENTENCE_SERVED,
                    texts.actor(suspect), link.get().effectiveDays())));
        } else if (guilty) {
            registry.cuff(suspect, today); // keine freie Zelle: nur gefesselt
        }
        save();
    }

    // ------------------------------------------------------------------ Hilfen

    private Optional<Reply> arrestRefusal(PolityView town, PlayerRef actor, PoliceRank own, ActorRef target) {
        if (target.equals(actor)) {
            return Optional.of(fail(MessageKey.POLICE_SELF));
        }
        Optional<PoliceRank> theirs = registry.rankOf(town.id(), target);
        if (theirs.isPresent() && !own.outranks(theirs.get())) {
            return Optional.of(fail(MessageKey.POLICE_NOT_COMMANDER));
        }
        if (town.holds(target, Office.MAYOR) && !town.holds(actor, Office.MAYOR)) {
            return Optional.of(fail(MessageKey.POLICE_IMMUNE));
        }
        return Optional.empty();
    }

    private boolean allowedWithoutReport(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.POLICE_ARREST_WITHOUT_REPORT);
    }

    /** Bürgermeister dürfen alles; sonst muss der Anweisende über Ziel und neuem Rang stehen. */
    private boolean mayCommand(PolityView town, PlayerRef actor, PlayerRef target, PoliceRank newRank) {
        if (town.holds(actor, Office.MAYOR)) {
            return true;
        }
        Optional<PoliceRank> own = registry.rankOf(town.id(), actor);
        if (own.isEmpty() || !own.get().outranks(newRank)) {
            return false;
        }
        return registry.rankOf(town.id(), target).map(r -> own.get().outranks(r)).orElse(true)
                && !actor.equals(target);
    }

    private Optional<PolityView> ownTown(Caller caller) {
        return queries.municipalityOf(caller.player());
    }

    private static Reply fail(MessageKey key) {
        return Reply.failure(Message.of(key));
    }

    static Message rankName(PoliceRank rank) {
        return Message.of(switch (rank) {
            case CADET -> MessageKey.POLICE_RANK_CADET;
            case OFFICER -> MessageKey.POLICE_RANK_OFFICER;
            case SERGEANT -> MessageKey.POLICE_RANK_SERGEANT;
            case COMMISSIONER -> MessageKey.POLICE_RANK_COMMISSIONER;
        });
    }

    static Message crimeName(CrimeKind kind) {
        return Message.of(switch (kind) {
            case THEFT -> MessageKey.POLICE_CRIME_THEFT;
            case TRESPASS -> MessageKey.POLICE_CRIME_TRESPASS;
            case ASSAULT -> MessageKey.POLICE_CRIME_ASSAULT;
            case MURDER -> MessageKey.POLICE_CRIME_MURDER;
        });
    }

    private void save() {
        store.save(registry);
    }
}

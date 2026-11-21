package de.etiennebader.civ.server.war;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.SpendFunds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.territory.ConquerChunks;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.war.BattleKind;
import de.etiennebader.civ.domain.war.BattleOutcome;
import de.etiennebader.civ.domain.war.BattleReport;
import de.etiennebader.civ.domain.war.Campaign;
import de.etiennebader.civ.domain.war.Intel;
import de.etiennebader.civ.domain.war.MilitaryRank;
import de.etiennebader.civ.domain.war.PeaceTerms;
import de.etiennebader.civ.domain.war.War;
import de.etiennebader.civ.domain.war.WarRegistry;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Krieg zwischen Gemeinden (Issue #27).
 *
 * <ul>
 *     <li><b>Kriegswille:</b> Ob Krieg erklärt werden darf, hängt vom Willen des Volkes ab (siehe {@link Will}):
 *         Persönlichkeit der Dorfbewohner, Richtung der Partei des Bürgermeisters, Ansprüche und Kräfteverhältnis.</li>
 *     <li><b>Gefechte:</b> Feldzüge werden für einen Tag geplant (Angriff, Hinterhalt mit Ausspähern, Verteidigung)
 *         und am Tagesende ausgetragen; sie verschieben die Front. Eine Entscheidungsschlacht beendet den Krieg sofort.
 *         Der Sieger erhält Reparationen und erobert Grenz-Chunks ohne Grundstücke.</li>
 *     <li><b>Truppe:</b> Soldaten, Offiziere und Ausspäher (Spieler und kriegerische Dorfbewohner), Ausbildung und
 *         Ausrüstung aus der Gemeindekasse, täglicher Sold, Verwundete fallen aus (im Lazarett kürzer).</li>
 *     <li><b>Bündnisse und Ansprüche</b> zwischen beliebigen Gemeinden/Bünden; Verbündete stützen sich im Krieg.</li>
 *     <li>Dorfbewohner als Bürgermeister führen dieselben Entscheidungen selbst (Ansprüche, Krieg, Feldzüge, Frieden).</li>
 * </ul>
 */
public final class WarService {

    static final int VILLAGER_SOLDIER_PEACE = -20;
    static final int VILLAGER_OFFICER_PEACE = -60;
    static final int VILLAGER_SCOUT_PEACE = -35;
    static final int BASE_WILL = 30;

    /** Kriegswille mit Aufschlüsselung. */
    public record Will(int value, int villagers, int party, int claim, int balance) {
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final de.etiennebader.civ.application.day.Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;
    private final IdGenerator ids;
    private final ServerSettings settings;
    private final WarStore store;
    private final WarRegistry registry;

    public WarService(CivApplication app, de.etiennebader.civ.application.day.Calendar calendar, Texts texts,
                      Announcements announcements, IdGenerator ids, ServerSettings settings, Optional<Path> file) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = new WarStore(file);
        this.registry = store.load();
    }

    public WarRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------ Kriegswille

    /** Kriegswille von {@code town} gegen {@code target} (0 bis 100). */
    public Will will(PolityView town, PolityView target, Day day) {
        List<Integer> peaces = new ArrayList<>();
        for (Membership membership : town.members()) {
            if (membership.type() == MembershipType.CITIZEN && membership.member() instanceof PersonRef person) {
                peaces.add(Personality.of(person).peace());
            }
        }
        int average = peaces.isEmpty() ? 0 : (int) Math.round(peaces.stream().mapToInt(Integer::intValue).average().orElse(0));
        int villagers = clamp(-average, -40, 40);
        int party = town.mayor(settings.villagerPolitics()).flatMap(queries::partyOf)
                .flatMap(PolityView::partyProfile)
                .map(profile -> clamp(profile.order() / 5 + profile.economy() / 10, -30, 30)).orElse(0);
        int claim = registry.hasClaim(town.id(), target.id()) ? 25 : 0;
        long own = registry.power(town.id(), day);
        Intel intel = registry.intel(town.id(), target.id(), day);
        long enemy = intel.exact() ? intel.power() : intel.members() * 100L;
        int balance = own * 10 > enemy * 12 ? 15 : own * 10 < enemy * 8 ? -15 : 0;
        return new Will(clamp(BASE_WILL + villagers + party + claim + balance, 0, 100), villagers, party, claim, balance);
    }

    private long threshold(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.WAR_WILL_THRESHOLD);
    }

    /** Zeigt den Kriegswille der eigenen Gemeinde gegen eine andere. */
    public synchronized Reply willAgainst(Caller caller, String targetName) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        Optional<PolityView> target = territorial(targetName);
        if (target.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_UNKNOWN_POLITY, targetName));
        }
        Will will = will(own.get(), target.get(), calendar.today());
        return Reply.success(Message.of(MessageKey.WAR_WILL, own.get().name().toString(),
                target.get().name().toString(), will.value(), threshold(own.get()), signed(will.villagers()),
                signed(will.party()), signed(will.claim()), signed(will.balance())));
    }

    // ------------------------------------------------------------------ Krieg und Frieden

    /** Der Bürgermeister erklärt einer anderen Gemeinde den Krieg; das Volk muss es wollen. */
    public synchronized Reply declare(Caller caller, String targetName) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        Optional<PolityView> target = territorial(targetName);
        if (target.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_UNKNOWN_POLITY, targetName));
        }
        if (target.get().id().equals(own.get().id())) {
            return Reply.failure(Message.of(MessageKey.WAR_SELF));
        }
        Will will = will(own.get(), target.get(), calendar.today());
        if (will.value() < threshold(own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_WILL, will.value(), threshold(own.get())));
        }
        return declareWar(own.get(), target.get());
    }

    private Reply declareWar(PolityView aggressor, PolityView defender) {
        try {
            registry.declareWar(ids, aggressor.id(), defender.id(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        save();
        Message line = Message.of(MessageKey.WAR_DECLARED, aggressor.name().toString(), defender.name().toString());
        announcements.add(Announcement.toAll(line));
        return Reply.success(line);
    }

    /** Alle Kriege, Nummer = Reihenfolge der Erklärung. */
    public synchronized Reply list() {
        List<War> wars = registry.wars();
        if (wars.isEmpty()) {
            return Reply.success(Message.of(MessageKey.WAR_LIST_EMPTY));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.WAR_LIST_HEADER));
        for (int i = 0; i < wars.size(); i++) {
            War war = wars.get(i);
            lines.add(Message.of(MessageKey.WAR_LIST_LINE, i + 1, nameOf(war.aggressor()), nameOf(war.defender()),
                    registry.services().getOrDefault(war.aggressor(), Map.of()).size(),
                    registry.services().getOrDefault(war.defender(), Map.of()).size(),
                    Message.of(war.ongoing() ? MessageKey.WAR_STATUS_ONGOING : MessageKey.WAR_STATUS_ENDED)));
        }
        return Reply.success(lines);
    }

    /** Frontverlauf eines Kriegs; die gegnerische Truppe nur grob, außer mit eigenen Ausspähern. */
    public synchronized Reply front(Caller caller, int number) {
        List<War> wars = registry.wars();
        if (number < 1 || number > wars.size()) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_PARTY, number));
        }
        War war = wars.get(number - 1);
        Optional<PolityView> own = ownTown(caller);
        PolityId viewer = own.filter(town -> war.involves(town.id())).map(PolityView::id).orElse(null);
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.WAR_FRONT_HEADER, number, nameOf(war.aggressor()), nameOf(war.defender()),
                registry.frontOf(war.id()), Message.of(war.ongoing() ? MessageKey.WAR_STATUS_ONGOING
                        : MessageKey.WAR_STATUS_ENDED)));
        Day today = calendar.today();
        for (PolityId side : List.of(war.aggressor(), war.defender())) {
            lines.add(forces(side, viewer, today));
        }
        for (Campaign campaign : registry.campaigns()) {
            if (campaign.war().equals(war.id()) && (viewer == null || campaign.initiator().equals(viewer))) {
                lines.add(Message.of(MessageKey.WAR_FRONT_CAMPAIGN, nameOf(campaign.initiator()),
                        kindName(campaign.kind()), campaign.on().value()));
            }
        }
        return Reply.success(lines);
    }

    private Message forces(PolityId side, PolityId viewer, Day today) {
        Intel intel = viewer == null || viewer.equals(side)
                ? new Intel(true, registry.services().getOrDefault(side, Map.of()).size(), registry.power(side, today),
                (int) registry.wounded().getOrDefault(side, Map.of()).values().stream().filter(today::isBefore).count())
                : registry.intel(viewer, side, today);
        if (!intel.exact()) {
            return Message.of(MessageKey.WAR_FRONT_FORCES_UNKNOWN, nameOf(side), intel.members());
        }
        return Message.of(MessageKey.WAR_FRONT_FORCES, nameOf(side), intel.members(), intel.power() / 100.0,
                intel.wounded());
    }

    /** Der Bürgermeister plant einen Feldzug für {@code inDays} Tage ab heute. */
    public synchronized Reply campaign(Caller caller, int number, BattleKind kind, int inDays) {
        Optional<War> war = partyWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_PARTY, number));
        }
        if (inDays < 0 || inDays > 30) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_CAMPAIGN_DATE));
        }
        Day today = calendar.today();
        try {
            registry.planCampaign(war.get().id(), ownTown(caller).orElseThrow().id(), kind, today.plus(inDays), today);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        save();
        return Reply.success(Message.of(MessageKey.WAR_CAMPAIGN_PLANNED, kindName(kind), today.plus(inDays).value(), number));
    }

    /** Der Bürgermeister einer Kriegspartei löst sofort die Entscheidungsschlacht aus. */
    public synchronized Reply battle(Caller caller, int number) {
        Optional<War> war = partyWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_PARTY, number));
        }
        PolityView aggressor = queries.polity(war.get().aggressor()).orElseThrow();
        long rate = reparationRate(aggressor);
        int conquest = conquestCount(aggressor);
        BattleOutcome outcome;
        try {
            outcome = registry.resolveBattle(war.get().id(), rate, conquest, calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        if (outcome == BattleOutcome.DRAW) {
            save();
            return Reply.success(Message.of(MessageKey.WAR_BATTLE_DRAW, number));
        }
        PeaceTerms terms = registry.war(war.get().id()).orElseThrow().outcome().orElseThrow();
        Message line = applyTerms(terms);
        save();
        return Reply.success(line);
    }

    /** Der Bürgermeister einer Kriegspartei beendet den Krieg ohne Sieger. */
    public synchronized Reply peace(Caller caller, int number) {
        Optional<War> war = partyWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_PARTY, number));
        }
        return makePeace(war.get());
    }

    private Reply makePeace(War war) {
        try {
            registry.negotiatePeace(war.id(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        save();
        Message line = Message.of(MessageKey.WAR_PEACE, nameOf(war.aggressor()), nameOf(war.defender()));
        announcements.add(Announcement.toAll(line));
        return Reply.success(line);
    }

    /** Der Bürgermeister kauft seiner Gemeinde für {@code days} Tage Neutralität beim übergeordneten Bund. */
    public synchronized Reply neutrality(Caller caller, int days) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        if (days < 1) {
            return Reply.failure(Message.of(MessageKey.WAR_NEUTRALITY_DAYS));
        }
        PolityView town = own.get();
        Optional<PolityView> protector = queries.parentOf(town.id());
        if (protector.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NEUTRALITY_NO_PROTECTOR));
        }
        Day today = calendar.today();
        long fee = queries.effectiveLaws(town).valueOf(StandardParameters.NEUTRALITY_FEE_PER_DAY) * days;
        try {
            if (!registry.ongoingWarsOf(town.id()).isEmpty()) {
                throw new de.etiennebader.civ.domain.war.AtWarException(town.id());
            }
            if (fee > 0) {
                app.transferFunds().execute(new TransferFunds.Command(town.asActor(), protector.get().asActor(),
                        Money.of(fee, town.currency()), TransferReason.FEE, today));
            }
            registry.purchaseNeutrality(town.id(), today.plus(days), today);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        save();
        return Reply.success(Message.of(MessageKey.WAR_NEUTRAL, town.name().toString(), days,
                texts.money(Money.of(fee, town.currency()))));
    }

    // ------------------------------------------------------------------ Truppe

    /** Ein Spieler dient mit Rang in der Garnison seiner Gemeinde. */
    public synchronized Reply enlist(Caller caller, MilitaryRank rank) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        try {
            registry.enlist(own.get().id(), caller.player(), rank, calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        save();
        return Reply.success(Message.of(MessageKey.WAR_ENLISTED, own.get().name().toString(), rankName(rank)));
    }

    public synchronized Reply discharge(Caller caller) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        try {
            registry.discharge(own.get().id(), caller.player(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        save();
        return Reply.success(Message.of(MessageKey.WAR_DISCHARGED, own.get().name().toString()));
    }

    /** Der Bürgermeister bildet die Garnison eine Stufe weiter aus, bezahlt aus der Gemeindekasse. */
    public synchronized Reply train(Caller caller) {
        return improve(caller, true);
    }

    /** Der Bürgermeister rüstet die Garnison eine Stufe besser aus. */
    public synchronized Reply equip(Caller caller) {
        return improve(caller, false);
    }

    private Reply improve(Caller caller, boolean training) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        return improveTroops(own.get(), training);
    }

    private Reply improveTroops(PolityView town, boolean training) {
        int heads = training ? registry.trainable(town.id()) : registry.equippable(town.id());
        long unit = queries.effectiveLaws(town).valueOf(training ? StandardParameters.TRAINING_COST_PER_HEAD
                : StandardParameters.EQUIPMENT_COST_PER_HEAD);
        Money cost = Money.of(unit * heads, town.currency());
        Day today = calendar.today();
        try {
            if (heads == 0) {
                // löst im Register die passende Fehlermeldung aus
                return Reply.failure(texts.error(improveFailure(town, training)));
            }
            if (cost.amount() > 0) {
                app.spendFunds().execute(new SpendFunds.Command(town.asActor(), cost, SupplyChange.MILITARY_SPENDING, today));
            }
            int improved = training ? registry.train(town.id(), today) : registry.equip(town.id(), today);
            save();
            return Reply.success(Message.of(training ? MessageKey.WAR_TRAINED : MessageKey.WAR_EQUIPPED, improved,
                    texts.money(cost)));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    private DomainException improveFailure(PolityView town, boolean training) {
        try {
            if (training) {
                registry.train(town.id(), calendar.today());
            } else {
                registry.equip(town.id(), calendar.today());
            }
        } catch (DomainException e) {
            return e;
        }
        throw new IllegalStateException("Keine Verbesserung möglich, aber auch kein Fehler");
    }

    /** Der Bürgermeister baut Lazarettbetten, bezahlt aus der Gemeindekasse. */
    public synchronized Reply hospital(Caller caller, int beds) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        PolityView town = own.get();
        Money cost = Money.of(queries.effectiveLaws(town).valueOf(StandardParameters.HOSPITAL_BED_COST) * beds,
                town.currency());
        try {
            if (cost.amount() > 0) {
                app.spendFunds().execute(new SpendFunds.Command(town.asActor(), cost, SupplyChange.MILITARY_SPENDING,
                        calendar.today()));
            }
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        registry.buildHospitalBeds(town.id(), beds);
        save();
        return Reply.success(Message.of(MessageKey.WAR_HOSPITAL_BUILT, beds, texts.money(cost),
                registry.hospitalBeds().get(town.id())));
    }

    // ------------------------------------------------------------------ Bündnisse und Ansprüche

    /** Der Bürgermeister schlägt einer anderen Gemeinde ein Bündnis vor oder nimmt deren Vorschlag an. */
    public synchronized Reply propose(Caller caller, String otherName) {
        return diplomacy(caller, otherName, (own, other) -> {
            boolean formed = registry.proposeAlliance(own.id(), other.id(), calendar.today());
            return formed ? allianceFormed(own, other) : Message.of(MessageKey.WAR_ALLIANCE_PROPOSED,
                    other.name().toString());
        });
    }

    public synchronized Reply accept(Caller caller, String otherName) {
        return diplomacy(caller, otherName, (own, other) -> {
            registry.acceptAlliance(own.id(), other.id(), calendar.today());
            return allianceFormed(own, other);
        });
    }

    public synchronized Reply breakAlliance(Caller caller, String otherName) {
        return diplomacy(caller, otherName, (own, other) -> {
            registry.breakAlliance(own.id(), other.id(), calendar.today());
            Message line = Message.of(MessageKey.WAR_ALLIANCE_BROKEN, own.name().toString(), other.name().toString());
            announcements.add(Announcement.toAll(line));
            return line;
        });
    }

    /** Der Bürgermeister erhebt Anspruch auf eine andere Gemeinde (Grundlage für Kriegswille). */
    public synchronized Reply claim(Caller caller, String otherName) {
        return diplomacy(caller, otherName, (own, other) -> {
            registry.lodgeClaim(own.id(), other.id(), calendar.today());
            Message line = Message.of(MessageKey.WAR_CLAIM_LODGED, own.name().toString(), other.name().toString());
            announcements.add(Announcement.toAll(line));
            return line;
        });
    }

    /** Die Verbündeten der eigenen Gemeinde. */
    public synchronized Reply allies(Caller caller) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        Set<PolityId> allies = registry.alliesOf(own.get().id());
        Object names = allies.isEmpty() ? Message.of(MessageKey.WAR_NONE)
                : String.join(", ", allies.stream().map(this::nameOf).toList());
        return Reply.success(Message.of(MessageKey.WAR_ALLIES_LIST, own.get().name().toString(), names));
    }

    private interface Diplomatic {
        Message apply(PolityView own, PolityView other);
    }

    private Reply diplomacy(Caller caller, String otherName, Diplomatic action) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        Optional<PolityView> other = territorial(otherName);
        if (other.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_UNKNOWN_POLITY, otherName));
        }
        try {
            Message line = action.apply(own.get(), other.get());
            save();
            return Reply.success(line);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    private Message allianceFormed(PolityView first, PolityView second) {
        Message line = Message.of(MessageKey.WAR_ALLIANCE_FORMED, first.name().toString(), second.name().toString());
        announcements.add(Announcement.toAll(line));
        return line;
    }

    // ------------------------------------------------------------------ Tagesablauf

    /** Täglich: Dorfbewohner melden sich, Feldzüge laufen, Dorfbewohner-Bürgermeister handeln, dann wird Sold gezahlt. */
    public synchronized void endOfDay(Day day) {
        for (War war : registry.wars()) {
            if (war.ongoing()) {
                villagersEnlist(war.aggressor(), day);
                villagersEnlist(war.defender(), day);
            }
        }
        for (BattleReport report : registry.runDueCampaigns(day, this::reparationRate, this::conquestCount)) {
            War fought = war(report);
            Message line = Message.of(MessageKey.WAR_BATTLE_REPORT, nameOf(fought.aggressor()) + " – "
                            + nameOf(fought.defender()), kindName(report.kind()), report.front(), report.frontShift(), report.woundedAggressor(),
                    report.woundedDefender());
            announcements.add(Announcement.toAll(line));
            report.decided().ifPresent(this::applyTerms);
        }
        villagerRulers(day);
        payWages(day);
        save();
    }

    private War war(BattleReport report) {
        return registry.war(report.war()).orElseThrow();
    }

    private long reparationRate(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.WAR_REPARATIONS_PER_STRENGTH_POINT);
    }

    private long reparationRate(War war) {
        return queries.polity(war.aggressor()).map(this::reparationRate).orElse(0L);
    }

    private int conquestCount(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.CONQUEST_CHUNKS_PER_WIN).intValue();
    }

    private int conquestCount(War war) {
        return queries.polity(war.aggressor()).map(this::conquestCount).orElse(0);
    }

    /** Zahlt Reparationen und erobert Chunks; gibt die Ergebnismeldung zurück und gibt sie bekannt. */
    private Message applyTerms(PeaceTerms terms) {
        PolityView winner = queries.polity(terms.winner()).orElseThrow();
        PolityView loser = queries.polity(terms.loser()).orElseThrow();
        Message paid = Message.of(MessageKey.WAR_NO_REPARATIONS);
        if (terms.reparations() > 0) {
            Money amount = Money.of(terms.reparations(), loser.currency());
            try {
                app.transferFunds().execute(new TransferFunds.Command(loser.asActor(), winner.asActor(), amount,
                        TransferReason.WAR_REPARATIONS, calendar.today()));
                paid = texts.money(amount);
            } catch (DomainException e) {
                paid = Message.of(MessageKey.WAR_REPARATIONS_UNPAID, texts.money(amount));
            }
        }
        Message line = Message.of(MessageKey.WAR_BATTLE_WON, winner.name().toString(), loser.name().toString(), paid);
        announcements.add(Announcement.toAll(line));
        if (terms.conquest() > 0) {
            List<ChunkCoord> taken = app.conquerChunks().execute(new ConquerChunks.Command(terms.winner(),
                    terms.loser(), terms.conquest(), calendar.today()));
            if (!taken.isEmpty()) {
                announcements.add(Announcement.toAll(Message.of(MessageKey.WAR_CONQUERED, winner.name().toString(),
                        taken.size(), loser.name().toString())));
            }
        }
        return line;
    }

    private void villagersEnlist(PolityId townId, Day day) {
        Optional<PolityView> town = queries.polity(townId);
        if (town.isEmpty()) {
            return;
        }
        Map<ActorRef, MilitaryRank> garrison = registry.garrisonOf(townId);
        for (Membership membership : town.get().members()) {
            if (membership.type() != MembershipType.CITIZEN || !(membership.member() instanceof PersonRef person)
                    || garrison.containsKey(person)) {
                continue;
            }
            int peace = Personality.of(person).peace();
            if (peace <= VILLAGER_OFFICER_PEACE) {
                registry.enlist(townId, person, MilitaryRank.OFFICER, day);
            } else if (peace <= VILLAGER_SCOUT_PEACE && registry.healthyScouts(townId, day) < 2) {
                registry.enlist(townId, person, MilitaryRank.SCOUT, day);
            } else if (peace <= VILLAGER_SOLDIER_PEACE) {
                registry.enlist(townId, person, MilitaryRank.SOLDIER, day);
            }
        }
    }

    /** Dorfbewohner als Bürgermeister: Ansprüche erheben, Krieg erklären, Feldzüge planen, Frieden suchen, aufrüsten. */
    private void villagerRulers(Day day) {
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL
                    || town.mayor(settings.villagerPolitics()).filter(mayor -> mayor instanceof PersonRef).isEmpty()) {
                continue;
            }
            for (War war : registry.ongoingWarsOf(town.id())) {
                rulerFights(town, war, day);
            }
            if (!registry.ongoingWarsOf(town.id()).isEmpty()) {
                continue;
            }
            for (PolityView neighbour : neighbours(town)) {
                Random dice = new Random(day.value() * 31 + town.id().hashCode() * 17L + neighbour.id().hashCode());
                Will will = will(town, neighbour, day);
                if (registry.hasClaim(town.id(), neighbour.id())) {
                    if (will.value() >= threshold(town) && dice.nextInt(100) < 25
                            && !registry.isNeutral(neighbour.id(), day)
                            && !registry.alliesOf(town.id()).contains(neighbour.id())) {
                        declareWar(town, neighbour);
                        break;
                    }
                } else if (will.value() >= threshold(town) - 10 && dice.nextInt(100) < 10
                        && !registry.alliesOf(town.id()).contains(neighbour.id())) {
                    try {
                        registry.lodgeClaim(town.id(), neighbour.id(), day);
                        announcements.add(Announcement.toAll(Message.of(MessageKey.WAR_CLAIM_LODGED,
                                town.name().toString(), neighbour.name().toString())));
                    } catch (DomainException ignored) {
                        // schon erhoben oder nicht möglich
                    }
                    break;
                }
            }
        }
    }

    private void rulerFights(PolityView town, War war, Day day) {
        int front = registry.frontOf(war.id());
        boolean aggressor = war.aggressor().equals(town.id());
        int own = aggressor ? front : -front;
        if (own <= -60) {
            makePeace(war);
            return;
        }
        boolean planned = registry.campaigns().stream()
                .anyMatch(campaign -> campaign.war().equals(war.id()) && campaign.initiator().equals(town.id()));
        if (!planned && registry.power(town.id(), day) > 0) {
            try {
                registry.planCampaign(war.id(), town.id(), aggressor ? BattleKind.ASSAULT : BattleKind.DEFENSIVE,
                        day.plus(1), day);
            } catch (DomainException ignored) {
                // z. B. nicht mehr beteiligt
            }
        }
        long cost = queries.effectiveLaws(town).valueOf(StandardParameters.TRAINING_COST_PER_HEAD)
                * Math.max(1, registry.trainable(town.id()));
        if (registry.trainable(town.id()) > 0
                && queries.ledger().balanceOf(town.asActor(), town.currency()).amount() >= cost * 10) {
            improveTroops(town, true);
        }
    }

    /** Gemeinden, deren Gebiet an das Gebiet von {@code town} grenzt. */
    private List<PolityView> neighbours(PolityView town) {
        Set<PolityId> found = new LinkedHashSet<>();
        var territory = queries.territory();
        for (ChunkCoord chunk : territory.territoryOf(town.id())) {
            for (ChunkCoord next : chunk.neighbours()) {
                territory.claimAt(next).map(claim -> claim.polity()).filter(id -> !id.equals(town.id()))
                        .ifPresent(found::add);
            }
        }
        return found.stream().sorted(Comparator.comparing(id -> id.value().toString()))
                .map(queries::polity).flatMap(Optional::stream)
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL).toList();
    }

    private void payWages(Day day) {
        for (Map.Entry<PolityId, Map<ActorRef, MilitaryRank>> entry : registry.garrisons().entrySet()) {
            Optional<PolityView> town = queries.polity(entry.getKey());
            if (town.isEmpty()) {
                continue;
            }
            long soldier = queries.effectiveLaws(town.get()).valueOf(StandardParameters.SOLDIER_WAGE);
            long officer = queries.effectiveLaws(town.get()).valueOf(StandardParameters.OFFICER_WAGE);
            for (Map.Entry<ActorRef, MilitaryRank> member : entry.getValue().entrySet()) {
                long wage = member.getValue() == MilitaryRank.OFFICER ? officer : soldier;
                if (wage == 0) {
                    continue;
                }
                try {
                    app.transferFunds().execute(new TransferFunds.Command(town.get().asActor(), member.getKey(),
                            Money.of(wage, town.get().currency()), TransferReason.WAGE, day));
                } catch (DomainException e) {
                    registry.discharge(entry.getKey(), member.getKey(), day);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private Optional<War> partyWar(Caller caller, int number) {
        Optional<PolityView> own = ownTown(caller);
        List<War> wars = registry.wars();
        if (own.isEmpty() || !isMayor(caller, own.get()) || number < 1 || number > wars.size()) {
            return Optional.empty();
        }
        return Optional.of(wars.get(number - 1)).filter(war -> war.involves(own.get().id()));
    }

    private Optional<PolityView> ownTown(Caller caller) {
        return queries.municipalityOf(caller.player());
    }

    private Optional<PolityView> territorial(String name) {
        return queries.polityNamed(name).filter(polity -> polity.kind() == PolityKind.TERRITORIAL);
    }

    private static boolean isMayor(Caller caller, PolityView town) {
        return town.holds(caller.player(), Office.MAYOR);
    }

    private String nameOf(PolityId id) {
        return queries.polity(id).map(polity -> polity.name().toString()).orElse("?");
    }

    private void save() {
        store.save(registry);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static Message rankName(MilitaryRank rank) {
        return Message.of(switch (rank) {
            case OFFICER -> MessageKey.WAR_RANK_OFFICER;
            case SCOUT -> MessageKey.WAR_RANK_SCOUT;
            case SOLDIER -> MessageKey.WAR_RANK_SOLDIER;
        });
    }

    private static Message kindName(BattleKind kind) {
        return Message.of(switch (kind) {
            case ASSAULT -> MessageKey.WAR_KIND_ASSAULT;
            case AMBUSH -> MessageKey.WAR_KIND_AMBUSH;
            case DEFENSIVE -> MessageKey.WAR_KIND_DEFENSIVE;
        });
    }
}

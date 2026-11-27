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
    /** Tage, die ein Spieler als Sieger Zeit hat, seine Siegpunkte auszugeben. */
    static final int SPOILS_DAYS = 3;

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
    private final de.etiennebader.civ.application.directory.PlayerDirectory players;
    private final WarStore store;
    private final List<LogEntry> log;
    private java.util.function.BiConsumer<String, String> news = (title, body) -> {
    };
    private final WarRegistry registry;

    public WarService(CivApplication app, de.etiennebader.civ.application.day.Calendar calendar, Texts texts,
                      Announcements announcements, IdGenerator ids, ServerSettings settings, Optional<Path> file,
                      de.etiennebader.civ.application.directory.PlayerDirectory players) {
        this.players = Objects.requireNonNull(players, "players");
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = new WarStore(file);
        this.registry = store.load();
        this.log = new ArrayList<>(store.loadLog());
    }

    /** Ein Eintrag im Schlachtenbuch (Web: Live-Stände und Verlauf). */
    public record LogEntry(de.etiennebader.civ.domain.war.WarId war, long day, BattleKind kind, int front, int shift,
                           int woundedAggressor, int woundedDefender) {
    }

    public static final int LOG_SIZE = 200;

    /** Das Schlachtenbuch, älteste zuerst. */
    public synchronized List<LogEntry> battleLog() {
        return List.copyOf(log);
    }

    /** Wohin Kriegsberichte gehen (Titel, Text): in die Zeitungen der Dorfbewohner-Redakteure. */
    public synchronized void onNews(java.util.function.BiConsumer<String, String> news) {
        this.news = Objects.requireNonNull(news, "news");
    }

    private void report(String title, String body) {
        try {
            news.accept(title, body);
        } catch (RuntimeException e) {
            // Berichterstattung darf den Krieg nie stören
        }
    }

    public WarRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------ Kriegswille

    /** Kriegswille von {@code town} gegen {@code target} (0 bis 100). */
    public Will will(PolityView town, PolityView target, Day day) {
        List<Integer> peaces = new ArrayList<>();
        for (Membership membership : town.members()) {
            if (membership.member() instanceof PersonRef person) {
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
        report("Krieg: " + aggressor.name() + " gegen " + defender.name(), aggressor.name() + " hat " + defender.name()
                + " den Krieg erklärt. Die Garnisonen sammeln sich; die Dorfbewohner fürchten um Felder und Höfe.");
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
        Optional<War> war = partyWar(caller, number, true);
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
        BattleOutcome outcome;
        try {
            outcome = registry.resolveBattle(war.get().id(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        if (outcome == BattleOutcome.DRAW) {
            save();
            return Reply.success(Message.of(MessageKey.WAR_BATTLE_DRAW, number));
        }
        War decided = registry.war(war.get().id()).orElseThrow();
        Message line = announceDecision(decided);
        save();
        return Reply.success(line);
    }

    // ------------------------------------------------------------------ Beute (Siegpunkte)

    /**
     * Zeigt dem Bürgermeister des Siegers die Siegpunkte des Kriegs {@code number} und was sie kosten.
     */
    public synchronized Reply spoilsInfo(Caller caller, int number) {
        Optional<War> war = winnerWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_SPOILS_NONE, number));
        }
        PeaceTerms terms = war.get().outcome().orElseThrow();
        PolityView lawgiver = queries.polity(war.get().aggressor()).orElseThrow();
        int deadline = (int) (war.get().endedOn().orElseThrow().value() + SPOILS_DAYS);
        return Reply.success(Message.of(MessageKey.WAR_SPOILS_INFO, number, nameOf(terms.loser()), terms.points(),
                chunkCost(lawgiver), conquestCount(lawgiver), reparationRate(lawgiver), deadline));
    }

    /**
     * Der Bürgermeister des Siegers gibt seine Siegpunkte aus: {@code chunks} Grenz-Chunks des Verlierers und
     * {@code reparationPoints} Punkte als Reparationen. Nicht ausgegebene Punkte verfallen.
     */
    public synchronized Reply spoils(Caller caller, int number, int chunks, int reparationPoints) {
        Optional<War> war = winnerWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_SPOILS_NONE, number));
        }
        PeaceTerms terms = war.get().outcome().orElseThrow();
        PolityView lawgiver = queries.polity(war.get().aggressor()).orElseThrow();
        long cost = chunkCost(lawgiver);
        if (chunks < 0 || reparationPoints < 0 || chunks > conquestCount(lawgiver)
                || chunks * cost + reparationPoints > terms.points()) {
            return Reply.failure(Message.of(MessageKey.WAR_SPOILS_INVALID, terms.points(), cost,
                    conquestCount(lawgiver)));
        }
        Message line = settle(war.get(), chunks, reparationPoints);
        save();
        return Reply.success(line);
    }

    /** Der entschiedene Krieg {@code number}, dessen Sieger die eigene Gemeinde ist und dessen Punkte noch offen sind. */
    private Optional<War> winnerWar(Caller caller, int number) {
        Optional<PolityView> own = ownTown(caller);
        List<War> wars = registry.wars();
        if (own.isEmpty() || !isMayor(caller, own.get()) || number < 1 || number > wars.size()) {
            return Optional.empty();
        }
        return Optional.of(wars.get(number - 1))
                .filter(war -> war.outcome().filter(terms -> !terms.settled()
                        && terms.winner().equals(own.get().id())).isPresent());
    }

    /**
     * Gibt den Sieg bekannt. Bei einem Dorfbewohner als Bürgermeister werden die Siegpunkte sofort verteilt, sonst
     * hat der Spieler {@value #SPOILS_DAYS} Tage Zeit (danach verteilt das Spiel sie).
     */
    private Message announceDecision(War war) {
        PeaceTerms terms = war.outcome().orElseThrow();
        Message line = Message.of(MessageKey.WAR_VICTORY, nameOf(terms.winner()), nameOf(terms.loser()),
                terms.points());
        announcements.add(Announcement.toAll(line));
        report("Sieg für " + nameOf(terms.winner()), nameOf(terms.winner()) + " hat " + nameOf(terms.loser())
                + " geschlagen und erhält " + terms.points() + " Siegpunkte.");
        if (villagerRuled(terms.winner())) {
            return settleByDefault(war);
        }
        return line;
    }

    private boolean villagerRuled(PolityId id) {
        return queries.polity(id).flatMap(town -> town.mayor(settings.villagerPolitics()))
                .filter(mayor -> mayor instanceof PersonRef).isPresent();
    }

    /** Standardverteilung: so viele Chunks wie erlaubt und bezahlbar, der Rest als Reparationen. */
    private Message settleByDefault(War war) {
        PeaceTerms terms = war.outcome().orElseThrow();
        PolityView lawgiver = queries.polity(war.aggressor()).orElseThrow();
        long cost = chunkCost(lawgiver);
        int chunks = (int) Math.min(conquestCount(lawgiver), terms.points() / cost);
        return settle(war, chunks, (int) (terms.points() - chunks * cost));
    }

    /**
     * Setzt die Verteilung um: erobert höchstens {@code chunksWanted} Chunks (was sich nicht erobern lässt, wird als
     * Reparationen verrechnet), zahlt die Reparationen und schreibt das Ergebnis fest.
     */
    private Message settle(War war, int chunksWanted, int reparationPoints) {
        PeaceTerms terms = war.outcome().orElseThrow();
        PolityView winner = queries.polity(terms.winner()).orElseThrow();
        PolityView loser = queries.polity(terms.loser()).orElseThrow();
        PolityView lawgiver = queries.polity(war.aggressor()).orElseThrow();
        List<ChunkCoord> taken = chunksWanted > 0
                ? app.conquerChunks().execute(new ConquerChunks.Command(terms.winner(), terms.loser(), chunksWanted,
                calendar.today()))
                : List.of();
        long refunded = (chunksWanted - taken.size()) * chunkCost(lawgiver);
        long amount = (reparationPoints + refunded) * reparationRate(lawgiver);
        Message paid = Message.of(MessageKey.WAR_NO_REPARATIONS);
        long received = 0;
        if (amount > 0) {
            Money money = Money.of(amount, loser.currency());
            try {
                app.transferFunds().execute(new TransferFunds.Command(loser.asActor(), winner.asActor(), money,
                        TransferReason.WAR_REPARATIONS, calendar.today()));
                paid = texts.money(money);
                received = amount;
            } catch (DomainException e) {
                paid = Message.of(MessageKey.WAR_REPARATIONS_UNPAID, texts.money(money));
            }
        }
        registry.settleSpoils(war.id(), received, taken.size(), calendar.today());
        Message line = Message.of(MessageKey.WAR_BATTLE_WON, winner.name().toString(), loser.name().toString(), paid);
        announcements.add(Announcement.toAll(line));
        if (!taken.isEmpty()) {
            announcements.add(Announcement.toAll(Message.of(MessageKey.WAR_CONQUERED, winner.name().toString(),
                    taken.size(), loser.name().toString())));
        }
        return line;
    }

    /** Verteilt die Siegpunkte, die ein Spieler nicht innerhalb der Frist ausgegeben hat. */
    private void settleExpiredSpoils(Day day) {
        for (War war : registry.pendingSpoils()) {
            if (!day.isBefore(war.endedOn().orElseThrow().plus(SPOILS_DAYS))) {
                settleByDefault(war);
            }
        }
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
        report("Frieden zwischen " + nameOf(war.aggressor()) + " und " + nameOf(war.defender()),
                "Der Krieg ist ohne Sieger beendet; die Waffen schweigen.");
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

    /**
     * Befördert oder degradiert einen dienenden Spieler. Der Bürgermeister darf jeden Rang vergeben; sonst nur ein
     * Vorgesetzter, und zwar nur Ränge unter dem eigenen und nur für Untergebene (Befehlskette).
     */
    public synchronized Reply promote(Caller caller, String name, MilitaryRank rank) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        Optional<de.etiennebader.civ.domain.actor.PlayerRef> target = players.findByName(name.strip());
        if (target.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_UNKNOWN_POLITY));
        }
        PolityId town = own.get().id();
        Map<ActorRef, MilitaryRank> garrison = registry.garrisonOf(town);
        MilitaryRank current = garrison.get(target.get());
        MilitaryRank mine = garrison.get(caller.player());
        boolean mayor = isMayor(caller, own.get());
        if (current == null) {
            return Reply.failure(Message.of(MessageKey.WAR_TARGET_NOT_SERVING));
        }
        if (!mayor && (mine == null || !mine.outranks(rank) || !mine.outranks(current)
                || target.get().equals(caller.player()))) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_COMMANDER));
        }
        registry.promote(town, target.get(), rank);
        save();
        return Reply.success(Message.of(MessageKey.WAR_PROMOTED, texts.actor(target.get()), rankName(rank)));
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
        for (BattleReport report : registry.runDueCampaigns(day)) {
            War fought = war(report);
            Message line = Message.of(MessageKey.WAR_BATTLE_REPORT, nameOf(fought.aggressor()) + " – "
                            + nameOf(fought.defender()), kindName(report.kind()), report.front(), report.frontShift(), report.woundedAggressor(),
                    report.woundedDefender());
            announcements.add(Announcement.toAll(line));
            log.add(new LogEntry(report.war(), day.value(), report.kind(), report.front(), report.frontShift(),
                    report.woundedAggressor(), report.woundedDefender()));
            while (log.size() > LOG_SIZE) {
                log.remove(0);
            }
            report("Schlacht: " + nameOf(fought.aggressor()) + " gegen " + nameOf(fought.defender()),
                    "Die Front liegt nun bei " + report.front() + " (Verschiebung " + report.frontShift() + "). Verwundet: "
                            + report.woundedAggressor() + " Angreifer, " + report.woundedDefender() + " Verteidiger.");
            if (report.decided().isPresent()) {
                announceDecision(war(report));
            }
        }
        settleExpiredSpoils(day);
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

    private int conquestCount(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.CONQUEST_CHUNKS_PER_WIN).intValue();
    }

    private long chunkCost(PolityView town) {
        return queries.effectiveLaws(town).valueOf(StandardParameters.CONQUEST_POINTS_PER_CHUNK);
    }

    private void villagersEnlist(PolityId townId, Day day) {
        Optional<PolityView> town = queries.polity(townId);
        if (town.isEmpty()) {
            return;
        }
        Map<ActorRef, MilitaryRank> garrison = registry.garrisonOf(townId);
        for (Membership membership : town.get().members()) {
            if (!(membership.member() instanceof PersonRef person) || garrison.containsKey(person)) {
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
        villagersPromote(townId);
    }

    /**
     * Befehlskette auffüllen: ab 10 Mann ein General, je 8 ein Hauptmann, je 6 ein Offizier, je 4 ein Unteroffizier.
     * Befördert werden nur Dorfbewohner (der dienstälteste der Vorstufe); Spieler befördern ihre Untergebenen selbst.
     */
    private void villagersPromote(PolityId townId) {
        int size = registry.garrisonOf(townId).size();
        if (size >= 10 && count(townId, MilitaryRank.GENERAL) == 0) {
            promoteVillager(townId, MilitaryRank.GENERAL, MilitaryRank.CAPTAIN, MilitaryRank.OFFICER);
        }
        if (size >= 8 && count(townId, MilitaryRank.CAPTAIN) < size / 8) {
            promoteVillager(townId, MilitaryRank.CAPTAIN, MilitaryRank.OFFICER, MilitaryRank.SERGEANT);
        }
        if (size >= 6 && count(townId, MilitaryRank.OFFICER) < size / 6) {
            promoteVillager(townId, MilitaryRank.OFFICER, MilitaryRank.SERGEANT);
        }
        if (size >= 4 && count(townId, MilitaryRank.SERGEANT) < size / 4) {
            promoteVillager(townId, MilitaryRank.SERGEANT, MilitaryRank.SOLDIER);
        }
    }

    private int count(PolityId townId, MilitaryRank rank) {
        return (int) registry.garrisonOf(townId).values().stream().filter(r -> r == rank).count();
    }

    private void promoteVillager(PolityId townId, MilitaryRank to, MilitaryRank... from) {
        for (MilitaryRank source : from) {
            Optional<ActorRef> pick = registry.garrisonOf(townId).entrySet().stream()
                    .filter(e -> e.getValue() == source && e.getKey() instanceof PersonRef)
                    .map(Map.Entry::getKey).min(Comparator.comparing(a -> a.uuid().toString()));
            if (pick.isPresent()) {
                registry.promote(townId, pick.get(), to);
                return;
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
                long wage = member.getValue().level() >= MilitaryRank.OFFICER.level() ? officer : soldier;
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
        return partyWar(caller, number, false);
    }

    /** Mit {@code highCommand} dürfen auch Hauptmann und General (Befehlskette) für ihre Gemeinde handeln. */
    private Optional<War> partyWar(Caller caller, int number, boolean highCommand) {
        Optional<PolityView> own = ownTown(caller);
        List<War> wars = registry.wars();
        boolean allowed = own.isPresent() && (isMayor(caller, own.get())
                || highCommand && registry.garrisonOf(own.get().id()).get(caller.player()) != null
                && registry.garrisonOf(own.get().id()).get(caller.player()).commandsCampaigns());
        if (!allowed || number < 1 || number > wars.size()) {
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
        store.save(registry, log);
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
            case SERGEANT -> MessageKey.WAR_RANK_SERGEANT;
            case CAPTAIN -> MessageKey.WAR_RANK_CAPTAIN;
            case GENERAL -> MessageKey.WAR_RANK_GENERAL;
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

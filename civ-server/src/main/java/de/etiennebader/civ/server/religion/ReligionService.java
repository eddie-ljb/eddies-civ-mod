package de.etiennebader.civ.server.religion;

import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.religion.Denomination;
import de.etiennebader.civ.domain.religion.Religion;
import de.etiennebader.civ.domain.religion.ReligionId;
import de.etiennebader.civ.domain.religion.ReligionRegistry;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Religionen (Issue #13): eigene oder reale Religionen gründen (Monotheismus, Schrift, Gotteshaus, Sprache,
 * Konfessionen), beitreten, beten, Gottesdienste, Klerus-Wahlen sowie Taufen und Segnungen von Hochzeiten.
 *
 * <p>Dorfbewohner haben mit {@link Personality#religiosity()} eine Gläubigkeit (positiv) bzw. Atheismus (negativ).
 * Gläubige schließen sich täglich mit etwas Wahrscheinlichkeit der größten Religion ihrer Gemeinde-Welt an,
 * Atheisten nie; Mitglieder wählen bei Klerus-Wahlen mit. Der Machtgrad des Klerus ({@link Religion#clergyPower()})
 * ist die politische Rolle: er gewichtet, wie stark ein Gottesdienst die Gläubigkeit der Gemeinde festigt.
 */
public final class ReligionService {

    /** Gläubigkeit, ab der ein Dorfbewohner sich einer Religion anschließen kann. */
    static final int JOIN_THRESHOLD = 20;
    private static final int JOIN_CHANCE_PERCENT = 15;
    /** So viele Tage läuft eine Klerus-Wahl. */
    static final int ELECTION_DAYS = 3;
    /** Alle so viele Tage wird ein vakantes Klerusamt neu zur Wahl gestellt. */
    static final int VACANCY_ELECTION_INTERVAL = 7;
    private static final int MAX_RITES = 200;

    private final WorldQueries queries;
    private final PlayerDirectory players;
    private final Announcements announcements;
    private final IdGenerator ids;
    private final ReligionStore store;
    private final ReligionRegistry registry;
    private final Map<ActorRef, ReligionId> members;
    private final Map<ActorRef, String> denominations;
    private final List<String> rites;
    private final Map<ReligionId, Integer> fervor;
    private final Map<ReligionId, String> logos;
    private final java.util.Set<PlayerRef> prayedToday = new java.util.HashSet<>();
    private final Random random = new Random();

    /** Obergrenze der Inbrunst: wie sehr Gebete und Gottesdienste die Gläubigen gerade stärken. */
    static final int MAX_FERVOR = 6;
    /** Ab diesem Machtgrad des Klerus fühlen sich Atheisten bedrängt. */
    static final int PRESSURE_POWER = 70;

    public ReligionService(WorldQueries queries, PlayerDirectory players, Announcements announcements,
                           IdGenerator ids, Optional<Path> file) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.players = Objects.requireNonNull(players, "players");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.store = new ReligionStore(file);
        ReligionStore.State state = store.load();
        this.registry = ReligionRegistry.restore(state.religions());
        this.members = new LinkedHashMap<>(state.members());
        this.denominations = new LinkedHashMap<>(state.denominations());
        this.rites = new ArrayList<>(state.rites());
        this.fervor = new LinkedHashMap<>(state.fervor());
        this.logos = new LinkedHashMap<>(state.logos());
    }

    private void save() {
        store.save(new ReligionStore.State(registry.religions(), members, denominations, rites, fervor, logos));
    }

    private static Reply ok(MessageKey key, Object... args) {
        return Reply.success(Message.of(key, args));
    }

    private static Reply no(MessageKey key, Object... args) {
        return Reply.failure(Message.of(key, args));
    }

    /** Fehlertext aus der Domäne (deutsch), unverändert durchgereicht. */
    private static Reply fail(String domainText) {
        return Reply.failure(Message.of(MessageKey.RELIGION_ERROR, domainText));
    }

    private static Message kind(Religion religion) {
        return Message.of(religion.monotheistic() ? MessageKey.RELIGION_MONO : MessageKey.RELIGION_POLY);
    }

    private Message head(Religion religion) {
        return religion.clergyHead().map(head -> Message.of(MessageKey.RELIGION_DONE, nameOf(head)))
                .orElseGet(() -> Message.of(MessageKey.RELIGION_VACANT));
    }

    private String logoPrefix(Religion religion) {
        String logo = logos.get(religion.id());
        return logo == null ? "" : "[" + logo + "] ";
    }

    private int memberCount(Religion religion) {
        return (int) members.values().stream().filter(religion.id()::equals).count();
    }

    private Optional<Religion> religionOf(ActorRef actor) {
        return Optional.ofNullable(members.get(actor)).flatMap(registry::religion);
    }

    private String nameOf(ActorRef actor) {
        if (actor instanceof PlayerRef player) {
            return players.nameOf(player).orElse("?");
        }
        if (actor instanceof PersonRef person) {
            return de.etiennebader.civ.domain.population.VillagerIdentity.of(person).fullName();
        }
        return actor.toString();
    }

    /** Gründet eine Religion; der Gründer wird erstes Mitglied und Klerus-Oberhaupt. */
    public synchronized Reply found(Caller caller, String name, boolean monotheistic, String scripture, String temple,
                                    String language, Day day) {
        try {
            Religion religion = registry.found(ReligionId.generate(ids), name, caller.player(), monotheistic, scripture,
                    temple, language, day);
            members.put(caller.player(), religion.id());
            save();
            announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_FOUNDED_ANNOUNCE, caller.name(),
                    religion.name())));
            return ok(MessageKey.RELIGION_FOUNDED, religion.name(), kind(religion));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    public synchronized Reply list() {
        if (registry.religions().isEmpty()) {
            return ok(MessageKey.RELIGION_NONE_YET);
        }
        List<Message> lines = new ArrayList<>();
        for (Religion religion : registry.religions()) {
            lines.add(Message.of(MessageKey.RELIGION_LIST_ENTRY, religion.name(), kind(religion),
                    memberCount(religion), head(religion), logoPrefix(religion)));
        }
        return Reply.success(lines);
    }

    public synchronized Reply info(String name) {
        Optional<Religion> found = registry.religionNamed(name);
        if (found.isEmpty()) {
            return no(MessageKey.RELIGION_UNKNOWN, name);
        }
        Religion r = found.get();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.RELIGION_INFO, r.name(), kind(r), r.scriptureName(), r.templeName(),
                r.language(), memberCount(r), nameOf(r.founder()), logoPrefix(r)));
        lines.add(Message.of(MessageKey.RELIGION_INFO_CLERGY, head(r),
                r.clergyPower(), r.denominations().isEmpty() ? "-"
                        : String.join(", ", r.denominations().stream().map(Denomination::name).toList()),
                fervor.getOrDefault(r.id(), 0)));
        r.election().ifPresent(e -> lines.add(Message.of(MessageKey.RELIGION_ELECTION_INFO, e.closesOn().value(),
                e.candidates().isEmpty() ? "-" : String.join(", ", e.candidates().stream().map(this::nameOf).toList()))));
        return Reply.success(lines);
    }

    public synchronized Reply join(Caller caller, String name) {
        Optional<Religion> found = registry.religionNamed(name);
        if (found.isEmpty()) {
            return no(MessageKey.RELIGION_UNKNOWN, name);
        }
        members.put(caller.player(), found.get().id());
        denominations.remove(caller.player());
        save();
        return ok(MessageKey.RELIGION_JOINED, found.get().name());
    }

    public synchronized Reply leave(Caller caller) {
        if (members.remove(caller.player()) == null) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        denominations.remove(caller.player());
        save();
        return ok(MessageKey.RELIGION_LEFT);
    }

    /** Fügt der eigenen Religion eine Konfession hinzu (Gründer/Klerus) und wählt sie, oder wählt eine bestehende. */
    public synchronized Reply denomination(Caller caller, String name, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        try {
            Religion r = religion.get();
            boolean known = r.denominations().stream().anyMatch(d -> d.name().equalsIgnoreCase(name.strip()));
            if (!known) {
                boolean allowed = r.founder().equals(caller.player())
                        || r.clergyHead().filter(caller.player()::equals).isPresent();
                if (!allowed) {
                    return no(MessageKey.RELIGION_DENOMINATION_FORBIDDEN);
                }
                registry.addDenomination(r.id(), new Denomination(name), day);
            }
            denominations.put(caller.player(), name.strip());
            save();
            return ok(MessageKey.RELIGION_DENOMINATION_SET, name.strip());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    /** Machtgrad des Klerus festlegen (nur Gründer oder Oberhaupt, 0-100). */
    public synchronized Reply power(Caller caller, int power, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Religion r = religion.get();
        if (r.clergyHead().isEmpty()) {
            return no(MessageKey.RELIGION_OFFICE_VACANT);
        }
        try {
            registry.appointClergy(r.id(), caller.player(), r.clergyHead().get(), power, day);
            save();
            return ok(MessageKey.RELIGION_POWER_SET, power);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    /** Beten: ein Gebet je Tag und Spieler festigt den Glauben (Anzeige in der Antwort). */
    public synchronized Reply pray(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Religion r = religion.get();
        if (!prayedToday.add(caller.player())) {
            return no(MessageKey.RELIGION_PRAYED_ALREADY);
        }
        fervor.merge(r.id(), 1, (a, b) -> Math.min(MAX_FERVOR, a + b));
        save();
        return ok(MessageKey.RELIGION_PRAYED, r.templeName(), r.scriptureName(), memberCount(r));
    }

    /** Gottesdienst, nur durch das Klerus-Oberhaupt; alle Gläubigen erfahren davon. */
    public synchronized Reply service(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty() || religion.get().clergyHead().filter(caller.player()::equals).isEmpty()) {
            return no(MessageKey.RELIGION_ONLY_HEAD_SERVICE);
        }
        Religion r = religion.get();
        int attendees = memberCount(r);
        fervor.merge(r.id(), 3, (a, b) -> Math.min(MAX_FERVOR, a + b));
        save();
        announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_SERVICE_ANNOUNCE, caller.name(),
                r.templeName(), r.name(), attendees)));
        return ok(MessageKey.RELIGION_SERVICE_DONE, attendees);
    }

    /** Taufe oder Segnung (z. B. einer Hochzeit): nur durch das Klerus-Oberhaupt, wird als Ritus festgehalten. */
    public synchronized Reply rite(Caller caller, String kind, String targetName, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty() || religion.get().clergyHead().filter(caller.player()::equals).isEmpty()) {
            return no(MessageKey.RELIGION_ONLY_HEAD_RITE);
        }
        Optional<PlayerRef> target = players.findByName(targetName);
        if (target.isEmpty()) {
            return no(MessageKey.RELIGION_UNKNOWN_PLAYER, targetName);
        }
        Religion r = religion.get();
        if (kind.equals("taufe")) {
            members.put(target.get(), r.id());
            denominations.remove(target.get());
        }
        Message text = Message.of(kind.equals("taufe") ? MessageKey.RELIGION_RITE_BAPTISM
                : MessageKey.RELIGION_RITE_BLESSING, targetName, r.name(), day.value());
        rites.add((kind.equals("taufe") ? "Taufe: " : "Segnung: ") + targetName + " (" + r.name() + ", " + day.value() + ")");
        while (rites.size() > MAX_RITES) {
            rites.removeFirst();
        }
        save();
        announcements.add(Announcement.toAll(text));
        return Reply.success(text);
    }

    /** Klerus-Wahl ausrufen (Mitglied). */
    public synchronized Reply callElection(Caller caller, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        try {
            registry.callClergyElection(religion.get().id(), day.plus(ELECTION_DAYS), day);
            save();
            return ok(MessageKey.RELIGION_ELECTION_CALLED, ELECTION_DAYS);
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    public synchronized Reply candidate(Caller caller, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        try {
            registry.nominateClergy(religion.get().id(), caller.player(), day);
            save();
            return ok(MessageKey.RELIGION_CANDIDATE);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    public synchronized Reply vote(Caller caller, String candidateName, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Optional<PlayerRef> candidate = players.findByName(candidateName);
        if (candidate.isEmpty()) {
            return no(MessageKey.RELIGION_UNKNOWN_PLAYER, candidateName);
        }
        try {
            registry.castClergyVote(religion.get().id(), caller.player(), candidate.get(), day);
            save();
            return ok(MessageKey.RELIGION_VOTED, candidateName);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    /** Logo der Religion setzen (1-24 Zeichen, z. B. ein Symbol); nur Gründer oder Klerus-Oberhaupt. */
    public synchronized Reply logo(Caller caller, String logo) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Religion r = religion.get();
        if (!r.founder().equals(caller.player()) && r.clergyHead().filter(caller.player()::equals).isEmpty()) {
            return no(MessageKey.RELIGION_LOGO_FORBIDDEN);
        }
        String stripped = logo.strip();
        if (stripped.isEmpty() || stripped.length() > 24) {
            return no(MessageKey.RELIGION_LOGO_USAGE);
        }
        logos.put(r.id(), stripped.replace('\t', ' ').replace('\n', ' '));
        save();
        return ok(MessageKey.RELIGION_LOGO_SET, stripped);
    }

    /** Eintrag der Religionsliste für die Oberfläche. */
    public record ReligionEntry(Religion religion, String logo, int members, Message head, boolean mine) {
    }

    public synchronized List<ReligionEntry> entries(ActorRef viewer) {
        Optional<Religion> mine = religionOf(viewer);
        return registry.religions().stream().map(r -> new ReligionEntry(r, logos.getOrDefault(r.id(), ""),
                memberCount(r), head(r), mine.filter(r::equals).isPresent())).toList();
    }

    /**
     * Zufriedenheitspunkte durch Glauben (politische Rolle der Religion): Gläubige Mitglieder gewinnen Halt, mehr bei
     * Inbrunst durch Gebete/Gottesdienste und bei starkem Klerus; Atheisten spüren den Druck eines sehr mächtigen
     * Klerus.
     */
    public synchronized int faithPoints(PersonRef villager) {
        int faith = Personality.of(villager).religiosity();
        Optional<Religion> own = religionOf(villager);
        if (own.isPresent() && faith > 0) {
            return 1 + fervor.getOrDefault(own.get().id(), 0) / 2 + Math.round(own.get().clergyPower() / 50f);
        }
        if (faith <= -JOIN_THRESHOLD && registry.religions().stream().anyMatch(r -> r.clergyPower() >= PRESSURE_POWER
                && r.clergyHead().isPresent() && memberCount(r) > 0)) {
            return -1;
        }
        return 0;
    }

    /** Kurzzeile zur Religion eines Dorfbewohners für die Auskunft beim Anschauen. */
    public synchronized List<Message> describe(PersonRef villager) {
        int faith = Personality.of(villager).religiosity();
        Message attitude = Message.of(faith >= JOIN_THRESHOLD ? MessageKey.RELIGION_ATTITUDE_BELIEVER
                : faith <= -JOIN_THRESHOLD ? MessageKey.RELIGION_ATTITUDE_ATHEIST : MessageKey.RELIGION_ATTITUDE_UNDECIDED);
        Object religion = religionOf(villager).<Object>map(Religion::name).orElse(Message.of(MessageKey.RELIGION_NONE));
        return List.of(Message.of(MessageKey.RELIGION_VILLAGER, religion, attitude, faith));
    }

    /** Ein Dorfbewohner ist gestorben oder verschwunden. */
    public synchronized void gone(PersonRef villager) {
        if (members.remove(villager) != null) {
            denominations.remove(villager);
            save();
        }
    }

    /**
     * Tagesabschluss: Gläubige Dorfbewohner schließen sich der größten Religion an; abgelaufene Klerus-Wahlen
     * werden mit den Stimmen der Dorfbewohner-Mitglieder ausgezählt; ein vakantes Amt wird regelmäßig neu
     * ausgeschrieben.
     */
    public synchronized void endOfDay(Day day) {
        prayedToday.clear();
        fervor.replaceAll((id, value) -> Math.max(0, value - 1));
        fervor.values().removeIf(value -> value == 0);
        logos.keySet().removeIf(id -> registry.religion(id).isEmpty());
        if (registry.religions().isEmpty()) {
            return;
        }
        Religion largest = registry.religions().stream()
                .max(Comparator.comparingInt(this::memberCount).thenComparing(r -> r.foundedOn().value() * -1))
                .orElseThrow();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            for (Membership membership : town.members()) {
                if (membership.member() instanceof PersonRef villager && !members.containsKey(villager)
                        && Personality.of(villager).religiosity() >= JOIN_THRESHOLD
                        && random.nextInt(100) < JOIN_CHANCE_PERCENT) {
                    members.put(villager, largest.id());
                }
            }
        }
        for (Religion religion : registry.religions()) {
            if (religion.election().isPresent() && !day.isBefore(religion.election().get().closesOn())) {
                closeElection(religion, day);
            } else if (religion.election().isEmpty() && religion.clergyHead().isEmpty()
                    && day.value() % VACANCY_ELECTION_INTERVAL == 0) {
                registry.callClergyElection(religion.id(), day.plus(ELECTION_DAYS), day);
            }
        }
        save();
    }

    private void closeElection(Religion religion, Day day) {
        List<ActorRef> candidates = religion.election().get().candidates();
        if (!candidates.isEmpty()) {
            for (Map.Entry<ActorRef, ReligionId> entry : members.entrySet()) {
                if (entry.getKey() instanceof PersonRef villager && entry.getValue().equals(religion.id())) {
                    registry.castClergyVote(religion.id(), villager, candidates.get(random.nextInt(candidates.size())), day);
                }
            }
        }
        Optional<ActorRef> before = religion.clergyHead();
        Religion after = registry.closeClergyElection(religion.id(), day);
        if (after.clergyHead().isPresent() && !after.clergyHead().equals(before)) {
            announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_NEW_HEAD,
                    nameOf(after.clergyHead().get()), after.name())));
        }
    }
}

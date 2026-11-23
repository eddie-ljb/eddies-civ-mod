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
    private final Random random = new Random();

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
    }

    private void save() {
        store.save(new ReligionStore.State(registry.religions(), members, denominations, rites));
    }

    private static Reply done(String text) {
        return Reply.success(Message.of(MessageKey.RELIGION_DONE, text));
    }

    private static Reply fail(String text) {
        return Reply.failure(Message.of(MessageKey.RELIGION_ERROR, text));
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
            announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_DONE,
                    caller.name() + " gründet die Religion " + religion.name() + "."))); 
            return done("Religion gegründet: " + religion.name() + (monotheistic ? " (monotheistisch)" : " (polytheistisch)"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    public synchronized Reply list() {
        if (registry.religions().isEmpty()) {
            return done("Es gibt noch keine Religionen.");
        }
        List<Message> lines = new ArrayList<>();
        for (Religion religion : registry.religions()) {
            lines.add(Message.of(MessageKey.RELIGION_LIST_ENTRY, religion.name(),
                    religion.monotheistic() ? "monotheistisch" : "polytheistisch", memberCount(religion),
                    religion.clergyHead().map(this::nameOf).orElse("vakant")));
        }
        return Reply.success(lines);
    }

    public synchronized Reply info(String name) {
        Optional<Religion> found = registry.religionNamed(name);
        if (found.isEmpty()) {
            return fail("Unbekannte Religion: " + name);
        }
        Religion r = found.get();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.RELIGION_INFO, r.name(), r.monotheistic() ? "monotheistisch" : "polytheistisch",
                r.scriptureName(), r.templeName(), r.language(), memberCount(r), nameOf(r.founder())));
        lines.add(Message.of(MessageKey.RELIGION_INFO_CLERGY, r.clergyHead().map(this::nameOf).orElse("vakant"),
                r.clergyPower(), r.denominations().isEmpty() ? "-"
                        : String.join(", ", r.denominations().stream().map(Denomination::name).toList())));
        r.election().ifPresent(e -> lines.add(Message.of(MessageKey.RELIGION_DONE, "Klerus-Wahl läuft bis Tag "
                + e.closesOn().value() + ", Kandidaten: " + (e.candidates().isEmpty() ? "-"
                : String.join(", ", e.candidates().stream().map(this::nameOf).toList())))));
        return Reply.success(lines);
    }

    public synchronized Reply join(Caller caller, String name) {
        Optional<Religion> found = registry.religionNamed(name);
        if (found.isEmpty()) {
            return fail("Unbekannte Religion: " + name);
        }
        members.put(caller.player(), found.get().id());
        denominations.remove(caller.player());
        save();
        return done("Du gehörst nun zu " + found.get().name() + ".");
    }

    public synchronized Reply leave(Caller caller) {
        if (members.remove(caller.player()) == null) {
            return fail("Du gehörst keiner Religion an.");
        }
        denominations.remove(caller.player());
        save();
        return done("Du bist aus deiner Religion ausgetreten.");
    }

    /** Fügt der eigenen Religion eine Konfession hinzu (Gründer/Klerus) und wählt sie, oder wählt eine bestehende. */
    public synchronized Reply denomination(Caller caller, String name, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return fail("Du gehörst keiner Religion an.");
        }
        try {
            Religion r = religion.get();
            boolean known = r.denominations().stream().anyMatch(d -> d.name().equalsIgnoreCase(name.strip()));
            if (!known) {
                boolean allowed = r.founder().equals(caller.player())
                        || r.clergyHead().filter(caller.player()::equals).isPresent();
                if (!allowed) {
                    return fail("Nur Gründer oder Klerus-Oberhaupt dürfen neue Konfessionen anlegen.");
                }
                registry.addDenomination(r.id(), new Denomination(name), day);
            }
            denominations.put(caller.player(), name.strip());
            save();
            return done("Konfession: " + name.strip());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    /** Machtgrad des Klerus festlegen (nur Gründer oder Oberhaupt, 0-100). */
    public synchronized Reply power(Caller caller, int power, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return fail("Du gehörst keiner Religion an.");
        }
        Religion r = religion.get();
        if (r.clergyHead().isEmpty()) {
            return fail("Das Klerusamt ist vakant.");
        }
        try {
            registry.appointClergy(r.id(), caller.player(), r.clergyHead().get(), power, day);
            save();
            return done("Machtgrad des Klerus: " + power);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    /** Beten: ein Gebet je Tag und Spieler festigt den Glauben (Anzeige in der Antwort). */
    public synchronized Reply pray(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return fail("Du gehörst keiner Religion an.");
        }
        Religion r = religion.get();
        long believers = members.entrySet().stream().filter(e -> e.getValue().equals(r.id())).count();
        return done("Du betest in " + r.templeName() + " (" + r.scriptureName() + "). " + believers
                + " Gläubige teilen deinen Glauben.");
    }

    /** Gottesdienst, nur durch das Klerus-Oberhaupt; alle Gläubigen erfahren davon. */
    public synchronized Reply service(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty() || religion.get().clergyHead().filter(caller.player()::equals).isEmpty()) {
            return fail("Nur das Klerus-Oberhaupt hält Gottesdienste.");
        }
        Religion r = religion.get();
        int attendees = (int) members.entrySet().stream().filter(e -> e.getValue().equals(r.id())).count();
        announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_DONE, caller.name() + " hält einen "
                + "Gottesdienst in " + r.templeName() + " (" + r.name() + ") mit " + attendees + " Gläubigen.")));
        return done("Gottesdienst gehalten, " + attendees + " Gläubige.");
    }

    /** Taufe oder Segnung (z. B. einer Hochzeit): nur durch das Klerus-Oberhaupt, wird als Ritus festgehalten. */
    public synchronized Reply rite(Caller caller, String kind, String targetName, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty() || religion.get().clergyHead().filter(caller.player()::equals).isEmpty()) {
            return fail("Nur das Klerus-Oberhaupt vollzieht Riten.");
        }
        Optional<PlayerRef> target = players.findByName(targetName);
        if (target.isEmpty()) {
            return fail("Unbekannter Spieler: " + targetName);
        }
        Religion r = religion.get();
        if (kind.equals("taufe")) {
            members.put(target.get(), r.id());
            denominations.remove(target.get());
        }
        String text = (kind.equals("taufe") ? "Taufe" : "Segnung") + ": " + targetName + " (" + r.name() + ", Tag "
                + day.value() + ")";
        rites.add(text);
        while (rites.size() > MAX_RITES) {
            rites.removeFirst();
        }
        save();
        announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_DONE, text)));
        return done(text);
    }

    /** Klerus-Wahl ausrufen (Mitglied). */
    public synchronized Reply callElection(Caller caller, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return fail("Du gehörst keiner Religion an.");
        }
        try {
            registry.callClergyElection(religion.get().id(), day.plus(ELECTION_DAYS), day);
            save();
            return done("Klerus-Wahl ausgerufen, sie endet in " + ELECTION_DAYS + " Tagen.");
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    public synchronized Reply candidate(Caller caller, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return fail("Du gehörst keiner Religion an.");
        }
        try {
            registry.nominateClergy(religion.get().id(), caller.player(), day);
            save();
            return done("Du kandidierst für das Klerusamt.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    public synchronized Reply vote(Caller caller, String candidateName, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return fail("Du gehörst keiner Religion an.");
        }
        Optional<PlayerRef> candidate = players.findByName(candidateName);
        if (candidate.isEmpty()) {
            return fail("Unbekannter Spieler: " + candidateName);
        }
        try {
            registry.castClergyVote(religion.get().id(), caller.player(), candidate.get(), day);
            save();
            return done("Stimme abgegeben für " + candidateName + ".");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    /** Kurzzeile zur Religion eines Dorfbewohners für die Auskunft beim Anschauen. */
    public synchronized List<Message> describe(PersonRef villager) {
        int faith = Personality.of(villager).religiosity();
        String attitude = faith >= JOIN_THRESHOLD ? "gläubig" : faith <= -JOIN_THRESHOLD ? "atheistisch" : "unentschieden";
        String religion = religionOf(villager).map(Religion::name).orElse("keine");
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
            announcements.add(Announcement.toAll(Message.of(MessageKey.RELIGION_DONE, nameOf(after.clergyHead().get())
                    + " ist neues Klerus-Oberhaupt von " + after.name() + ".")));
        }
    }
}

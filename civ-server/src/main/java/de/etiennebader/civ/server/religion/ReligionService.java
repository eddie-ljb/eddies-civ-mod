package de.etiennebader.civ.server.religion;

import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.population.VillagerAmbitions;
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
    /** Die Heiligen Schriften im Wortlaut, von der Führung der Religion verfasst. */
    private final Map<ReligionId, String> texts;
    private final List<ClergyRole> roles;
    /** Gesegnete bis zu einem Tag (einschließlich); Segen stärkt die Zufriedenheit. */
    private final Map<ActorRef, Long> blessed;
    /** Wie viele Tage ein Segen wirkt. */
    static final int BLESSING_DAYS = 5;
    static final int MAX_SCRIPTURE = 4000;
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
        this.texts = new LinkedHashMap<>(state.texts());
        this.roles = new ArrayList<>(state.roles());
        this.blessed = new LinkedHashMap<>(state.blessed());
    }

    private void save() {
        store.save(new ReligionStore.State(registry.religions(), members, denominations, rites, fervor, logos, texts,
                roles, blessed));
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

    /** Gläubigkeit, ab der ein Dorfbewohner eine eigene Religion gründet. */
    static final int FOUNDER_FAITH = 35;
    /** Eine Religion je so vielen Dorfbewohnern (mindestens eine) entsteht durch Dorfbewohner. */
    static final int VILLAGERS_PER_RELIGION = 12;
    /** Tageswahrscheinlichkeit einer Gründung bei vollem Glaubenseifer. */
    static final double FOUNDING_CHANCE = 0.08;

    /**
     * Sehr gläubige Dorfbewohner gründen Religionen: Wer noch keiner angehört und eine Gläubigkeit ab
     * {@value #FOUNDER_FAITH} hat, gründet mit täglicher Wahrscheinlichkeit eine, solange es weniger Religionen als ein
     * {@value #VILLAGERS_PER_RELIGION}-tel der Dorfbewohner gibt. Höchstens eine Gründung je Tag. Der Gründer wird
     * Klerus-Oberhaupt; Art (ein oder viele Götter), Namen und Sprache folgen seiner Persönlichkeit.
     *
     * @return ob heute eine Religion gegründet wurde
     */
    public synchronized boolean villagersFound(Day day) {
        List<PersonRef> villagers = new ArrayList<>();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            town.members().stream().map(Membership::member).filter(PersonRef.class::isInstance)
                    .map(PersonRef.class::cast).forEach(villagers::add);
        }
        if (registry.religions().size() >= Math.max(1, villagers.size() / VILLAGERS_PER_RELIGION)
                || registry.religions().size() >= de.etiennebader.civ.domain.religion.ReligionRegistry.MAX_RELIGIONS) {
            return false;
        }
        villagers.sort(Comparator.comparing(PersonRef::uuid));
        for (PersonRef villager : villagers) {
            Personality personality = Personality.of(villager);
            int faith = personality.religiosity();
            if (faith < FOUNDER_FAITH || members.containsKey(villager)) {
                continue;
            }
            double zeal = faith / (double) Personality.MAX;
            if (VillagerAmbitions.roll(villager, day, 0x93) >= FOUNDING_CHANCE * zeal) {
                continue;
            }
            if (found(villager, personality, day)) {
                return true;
            }
        }
        return false;
    }

    private boolean found(PersonRef founder, Personality personality, Day day) {
        boolean monotheistic = personality.order() + personality.tradition() >= 0;
        String stem = personality.nature() >= 30 ? "Hain" : personality.community() >= 30 ? "Gemeinschaft"
                : personality.order() >= 30 ? "Orden" : personality.tradition() < -30 ? "Neuer Weg"
                : monotheistic ? "Kirche" : "Glaube";
        String name = de.etiennebader.civ.application.population.FoundingNames.religion(founder, stem, monotheistic,
                candidate -> registry.religionNamed(candidate).isPresent());
        String scripture = de.etiennebader.civ.application.population.FoundingNames.scripture(founder, monotheistic,
                candidate -> registry.religions().stream().anyMatch(r -> r.scriptureName().equalsIgnoreCase(candidate)));
        String temple = personality.nature() >= 30 ? "Heiliger Hain" : monotheistic ? "Kirche" : "Tempel";
        String language = personality.tradition() >= 0 ? "Altdeutsch" : "Neudeutsch";
        try {
            Religion religion = registry.found(ReligionId.generate(ids), name, founder, monotheistic, scripture, temple,
                    language, day);
            members.put(founder, religion.id());
            save();
            announcements.add(Announcement.toAll(Message.of(MessageKey.VILLAGER_RELIGION_FOUNDED,
                    nameOf(founder), religion.name())));
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
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
        boolean inHouse = queries.territory().sovereignOf(caller.chunk())
                .flatMap(id -> queries.land(id).plotAt(caller.position().dimension(), caller.position().x(),
                        caller.position().z()))
                .filter(plot -> plot.type() == de.etiennebader.civ.domain.land.PlotType.RELIGIOUS).isPresent();
        fervor.merge(r.id(), inHouse ? 2 : 1, (a, b) -> Math.min(MAX_FERVOR, a + b));
        save();
        return ok(MessageKey.RELIGION_PRAYED, r.templeName(), r.scriptureName(), memberCount(r));
    }

    /** Gottesdienst, nur durch das Klerus-Oberhaupt; alle Gläubigen erfahren davon. */
    public synchronized Reply service(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty() || !may(religion.get(), caller.player(), ClergyRole.Right.SERVICE)) {
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

    /** Ob der Akteur das Recht hat: als Oberhaupt immer, sonst über ein Amt mit diesem Recht. */
    private boolean may(Religion religion, ActorRef actor, ClergyRole.Right right) {
        return religion.clergyHead().filter(actor::equals).isPresent() || roles.stream()
                .anyMatch(role -> role.religion().equals(religion.id()) && role.rights().contains(right)
                        && role.holders().contains(actor));
    }

    /** Taufe oder Segnung eines Spielers (z. B. einer Hochzeit), wird als Ritus festgehalten. */
    public synchronized Reply rite(Caller caller, String kind, String targetName, Day day) {
        Optional<PlayerRef> target = players.findByName(targetName);
        if (target.isEmpty()) {
            return no(MessageKey.RELIGION_UNKNOWN_PLAYER, targetName);
        }
        return rite(caller, kind, target.get(), targetName, day);
    }

    /**
     * Taufe oder Segnung eines Spielers oder Dorfbewohners. Das darf das Klerus-Oberhaupt und jeder Amtsträger mit dem
     * Recht dazu. Ein Segen stärkt einen Dorfbewohner {@value #BLESSING_DAYS} Tage lang; eine Taufe nimmt ihn in die
     * Religion auf.
     */
    public synchronized Reply rite(Caller caller, String kind, ActorRef target, String targetName, Day day) {
        boolean baptism = kind.equals("taufe");
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty() || !may(religion.get(), caller.player(),
                baptism ? ClergyRole.Right.BAPTISE : ClergyRole.Right.BLESS)) {
            return no(MessageKey.RELIGION_ONLY_HEAD_RITE);
        }
        Religion r = religion.get();
        if (baptism) {
            members.put(target, r.id());
            denominations.remove(target);
        } else {
            blessed.put(target, day.value() + BLESSING_DAYS);
            if (target instanceof PersonRef) {
                fervor.merge(r.id(), 1, (a, b) -> Math.min(MAX_FERVOR, a + b));
            }
        }
        Message text = Message.of(baptism ? MessageKey.RELIGION_RITE_BAPTISM
                : MessageKey.RELIGION_RITE_BLESSING, targetName, r.name(), day.value());
        rites.add((baptism ? "Taufe: " : "Segnung: ") + targetName + " (" + r.name() + ", " + day.value() + ")");
        while (rites.size() > MAX_RITES) {
            rites.removeFirst();
        }
        save();
        announcements.add(Announcement.toAll(text));
        return Reply.success(text);
    }

    // ------------------------------------------------------------------ Heilige Schrift

    /** Der Wortlaut der Heiligen Schrift der eigenen Religion; leer, wenn noch nichts geschrieben wurde. */
    public synchronized Reply scripture(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        return scriptureOf(religion.get());
    }

    /** Die Schrift einer Religion beim Namen (für alle lesbar). */
    public synchronized Reply scripture(String religionName) {
        return registry.religionNamed(religionName).map(this::scriptureOf)
                .orElseGet(() -> no(MessageKey.RELIGION_UNKNOWN, religionName));
    }

    private Reply scriptureOf(Religion r) {
        String text = texts.getOrDefault(r.id(), "");
        if (text.isBlank()) {
            return ok(MessageKey.RELIGION_SCRIPTURE_EMPTY, r.scriptureName(), r.name());
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.RELIGION_SCRIPTURE_HEADER, r.scriptureName(), r.name()));
        for (String line : text.split("\n")) {
            lines.add(Message.of(MessageKey.RELIGION_SCRIPTURE_LINE, line));
        }
        return Reply.success(lines);
    }

    /** Wortlaut der Schrift im Rohtext, z. B. für den Editor auf der Webseite. */
    public synchronized String scriptureText(ReligionId id) {
        return texts.getOrDefault(id, "");
    }

    /**
     * Schreibt an der Heiligen Schrift: ersetzt den Wortlaut ({@code append} falsch) oder hängt einen Absatz an.
     * Das darf das Oberhaupt oder ein Amtsträger mit dem Recht „schrift“. {@code \n} im Text ist ein Zeilenumbruch.
     */
    public synchronized Reply writeScripture(Caller caller, String text, boolean append, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        return writeScripture(religion.get(), caller.player(), text, append);
    }

    /** Wie {@link #writeScripture(Caller, String, boolean, Day)} für die Webseite, mit dem Namen der Religion. */
    public synchronized Reply writeScripture(PlayerRef editor, String religionName, String text, boolean append) {
        Optional<Religion> religion = registry.religionNamed(religionName);
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_UNKNOWN, religionName);
        }
        return writeScripture(religion.get(), editor, text, append);
    }

    private Reply writeScripture(Religion r, ActorRef editor, String text, boolean append) {
        if (!may(r, editor, ClergyRole.Right.SCRIPTURE) && !r.founder().equals(editor)) {
            return no(MessageKey.RELIGION_SCRIPTURE_FORBIDDEN);
        }
        String clean = text.replace("\\n", "\n").replace("\r", "").strip();
        String merged = append && !texts.getOrDefault(r.id(), "").isBlank()
                ? texts.get(r.id()) + "\n" + clean : clean;
        if (merged.length() > MAX_SCRIPTURE) {
            return no(MessageKey.RELIGION_SCRIPTURE_TOO_LONG, MAX_SCRIPTURE);
        }
        if (merged.isBlank()) {
            texts.remove(r.id());
        } else {
            texts.put(r.id(), merged);
        }
        save();
        return ok(MessageKey.RELIGION_SCRIPTURE_SET, r.scriptureName(), merged.length());
    }

    /**
     * Benennt Schrift, Gotteshaus oder Sprache der eigenen Religion um ({@code what}: {@code schrift},
     * {@code gotteshaus} oder {@code sprache}). Nur Gründer, Oberhaupt oder Amtsträger mit dem Recht „schrift“.
     */
    public synchronized Reply rename(Caller caller, String what, String value, Day day) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Religion r = religion.get();
        try {
            boolean allowed = may(r, caller.player(), ClergyRole.Right.SCRIPTURE);
            switch (what.toLowerCase(java.util.Locale.ROOT)) {
                case "schrift" -> registry.redefine(r.id(), caller.player(), allowed, value, "", "", day);
                case "gotteshaus" -> registry.redefine(r.id(), caller.player(), allowed, "", value, "", day);
                case "sprache" -> registry.redefine(r.id(), caller.player(), allowed, "", "", value, day);
                default -> {
                    return no(MessageKey.RELIGION_RENAME_USAGE);
                }
            }
            save();
            return ok(MessageKey.RELIGION_RENAMED, what, value.strip());
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Klerus-Ämter

    private boolean leads(Religion r, ActorRef actor) {
        return r.founder().equals(actor) || r.clergyHead().filter(actor::equals).isPresent();
    }

    /**
     * Legt ein Amt im Klerus an, z. B. „Priester“ mit den Rechten segnen und taufen, oder ändert die Rechte eines
     * bestehenden. Nur Gründer und Oberhaupt.
     */
    public synchronized Reply defineRole(Caller caller, String name, List<String> rightWords) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Religion r = religion.get();
        if (!leads(r, caller.player())) {
            return no(MessageKey.RELIGION_ROLE_FORBIDDEN);
        }
        java.util.Set<ClergyRole.Right> rights = new java.util.LinkedHashSet<>();
        for (String word : rightWords) {
            Optional<ClergyRole.Right> right = ClergyRole.Right.ofWord(word);
            if (right.isEmpty()) {
                return no(MessageKey.RELIGION_ROLE_UNKNOWN_RIGHT, word);
            }
            rights.add(right.get());
        }
        ClergyRole role;
        try {
            role = new ClergyRole(r.id(), name, rights, java.util.Set.of());
        } catch (IllegalArgumentException e) {
            return no(MessageKey.RELIGION_ROLE_BAD_NAME, ClergyRole.MAX_NAME);
        }
        Optional<ClergyRole> existing = roleOf(r, role.name());
        if (existing.isPresent()) {
            roles.remove(existing.get());
            role = new ClergyRole(r.id(), role.name(), rights, existing.get().holders());
        } else if (roles.stream().filter(other -> other.religion().equals(r.id())).count() >= ClergyRole.MAX_PER_RELIGION) {
            return no(MessageKey.RELIGION_ROLE_LIMIT, ClergyRole.MAX_PER_RELIGION);
        }
        roles.add(role);
        save();
        return ok(MessageKey.RELIGION_ROLE_DEFINED, role.name(), rightsText(role));
    }

    private Optional<ClergyRole> roleOf(Religion r, String name) {
        return roles.stream().filter(role -> role.religion().equals(r.id()) && role.name().equalsIgnoreCase(name.strip()))
                .findFirst();
    }

    private static String rightsText(ClergyRole role) {
        return role.rights().isEmpty() ? "-"
                : String.join(", ", role.rights().stream().map(ClergyRole.Right::word).toList());
    }

    /** Löscht ein Amt samt seinen Rechten. Nur Gründer und Oberhaupt. */
    public synchronized Reply deleteRole(Caller caller, String name) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        if (!leads(religion.get(), caller.player())) {
            return no(MessageKey.RELIGION_ROLE_FORBIDDEN);
        }
        Optional<ClergyRole> role = roleOf(religion.get(), name);
        if (role.isEmpty()) {
            return no(MessageKey.RELIGION_ROLE_UNKNOWN, name.strip());
        }
        roles.remove(role.get());
        save();
        return ok(MessageKey.RELIGION_ROLE_DELETED, role.get().name());
    }

    /** Überträgt ein Amt an ein Mitglied (Spieler oder Dorfbewohner); es muss selbst der Religion angehören. */
    public synchronized Reply assignRole(Caller caller, String roleName, ActorRef target, String targetName) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        Religion r = religion.get();
        if (!leads(r, caller.player())) {
            return no(MessageKey.RELIGION_ROLE_FORBIDDEN);
        }
        Optional<ClergyRole> role = roleOf(r, roleName);
        if (role.isEmpty()) {
            return no(MessageKey.RELIGION_ROLE_UNKNOWN, roleName.strip());
        }
        if (!r.id().equals(members.get(target))) {
            return no(MessageKey.RELIGION_ROLE_NOT_MEMBER, targetName);
        }
        role.get().holders().add(target);
        save();
        return ok(MessageKey.RELIGION_ROLE_ASSIGNED, role.get().name(), targetName);
    }

    /** Entzieht einem Mitglied alle Ämter. */
    public synchronized Reply revokeRoles(Caller caller, ActorRef target, String targetName) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        if (!leads(religion.get(), caller.player())) {
            return no(MessageKey.RELIGION_ROLE_FORBIDDEN);
        }
        boolean any = false;
        for (ClergyRole role : roles) {
            if (role.religion().equals(religion.get().id())) {
                any |= role.holders().remove(target);
            }
        }
        save();
        return any ? ok(MessageKey.RELIGION_ROLE_REVOKED, targetName) : no(MessageKey.RELIGION_ROLE_HOLDS_NONE, targetName);
    }

    /** Die Ämter der eigenen Religion mit Rechten und Amtsträgern. */
    public synchronized Reply listRoles(Caller caller) {
        Optional<Religion> religion = religionOf(caller.player());
        if (religion.isEmpty()) {
            return no(MessageKey.RELIGION_NOT_MEMBER);
        }
        List<Message> lines = new ArrayList<>();
        for (ClergyRole role : roles) {
            if (role.religion().equals(religion.get().id())) {
                lines.add(Message.of(MessageKey.RELIGION_ROLE_ENTRY, role.name(), rightsText(role),
                        role.holders().isEmpty() ? "-"
                                : String.join(", ", role.holders().stream().map(this::nameOf).toList())));
            }
        }
        return lines.isEmpty() ? ok(MessageKey.RELIGION_ROLE_NONE) : Reply.success(lines);
    }

    /** Die Namen der Ämter der eigenen Religion, für die Befehlsvorschläge. */
    public synchronized List<String> roleNames(ActorRef actor) {
        return religionOf(actor).map(r -> roles.stream().filter(role -> role.religion().equals(r.id()))
                .map(ClergyRole::name).toList()).orElse(List.of());
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

    /** Eine Religion für die Webseite: alles als Text und Zahl, ohne Domänentypen. */
    public record Summary(String id, String name, String logo, boolean monotheistic, String scripture, String temple,
                          String language, String head, int power, int members, int villagers, int fervor,
                          List<String> denominations, String founder, long founded, boolean electionOpen,
                          long electionCloses, List<String> candidates, boolean mine, String denomination) {
    }

    /** Wie gläubig die Dorfbewohner insgesamt sind. */
    public record Census(int believers, int undecided, int atheists, int organised) {
    }

    /** Alle Religionen für die Webseite; {@code viewer} darf leer sein. */
    public synchronized List<Summary> summaries(Optional<? extends ActorRef> viewer) {
        Optional<Religion> mine = viewer.flatMap(this::religionOf);
        List<Summary> out = new ArrayList<>();
        for (Religion r : registry.religions()) {
            int villagerMembers = (int) members.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(r.id()) && entry.getKey() instanceof PersonRef).count();
            out.add(new Summary(r.id().toString(), r.name(), logos.getOrDefault(r.id(), ""), r.monotheistic(),
                    r.scriptureName(), r.templeName(), r.language(), r.clergyHead().map(this::nameOf).orElse(""),
                    r.clergyPower(), memberCount(r), villagerMembers, fervor.getOrDefault(r.id(), 0),
                    r.denominations().stream().map(Denomination::name).toList(), nameOf(r.founder()),
                    r.foundedOn().value(), r.election().isPresent(),
                    r.election().map(e -> e.closesOn().value()).orElse(0L),
                    r.election().map(e -> e.candidates().stream().map(this::nameOf).toList()).orElse(List.of()),
                    mine.filter(r::equals).isPresent(),
                    viewer.map(denominations::get).orElse("")));
        }
        return out;
    }

    /** Glaubensverteilung der Dorfbewohner in allen Gemeinden. */
    public synchronized Census census() {
        int believers = 0;
        int undecided = 0;
        int atheists = 0;
        int organised = 0;
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            for (Membership membership : town.members()) {
                if (membership.member() instanceof PersonRef villager) {
                    int faith = Personality.of(villager).religiosity();
                    if (faith >= JOIN_THRESHOLD) {
                        believers++;
                    } else if (faith <= -JOIN_THRESHOLD) {
                        atheists++;
                    } else {
                        undecided++;
                    }
                    if (members.containsKey(villager)) {
                        organised++;
                    }
                }
            }
        }
        return new Census(believers, undecided, atheists, organised);
    }

    /** Die letzten Riten und Zeremonien. */
    public synchronized List<String> recentRites(int limit) {
        int from = Math.max(0, rites.size() - limit);
        return List.copyOf(rites.subList(from, rites.size())).reversed();
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
        if (blessed.containsKey(villager)) {
            return faithPoints(villager, faith) + 2;
        }
        return faithPoints(villager, faith);
    }

    private int faithPoints(PersonRef villager, int faith) {
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
        blessed.values().removeIf(until -> until < day.value());
        texts.keySet().removeIf(id -> registry.religion(id).isEmpty());
        roles.removeIf(role -> registry.religion(role.religion()).isEmpty());
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

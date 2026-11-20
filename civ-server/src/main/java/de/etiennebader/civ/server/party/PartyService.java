package de.etiennebader.civ.server.party;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.party.ChangePartyProfile;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.party.JoinParty;
import de.etiennebader.civ.application.party.LeaveParty;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.polity.VoteOutcome;
import de.etiennebader.civ.application.population.PartyAffiliation;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyList;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Parteien im Spiel.
 *
 * <ul>
 *   <li>{@code /civ partei gruenden <Richtung> <Name>}: Richtung ordnung, freiheit, steuern, ausbau oder mitte.</li>
 *   <li>{@code /civ partei beitreten <Name>}, {@code austreten}, {@code info [Name]}, {@code liste}.</li>
 *   <li>{@code /civ partei richtung <Ordnung> <Wirtschaft>} (je -100 bis 100), {@code wappen} (Item in der Hand),
 *   {@code aufloesen}: nur der Vorsitz.</li>
 * </ul>
 * Am Tagesende organisieren sich Dorfbewohner ({@link PartyAffiliation}).
 */
public final class PartyService {

    private static final int STRENGTH = 60;
    private static final Map<String, int[]> DIRECTIONS = Map.of(
            "ordnung", new int[]{STRENGTH, 0}, "order", new int[]{STRENGTH, 0},
            "freiheit", new int[]{-STRENGTH, 0}, "freedom", new int[]{-STRENGTH, 0},
            "steuern", new int[]{0, -STRENGTH}, "taxes", new int[]{0, -STRENGTH},
            "ausbau", new int[]{0, STRENGTH}, "investment", new int[]{0, STRENGTH},
            "mitte", new int[]{0, 0}, "centre", new int[]{0, 0});
    private static final Map<Platform, String> VILLAGER_NAMES = Map.of(
            Platform.ORDER, "Ordnungspartei", Platform.FREEDOM, "Freiheitsbund",
            Platform.LOW_TAXES, "Steuerzahlerpartei", Platform.INVESTMENT, "Aufbaupartei", Platform.NONE, "Mitte");

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final CurrencyId currency;
    private final ServerSettings.PartySettings settings;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;

    public PartyService(CivApplication app, Calendar calendar, CurrencyId currency,
                        ServerSettings.PartySettings settings, PlayerDirectory players, Texts texts,
                        Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /** Richtungen für die Vervollständigung. */
    public static List<String> directionNames() {
        return List.of("ordnung", "freiheit", "steuern", "ausbau", "mitte");
    }

    /** Alle Parteien aus Sicht eines Betrachters, die größten zuerst. */
    public List<PartyView> parties(ActorRef viewer) {
        return queries.parties().stream().map(party -> view(party, viewer))
                .sorted((a, b) -> Long.compare(b.players() + b.villagers(), a.players() + a.villagers()))
                .toList();
    }

    public PartyView view(PolityView party, ActorRef viewer) {
        PartyProfile profile = party.partyProfile().orElseThrow();
        long villagers = party.members().stream().map(Membership::member).filter(PersonRef.class::isInstance).count();
        long players = party.members().stream().map(Membership::member).filter(PlayerRef.class::isInstance).count();
        return new PartyView(party.id(), party.name().value(), profile.order(), profile.economy(), profile.emblem(),
                profile.platform(), party.mayor().or(() -> party.officeTerm(Office.MAYOR).map(term -> term.holder()))
                .map(texts::actor).orElse("-"), players, villagers, party.foundedOn().value(),
                party.isMember(viewer), party.holds(viewer, Office.MAYOR));
    }

    /** Name der Partei eines Akteurs, z. B. für Kandidatenlisten. */
    public Optional<String> partyNameOf(ActorRef actor) {
        return queries.partyOf(actor).map(party -> party.name().value());
    }

    /** {@code /civ partei gruenden <Richtung> <Name>} */
    public Reply found(Caller caller, String directionInput, String name) {
        int[] direction = DIRECTIONS.get(directionInput.strip().toLowerCase(Locale.ROOT));
        if (direction == null) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_DIRECTION, directionInput.strip(),
                    String.join(", ", directionNames())));
        }
        return attempt(() -> {
            PolityName partyName = PolityName.of(name);
            app.foundParty().execute(new FoundParty.Command(partyName, caller.player(),
                    new PartyProfile(direction[0], direction[1], PartyProfile.DEFAULT_EMBLEM), currency,
                    calendar.today()));
            Message platform = Message.of(TranslationKeys.platform(
                    new PartyProfile(direction[0], direction[1], PartyProfile.DEFAULT_EMBLEM).platform()));
            announcements.add(Announcement.toAll(Message.of(MessageKey.PARTY_ANNOUNCED, caller.name(),
                    partyName.value(), platform)));
            return Reply.success(Message.of(MessageKey.PARTY_FOUNDED, partyName.value(), platform));
        });
    }

    /** {@code /civ partei beitreten <Name>} */
    public Reply join(Caller caller, String name) {
        return withParty(name, party -> join(caller, party.id()));
    }

    /** Beitritt aus dem Parteienmenü. */
    public Reply join(Caller caller, PolityId party) {
        return attempt(() -> {
            app.joinParty().execute(new JoinParty.Command(party, caller.player(), calendar.today()));
            String name = queries.polity(party).map(found -> found.name().value()).orElse("?");
            announcements.add(Announcement.toTown(party, Message.of(MessageKey.PARTY_JOINED, caller.name(), name)));
            return Reply.success(Message.of(MessageKey.PARTY_JOINED, caller.name(), name));
        });
    }

    /** {@code /civ partei austreten} */
    public Reply leave(Caller caller) {
        Optional<String> name = partyNameOf(caller.player());
        return attempt(() -> {
            LeaveParty.Result result = app.leaveParty().execute(new LeaveParty.Command(caller.player(),
                    calendar.today()));
            String partyName = name.orElse("?");
            if (result.dissolved()) {
                return Reply.success(Message.of(MessageKey.PARTY_DISSOLVED, partyName));
            }
            announcements.add(Announcement.toTown(result.party(), Message.of(MessageKey.PARTY_LEFT, caller.name(),
                    partyName)));
            return Reply.success(Message.of(MessageKey.PARTY_LEFT, caller.name(), partyName));
        });
    }

    /** {@code /civ partei aufloesen}: nur der Vorsitz; die Kasse geht an ihn. */
    public Reply dissolve(Caller caller) {
        Optional<PolityView> party = queries.partyOf(caller.player());
        if (party.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NO_PARTY));
        }
        if (!party.get().holds(caller.player(), Office.MAYOR)) {
            return Reply.failure(Message.of(MessageKey.NOT_PARTY_HEAD));
        }
        String name = party.get().name().value();
        return attempt(() -> {
            announcements.add(Announcement.toTown(party.get().id(), Message.of(MessageKey.PARTY_DISSOLVED, name)));
            app.dissolvePolity().execute(new DissolvePolity.Command(party.get().id(), caller.player(),
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.PARTY_DISSOLVED, name));
        });
    }

    /** {@code /civ partei richtung <Ordnung> <Wirtschaft>} */
    public Reply setDirection(Caller caller, int order, int economy) {
        return withOwnParty(caller, party -> {
            if (Math.abs(order) > PartyProfile.MAX || Math.abs(economy) > PartyProfile.MAX) {
                return Reply.failure(Message.of(MessageKey.INVALID_DIRECTION));
            }
            PartyProfile profile = party.partyProfile().orElseThrow().withDirection(order, economy);
            return change(caller, party, profile, Message.of(MessageKey.PARTY_DIRECTION_SET, party.name().value(),
                    order, economy, Message.of(TranslationKeys.platform(profile.platform()))));
        });
    }

    /** {@code /civ partei wappen} mit dem Item in der Hand. */
    public Reply setEmblem(Caller caller, String itemId) {
        return withOwnParty(caller, party -> {
            PartyProfile profile;
            try {
                profile = party.partyProfile().orElseThrow().withEmblem(itemId);
            } catch (IllegalArgumentException e) {
                return Reply.failure(Message.of(MessageKey.INVALID_EMBLEM));
            }
            return change(caller, party, profile, Message.of(MessageKey.PARTY_EMBLEM_SET, party.name().value()));
        });
    }

    /** {@code /civ partei info [Name]} */
    public Reply info(Caller caller, Optional<String> name) {
        Optional<PolityView> party = name.filter(found -> !found.isBlank())
                .flatMap(found -> queries.parties().stream()
                        .filter(candidate -> candidate.name().value().equalsIgnoreCase(found.strip())).findFirst())
                .or(() -> name.filter(found -> !found.isBlank()).isPresent() ? Optional.empty()
                        : queries.partyOf(caller.player()));
        if (party.isEmpty()) {
            return Reply.failure(name.filter(found -> !found.isBlank())
                    .map(found -> Message.of(MessageKey.UNKNOWN_PARTY, found.strip()))
                    .orElseGet(() -> Message.of(MessageKey.NO_PARTY)));
        }
        PartyView view = view(party.get(), caller.player());
        return Reply.success(
                Message.of(MessageKey.PARTY_INFO_HEADER, view.name(), view.foundedOn()),
                Message.of(MessageKey.PARTY_INFO_DIRECTION, view.order(), view.economy(),
                        Message.of(TranslationKeys.platform(view.platform()))),
                Message.of(MessageKey.PARTY_INFO_CHAIR, view.chair()),
                Message.of(MessageKey.PARTY_INFO_MEMBERS, view.players(), view.villagers()));
    }

    /** {@code /civ partei liste} */
    public Reply list(Caller caller) {
        List<PartyView> parties = parties(caller.player());
        if (parties.isEmpty()) {
            return Reply.success(Message.of(MessageKey.PARTIES_NONE));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PARTIES_HEADER, parties.size()));
        for (PartyView party : parties) {
            lines.add(Message.of(MessageKey.PARTY_ENTRY, party.name(),
                    Message.of(TranslationKeys.platform(party.platform())), party.players(), party.villagers()));
        }
        return Reply.success(lines);
    }

    /** {@code /civ partei liste}: die Parteiliste der eigenen Partei für Listenwahlen, mit Modus. */
    public Reply partyList(Caller caller) {
        return withOwnParty(caller, party -> {
            boolean byVote = party.laws().valueOf(StandardParameters.PARTY_LIST_BY_VOTE);
            Message mode = Message.of(TranslationKeys.yesNo(byVote));
            List<ActorRef> order = PartyList.decode(party.laws().valueOf(StandardParameters.PARTY_LIST));
            if (order.isEmpty()) {
                return Reply.success(Message.of(MessageKey.PARTY_CANDIDATES_EMPTY, party.name().value(), mode));
            }
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.PARTY_CANDIDATES_HEADER, party.name().value(), mode));
            for (int position = 0; position < order.size(); position++) {
                lines.add(Message.of(MessageKey.PARTY_CANDIDATES_ENTRY, position + 1, texts.actor(order.get(position))));
            }
            return Reply.success(lines);
        });
    }

    /** {@code /civ partei liste modus abstimmung|vorsitz}: nur der Vorsitz legt den Entscheidungsmodus fest. */
    public Reply setPartyListMode(Caller caller, boolean byVote) {
        return withOwnParty(caller, party -> {
            if (!party.holds(caller.player(), Office.MAYOR)) {
                return Reply.failure(Message.of(MessageKey.NOT_PARTY_HEAD));
            }
            return attempt(() -> {
                app.amendLaw().execute(new AmendLaw.Command(party.id(), caller.player(),
                        ParameterValue.of(StandardParameters.PARTY_LIST_BY_VOTE, byVote), calendar.today()));
                return Reply.success(Message.of(MessageKey.PARTY_CANDIDATES_MODE_SET, party.name().value(),
                        Message.of(TranslationKeys.yesNo(byVote))));
            });
        });
    }

    /**
     * {@code /civ partei liste setzen <Spieler...>}: legt die Reihenfolge der Parteiliste fest. Gilt
     * {@link StandardParameters#PARTY_LIST_BY_VOTE}, wird daraus ein Antrag, über den die Parteimitglieder abstimmen;
     * sonst entscheidet allein der Vorsitz. Dorfbewohner-Mitglieder bleiben in Kandidatur-Reihenfolge am Ende, da sie
     * sich nicht per Name angeben lassen.
     */
    public Reply setPartyList(Caller caller, List<String> playerNames) {
        return withOwnParty(caller, party -> {
            List<ActorRef> order;
            try {
                order = resolveMembers(party, playerNames);
            } catch (IllegalArgumentException e) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_PLAYER, e.getMessage()));
            }
            ParameterValue<String> change = ParameterValue.of(StandardParameters.PARTY_LIST, PartyList.encode(order));
            if (party.laws().valueOf(StandardParameters.PARTY_LIST_BY_VOTE)) {
                return attempt(() -> {
                    ProposeMotion.Result result = app.proposeMotion().execute(new ProposeMotion.Command(party.id(),
                            caller.player(), new Motion.Change(change), calendar.today()));
                    if (result.outcome() == VoteOutcome.ACCEPTED) {
                        return Reply.success(Message.of(MessageKey.PARTY_CANDIDATES_SET, party.name().value()));
                    }
                    return Reply.success(Message.of(MessageKey.PARTY_CANDIDATES_PROPOSED, party.name().value()));
                });
            }
            if (!party.holds(caller.player(), Office.MAYOR)) {
                return Reply.failure(Message.of(MessageKey.NOT_PARTY_HEAD));
            }
            return attempt(() -> {
                app.amendLaw().execute(new AmendLaw.Command(party.id(), caller.player(), change, calendar.today()));
                return Reply.success(Message.of(MessageKey.PARTY_CANDIDATES_SET, party.name().value()));
            });
        });
    }

    /** Löst Spielernamen auf Mitglieder der Partei auf; @throws IllegalArgumentException mit dem unbekannten Namen. */
    private List<ActorRef> resolveMembers(PolityView party, List<String> playerNames) {
        List<ActorRef> order = new ArrayList<>();
        for (String name : playerNames) {
            PlayerRef player = players.findByName(name.strip()).orElseThrow(() -> new IllegalArgumentException(name));
            if (!party.isMember(player)) {
                throw new IllegalArgumentException(name);
            }
            order.add(player);
        }
        return order;
    }

    /** Tagesende: Dorfbewohner treten Parteien bei, aus oder gründen eine. */
    public void endOfDay(Day day) {
        if (!settings.villagers()) {
            return;
        }
        for (PartyAffiliation.Step step : PartyAffiliation.plan(queries.polities(), day, settings.founders())) {
            try {
                switch (step) {
                    case PartyAffiliation.Join join ->
                            app.joinParty().execute(new JoinParty.Command(join.party(), join.villager(), day));
                    case PartyAffiliation.Leave leave ->
                            app.leaveParty().execute(new LeaveParty.Command(leave.villager(), day));
                    case PartyAffiliation.Found found -> foundForVillagers(found, day);
                }
            } catch (DomainException e) {
                // Lage hat sich inzwischen geändert, z. B. Partei aufgelöst: Schritt auslassen
            }
        }
    }

    private void foundForVillagers(PartyAffiliation.Found found, Day day) {
        String town = queries.polity(found.town()).map(polity -> polity.name().value()).orElse("");
        String base = VILLAGER_NAMES.get(found.direction()) + " " + town;
        String name = uniqueName(base);
        PersonRef chair = found.members().getFirst();
        app.foundParty().execute(new FoundParty.Command(PolityName.of(name), chair,
                PartyAffiliation.profileFor(found), currency, found.members().subList(1, found.members().size()), day));
        announcements.add(Announcement.toAll(Message.of(MessageKey.VILLAGER_PARTY_FOUNDED, found.members().size(),
                town, name, Message.of(TranslationKeys.platform(found.direction())))));
    }

    private String uniqueName(String base) {
        String trimmed = base.length() > PolityName.MAX_LENGTH ? base.substring(0, PolityName.MAX_LENGTH).strip() : base;
        String name = trimmed;
        for (int number = 2; queries.polityNamed(name).isPresent(); number++) {
            String suffix = " " + number;
            name = (trimmed.length() + suffix.length() > PolityName.MAX_LENGTH
                    ? trimmed.substring(0, PolityName.MAX_LENGTH - suffix.length()).strip() : trimmed) + suffix;
        }
        return name;
    }

    private Reply change(Caller caller, PolityView party, PartyProfile profile, Message success) {
        return attempt(() -> {
            app.changePartyProfile().execute(new ChangePartyProfile.Command(party.id(), caller.player(), profile,
                    calendar.today()));
            return Reply.success(success);
        });
    }

    private Reply withOwnParty(Caller caller, Function<PolityView, Reply> action) {
        return queries.partyOf(caller.player()).map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NO_PARTY)));
    }

    private Reply withParty(String name, Function<PolityView, Reply> action) {
        return queries.parties().stream()
                .filter(party -> party.name().value().equalsIgnoreCase(name.strip()))
                .findFirst()
                .map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.UNKNOWN_PARTY, name.strip())));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}

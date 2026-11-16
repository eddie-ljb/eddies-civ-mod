package de.etiennebader.civ.server.federation;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.federation.JoinFederation;
import de.etiennebader.civ.application.federation.LeaveFederation;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Bünde über den Gemeinden: Länder, Nationen und eigene Ebenen.
 *
 * <ul>
 *   <li>{@code /civ bund gruenden <Ebene> <Name>}: Wer eine Gemeinde (oder einen Bund) führt, gründet darüber einen Bund.</li>
 *   <li>{@code /civ bund einladen <Gemeinde>}, {@code /civ bund beitreten <Bund>}: Aufnahme nur auf Einladung.</li>
 *   <li>{@code /civ bund austreten}, {@code /civ bund aufloesen}, {@code /civ bund info [Name]}.</li>
 * </ul>
 *
 * <p>Wer mehrere Ebenen zugleich führt, handelt für die oberste davon. Einladungen gelten bis zum Neustart.
 */
public final class FederationService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Levels levels;
    private final Texts texts;
    private final Announcements announcements;
    private final Map<PolityId, Set<PolityId>> invitations = new HashMap<>();

    public FederationService(CivApplication app, Calendar calendar, Levels levels, Texts texts,
                             Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    public Levels levels() {
        return levels;
    }

    /** Kennungen der Ebenen, auf denen Bünde gegründet werden können, für die Vervollständigung. */
    public List<String> levelIds() {
        return levels.federal().stream().map(Level::id).toList();
    }

    /** Namen aller Bünde, für die Vervollständigung. */
    public List<String> federationNames() {
        return queries.polities().stream().filter(polity -> polity.kind() == PolityKind.FEDERATION)
                .map(polity -> polity.name().value()).toList();
    }

    /** Die Ebene einer Gemeinde oder eines Bundes. */
    public Level levelOf(PolityView polity) {
        return levels.of(polity.level());
    }

    /**
     * Die Polity, für die der Spieler handelt: die oberste in der Kette seiner Gemeinde, die er führt.
     */
    public Optional<PolityView> actingPolity(PlayerRef player) {
        Optional<PolityView> town = queries.municipalityOf(player);
        if (town.isEmpty()) {
            return Optional.empty();
        }
        List<PolityView> chain = new ArrayList<>();
        chain.add(town.get());
        chain.addAll(queries.ancestorsOf(town.get().id()));
        PolityView acting = null;
        for (PolityView polity : chain) {
            if (polity.mayor().filter(player::equals).isPresent()) {
                acting = polity;
            }
        }
        return Optional.ofNullable(acting);
    }

    /** „Gehört zu: Westmark (Land) › Reich (Nation)“, wenn die Gemeinde einem Bund angehört. */
    public Optional<Message> belongsTo(PolityView town) {
        List<PolityView> ancestors = queries.ancestorsOf(town.id());
        if (ancestors.isEmpty()) {
            return Optional.empty();
        }
        Message chain = link(ancestors.getFirst());
        for (PolityView ancestor : ancestors.subList(1, ancestors.size())) {
            chain = Message.of(MessageKey.FEDERATION_CHAIN, chain, link(ancestor));
        }
        return Optional.of(Message.of(MessageKey.FEDERATION_BELONGS_TO, chain));
    }

    /** {@code /civ bund gruenden <Ebene> <Name>} */
    public Reply found(Caller caller, String levelInput, String name) {
        Optional<Level> level = levels.find(levelInput).filter(found -> found.rank() > 0);
        if (level.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_LEVEL, levelInput.strip(), String.join(", ", levelIds())));
        }
        Optional<PolityView> acting = actingPolity(caller.player());
        if (acting.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_A_HEAD));
        }
        PolityView constituent = acting.get();
        if (level.get().rank() <= levelOf(constituent).rank()) {
            return Reply.failure(tooLow(level.get(), constituent));
        }
        return attempt(() -> {
            PolityName federationName = PolityName.of(name);
            app.foundFederation().execute(new FoundFederation.Command(federationName, level.get().id(),
                    constituent.id(), caller.player(), calendar.today()));
            announcements.add(Announcement.toAll(Message.of(MessageKey.FEDERATION_ANNOUNCED, caller.name(),
                    level.get().displayName(), federationName.value(), constituent.name().value())));
            return Reply.success(Message.of(MessageKey.FEDERATION_FOUNDED, federationName.value(),
                    level.get().displayName(), constituent.name().value()));
        });
    }

    /** {@code /civ bund einladen <Gemeinde oder Bund>} */
    public Reply invite(Caller caller, String name) {
        Optional<PolityView> federation = headedFederation(caller.player());
        if (federation.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_HEAD_OF_FEDERATION));
        }
        Optional<PolityView> target = queries.polityNamed(name);
        if (target.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, name.strip()));
        }
        PolityView guest = target.get();
        PolityView host = federation.get();
        if (levelOf(host).rank() <= levelOf(guest).rank()) {
            return Reply.failure(tooLow(levelOf(host), guest));
        }
        Optional<PolityView> parent = queries.parentOf(guest.id());
        if (parent.isPresent()) {
            return Reply.failure(Message.of(MessageKey.FEDERATION_GUEST_TAKEN, guest.name().value(),
                    parent.get().name().value()));
        }
        invitations.computeIfAbsent(guest.id(), id -> new LinkedHashSet<>()).add(host.id());
        announcements.add(Announcement.toTown(guest.id(), Message.of(MessageKey.FEDERATION_INVITATION,
                host.name().value(), guest.name().value())));
        return Reply.success(Message.of(MessageKey.FEDERATION_INVITED, guest.name().value(), host.name().value()));
    }

    /** {@code /civ bund beitreten <Bund>}: nimmt eine Einladung an. */
    public Reply join(Caller caller, String name) {
        Optional<PolityView> acting = actingPolity(caller.player());
        if (acting.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_A_HEAD));
        }
        Optional<PolityView> target = queries.polityNamed(name);
        if (target.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, name.strip()));
        }
        PolityView guest = acting.get();
        PolityView host = target.get();
        if (!invitations.getOrDefault(guest.id(), Set.of()).contains(host.id())) {
            return Reply.failure(Message.of(MessageKey.FEDERATION_NOT_INVITED, host.name().value(),
                    guest.name().value()));
        }
        if (levelOf(host).rank() <= levelOf(guest).rank()) {
            return Reply.failure(tooLow(levelOf(host), guest));
        }
        return attempt(() -> {
            app.joinFederation().execute(new JoinFederation.Command(host.id(), guest.id(), calendar.today()));
            invitations.remove(guest.id());
            announcements.add(Announcement.toTown(host.id(), Message.of(MessageKey.FEDERATION_JOINED,
                    guest.name().value(), host.name().value())));
            return Reply.success(Message.of(MessageKey.FEDERATION_JOINED, guest.name().value(), host.name().value()));
        });
    }

    /** {@code /civ bund austreten}: Die oberste geführte Polity verlässt ihren Bund. */
    public Reply leave(Caller caller) {
        Optional<PolityView> acting = actingPolity(caller.player());
        if (acting.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_A_HEAD));
        }
        PolityView constituent = acting.get();
        return attempt(() -> {
            PolityId host = app.leaveFederation().execute(new LeaveFederation.Command(constituent.id(),
                    calendar.today()));
            String hostName = queries.polity(host).map(polity -> polity.name().value()).orElse("?");
            announcements.add(Announcement.toTown(host, Message.of(MessageKey.FEDERATION_LEFT,
                    constituent.name().value(), hostName)));
            return Reply.success(Message.of(MessageKey.FEDERATION_LEFT, constituent.name().value(), hostName));
        });
    }

    /** {@code /civ bund aufloesen}: Die Glieder werden frei, die Kasse geht an den Vorsitz. */
    public Reply dissolve(Caller caller) {
        Optional<PolityView> federation = headedFederation(caller.player());
        if (federation.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_HEAD_OF_FEDERATION));
        }
        return dissolve(federation.get(), caller.player());
    }

    /** {@code /civ admin bund aufloesen <Name>} */
    public Reply dissolveByAdmin(Caller caller, String name) {
        Optional<PolityView> federation = queries.polityNamed(name)
                .filter(polity -> polity.kind() == PolityKind.FEDERATION);
        if (federation.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_FEDERATION, name.strip()));
        }
        return dissolve(federation.get(), federation.get().mayor().orElse(caller.player()));
    }

    private Reply dissolve(PolityView federation, ActorRef heir) {
        List<PolityId> constituents = federation.constituents().stream().map(PolityRef::id).toList();
        String name = federation.name().value();
        return attempt(() -> {
            app.dissolvePolity().execute(new DissolvePolity.Command(federation.id(), heir, calendar.today()));
            invitations.values().forEach(hosts -> hosts.remove(federation.id()));
            for (PolityId constituent : constituents) {
                announcements.add(Announcement.toTown(constituent, Message.of(MessageKey.FEDERATION_DISSOLVED, name)));
            }
            return Reply.success(Message.of(MessageKey.FEDERATION_DISSOLVED, name));
        });
    }

    /** {@code /civ bund info [Name]}: ohne Namen alle Bünde über der eigenen Gemeinde. */
    public Reply info(Caller caller, Optional<String> name) {
        if (name.isPresent()) {
            Optional<PolityView> polity = queries.polityNamed(name.get());
            if (polity.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, name.get().strip()));
            }
            return polity.get().kind() == PolityKind.FEDERATION ? Reply.success(describe(polity.get()))
                    : chainOf(polity.get());
        }
        return queries.municipalityOf(caller.player()).map(this::chainOf)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
    }

    /** {@code /civ admin ebenen} */
    public Reply listLevels() {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.LEVELS_HEADER));
        for (Level level : levels.all()) {
            long count = queries.polities().stream().filter(polity -> polity.level().equals(level.id())).count();
            lines.add(Message.of(MessageKey.LEVEL_ENTRY, level.displayName(), level.id(), level.rank(), count));
        }
        return Reply.success(lines);
    }

    private Reply chainOf(PolityView town) {
        List<PolityView> ancestors = queries.ancestorsOf(town.id());
        if (ancestors.isEmpty()) {
            return Reply.success(Message.of(MessageKey.FEDERATION_NONE, town.name().value()));
        }
        List<Message> lines = new ArrayList<>();
        for (PolityView ancestor : ancestors) {
            lines.addAll(describe(ancestor));
        }
        return Reply.success(lines);
    }

    private List<Message> describe(PolityView federation) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.FEDERATION_INFO_HEADER, federation.name().value(),
                levelOf(federation).displayName(), federation.foundedOn().value()));
        lines.add(Message.of(MessageKey.FEDERATION_INFO_HEAD, federation.mayor().map(texts::actor).orElse("-")));
        List<String> constituents = federation.constituents().stream()
                .map(ref -> queries.polity(ref.id()).map(polity -> polity.name().value()).orElse("?"))
                .toList();
        lines.add(Message.of(MessageKey.FEDERATION_INFO_CONSTITUENTS, constituents.size(),
                String.join(", ", constituents)));
        long villagers = federation.members().stream().map(Membership::member)
                .filter(member -> member instanceof PersonRef).count();
        long players = federation.members().stream().map(Membership::member)
                .filter(member -> member instanceof PlayerRef).count();
        lines.add(Message.of(MessageKey.FEDERATION_INFO_MEMBERS, players, villagers));
        lines.add(Message.of(MessageKey.FEDERATION_INFO_TREASURY, texts.money(queries.ledger()
                .balanceOf(federation.asActor(), federation.currency())),
                federation.laws().valueOf(StandardParameters.FEDERAL_LEVY_PERCENT)));
        queries.parentOf(federation.id()).ifPresent(parent -> lines.add(Message.of(MessageKey.FEDERATION_INFO_PARENT,
                link(parent))));
        return lines;
    }

    private Optional<PolityView> headedFederation(PlayerRef player) {
        return actingPolity(player).filter(polity -> polity.kind() == PolityKind.FEDERATION);
    }

    private Message link(PolityView polity) {
        return Message.of(MessageKey.FEDERATION_LINK, polity.name().value(), levelOf(polity).displayName());
    }

    private Message tooLow(Level level, PolityView constituent) {
        return Message.of(MessageKey.LEVEL_TOO_LOW, level.displayName(), constituent.name().value(),
                levelOf(constituent).displayName());
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}

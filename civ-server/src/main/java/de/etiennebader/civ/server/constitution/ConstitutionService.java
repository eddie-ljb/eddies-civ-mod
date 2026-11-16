package de.etiennebader.civ.server.constitution;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.constitution.ChangePreamble;
import de.etiennebader.civ.application.constitution.ProclaimConstitution;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.polity.Majorities;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.federation.FederationService;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.town.LawSetting;
import de.etiennebader.civ.server.vote.ProposalView;
import de.etiennebader.civ.server.vote.VotingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Verfassungen im Spiel.
 *
 * <ul>
 *   <li>{@code /civ verfassung [Name]}: Verfassung der eigenen Gemeinde oder einer anderen Gemeinde bzw. eines Bundes.</li>
 *   <li>{@code /civ verfassung praeambel <Text>}, {@code /civ verfassung ausrufen [Name]}: nur die Spitze, nur beim
 *   Entwurf.</li>
 *   <li>{@code /civ verfassung artikel <Regel> <Wert>}: Verfassungsartikel ändern; nach dem Ausrufen per Abstimmung mit
 *   Verfassungsmehrheit.</li>
 * </ul>
 * Ohne Namen handelt der Spieler für die oberste Ebene, die er führt, sonst für seine Gemeinde.
 */
public final class ConstitutionService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;
    private final CivCommands commands;
    private final OfficeService offices;
    private final VotingService voting;
    private final FederationService federations;

    public ConstitutionService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements,
                               CivCommands commands, OfficeService offices, VotingService voting,
                               FederationService federations) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.offices = Objects.requireNonNull(offices, "offices");
        this.voting = Objects.requireNonNull(voting, "voting");
        this.federations = Objects.requireNonNull(federations, "federations");
    }

    /** Kennungen der Verfassungsartikel, für die Vervollständigung. */
    public static List<String> articleIds() {
        return StandardParameters.constitutional().stream().map(ParameterKey::id).toList();
    }

    /** Die Gemeinde, deren Gebiet an dieser Stelle liegt; dort gilt ihre Verfassung. */
    public Optional<PolityView> governing(BlockPosition position) {
        return queries.territory().claimAt(position.chunk()).map(Claim::polity)
                .flatMap(queries::polity);
    }

    /** Die Verfassung einer Gemeinde oder eines Bundes aus Sicht eines Betrachters. */
    public ConstitutionView view(PolityView polity, ActorRef viewer) {
        List<LawSetting> articles = new ArrayList<>();
        for (ParameterKey<?> key : StandardParameters.constitutional()) {
            articles.add(new LawSetting(key.id(), Message.of(TranslationKeys.law(key)), polity.laws().valueOf(key)));
        }
        List<OfficeView> organs = offices.officesOf(polity, viewer);
        List<ProposalView> amendments = voting.proposalsOf(polity, viewer).stream()
                .filter(proposal -> polity.proposals().stream()
                        .filter(open -> open.id().equals(proposal.id()))
                        .anyMatch(open -> Majorities.affected(polity, open.motion()).stream()
                                .anyMatch(StandardParameters::isConstitutional)))
                .toList();
        Optional<ConstitutionView.Link> parent = queries.parentOf(polity.id()).map(ConstitutionService::link);
        List<ConstitutionView.Link> constituents = polity.constituents().stream()
                .flatMap(ref -> queries.polity(ref.id()).stream())
                .map(ConstitutionService::link)
                .toList();
        return new ConstitutionView(polity.id(), polity.name().value(), federations.levelOf(polity).displayName(),
                polity.kind() == PolityKind.FEDERATION, polity.constitution().proclaimedOn().map(Day::value),
                polity.constitution().preamble(), articles,
                polity.laws().valueOf(StandardParameters.AMENDMENT_MAJORITY_PERCENT), organs, amendments, parent,
                constituents, polity.holds(viewer, Office.MAYOR), polity.isMember(viewer));
    }

    /** {@code /civ verfassung [Name]} */
    public Reply show(Caller caller, Optional<String> name) {
        return target(caller, name, false).map(polity -> Reply.success(describe(view(polity, caller.player()))))
                .orElseGet(() -> notFound(name));
    }

    /** {@code /civ verfassung ausrufen [Name]} */
    public Reply proclaim(Caller caller, Optional<String> name) {
        return target(caller, name, true).map(polity -> proclaim(caller, polity.id())).orElseGet(() -> notFound(name));
    }

    /** Ausrufen aus dem Verfassungsmenü. */
    public Reply proclaim(Caller caller, PolityId polity) {
        return attempt(() -> {
            app.proclaimConstitution().execute(new ProclaimConstitution.Command(polity, caller.player(),
                    calendar.today()));
            PolityView proclaimed = queries.polity(polity).orElseThrow();
            Message message = Message.of(MessageKey.CONSTITUTION_PROCLAIMED, proclaimed.name().value(),
                    proclaimed.laws().valueOf(StandardParameters.AMENDMENT_MAJORITY_PERCENT));
            announcements.add(Announcement.toTown(polity, message));
            return Reply.success(message);
        });
    }

    /** {@code /civ verfassung praeambel <Text>} */
    public Reply setPreamble(Caller caller, String text) {
        Optional<PolityView> polity = target(caller, Optional.empty(), true);
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return attempt(() -> {
            app.changePreamble().execute(new ChangePreamble.Command(polity.get().id(), caller.player(), text,
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.PREAMBLE_SET, polity.get().name().value()));
        });
    }

    /** {@code /civ verfassung artikel <Regel> <Wert>} */
    public Reply amend(Caller caller, String article, String value) {
        Optional<ParameterKey<?>> key = StandardParameters.constitutional().stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(article.strip()))
                .findFirst();
        if (key.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_ARTICLE, article.strip(), String.join(", ", articleIds())));
        }
        Optional<PolityView> polity = target(caller, Optional.empty(), true);
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return commands.amendLaw(caller, polity.get(), key.get(), value);
    }

    /** Chatform einer Verfassung. */
    public List<Message> describe(ConstitutionView constitution) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.CONSTITUTION_HEADER, constitution.name(), constitution.level()));
        lines.add(constitution.proclaimedOn()
                .map(day -> Message.of(MessageKey.CONSTITUTION_PROCLAIMED_ON, day, constitution.amendmentPercent()))
                .orElseGet(() -> Message.of(MessageKey.CONSTITUTION_DRAFT)));
        if (!constitution.preamble().isEmpty()) {
            lines.add(Message.of(MessageKey.CONSTITUTION_PREAMBLE, constitution.preamble()));
        }
        for (LawSetting article : constitution.articles()) {
            lines.add(Message.of(MessageKey.CONSTITUTION_ARTICLE, article.name(), texts.lawValue(article.value())));
        }
        for (OfficeView office : constitution.offices()) {
            lines.add(Message.of(MessageKey.CONSTITUTION_ORGAN, office.name(), office.holder().orElse("-")));
        }
        constitution.parent().ifPresent(parent -> lines.add(Message.of(MessageKey.FEDERATION_INFO_PARENT,
                parent.name())));
        if (!constitution.amendments().isEmpty()) {
            lines.add(Message.of(MessageKey.CONSTITUTION_AMENDMENTS, constitution.amendments().size()));
        }
        return lines;
    }

    /**
     * Mit Namen die benannte Polity. Ohne Namen: wer führt, handelt für die oberste geführte Ebene
     * ({@code forHead}) bzw. sieht die eigene Gemeinde.
     */
    private Optional<PolityView> target(Caller caller, Optional<String> name, boolean forHead) {
        if (name.isPresent() && !name.get().isBlank()) {
            return queries.polityNamed(name.get());
        }
        if (forHead) {
            Optional<PolityView> acting = federations.actingPolity(caller.player());
            if (acting.isPresent()) {
                return acting;
            }
        }
        return queries.municipalityOf(caller.player());
    }

    private static Reply notFound(Optional<String> name) {
        return name.filter(found -> !found.isBlank())
                .map(found -> Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, found.strip())))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
    }

    private static ConstitutionView.Link link(PolityView polity) {
        return new ConstitutionView.Link(polity.id(), polity.name().value());
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}

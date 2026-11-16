package de.etiennebader.civ.server.parliament;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.vote.VotingService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Parlamente im Spiel.
 *
 * <ul>
 *   <li>{@code /civ parlament [Name]}: Sitzverteilung, Präsidium, Wahlperiode, laufende Wahl und Tagesordnung.</li>
 *   <li>Wahl, Kandidatur und Stimme laufen über {@code /civ wahl … parlament}, Anträge über die Abstimmungen.</li>
 * </ul>
 */
public final class ParliamentService {

    /** Wappen der Fraktionslosen. */
    public static final String INDEPENDENT_EMBLEM = "minecraft:light_gray_wool";

    private final WorldQueries queries;
    private final Texts texts;
    private final OfficeService offices;
    private final VotingService voting;

    public ParliamentService(CivApplication app, Texts texts, OfficeService offices, VotingService voting) {
        this.queries = Objects.requireNonNull(app, "app").queries();
        this.texts = Objects.requireNonNull(texts, "texts");
        this.offices = Objects.requireNonNull(offices, "offices");
        this.voting = Objects.requireNonNull(voting, "voting");
    }

    /** Das Parlament einer Gemeinde oder eines Bundes aus Sicht eines Betrachters. */
    public ParliamentView view(PolityView polity, ActorRef viewer) {
        Parliament parliament = polity.parliament();
        Optional<ActorRef> president = parliament.president();
        List<ParliamentView.Faction> factions = new ArrayList<>();
        for (Map.Entry<Optional<PolityId>, List<Parliament.Seat>> faction : parliament.factions().entrySet()) {
            Optional<PolityView> party = faction.getKey().flatMap(queries::polity);
            List<ParliamentView.Member> members = faction.getValue().stream()
                    .map(seat -> new ParliamentView.Member(texts.actor(seat.member()), seat.votes(),
                            seat.member() instanceof PersonRef, president.filter(seat.member()::equals).isPresent()))
                    .toList();
            factions.add(new ParliamentView.Faction(faction.getKey(),
                    faction.getKey().map(id -> party.map(found -> found.name().value()).orElse("?")),
                    party.flatMap(PolityView::partyProfile).map(PartyProfile::emblem)
                            .orElse(faction.getKey().isPresent() ? PartyProfile.DEFAULT_EMBLEM : INDEPENDENT_EMBLEM),
                    members));
        }
        factions.sort(Comparator.comparingInt((ParliamentView.Faction faction) -> faction.members().size()).reversed());
        Optional<ElectionView> election = offices.officesOf(polity, viewer).stream()
                .filter(office -> office.office() == Office.PARLIAMENT)
                .findFirst()
                .flatMap(OfficeView::election);
        boolean citizen = polity.membershipOf(viewer)
                .filter(membership -> membership.type() == MembershipType.CITIZEN).isPresent();
        return new ParliamentView(polity.id(), polity.name().value(),
                polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS), factions, president.map(texts::actor),
                parliament.electedOn().map(Day::value), parliament.until().map(Day::value), election,
                voting.proposalsOf(polity, viewer), parliament.holdsSeat(viewer), citizen);
    }

    /** Das Parlament einer bestimmten Polity, z. B. für das Menü. */
    public Optional<ParliamentView> view(PolityId polity, ActorRef viewer) {
        return queries.polity(polity).map(found -> view(found, viewer));
    }

    /** {@code /civ parlament [Name]}: ohne Namen das der eigenen Gemeinde. */
    public Reply info(Caller caller, Optional<String> name) {
        Optional<PolityView> polity = target(caller, name);
        if (polity.isEmpty()) {
            return name.filter(found -> !found.isBlank())
                    .map(found -> Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, found.strip())))
                    .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
        }
        return Reply.success(describe(view(polity.get(), caller.player())));
    }

    /** Die Polity zu einem Namen, ohne Namen die eigene Gemeinde. */
    public Optional<PolityView> target(Caller caller, Optional<String> name) {
        if (name.isPresent() && !name.get().isBlank()) {
            return queries.polityNamed(name.get().strip());
        }
        return queries.municipalityOf(caller.player());
    }

    /** Chatzeilen zu einem Parlament. */
    public List<Message> describe(ParliamentView view) {
        List<Message> lines = new ArrayList<>();
        if (view.seatsByLaw() <= 0 && !view.seated()) {
            lines.add(Message.of(MessageKey.PARLIAMENT_NONE, view.name()));
            return lines;
        }
        if (!view.seated()) {
            lines.add(Message.of(MessageKey.PARLIAMENT_NOT_ELECTED, view.name(), view.seatsByLaw()));
        } else {
            lines.add(Message.of(MessageKey.PARLIAMENT_HEADER, view.name(), view.seats(), view.seatsByLaw(),
                    view.electedOn().orElse(0L)));
            view.until().ifPresent(until -> lines.add(Message.of(MessageKey.PARLIAMENT_UNTIL, until)));
            view.president().ifPresent(president -> lines.add(Message.of(MessageKey.PARLIAMENT_PRESIDENT, president)));
            for (ParliamentView.Faction faction : view.factions()) {
                lines.add(Message.of(MessageKey.PARLIAMENT_FACTION, factionName(faction), faction.members().size(),
                        faction.members().stream().map(ParliamentView.Member::name).collect(Collectors.joining(", "))));
            }
        }
        view.election().ifPresent(election -> lines.add(Message.of(MessageKey.PARLIAMENT_ELECTION_RUNNING,
                election.closesOn(), election.candidates().size(), election.ballots(), election.electorate())));
        lines.add(Message.of(MessageKey.PARLIAMENT_AGENDA, view.agenda().size()));
        return lines;
    }

    /** Name einer Fraktion: die Partei oder „Fraktionslos“. */
    public static Object factionName(ParliamentView.Faction faction) {
        return faction.name().<Object>map(name -> name).orElseGet(() -> Message.of(MessageKey.PARLIAMENT_INDEPENDENT));
    }
}

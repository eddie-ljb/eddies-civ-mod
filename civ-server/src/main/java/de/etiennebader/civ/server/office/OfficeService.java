package de.etiennebader.civ.server.office;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.office.AppointOfficer;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.CastBallot;
import de.etiennebader.civ.application.office.ElectionCount;
import de.etiennebader.civ.application.office.RunElections;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.Ballot;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
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
import java.util.stream.Collectors;

/**
 * Ämter und Wahlen im Spiel.
 *
 * <ul>
 *   <li>{@code /civ aemter}: Inhaber und laufende Wahlen.</li>
 *   <li>{@code /civ wahl ausrufen|kandidieren <Amt>}, {@code /civ wahl stimmen <Amt> <Spieler>}.</li>
 *   <li>{@code /civ amt ernennen <Amt> <Spieler>}, {@code /civ amt entlassen <Amt>} (nur Bürgermeister).</li>
 * </ul>
 * Am Tagesende werden fällige Wahlen ausgezählt und neue ausgerufen; die Mitglieder der Gemeinde erfahren davon.
 */
public final class OfficeService {

    private static final Map<String, Platform> PLATFORMS = Map.of(
            "ohne", Platform.NONE, "ordnung", Platform.ORDER, "freiheit", Platform.FREEDOM,
            "steuern", Platform.LOW_TAXES, "sparen", Platform.LOW_TAXES, "ausbau", Platform.INVESTMENT,
            "none", Platform.NONE, "order", Platform.ORDER, "freedom", Platform.FREEDOM, "investment", Platform.INVESTMENT);

    private static final Map<String, Office> NAMES = Map.of(
            "buergermeister", Office.MAYOR, "bürgermeister", Office.MAYOR, "mayor", Office.MAYOR,
            "schatzmeister", Office.TREASURER, "treasurer", Office.TREASURER,
            "bauamt", Office.PLANNER, "planner", Office.PLANNER);

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;

    public OfficeService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts,
                         Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /** Ämternamen für die Vervollständigung im Chat. */
    public static List<String> officeNames() {
        return List.of("buergermeister", "schatzmeister", "bauamt");
    }

    /** Programmnamen für die Vervollständigung im Chat. */
    public static List<String> platformNames() {
        return List.of("ordnung", "freiheit", "steuern", "ausbau", "ohne");
    }

    /** Das Wahlprogramm zu einem Namen wie {@code steuern}. */
    public static Optional<Platform> parsePlatform(String input) {
        return Optional.ofNullable(PLATFORMS.get(input.strip().toLowerCase(Locale.ROOT)));
    }

    /** Das Amt zu einem Namen wie {@code bauamt}. */
    public static Optional<Office> parse(String input) {
        return Optional.ofNullable(NAMES.get(input.strip().toLowerCase(Locale.ROOT)));
    }

    /** {@code /civ aemter} */
    public Reply list(Caller caller) {
        return withTown(caller, town -> {
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.OFFICES_HEADER, town.name().value()));
            for (OfficeView office : officesOf(town, caller.player())) {
                if (office.holder().isEmpty()) {
                    lines.add(Message.of(MessageKey.OFFICE_VACANT, office.name()));
                } else if (office.until().isPresent()) {
                    lines.add(Message.of(MessageKey.OFFICE_HELD_UNTIL, office.name(), office.holder().get(),
                            office.until().get()));
                } else {
                    lines.add(Message.of(office.implicit() ? MessageKey.OFFICE_HELD_ACTING : MessageKey.OFFICE_HELD,
                            office.name(), office.holder().get()));
                }
                office.election().ifPresent(election -> lines.add(Message.of(MessageKey.ELECTION_ENTRY, office.name(),
                        election.closesOn(), candidateNames(election), election.ballots(), election.electorate())));
            }
            return Reply.success(lines);
        });
    }

    /** {@code /civ wahl ausrufen <Amt>} */
    public Reply callElection(Caller caller, String officeInput) {
        return withOffice(caller, officeInput, (town, office) -> attempt(() -> {
            app.callElection().execute(new CallElection.Command(town.id(), caller.player(), office, calendar.today()));
            long closesOn = closesOn(town, office);
            announcements.add(Announcement.toTown(town.id(),
                    Message.of(MessageKey.ELECTION_ANNOUNCED, caller.name(), officeName(office), closesOn)));
            return Reply.success(Message.of(MessageKey.ELECTION_CALLED, officeName(office), closesOn));
        }));
    }

    /** {@code /civ wahl kandidieren <Amt>} ohne Wahlprogramm. */
    public Reply stand(Caller caller, String officeInput) {
        return stand(caller, officeInput, Platform.NONE);
    }

    /** {@code /civ wahl kandidieren <Amt> <Programm>} */
    public Reply stand(Caller caller, String officeInput, String platformInput) {
        Optional<Platform> platform = parsePlatform(platformInput);
        if (platform.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_PLATFORM, platformInput.strip()));
        }
        return stand(caller, officeInput, platform.get());
    }

    private Reply stand(Caller caller, String officeInput, Platform platform) {
        return withElection(caller, officeInput, (town, election) -> attempt(() -> {
            app.standForElection().execute(new StandForElection.Command(town.id(), election.id(), caller.player(),
                    platform, calendar.today()));
            Message programme = platformName(platform);
            announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.CANDIDACY_ANNOUNCED, caller.name(),
                    officeName(election.office()), programme)));
            return Reply.success(Message.of(MessageKey.CANDIDACY_DECLARED, officeName(election.office()), programme));
        }));
    }

    /** {@code /civ wahl stimmen <Amt> <Spieler>} */
    public Reply vote(Caller caller, String officeInput, String candidateName) {
        return withElection(caller, officeInput, (town, election) -> {
            Optional<ActorRef> candidate = election.candidates().stream()
                    .filter(actor -> texts.actor(actor).equalsIgnoreCase(candidateName.strip()))
                    .findFirst();
            if (candidate.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_CANDIDATE, candidateName.strip(),
                        officeName(election.office())));
            }
            return vote(caller, town, election, candidate.get());
        });
    }

    /** Stimme aus einem Menü oder von der Webseite. */
    public Reply vote(Caller caller, Office office, ActorRef candidate) {
        return withTown(caller, town -> town.electionFor(office)
                .map(election -> vote(caller, town, election, candidate))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NO_ELECTION_FOR, officeName(office)))));
    }

    private Reply vote(Caller caller, PolityView town, Election election, ActorRef candidate) {
        return attempt(() -> {
            app.castBallot().execute(new CastBallot.Command(town.id(), election.id(), caller.player(), candidate,
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.BALLOT_CAST, officeName(election.office()),
                    texts.actor(candidate)));
        });
    }

    /** Kandidatur aus einem Menü oder von der Webseite. */
    public Reply stand(Caller caller, Office office, Platform platform) {
        return stand(caller, office.name(), platform);
    }

    /** {@code /civ amt ernennen <Amt> <Spieler>} */
    public Reply appoint(Caller caller, String officeInput, String playerName) {
        return withOffice(caller, officeInput, (town, office) -> {
            Optional<PlayerRef> appointee = players.findByName(playerName.strip());
            if (appointee.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
            }
            return attempt(() -> {
                app.appointOfficer().execute(new AppointOfficer.Command(town.id(), caller.player(), office,
                        Optional.of(appointee.get()), calendar.today()));
                String name = texts.actor(appointee.get());
                announcements.add(Announcement.toTown(town.id(),
                        Message.of(MessageKey.OFFICER_APPOINTED, name, officeName(office))));
                return Reply.success(Message.of(MessageKey.OFFICER_APPOINTED, name, officeName(office)));
            });
        });
    }

    /** {@code /civ amt entlassen <Amt>} */
    public Reply dismiss(Caller caller, String officeInput) {
        return withOffice(caller, officeInput, (town, office) -> attempt(() -> {
            boolean changed = app.appointOfficer().execute(new AppointOfficer.Command(town.id(), caller.player(),
                    office, Optional.empty(), calendar.today()));
            if (!changed) {
                return Reply.failure(Message.of(MessageKey.OFFICE_ALREADY_VACANT, officeName(office)));
            }
            announcements.add(Announcement.toTown(town.id(),
                    Message.of(MessageKey.OFFICER_DISMISSED, officeName(office))));
            return Reply.success(Message.of(MessageKey.OFFICER_DISMISSED, officeName(office)));
        }));
    }

    /** Tagesende: fällige Wahlen auszählen und ausrufen, Ergebnisse melden. */
    public void endOfDay(Day day) {
        for (RunElections.Outcome outcome : app.runElections().execute(day)) {
            Optional<PolityView> town = queries.polity(outcome.polity());
            if (town.isEmpty()) {
                continue;
            }
            Message office = officeName(outcome.office());
            Message message = switch (outcome) {
                case RunElections.Called called ->
                        Message.of(MessageKey.ELECTION_AUTO_CALLED, office, called.closesOn().value());
                case RunElections.Decided decided -> decided.count().villagers() > 0
                        ? Message.of(MessageKey.ELECTION_WON_WITH_VILLAGERS, town.get().name().value(), office,
                                texts.actor(decided.winner()), result(decided.count()), decided.count().villagers())
                        : Message.of(MessageKey.ELECTION_WON, town.get().name().value(), office,
                                texts.actor(decided.winner()), result(decided.count()));
                case RunElections.NoCandidates ignored -> Message.of(MessageKey.ELECTION_NO_CANDIDATES, office);
            };
            announcements.add(Announcement.toTown(outcome.polity(), message));
        }
    }

    /** Alle Ämter einer Gemeinde in fester Reihenfolge, mit laufenden Wahlen aus Sicht des Betrachters. */
    public List<OfficeView> officesOf(PolityView town, ActorRef viewer) {
        long electorate = town.members().stream()
                .filter(membership -> membership.type() == MembershipType.CITIZEN)
                .filter(membership -> !(membership.member() instanceof PersonRef))
                .count();
        List<OfficeView> views = new ArrayList<>();
        for (Office office : Office.values()) {
            Optional<OfficeTerm> term = town.officeTerm(office);
            Optional<String> holder = term.map(OfficeTerm::holder).map(texts::actor);
            boolean implicit = false;
            if (holder.isEmpty() && office == Office.MAYOR) {
                holder = town.mayor().map(texts::actor);
                implicit = holder.isPresent();
            }
            Optional<ElectionView> election = town.electionFor(office).map(open -> new ElectionView(open.id(),
                    open.closesOn().value(),
                    open.candidates().stream().map(actor -> new ElectionView.Candidate(actor, texts.actor(actor),
                                    open.platformOf(actor)))
                            .toList(),
                    open.ballots().stream().filter(ballot -> ballot.voter().equals(viewer)).map(Ballot::candidate)
                            .findFirst(),
                    open.isCandidate(viewer), open.ballots().size(), electorate));
            views.add(new OfficeView(office, officeName(office), holder,
                    term.flatMap(OfficeTerm::until).map(Day::value), implicit, election));
        }
        return views;
    }

    public Message platformName(Platform platform) {
        return Message.of(TranslationKeys.platform(platform));
    }

    public Message officeName(Office office) {
        return Message.of(TranslationKeys.office(office));
    }

    private String result(ElectionCount count) {
        return count.totals().entrySet().stream()
                .map(entry -> texts.actor(entry.getKey()) + " " + formatVotes(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    /** Gewichtete Stimmen; 100 sind eine Bürgerstimme. */
    private static String formatVotes(long weighted) {
        long whole = weighted / ElectionCount.CITIZEN_WEIGHT;
        long rest = weighted % ElectionCount.CITIZEN_WEIGHT;
        return rest == 0 ? Long.toString(whole) : whole + "," + String.format(Locale.ROOT, "%02d", rest);
    }

    private static String candidateNames(ElectionView election) {
        if (election.candidates().isEmpty()) {
            return "-";
        }
        return election.candidates().stream().map(ElectionView.Candidate::name).collect(Collectors.joining(", "));
    }

    private long closesOn(PolityView town, Office office) {
        return queries.polity(town.id()).flatMap(current -> current.electionFor(office))
                .map(election -> election.closesOn().value()).orElse(calendar.today().value());
    }

    private interface OfficeAction {

        Reply apply(PolityView town, Office office);
    }

    private interface ElectionAction {

        Reply apply(PolityView town, Election election);
    }

    private Reply withTown(Caller caller, Function<PolityView, Reply> action) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return action.apply(town.get());
    }

    private Reply withOffice(Caller caller, String officeInput, OfficeAction action) {
        Optional<Office> office = parse(officeInput);
        if (office.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_OFFICE, officeInput.strip()));
        }
        return withTown(caller, town -> action.apply(town, office.get()));
    }

    private Reply withElection(Caller caller, String officeInput, ElectionAction action) {
        return withOffice(caller, officeInput, (town, office) -> town.electionFor(office)
                .map(election -> action.apply(town, election))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NO_ELECTION_FOR, officeName(office)))));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}

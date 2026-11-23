package de.etiennebader.civ.server.campaign;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.office.ElectionPoll;
import de.etiennebader.civ.application.office.MakePromise;
import de.etiennebader.civ.application.office.PutUpPoster;
import de.etiennebader.civ.application.population.Campaign;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.law.VotingMethod;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Poster;
import de.etiennebader.civ.domain.polity.Promise;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.parliament.ParliamentService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Wahlkampf und Wahlauswertung.
 *
 * <ul>
 *   <li>{@code /civ rede <Text>}: Dorfbewohner in Hörweite reagieren nach Thema und Persönlichkeit.</li>
 *   <li>{@code /civ versprechen <Text>} als Kandidat; {@code /civ versprechen liste [Name]} mit Bilanz.</li>
 *   <li>Wahlplakate (Litfaßsäule): werben bei den Dorfbewohnern der Gemeinde.</li>
 *   <li>{@code /civ umfrage [Name]}: Umfrage unter Dorfbewohnern zu laufenden Wahlen, beim Parlament mit Prognose.</li>
 *   <li>{@code /civ wahlergebnis [Name]}: letztes Ergebnis je Wahlbezirk.</li>
 * </ul>
 */
public final class CampaignService {

    /** So weit hören Dorfbewohner und Spieler eine Rede, in Blöcken. */
    public static final int SPEECH_RANGE = 32;
    public static final int MAX_SPEECH = 256;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final OfficeService offices;

    public CampaignService(CivApplication app, Calendar calendar, Texts texts, OfficeService offices) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.offices = Objects.requireNonNull(offices, "offices");
    }

    // ---- Reden ----

    /**
     * Eine Rede: wirkt auf die Dorfbewohner in Hörweite, wenn der Redner bei einer laufenden Wahl kandidiert.
     *
     * @param listeners Dorfbewohner in Hörweite
     */
    public Reply speak(Caller caller, String text, Collection<PersonRef> listeners) {
        String speech = text.strip();
        if (speech.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.SPEECH_EMPTY));
        }
        Platform topic = Campaign.topicOf(speech);
        Message topicName = Message.of(TranslationKeys.platform(topic));
        if (!isCandidateAnywhere(caller.player())) {
            return Reply.success(Message.of(MessageKey.SPEECH_NO_EFFECT, topicName));
        }
        Optional<Campaign.Reception> reception = app.campaign().speak(caller.player(), topic, listeners,
                calendar.today());
        if (reception.isEmpty()) {
            return Reply.success(Message.of(MessageKey.SPEECH_ALREADY_TODAY));
        }
        Campaign.Reception heard = reception.get();
        return Reply.success(Message.of(MessageKey.SPEECH_HEARD, topicName, heard.listeners(), heard.convinced(),
                heard.repelled()));
    }

    private boolean isCandidateAnywhere(ActorRef actor) {
        return queries.polities().stream()
                .flatMap(polity -> polity.elections().stream())
                .anyMatch(election -> election.isCandidate(actor));
    }

    /** Tagesende: alte Reden vergessen. */
    public void endOfDay(Day day) {
        app.campaign().endOfDay(day);
    }

    // ---- Versprechen ----

    /** {@code /civ versprechen <Text>}: in der Gemeinde oder dem Bund, in dem der Spieler kandidiert. */
    public Reply promise(Caller caller, String text) {
        Optional<PolityView> polity = electingPolities(caller.player()).stream()
                .filter(candidate -> candidate.elections().stream().anyMatch(open -> open.isCandidate(caller.player())))
                .findFirst();
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PROMISE_NOT_RUNNING));
        }
        return attempt(() -> {
            Promise promise = app.makePromise().execute(new MakePromise.Command(polity.get().id(), caller.player(),
                    text, calendar.today()));
            return Reply.success(Message.of(MessageKey.PROMISE_MADE, polity.get().name().value(),
                    offices.officeName(polity.get(), promise.office()), values(promise.values())));
        });
    }

    /** {@code /civ versprechen liste [Name]}: Versprechen mit Bilanz. */
    public Reply listPromises(Caller caller, Optional<String> name) {
        Optional<PolityView> polity = name.filter(found -> !found.isBlank()).flatMap(queries::polityNamed)
                .or(() -> name.filter(found -> !found.isBlank()).isPresent() ? Optional.empty()
                        : queries.municipalityOf(caller.player()));
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PROMISES_HEADER, polity.get().name().value(), polity.get().promises().size()));
        for (PromiseView promise : promisesOf(polity.get())) {
            lines.add(Message.of(MessageKey.PROMISE_ENTRY, promise.candidate(), promise.office(), promise.text(),
                    statusMessage(promise.kept())));
        }
        return Reply.success(lines);
    }

    /** Ein Versprechen für Anzeigen. */
    public record PromiseView(String candidate, ActorRef candidateRef, Office officeId, Message office, String text,
                              Optional<Double> kept, long madeOn) {
    }

    public List<PromiseView> promisesOf(PolityView polity) {
        return polity.promises().stream()
                .map(promise -> new PromiseView(texts.actor(promise.candidate()), promise.candidate(), promise.office(),
                        offices.officeName(polity, promise.office()), promise.text(),
                        app.campaign().kept(promise, polity), promise.madeOn().value()))
                .toList();
    }

    /** Versprechen eines Kandidaten, z. B. für Tooltips. */
    public List<PromiseView> promisesOf(PolityView polity, ActorRef candidate) {
        return promisesOf(polity).stream().filter(promise -> promise.candidateRef().equals(candidate)).toList();
    }

    /** „gehalten“, „gebrochen“, „teilweise“ oder „nicht messbar“. */
    public static Message statusMessage(Optional<Double> kept) {
        if (kept.isEmpty()) {
            return Message.of(MessageKey.PROMISE_UNMEASURABLE);
        }
        if (kept.get() >= 1) {
            return Message.of(MessageKey.PROMISE_KEPT);
        }
        return kept.get() <= 0 ? Message.of(MessageKey.PROMISE_BROKEN) : Message.of(MessageKey.PROMISE_PARTLY);
    }

    private String values(List<ParameterValue<?>> values) {
        if (values.isEmpty()) {
            return "-";
        }
        return values.stream().map(value -> value.key().id() + " = " + value.value()).collect(Collectors.joining(", "));
    }

    // ---- Plakate ----

    /** Ein Plakat wurde aufgestellt. */
    public Reply posterPlaced(Caller caller, BlockPosition position) {
        return attempt(() -> app.putUpPoster().execute(position, caller.player(), calendar.today())
                .map(result -> Reply.success(result.active()
                        ? Message.of(MessageKey.POSTER_PLACED, texts.actor(result.poster().subject()),
                        texts.polityName(result.town()))
                        : Message.of(MessageKey.POSTER_WAITING, texts.polityName(result.town()))))
                .orElseGet(() -> Reply.success(Message.of(MessageKey.POSTER_DECORATION))));
    }

    /** Ein Plakat mit seiner Aufschrift für die Anzeige über der Litfaßsäule. */
    public record PosterLabel(BlockPosition position, List<Message> lines, boolean active) {

        public PosterLabel {
            lines = List.copyOf(lines);
        }
    }

    /** Alle registrierten Plakate mit Aufschrift, z. B. um die Anzeigen am Tagesende aufzufrischen. */
    public List<PosterLabel> posters() {
        List<PosterLabel> labels = new ArrayList<>();
        for (PolityView polity : queries.polities()) {
            for (Poster poster : polity.posters()) {
                labels.add(label(poster));
            }
        }
        return labels;
    }

    /** Die Aufschrift des Plakats an dieser Stelle. */
    public Optional<PosterLabel> posterLabel(BlockPosition position) {
        return queries.polities().stream().flatMap(polity -> polity.posters().stream())
                .filter(poster -> poster.position().equals(position)).findFirst().map(this::label);
    }

    private PosterLabel label(Poster poster) {
        List<Message> lines = new ArrayList<>();
        boolean active;
        if (poster.subject() instanceof PolityRef party) {
            active = true;
            lines.add(Message.of(MessageKey.POSTER_LABEL_PARTY, texts.actor(party)));
            queries.polity(party.id()).flatMap(PolityView::partyProfile).ifPresent(profile -> lines.add(
                    Message.of(MessageKey.POSTER_LABEL_LINE, Message.of(TranslationKeys.platform(profile.platform())))));
        } else {
            List<PolityView> electing = electingFor(poster.subject());
            active = !electing.isEmpty();
            lines.add(Message.of((active ? MessageKey.POSTER_LABEL_CANDIDATE : MessageKey.POSTER_LABEL_WAITING).key(),
                    texts.actor(poster.subject())));
            for (PolityView polity : electing) {
                polity.elections().stream().filter(election -> election.isCandidate(poster.subject())).findFirst()
                        .ifPresent(election -> {
                            lines.add(Message.of(MessageKey.POSTER_LABEL_LINE,
                                    Message.of(TranslationKeys.office(election.office()))));
                            if (election.platformOf(poster.subject()) != de.etiennebader.civ.domain.polity.Platform.NONE) {
                                lines.add(Message.of(MessageKey.POSTER_LABEL_LINE, Message.of(
                                        TranslationKeys.platform(election.platformOf(poster.subject())))));
                            }
                        });
                promisesOf(polity, poster.subject()).stream().findFirst().ifPresent(promise ->
                        lines.add(Message.of(MessageKey.POSTER_LABEL_PROMISE, promise.text())));
            }
        }
        return new PosterLabel(poster.position(), lines, active);
    }

    /** Ein Plakat wurde abgebaut. */
    public void posterRemoved(BlockPosition position) {
        app.putUpPoster().takeDown(position);
    }

    /** Rechtsklick auf ein Plakat: wofür es wirbt. */
    public Reply posterInfo(BlockPosition position) {
        for (PolityView polity : queries.polities()) {
            for (Poster poster : polity.posters()) {
                if (!poster.position().equals(position)) {
                    continue;
                }
                List<Message> lines = new ArrayList<>();
                lines.add(Message.of(MessageKey.POSTER_INFO, texts.actor(poster.subject()), polity.name().value()));
                if (poster.subject() instanceof PolityRef party) {
                    queries.polity(party.id()).flatMap(PolityView::partyProfile).ifPresent(profile ->
                            lines.add(Message.of(MessageKey.POSTER_PROGRAMME,
                                    Message.of(TranslationKeys.platform(profile.platform())))));
                } else {
                    for (PolityView electing : electingFor(poster.subject())) {
                        for (PromiseView promise : promisesOf(electing, poster.subject())) {
                            lines.add(Message.of(MessageKey.POSTER_PROMISE, promise.text()));
                        }
                    }
                }
                return Reply.success(lines);
            }
        }
        return Reply.success(Message.of(MessageKey.POSTER_EMPTY));
    }

    private List<PolityView> electingFor(ActorRef candidate) {
        return queries.polities().stream()
                .filter(polity -> polity.elections().stream().anyMatch(open -> open.isCandidate(candidate)))
                .toList();
    }

    // ---- Umfragen ----

    /** Umfragen zu allen laufenden Wahlen einer Polity. */
    public List<PollView> polls(PolityView polity) {
        List<PollView> polls = new ArrayList<>();
        for (Election election : polity.elections()) {
            ElectionPoll poll = ElectionPoll.of(polity, election, app.villagerVoter(), queries::partyOf);
            int decided = poll.sample() - poll.undecided();
            List<PollView.Entry> entries = poll.candidates().entrySet().stream()
                    .sorted(Map.Entry.<ActorRef, Integer>comparingByValue().reversed())
                    .map(entry -> new PollView.Entry(texts.actor(entry.getKey()),
                            queries.partyOf(entry.getKey()).map(party -> party.name().value()).orElse(""),
                            entry.getValue(), poll.percentOf(entry.getKey())))
                    .toList();
            List<PollView.ListEntry> lists = new ArrayList<>();
            if (election.office() == Office.PARLIAMENT) {
                for (Map.Entry<Object, Integer> seats : poll.seats().entrySet()) {
                    int answers = answersOf(poll, seats.getKey());
                    lists.add(new PollView.ListEntry(listName(seats.getKey()), listEmblem(seats.getKey()), answers,
                            decided == 0 ? 0 : Math.round(answers * 100.0 / decided), seats.getValue()));
                }
                lists.sort(Comparator.comparingInt(PollView.ListEntry::answers).reversed());
            }
            polls.add(new PollView(polity.id(), polity.name().value(), election.office(),
                    offices.officeName(polity, election.office()), election.closesOn().value(), entries, lists,
                    poll.sample(), poll.undecided()));
        }
        return polls;
    }

    private int answersOf(ElectionPoll poll, Object list) {
        if (list instanceof PolityId party) {
            return poll.parties().getOrDefault(Optional.of(party), 0);
        }
        return poll.candidates().getOrDefault((ActorRef) list, 0);
    }

    private String listName(Object list) {
        if (list instanceof PolityId party) {
            return texts.polityName(party);
        }
        return texts.actor((ActorRef) list);
    }

    private String listEmblem(Object list) {
        if (list instanceof PolityId party) {
            return queries.polity(party).flatMap(PolityView::partyProfile).map(PartyProfile::emblem)
                    .orElse(PartyProfile.DEFAULT_EMBLEM);
        }
        return ParliamentService.INDEPENDENT_EMBLEM;
    }

    /** {@code /civ umfrage [Name]} */
    public Reply poll(Caller caller, Optional<String> name) {
        Optional<PolityView> polity = target(caller, name);
        if (polity.isEmpty()) {
            return notFound(name);
        }
        List<PollView> polls = polls(polity.get());
        if (polls.isEmpty()) {
            return Reply.success(Message.of(MessageKey.POLL_NONE, polity.get().name().value()));
        }
        List<Message> lines = new ArrayList<>();
        for (PollView poll : polls) {
            lines.add(Message.of(MessageKey.POLL_HEADER, poll.polityName(), poll.officeName(), poll.sample(),
                    poll.undecided()));
            if (poll.sample() == 0) {
                lines.add(Message.of(MessageKey.POLL_NO_VILLAGERS));
                continue;
            }
            for (PollView.Entry entry : poll.entries()) {
                lines.add(Message.of(MessageKey.POLL_ENTRY, entry.name(), entry.percent(),
                        entry.party().isEmpty() ? "" : " (" + entry.party() + ")"));
            }
            if (!poll.lists().isEmpty()) {
                lines.add(Message.of(MessageKey.POLL_PROJECTION, poll.lists().stream()
                        .map(list -> list.name() + " " + list.seats()).collect(Collectors.joining(", "))));
            }
        }
        return Reply.success(lines);
    }

    // ---- Ergebnisse ----

    /** Gespeicherte Wahlergebnisse einer Polity, das neueste zuerst. */
    public List<ResultView> results(PolityView polity) {
        List<ResultView> views = new ArrayList<>();
        List<ElectionResult> results = new ArrayList<>(polity.results());
        Collections.reverse(results);
        for (ElectionResult result : results) {
            boolean byList = listBallot(polity, result.office());
            Map<ActorRef, Long> totals = result.totals();
            long sum = totals.values().stream().mapToLong(Long::longValue).sum();
            List<ResultView.District> districts = result.districts().stream()
                    .map(district -> district(district, byList))
                    .toList();
            views.add(new ResultView(polity.id(), polity.name().value(), result.office(),
                    offices.officeName(polity, result.office()), result.closedOn().value(), lines(totals, sum, byList),
                    districts, result.winners().stream().map(texts::actor).toList()));
        }
        return views;
    }

    /** Listenwahl (#47): Parlamentswahl nach Listen; Auswertung und Karte zeigen dann nur die Parteien. */
    private static boolean listBallot(PolityView polity, Office office) {
        return office == Office.PARLIAMENT && VotingMethod.Ballot.of(polity.laws().valueOf(StandardParameters.BALLOT_TYPE))
                == VotingMethod.Ballot.LIST;
    }

    private ResultView.District district(ElectionResult.District district, boolean byList) {
        long sum = district.total();
        Optional<ActorRef> leader = district.leader();
        Optional<PolityView> party = leader.flatMap(queries::partyOf);
        return new ResultView.District(district.district(),
                district.district().map(texts::polityName).orElseGet(() -> "-"),
                byList && party.isPresent() ? party.get().name().value() : leader.map(texts::actor).orElse("-"),
                party.map(found -> found.name().value()),
                party.map(found -> found.id().value().toString())
                        .orElseGet(() -> leader.map(actor -> actor.uuid().toString()).orElse("")),
                sum, lines(district.votes(), sum, byList));
    }

    private List<ResultView.Line> lines(Map<ActorRef, Long> votes, long sum, boolean byList) {
        if (byList) {
            return listLines(votes, sum);
        }
        return votes.entrySet().stream()
                .sorted(Map.Entry.<ActorRef, Long>comparingByValue().reversed())
                .map(entry -> {
                    Optional<PolityView> party = queries.partyOf(entry.getKey());
                    return new ResultView.Line(texts.actor(entry.getKey()), party.map(found -> found.name().value()),
                            party.map(PolityView::id), entry.getValue(),
                            sum == 0 ? 0 : Math.round(entry.getValue() * 100.0 / sum));
                })
                .toList();
    }

    /** Listenwahl: die Stimmen der Kandidaten einer Partei zu einer Zeile der Partei, Parteilose einzeln. */
    private List<ResultView.Line> listLines(Map<ActorRef, Long> votes, long sum) {
        Map<Object, Long> byKey = new java.util.LinkedHashMap<>();
        Map<Object, Optional<PolityView>> parties = new java.util.HashMap<>();
        Map<Object, ActorRef> single = new java.util.HashMap<>();
        votes.forEach((candidate, count) -> {
            Optional<PolityView> party = queries.partyOf(candidate);
            Object key = party.<Object>map(PolityView::id).orElse(candidate);
            byKey.merge(key, count, Long::sum);
            parties.put(key, party);
            single.put(key, candidate);
        });
        return byKey.entrySet().stream()
                .sorted(Map.Entry.<Object, Long>comparingByValue().reversed())
                .map(entry -> {
                    Optional<PolityView> party = parties.get(entry.getKey());
                    String name = party.map(found -> found.name().value())
                            .orElseGet(() -> texts.actor(single.get(entry.getKey())));
                    return new ResultView.Line(name, party.map(found -> found.name().value()),
                            party.map(PolityView::id), entry.getValue(),
                            sum == 0 ? 0 : Math.round(entry.getValue() * 100.0 / sum));
                })
                .toList();
    }

    /** {@code /civ wahlergebnis [Name]}: das letzte Ergebnis, je Bezirk. */
    public Reply lastResult(Caller caller, Optional<String> name) {
        Optional<PolityView> polity = target(caller, name);
        if (polity.isEmpty()) {
            return notFound(name);
        }
        List<ResultView> results = results(polity.get());
        if (results.isEmpty()) {
            return Reply.success(Message.of(MessageKey.RESULT_NONE, polity.get().name().value()));
        }
        ResultView result = results.getFirst();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.RESULT_HEADER, result.polityName(), result.officeName(), result.closedOn(),
                result.winners().isEmpty() ? "-" : String.join(", ", result.winners())));
        for (ResultView.Line line : result.totals()) {
            lines.add(Message.of(MessageKey.RESULT_LINE, line.name(), votes(line.votes()), line.percent()));
        }
        if (result.districts().size() > 1) {
            for (ResultView.District district : result.districts()) {
                lines.add(Message.of(MessageKey.RESULT_DISTRICT, district.name(), district.leader(),
                        votes(district.votes())));
            }
        }
        return Reply.success(lines);
    }

    /** Gewichtete Stimmen; 100 sind eine Bürgerstimme. */
    public static String votes(long weighted) {
        long whole = weighted / 100;
        long rest = weighted % 100;
        return rest == 0 ? Long.toString(whole) : whole + "," + String.format(Locale.ROOT, "%02d", rest);
    }

    // ---- Hilfen ----

    /** Gemeinde des Spielers und ihre Bünde, von unten nach oben. */
    public List<PolityView> electingPolities(ActorRef actor) {
        List<PolityView> polities = new ArrayList<>();
        queries.municipalityOf(actor).ifPresent(town -> {
            polities.add(town);
            polities.addAll(queries.ancestorsOf(town.id()));
        });
        return polities;
    }

    /** Gemeinde an einer Stelle und ihre Bünde, z. B. für die Wahlurne. */
    public List<PolityView> electingPolitiesAt(BlockPosition position) {
        List<PolityView> polities = new ArrayList<>();
        queries.territory().claimAt(position.chunk()).flatMap(claim -> queries.polity(claim.polity()))
                .ifPresent(town -> {
                    polities.add(town);
                    polities.addAll(queries.ancestorsOf(town.id()));
                });
        return polities;
    }

    /** Gemeinde und Bünde eines Spielers, in denen Briefwahl erlaubt ist, z. B. für den Briefkasten. */
    public List<PolityView> postalPolitiesOf(ActorRef voter) {
        List<PolityView> polities = new ArrayList<>();
        queries.municipalityOf(voter).ifPresent(town -> {
            polities.add(town);
            polities.addAll(queries.ancestorsOf(town.id()));
        });
        polities.removeIf(polity -> !polity.laws().valueOf(StandardParameters.POSTAL_VOTE_ALLOWED));
        return polities;
    }

    private Optional<PolityView> target(Caller caller, Optional<String> name) {
        if (name.isPresent() && !name.get().isBlank()) {
            return queries.polityNamed(name.get().strip());
        }
        return queries.municipalityOf(caller.player());
    }

    private static Reply notFound(Optional<String> name) {
        return name.filter(found -> !found.isBlank())
                .map(found -> Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, found.strip())))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}

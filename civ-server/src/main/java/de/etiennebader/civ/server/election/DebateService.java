package de.etiennebader.civ.server.election;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.population.Campaign;
import de.etiennebader.civ.application.population.PlatformAffinity;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Wahldebatten: Die Kandidaten einer laufenden Wahl treten öffentlich gegeneinander an.
 *
 * <ul>
 *   <li>Eine Debatte beginnt am letzten Tag einer Wahl von selbst, wenn das Recht Debatten vorsieht
 *       ({@link StandardParameters#ELECTION_DEBATES}) und mindestens zwei kandidieren; ein Kandidat oder die Spitze
 *       kann sie auch vorher eröffnen.</li>
 *   <li>Spieler sprechen mit {@code /civ debatte <Text>}. Wer in einem Drittel der Debatte schweigt, für den spricht
 *       sein Wahlkampfteam einen Satz zu seinem Programm, damit jede Debatte stattfindet.</li>
 *   <li>Jeder Beitrag wirkt auf alle Dorfbewohner der Polity wie eine halbe Rede, je nach Thema und Persönlichkeit.
 *       Am Ende sagt eine Blitzumfrage, wer überzeugt hat.</li>
 * </ul>
 */
public final class DebateService {

    private static final int ROUNDS = 3;
    private static final int MAX_TEXT = 200;

    /** Ein Beitrag in einer Debatte. */
    public record Contribution(String speaker, String text, Message topic, int convinced, int repelled) {
    }

    /** Eine laufende oder beendete Debatte für Anzeigen. */
    public record DebateView(PolityId polity, String polityName, Message office, List<String> candidates,
                             List<Contribution> contributions, Map<String, Double> verdict, double progress,
                             boolean finished) {

        public DebateView {
            candidates = List.copyOf(candidates);
            contributions = List.copyOf(contributions);
            verdict = Map.copyOf(verdict);
        }
    }

    private static final class Debate {
        final PolityId polity;
        final Office office;
        final List<ActorRef> candidates;
        final long start;
        final long duration;
        final Map<ActorRef, Integer> score = new LinkedHashMap<>();
        final Map<ActorRef, Integer> lastRound = new LinkedHashMap<>();
        final List<Contribution> contributions = new ArrayList<>();

        Debate(PolityId polity, Office office, List<ActorRef> candidates, long start, long duration) {
            this.polity = polity;
            this.office = office;
            this.candidates = List.copyOf(candidates);
            this.start = start;
            this.duration = duration;
            candidates.forEach(candidate -> {
                score.put(candidate, 0);
                lastRound.put(candidate, -1);
            });
        }

        int round(long now) {
            return (int) Math.min(ROUNDS - 1, (now - start) * ROUNDS / Math.max(1, duration));
        }
    }

    private static final Map<Platform, List<String>> LINES = new EnumMap<>(Platform.class);

    static {
        LINES.put(Platform.NONE, List.of("Ich höre zu und entscheide mit gesundem Menschenverstand.",
                "Unsere Gemeinde braucht keine Ideologie, sondern Arbeit."));
        LINES.put(Platform.ORDER, List.of("Mit mir gibt es klare Regeln und sichere Straßen.",
                "Wer baut, wo er nicht darf, muss die Folgen tragen. Sicherheit zuerst!"));
        LINES.put(Platform.FREEDOM, List.of("Weniger Verbote, mehr Freiheit für alle!",
                "Jeder soll selbst entscheiden, was er auf seinem Land baut."));
        LINES.put(Platform.LOW_TAXES, List.of("Ich senke die Steuern, das Geld gehört euch.",
                "Sparen statt verschwenden: Die Kasse ist kein Selbstbedienungsladen."));
        LINES.put(Platform.INVESTMENT, List.of("Wir bauen Straßen, Märkte und ein neues Rathaus!",
                "Investitionen von heute sind der Wohlstand von morgen."));
        LINES.put(Platform.ENVIRONMENT, List.of("Unsere Wälder und Tiere verdienen Schutz. Keine Explosionen!",
                "Die Natur ist unser Erbe, ich werde sie bewahren."));
        LINES.put(Platform.WELFARE, List.of("Niemand soll zurückbleiben: gerechte Gehälter und Hilfe für alle.",
                "Soziale Gerechtigkeit ist kein Luxus, sondern Pflicht."));
        LINES.put(Platform.ECONOMY, List.of("Starke Firmen, volle Märkte, eine Börse, die brummt!",
                "Handel schafft Arbeitsplätze, ich mache den Weg frei."));
        LINES.put(Platform.EDUCATION, List.of("Bildung für alle: Bibliotheken in jede Gemeinde!",
                "Wissen ist das beste Werkzeug, das wir unseren Kindern geben können."));
        LINES.put(Platform.TRADITION, List.of("Wir bewahren, was unsere Ahnen aufgebaut haben.",
                "Heimat und Werte sind das Fundament unserer Gemeinde."));
        LINES.put(Platform.PROGRESS, List.of("Redstone, Technik, Fortschritt: Die Zukunft gehört uns!",
                "Wer stehen bleibt, fällt zurück. Ich will modernisieren."));
        LINES.put(Platform.PEACE, List.of("Bündnisse statt Streit, Diplomatie statt Schwerter.",
                "Frieden mit unseren Nachbarn ist mein oberstes Ziel."));
        LINES.put(Platform.EXPANSION, List.of("Mehr Land, mehr Stärke: Wir wachsen über unsere Grenzen hinaus!",
                "Eine starke Verteidigung schützt unsere Gemeinde."));
        LINES.put(Platform.DEMOCRACY, List.of("Mehr Abstimmungen, mehr Transparenz, freie Presse!",
                "Die Bürger sollen mitentscheiden, nicht nur zuschauen."));
        LINES.put(Platform.AGRICULTURE, List.of("Volle Felder, volle Scheunen: Ich stehe zu unseren Bauern.",
                "Ohne Ernte keine Gemeinde. Die Landwirtschaft kommt zuerst."));
        LINES.put(Platform.AUTONOMY, List.of("Wir entscheiden hier vor Ort, nicht die da oben im Bund!",
                "Selbstbestimmung für unsere Gemeinde."));
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;
    private final long durationMillis;
    private final List<Debate> running = new ArrayList<>();
    private final List<DebateView> finished = new ArrayList<>();

    public DebateService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements,
                         int minutes) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.durationMillis = minutes * 60_000L;
    }

    public boolean enabled() {
        return durationMillis > 0;
    }

    /** Tagesbeginn: Debatten für Wahlen, die heute enden. */
    public synchronized void newDay(Day today, long now) {
        if (!enabled()) {
            return;
        }
        for (PolityView polity : queries.polities()) {
            if (!polity.laws().valueOf(StandardParameters.ELECTION_DEBATES)) {
                continue;
            }
            for (Election election : polity.elections()) {
                if (election.closesOn().equals(today) && election.candidates().size() >= 2) {
                    begin(polity, election, now);
                }
            }
        }
    }

    /** {@code /civ <Ebene> debatte starten [Amt]}: Ein Kandidat oder die Spitze eröffnet die Debatte. */
    public synchronized Reply start(Caller caller, PolityView polity, Optional<Office> office, long now) {
        if (!enabled()) {
            return Reply.failure(Message.of(MessageKey.DEBATE_DISABLED));
        }
        Optional<Election> election = polity.elections().stream()
                .filter(open -> office.map(wanted -> open.office() == wanted).orElse(true))
                .filter(open -> open.candidates().size() >= 2).findFirst();
        if (election.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.DEBATE_NO_ELECTION, polity.name().value()));
        }
        boolean head = polity.holds(caller.player(), Office.MAYOR)
                || polity.mayor().filter(caller.player()::equals).isPresent();
        if (!head && !election.get().isCandidate(caller.player())) {
            return Reply.failure(Message.of(MessageKey.DEBATE_NOT_ALLOWED));
        }
        if (running.stream().anyMatch(debate -> debate.polity.equals(polity.id()))) {
            return Reply.failure(Message.of(MessageKey.DEBATE_RUNNING, polity.name().value()));
        }
        begin(polity, election.get(), now);
        return Reply.success(Message.of(MessageKey.DEBATE_STARTED_BY_YOU, polity.name().value()));
    }

    /** {@code /civ debatte <Text>}: ein Beitrag in der laufenden Debatte, in der man kandidiert. */
    public synchronized Reply speak(Caller caller, String text, long now) {
        String clean = text.strip();
        if (clean.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CHAT_EMPTY));
        }
        Optional<Debate> debate = running.stream().filter(open -> open.candidates.contains(caller.player())).findFirst();
        if (debate.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.DEBATE_NOT_CANDIDATE));
        }
        Contribution said = contribute(debate.get(), caller.player(), clean.length() > MAX_TEXT
                ? clean.substring(0, MAX_TEXT) : clean, Optional.empty(), now);
        return Reply.success(Message.of(MessageKey.DEBATE_RECEPTION, said.convinced(), said.repelled(), said.topic()));
    }

    /** Lässt schweigende Kandidaten sprechen und beendet abgelaufene Debatten. */
    public synchronized void tick(long now) {
        List<Debate> done = new ArrayList<>();
        for (Debate debate : running) {
            int round = debate.round(now);
            for (ActorRef candidate : debate.candidates) {
                if (debate.lastRound.get(candidate) < round && now - debate.start > (round + 0.5) * debate.duration / ROUNDS) {
                    Platform platform = platformOf(debate, candidate);
                    List<String> lines = LINES.getOrDefault(platform, LINES.get(Platform.NONE));
                    contribute(debate, candidate, lines.get(Math.floorMod(round + candidate.hashCode(), lines.size())),
                            Optional.of(platform), now);
                }
            }
            if (now - debate.start >= debate.duration) {
                done.add(debate);
            }
        }
        for (Debate debate : done) {
            running.remove(debate);
            Map<String, Double> verdict = verdict(debate);
            announcements.add(Announcement.toTown(debate.polity, Message.of(MessageKey.DEBATE_ENDED,
                    texts.polityName(debate.polity), LiveCountService.format(verdict))));
            finished.add(view(debate, now, verdict, true));
            while (finished.size() > 5) {
                finished.removeFirst();
            }
        }
    }

    /** Laufende Debatten und die zuletzt beendeten. */
    public synchronized List<DebateView> debates(long now) {
        List<DebateView> views = new ArrayList<>();
        running.forEach(debate -> views.add(view(debate, now, Map.of(), false)));
        views.addAll(finished.reversed());
        return views;
    }

    private void begin(PolityView polity, Election election, long now) {
        if (running.stream().anyMatch(debate -> debate.polity.equals(polity.id()) && debate.office == election.office())) {
            return;
        }
        Debate debate = new Debate(polity.id(), election.office(), election.candidates(), now, durationMillis);
        running.add(debate);
        announcements.add(Announcement.toTown(polity.id(), Message.of(MessageKey.DEBATE_STARTED,
                polity.name().value(), Message.of(TranslationKeys.office(election.office())),
                String.join(" vs. ", election.candidates().stream().map(texts::actor).toList()),
                Math.max(1, durationMillis / 60_000))));
    }

    private Contribution contribute(Debate debate, ActorRef speaker, String text, Optional<Platform> given, long now) {
        Platform topic = given.orElseGet(() -> {
            Platform found = PlatformAffinity.topicOf(text);
            return found == Platform.NONE ? platformOf(debate, speaker) : found;
        });
        Campaign.Reception reception = app.campaign().debate(speaker, topic, listeners(debate.polity),
                calendar.today());
        debate.score.merge(speaker, reception.convinced() - reception.repelled(), Integer::sum);
        debate.lastRound.put(speaker, debate.round(now));
        Contribution said = new Contribution(texts.actor(speaker), text,
                Message.of(TranslationKeys.platform(topic)), reception.convinced(), reception.repelled());
        debate.contributions.add(said);
        announcements.add(Announcement.toTown(debate.polity, Message.of(MessageKey.DEBATE_LINE,
                texts.polityName(debate.polity), said.speaker(), text)));
        return said;
    }

    private Platform platformOf(Debate debate, ActorRef candidate) {
        return queries.polity(debate.polity).flatMap(polity -> polity.electionFor(debate.office))
                .map(election -> election.platformOf(candidate)).orElse(Platform.NONE);
    }

    /** Alle Dorfbewohner der Polity, bei Bünden auch die ihrer Glieder. */
    private List<PersonRef> listeners(PolityId polity) {
        Set<PersonRef> listeners = new LinkedHashSet<>();
        collect(polity, listeners, 0);
        return List.copyOf(listeners);
    }

    private void collect(PolityId id, Set<PersonRef> listeners, int depth) {
        Optional<PolityView> polity = queries.polity(id);
        if (polity.isEmpty() || depth > 6) {
            return;
        }
        for (Membership membership : polity.get().members()) {
            if (membership.member() instanceof PersonRef person) {
                listeners.add(person);
            }
        }
        for (PolityRef constituent : polity.get().constituents()) {
            collect(constituent.id(), listeners, depth + 1);
        }
    }

    /** Blitzumfrage: Anteile nach Punkten (überzeugt minus abgeschreckt), weich umgerechnet. */
    private Map<String, Double> verdict(Debate debate) {
        Map<String, Double> weights = new LinkedHashMap<>();
        int listeners = Math.max(1, listeners(debate.polity).size());
        for (Map.Entry<ActorRef, Integer> entry : debate.score.entrySet()) {
            weights.put(texts.actor(entry.getKey()), Math.exp(entry.getValue() * 2.0 / listeners));
        }
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> shares = new LinkedHashMap<>();
        weights.forEach((name, weight) -> shares.put(name, weight * 100 / sum));
        return shares;
    }

    private DebateView view(Debate debate, long now, Map<String, Double> verdict, boolean done) {
        return new DebateView(debate.polity, texts.polityName(debate.polity),
                Message.of(TranslationKeys.office(debate.office)),
                debate.candidates.stream().map(texts::actor).toList(), debate.contributions, verdict,
                Math.min(1, (now - debate.start) / (double) Math.max(1, debate.duration)), done);
    }
}

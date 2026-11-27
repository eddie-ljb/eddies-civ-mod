package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Poster;
import de.etiennebader.civ.domain.polity.Promise;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Der Wahlkampf: Reden, Plakate und gehaltene oder gebrochene Versprechen.
 *
 * <ul>
 *   <li><b>Rede:</b> Dorfbewohner in Hörweite merken sich den Redner drei Tage lang. Je nachdem, wie gut das Thema
 *       der Rede zu ihrer Persönlichkeit passt, zieht es sie zu ihm hin (bis +{@value #SPEECH}) oder stößt sie ab; eine
 *       Rede ohne erkennbares Thema wirkt schwach positiv. Mehrere Reden summieren sich bis ±{@value #SPEECH_CAP}. Jeder
 *       redet höchstens einmal am Tag wirksam.</li>
 *   <li><b>Plakat:</b> Jedes Plakat in der Gemeinde eines Dorfbewohners für den Kandidaten oder seine Partei bringt
 *       +{@value #POSTER}, höchstens +{@value #POSTER_CAP}.</li>
 *   <li><b>Versprechen:</b> Wer das Amt schon hat (oder einen Sitz) und messbare Versprechen gegeben hat, wird nach
 *       seiner Bilanz beurteilt: alles gehalten +{@value #PROMISES}, nichts gehalten −{@value #PROMISES}.</li>
 * </ul>
 * Reden leben nur im Speicher des laufenden Servers; Plakate und Versprechen werden mit der Gemeinde gespeichert.
 */
public final class Campaign implements CampaignInfluence {

    static final double SPEECH = 0.3;
    static final double SPEECH_CAP = 0.6;
    static final double NEUTRAL_SPEECH = 0.1;
    static final long SPEECH_DAYS = 3;
    static final double POSTER = 0.05;
    static final double POSTER_CAP = 0.15;
    static final double PROMISES = 0.3;

    /** Eine gehörte Rede. */
    private record Heard(ActorRef speaker, double strength, Day until) {
    }

    /** Wie eine Rede ankam. */
    public record Reception(int listeners, int convinced, int repelled, Platform topic) {
    }

    private final Function<ActorRef, Optional<PolityView>> partyOf;
    private final Function<ActorRef, Optional<PolityView>> homeOf;
    private final Function<PolityView, LawView> lawsOf;
    private final Map<PersonRef, List<Heard>> heard = new HashMap<>();
    private final Map<ActorRef, Day> lastSpeech = new HashMap<>();
    private final Map<PersonRef, Day> lastPosterRead = new HashMap<>();

    /** Ohne Parteien und Wohnorte, z. B. für Tests. */
    public Campaign() {
        this(actor -> Optional.empty(), actor -> Optional.empty(), PolityView::laws);
    }

    /**
     * @param partyOf die Partei eines Akteurs
     * @param homeOf  die Gemeinde eines Akteurs
     * @param lawsOf  das tatsächlich geltende Recht einer Polity
     */
    public Campaign(Function<ActorRef, Optional<PolityView>> partyOf, Function<ActorRef, Optional<PolityView>> homeOf,
                    Function<PolityView, LawView> lawsOf) {
        this.partyOf = Objects.requireNonNull(partyOf, "partyOf");
        this.homeOf = Objects.requireNonNull(homeOf, "homeOf");
        this.lawsOf = Objects.requireNonNull(lawsOf, "lawsOf");
    }

    /**
     * Eine Rede vor Dorfbewohnern.
     *
     * @param topic Thema der Rede, z. B. aus {@link #topicOf(String)}
     * @return wie sie ankam; leer, wenn der Redner heute schon geredet hat
     */
    public synchronized Optional<Reception> speak(ActorRef speaker, Platform topic, Collection<PersonRef> listeners,
                                                  Day day) {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(listeners, "listeners");
        Objects.requireNonNull(day, "day");
        if (day.equals(lastSpeech.get(speaker))) {
            return Optional.empty();
        }
        lastSpeech.put(speaker, day);
        int convinced = 0;
        int repelled = 0;
        for (PersonRef listener : listeners) {
            double strength = topic == Platform.NONE ? NEUTRAL_SPEECH
                    : SPEECH * agreement(Personality.of(listener), topic);
            if (strength > 0) {
                convinced++;
            } else if (strength < 0) {
                repelled++;
            }
            heard.computeIfAbsent(listener, ignored -> new ArrayList<>())
                    .add(new Heard(speaker, strength, day.plus(SPEECH_DAYS)));
        }
        return Optional.of(new Reception(listeners.size(), convinced, repelled, topic));
    }

    /**
     * Ein Beitrag in einer Wahldebatte: wirkt wie eine halbe Rede auf alle Zuhörer, ohne Tageslimit.
     *
     * @return wie der Beitrag ankam
     */
    public synchronized Reception debate(ActorRef speaker, Platform topic, Collection<PersonRef> listeners, Day day) {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(day, "day");
        int convinced = 0;
        int repelled = 0;
        for (PersonRef listener : listeners) {
            double strength = topic == Platform.NONE ? NEUTRAL_SPEECH / 2
                    : SPEECH * 0.5 * agreement(Personality.of(listener), topic);
            if (strength > 0) {
                convinced++;
            } else if (strength < 0) {
                repelled++;
            }
            heard.computeIfAbsent(listener, ignored -> new ArrayList<>())
                    .add(new Heard(speaker, strength, day.plus(SPEECH_DAYS)));
        }
        return new Reception(listeners.size(), convinced, repelled, topic);
    }

    /**
     * Ein Dorfbewohner liest ein Plakat, an dem er vorbeikommt: wirkt wie ein Fünftel einer Rede, höchstens einmal am
     * Tag je Dorfbewohner (egal an welchem Plakat), und zählt wie Reden zur selben Obergrenze von ±{@value #SPEECH_CAP}.
     * Damit nutzen Villager die Litfaßsäulen, aber nicht zu viel.
     *
     * @return ob er es heute gelesen hat; nicht, wenn er heute schon ein Plakat gelesen hatte
     */
    public synchronized boolean readPoster(PersonRef villager, ActorRef subject, Platform topic, Day day) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(day, "day");
        if (day.equals(lastPosterRead.get(villager))) {
            return false;
        }
        lastPosterRead.put(villager, day);
        double strength = topic == Platform.NONE ? NEUTRAL_SPEECH / 5
                : SPEECH / 5 * agreement(Personality.of(villager), topic);
        heard.computeIfAbsent(villager, ignored -> new ArrayList<>())
                .add(new Heard(subject, strength, day.plus(SPEECH_DAYS)));
        return true;
    }

    /** Wie sehr ein Thema einer Persönlichkeit zusagt: -1 bis 1. */
    static double agreement(Personality personality, Platform topic) {
        return PlatformAffinity.of(topic, personality);
    }

    /** Das Thema einer Rede nach Stichworten; ohne erkennbares Thema {@link Platform#NONE}. */
    public static Platform topicOf(String text) {
        return PlatformAffinity.topicOf(text);
    }

    /** Tagesende: abgelaufene Reden vergessen. */
    public synchronized void endOfDay(Day day) {
        Objects.requireNonNull(day, "day");
        heard.values().forEach(list -> list.removeIf(entry -> !entry.until().isAfter(day)));
        heard.values().removeIf(List::isEmpty);
        lastPosterRead.values().removeIf(read -> read.isBefore(day));
    }

    @Override
    public synchronized double bonus(PersonRef villager, ActorRef candidate, Election election, PolityView polity) {
        double speeches = heard.getOrDefault(villager, List.of()).stream()
                .filter(entry -> entry.speaker().equals(candidate))
                .mapToDouble(Heard::strength).sum();
        double bonus = Math.max(-SPEECH_CAP, Math.min(SPEECH_CAP, speeches));
        bonus += posters(villager, candidate);
        bonus += promises(candidate, election, polity);
        return bonus;
    }

    private double posters(PersonRef villager, ActorRef candidate) {
        Optional<PolityView> home = homeOf.apply(villager);
        if (home.isEmpty()) {
            return 0;
        }
        Optional<PolityRef> party = partyOf.apply(candidate).map(PolityView::asActor);
        long count = home.get().posters().stream()
                .map(Poster::subject)
                .filter(subject -> subject.equals(candidate) || party.filter(subject::equals).isPresent())
                .count();
        return Math.min(POSTER_CAP, count * POSTER);
    }

    private double promises(ActorRef candidate, Election election, PolityView polity) {
        boolean incumbent = election.office() == Office.PARLIAMENT
                ? polity.parliament().holdsSeat(candidate)
                : polity.holds(candidate, election.office());
        if (!incumbent) {
            return 0;
        }
        return balance(candidate, election.office(), polity).map(kept -> (kept * 2 - 1) * PROMISES).orElse(0.0);
    }

    /**
     * Die Bilanz eines Kandidaten: Anteil gehaltener messbarer Versprechen für dieses Amt.
     *
     * @return leer, wenn er nichts Messbares versprochen hat
     */
    public Optional<Double> balance(ActorRef candidate, Office office, PolityView polity) {
        LawView laws = lawsOf.apply(polity);
        List<Double> shares = polity.promises().stream()
                .filter(promise -> promise.candidate().equals(candidate) && promise.office() == office)
                .map(promise -> promise.keptShare(laws))
                .flatMap(Optional::stream)
                .toList();
        if (shares.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(shares.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    /** Für Anzeigen: ob ein einzelnes Versprechen nach geltendem Recht erfüllt ist. */
    public Optional<Double> kept(Promise promise, PolityView polity) {
        return promise.keptShare(lawsOf.apply(polity));
    }
}

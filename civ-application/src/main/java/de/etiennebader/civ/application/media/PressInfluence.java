package de.etiennebader.civ.application.media;

import de.etiennebader.civ.application.population.CampaignInfluence;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.media.PressView;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wie Zeitungsartikel auf Dorfbewohner wirken.
 *
 * <p>Ein Artikel wirkt {@value #FRESH_DAYS} Tage lang in den Gemeinden, in denen er erscheint, auf die Dorfbewohner,
 * die ihn lesen (zwischen 15 % und 65 %, je nach Zugehörigkeitsgefühl). Er wirkt stärker, wenn sein Thema der
 * Persönlichkeit des Lesers zusagt (Framing).
 *
 * <ul>
 *   <li><b>Wahl:</b> Tendenz × 0,08 auf den Kandidaten, × 0,05 auf seine Partei; zusammen höchstens ±0,3.</li>
 *   <li><b>Zufriedenheit:</b> Berichte über die Gemeinde oder ihre Führung: Tendenz × 2 Punkte, höchstens ±6.</li>
 * </ul>
 */
public final class PressInfluence implements CampaignInfluence {

    public static final int FRESH_DAYS = 3;
    private static final double CANDIDATE_WEIGHT = 0.08;
    private static final double PARTY_WEIGHT = 0.05;
    private static final double VOTE_CAP = 0.3;
    private static final int MOOD_CAP = 6;

    private final Supplier<PressView> press;
    private volatile Day today = Day.of(1);
    private final Function<ActorRef, Optional<PolityView>> homeOf;
    private final Function<ActorRef, Optional<PolityView>> partyOf;

    public PressInfluence(Supplier<PressView> press, Function<ActorRef, Optional<PolityView>> homeOf,
                          Function<ActorRef, Optional<PolityView>> partyOf) {
        this.press = Objects.requireNonNull(press, "press");
        this.homeOf = Objects.requireNonNull(homeOf, "homeOf");
        this.partyOf = Objects.requireNonNull(partyOf, "partyOf");
    }

    /** Der laufende Spieltag; Artikel wirken ab ihrem Erscheinungstag. */
    public void today(Day day) {
        this.today = Objects.requireNonNull(day, "day");
    }

    public Day today() {
        return today;
    }

    /** Ob ein Dorfbewohner einen Artikel liest; fest je Leser und Artikel. */
    public static boolean reads(PersonRef reader, Article article) {
        long mix = reader.uuid().getMostSignificantBits() ^ article.id().value().getLeastSignificantBits()
                ^ Long.rotateLeft(reader.uuid().getLeastSignificantBits(), 17);
        int roll = (int) Math.floorMod(mix * 0x9E3779B97F4A7C15L, 100L);
        return roll < 40 + Personality.of(reader).belonging() / 4;
    }

    /** Wie stark ein Thema bei diesem Leser verfängt: 1 bis 1,5. */
    static double framing(Personality personality, Platform topic) {
        double agreement = switch (topic) {
            case NONE -> 0;
            case ORDER -> personality.order() / 100.0;
            case FREEDOM -> -personality.order() / 100.0;
            case LOW_TAXES -> personality.thrift() / 100.0;
            case INVESTMENT -> -personality.thrift() / 100.0;
        };
        return 1 + Math.max(0, agreement) * 0.5;
    }

    @Override
    public double bonus(PersonRef villager, ActorRef candidate, Election election, PolityView polity) {
        Optional<PolityView> home = homeOf.apply(villager);
        if (home.isEmpty()) {
            return 0;
        }
        Optional<ActorRef> party = partyOf.apply(candidate).map(PolityView::asActor);
        Personality personality = Personality.of(villager);
        double sum = 0;
        for (Article article : press.get().fresh(today, FRESH_DAYS)) {
            if (article.subject().isEmpty() || !article.towns().contains(home.get().id()) || !reads(villager, article)) {
                continue;
            }
            ActorRef subject = article.subject().get();
            double weight = subject.equals(candidate) ? CANDIDATE_WEIGHT
                    : party.filter(subject::equals).isPresent() ? PARTY_WEIGHT : 0;
            sum += weight * article.tendency() * framing(personality, article.topic());
        }
        return Math.max(-VOTE_CAP, Math.min(VOTE_CAP, sum));
    }

    /** Zufriedenheitspunkte eines Dorfbewohners aus Berichten über seine Gemeinde und deren Führung. */
    public int satisfactionPoints(PersonRef villager, PolityView town) {
        Objects.requireNonNull(town, "town");
        Personality personality = Personality.of(villager);
        Optional<ActorRef> mayor = town.mayor();
        double sum = 0;
        for (Article article : press.get().fresh(today, FRESH_DAYS)) {
            if (article.subject().isEmpty() || !article.towns().contains(town.id()) || !reads(villager, article)) {
                continue;
            }
            ActorRef subject = article.subject().get();
            if (subject.equals(town.asActor()) || mayor.filter(subject::equals).isPresent()) {
                sum += 2 * article.tendency() * framing(personality, article.topic());
            }
        }
        return (int) Math.max(-MOOD_CAP, Math.min(MOOD_CAP, Math.round(sum)));
    }
}

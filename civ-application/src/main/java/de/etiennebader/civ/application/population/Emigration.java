package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Wer am Ende eines Tages seine Gemeinde verlässt, und wohin.
 *
 * <ul>
 *   <li>Nur wer sehr unzufrieden ist (unter {@link #LEAVE_BELOW}), denkt ans Gehen. Die Tageswahrscheinlichkeit
 *       steigt mit der Unzufriedenheit bis zum Vollwert bei -70 und wird mit der Rastlosigkeit gewichtet: ganz Rastlose
 *       gehen dann sicher, ganz Heimatverbundene nur zu 20 %.</li>
 *   <li>Ob es heute so weit ist, entscheidet ein fester Zufall je Dorfbewohner und Tag; dasselbe Spiel verläuft gleich.</li>
 *   <li>Ziel ist die Gemeinde, in der der Dorfbewohner am zufriedensten wäre, wenn er dort mindestens
 *       {@link #MOVE_GAIN} Punkte zufriedener und nicht unzufrieden wäre. Sonst zieht er in die Wildnis.</li>
 *   <li>Höchstens ein Viertel der Dorfbewohner einer Gemeinde geht an einem Tag, zuerst die Unzufriedensten.</li>
 * </ul>
 */
public final class Emigration {

    /** Unter dieser Zufriedenheit denkt ein Dorfbewohner ans Gehen. */
    public static final int LEAVE_BELOW = -30;
    /** So viele Punkte unter {@link #LEAVE_BELOW} ist die Unzufriedenheit voll ausgeprägt. */
    static final int FULL_MISERY = 40;
    /** So viel zufriedener muss eine andere Gemeinde ihn machen, damit er dorthin zieht. */
    public static final int MOVE_GAIN = 30;

    /** Ein Dorfbewohner verlässt seine Gemeinde. */
    public record Departure(PersonRef villager, PolityId from, Optional<PolityId> to) {

        public Departure {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    private record Candidate(PersonRef villager, Personality personality, int score) {
    }

    private Emigration() {
    }

    /** Ob ein Dorfbewohner mit dieser Zufriedenheit überhaupt ans Gehen denkt. */
    public static boolean atRisk(int satisfaction) {
        return satisfaction < LEAVE_BELOW;
    }

    public static List<Departure> plan(List<? extends PolityView> towns, Day day) {
        Objects.requireNonNull(towns, "towns");
        Objects.requireNonNull(day, "day");
        List<PolityView> settlements = towns.stream()
                .filter(town -> town.kind() == PolityKind.TERRITORIAL)
                .map(PolityView.class::cast)
                .toList();
        List<Departure> departures = new ArrayList<>();
        for (PolityView town : settlements) {
            List<PersonRef> villagers = town.members().stream()
                    .map(Membership::member)
                    .filter(PersonRef.class::isInstance)
                    .map(PersonRef.class::cast)
                    .toList();
            List<Candidate> leaving = new ArrayList<>();
            for (PersonRef villager : villagers) {
                Personality personality = Personality.of(villager);
                int score = Satisfaction.of(personality, town).score();
                if (atRisk(score) && roll(villager, day) < chance(score, personality)) {
                    leaving.add(new Candidate(villager, personality, score));
                }
            }
            int limit = Math.max(1, villagers.size() / 4);
            leaving.stream()
                    .sorted(Comparator.comparingInt(Candidate::score))
                    .limit(limit)
                    .forEach(candidate -> departures.add(new Departure(candidate.villager(), town.id(),
                            destination(candidate.personality(), candidate.score(), town, settlements))));
        }
        return departures;
    }

    /** Tageswahrscheinlichkeit zu gehen, 0 bis 1. */
    static double chance(int satisfaction, Personality personality) {
        double misery = Math.min(1.0, (LEAVE_BELOW - satisfaction) / (double) FULL_MISERY);
        double restlessness = (150 - personality.belonging()) / 250.0;
        return Math.max(0, misery) * restlessness;
    }

    private static Optional<PolityId> destination(Personality personality, int score, PolityView home,
                                                  List<PolityView> towns) {
        PolityView best = null;
        int bestScore = Integer.MIN_VALUE;
        for (PolityView town : towns) {
            if (town.id().equals(home.id())) {
                continue;
            }
            int there = Satisfaction.of(personality, town).score();
            if (there > bestScore) {
                bestScore = there;
                best = town;
            }
        }
        if (best == null || bestScore < 0 || bestScore < score + MOVE_GAIN) {
            return Optional.empty();
        }
        return Optional.of(best.id());
    }

    /** Fester Zufall je Dorfbewohner und Tag, 0 bis unter 1. */
    private static double roll(PersonRef villager, Day day) {
        long mixed = villager.uuid().getMostSignificantBits() ^ villager.uuid().getLeastSignificantBits()
                ^ (day.value() * 0x9E3779B97F4A7C15L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }
}

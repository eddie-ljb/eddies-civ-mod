package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.office.VillagerVoter;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;

import java.util.Optional;

/**
 * Dorfbewohner wählen nach ihrer Persönlichkeit und ihrer Zufriedenheit.
 *
 * <p>Jeder Kandidat bekommt einen Nutzen:
 * <ul>
 *   <li><b>Programm:</b> Ordnungsliebende mögen {@link Platform#ORDER}, Freigeister {@link Platform#FREEDOM};
 *       bis ±1. Sparsame mögen {@link Platform#LOW_TAXES}, Großzügige {@link Platform#INVESTMENT}; dazu zieht eine
 *       spürbare Steuerlast jeden zu niedrigen Steuern, solange er nicht großzügig ist.</li>
 *   <li><b>Amtsinhaber:</b> Zufriedene halten zum Inhaber (bei Heimatverbundenen stärker), Unzufriedene wollen Wechsel;
 *       bis ±0,9.</li>
 *   <li><b>Zufall:</b> eine feste kleine Vorliebe je Paar aus Dorfbewohner und Kandidat (±0,15), damit nicht alle
 *       gleich wählen.</li>
 * </ul>
 * Gewählt wird der Kandidat mit dem höchsten Nutzen; liegt er unter -0,5, bleibt der Dorfbewohner zu Hause.
 */
public final class PersonalityVoter implements VillagerVoter {

    static final double ABSTAIN_BELOW = -0.5;

    @Override
    public Optional<ActorRef> choose(PersonRef villager, Election election, PolityView polity) {
        Personality personality = Personality.of(villager);
        Satisfaction satisfaction = Satisfaction.of(personality, polity);
        Optional<ActorRef> incumbent = election.office() == Office.MAYOR
                ? polity.mayor()
                : polity.officeTerm(election.office()).map(term -> term.holder());
        ActorRef best = null;
        double bestUtility = Double.NEGATIVE_INFINITY;
        for (ActorRef candidate : election.candidates()) {
            double utility = utility(personality, satisfaction, election.platformOf(candidate),
                    incumbent.filter(candidate::equals).isPresent(), villager, candidate);
            if (utility > bestUtility) {
                bestUtility = utility;
                best = candidate;
            }
        }
        return best == null || bestUtility < ABSTAIN_BELOW ? Optional.empty() : Optional.of(best);
    }

    static double utility(Personality personality, Satisfaction satisfaction, Platform platform, boolean incumbent,
                          PersonRef villager, ActorRef candidate) {
        double taxBurden = -satisfaction.points(Satisfaction.Factor.TAXES) / 40.0;
        double programme = switch (platform) {
            case NONE -> 0;
            case ORDER -> personality.order() / 100.0;
            case FREEDOM -> -personality.order() / 100.0;
            case LOW_TAXES -> personality.thrift() / 100.0 * 0.6 + taxBurden * 0.8;
            case INVESTMENT -> -personality.thrift() / 100.0 * 0.6;
        };
        double mood = satisfaction.score() / 100.0;
        double loyalty = 0.6 + personality.belonging() / 333.0;
        double incumbency = incumbent ? mood * loyalty : Math.max(0, -mood) * 0.4;
        return programme + incumbency + noise(villager, candidate);
    }

    private static double noise(PersonRef villager, ActorRef candidate) {
        long mixed = villager.uuid().getLeastSignificantBits() * 0x9E3779B97F4A7C15L
                ^ candidate.uuid().getMostSignificantBits();
        mixed = (mixed ^ (mixed >>> 31)) * 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 29;
        return (Math.floorMod(mixed, 301) - 150) / 1000.0;
    }
}

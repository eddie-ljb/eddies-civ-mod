package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.office.VillagerVoter;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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
    /** Bonus für Kandidaten der eigenen Partei. */
    static final double OWN_PARTY = 0.6;

    private final Function<PolityView, LawView> lawsOf;
    private final Function<ActorRef, Optional<PolityView>> partyOf;

    /** Ohne Bundesrecht und Parteien, z. B. für Tests. */
    public PersonalityVoter() {
        this(PolityView::laws, actor -> Optional.empty());
    }

    /**
     * @param lawsOf  das tatsächlich geltende Recht einer Polity
     * @param partyOf die Partei eines Akteurs
     */
    public PersonalityVoter(Function<PolityView, LawView> lawsOf, Function<ActorRef, Optional<PolityView>> partyOf) {
        this.lawsOf = Objects.requireNonNull(lawsOf, "lawsOf");
        this.partyOf = Objects.requireNonNull(partyOf, "partyOf");
    }

    @Override
    public Optional<ActorRef> choose(PersonRef villager, Election election, PolityView polity) {
        Personality personality = Personality.of(villager);
        Satisfaction satisfaction = Satisfaction.of(personality, polity, lawsOf.apply(polity));
        Optional<PolityId> ownParty = partyOf.apply(villager).map(PolityView::id);
        Optional<ActorRef> incumbent = election.office() == Office.MAYOR
                ? polity.mayor()
                : polity.officeTerm(election.office()).map(term -> term.holder());
        ActorRef best = null;
        double bestUtility = Double.NEGATIVE_INFINITY;
        for (ActorRef candidate : election.candidates()) {
            Optional<PolityView> party = partyOf.apply(candidate);
            double utility = utility(personality, satisfaction, election.platformOf(candidate),
                    incumbent.filter(candidate::equals).isPresent(), villager, candidate)
                    + partyAffinity(personality, ownParty, party);
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

    /**
     * Eigene Partei: fester Bonus. Fremde Partei: je näher ihre Richtung an der eigenen Persönlichkeit, desto besser
     * (bis ±0,3).
     */
    public static double partyAffinity(Personality personality, Optional<PolityId> ownParty, Optional<PolityView> party) {
        if (party.isEmpty()) {
            return 0;
        }
        if (ownParty.filter(party.get().id()::equals).isPresent()) {
            return OWN_PARTY;
        }
        return party.get().partyProfile()
                .map(profile -> (0.5 - profile.distanceTo(personality.order(), -personality.thrift()) / 200.0) * 0.6)
                .orElse(0.0);
    }

    private static double noise(PersonRef villager, ActorRef candidate) {
        long mixed = villager.uuid().getLeastSignificantBits() * 0x9E3779B97F4A7C15L
                ^ candidate.uuid().getMostSignificantBits();
        mixed = (mixed ^ (mixed >>> 31)) * 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 29;
        return (Math.floorMod(mixed, 301) - 150) / 1000.0;
    }
}

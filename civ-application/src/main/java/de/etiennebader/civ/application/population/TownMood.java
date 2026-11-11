package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.population.Trait;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Die Stimmung aller Dorfbewohner einer Gemeinde.
 *
 * @param villagers      wie viele Dorfbewohner gezählt wurden
 * @param averageScore   mittlere Zufriedenheit, -100 bis 100; 0 ohne Dorfbewohner
 * @param content        wie viele zufrieden sind (ab {@link Satisfaction#CONTENT})
 * @param discontent     wie viele unzufrieden sind (unter -{@link Satisfaction#CONTENT})
 * @param averageFactors mittlere Punkte je Einflussgröße
 * @param traits         wie viele Dorfbewohner welche hervorstechende Eigenschaft haben
 * @param productivity   Summe der Wirtschaftsleistung in Prozent eines Dorfbewohners (100 = ein durchschnittlicher)
 * @param atRisk         wie viele so unzufrieden sind, dass sie ans Gehen denken ({@link Emigration#atRisk})
 */
public record TownMood(int villagers, int averageScore, int content, int discontent,
                       Map<Satisfaction.Factor, Integer> averageFactors, Map<Trait, Integer> traits,
                       long productivity, int atRisk) {

    public TownMood {
        averageFactors = Map.copyOf(Objects.requireNonNull(averageFactors, "averageFactors"));
        traits = Map.copyOf(Objects.requireNonNull(traits, "traits"));
    }

    public static TownMood of(PolityView town) {
        Objects.requireNonNull(town, "town");
        List<PersonRef> villagers = town.members().stream()
                .map(Membership::member)
                .filter(PersonRef.class::isInstance)
                .map(PersonRef.class::cast)
                .toList();
        Map<Satisfaction.Factor, Integer> sums = new EnumMap<>(Satisfaction.Factor.class);
        Map<Trait, Integer> traits = new EnumMap<>(Trait.class);
        long total = 0;
        long productivity = 0;
        int content = 0;
        int discontent = 0;
        int atRisk = 0;
        for (PersonRef villager : villagers) {
            Personality personality = Personality.of(villager);
            Satisfaction satisfaction = Satisfaction.of(personality, town);
            int score = satisfaction.score();
            total += score;
            productivity += satisfaction.productivityPercent();
            if (score >= Satisfaction.CONTENT) {
                content++;
            } else if (score < -Satisfaction.CONTENT) {
                discontent++;
            }
            if (Emigration.atRisk(score)) {
                atRisk++;
            }
            satisfaction.factors().forEach((factor, points) -> sums.merge(factor, points, Integer::sum));
            traits.merge(personality.dominant(), 1, Integer::sum);
        }
        int count = villagers.size();
        Map<Satisfaction.Factor, Integer> averages = new EnumMap<>(Satisfaction.Factor.class);
        for (Satisfaction.Factor factor : Satisfaction.Factor.values()) {
            averages.put(factor, count == 0 ? 0 : Math.round((float) sums.getOrDefault(factor, 0) / count));
        }
        return new TownMood(count, count == 0 ? 0 : (int) Math.round((double) total / count), content, discontent,
                averages, traits, productivity, atRisk);
    }
}

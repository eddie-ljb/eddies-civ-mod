package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wie zufrieden ein Dorfbewohner mit seiner Gemeinde ist, von -100 bis 100, und woraus sich das zusammensetzt.
 *
 * <p>Die Zufriedenheit wird jeden Tag frisch aus Persönlichkeit und Gemeinde berechnet:
 * <ul>
 *   <li><b>Grundstimmung:</b> +10, Heimatverbundene mehr, Rastlose weniger.</li>
 *   <li><b>Ordnung:</b> Wie streng die Schutzregeln sind (Explosionen, Feuer, Rechte von Fremden). Ordnungsliebende
 *       mögen strenge Regeln, Freigeister lockere; bis ±25.</li>
 *   <li><b>Steuern:</b> Grundsteuer und Umsatzsteuer stören jeden (bis -15), Sparsame stark (bis -53).</li>
 *   <li><b>Mitsprache:</b> ein gewählter Bürgermeister, Abstimmungen über Gesetze und das Wahlrecht für Dorfbewohner.</li>
 *   <li><b>Wohlstand:</b> +5 je Siedlungsstufe.</li>
 * </ul>
 *
 * @param factors Punkte je Einflussgröße; ihre Summe, begrenzt auf ±100, ist {@link #score()}
 */
public record Satisfaction(Map<Factor, Integer> factors) {

    /** Einflussgrößen der Zufriedenheit. */
    public enum Factor {
        BASE,
        ORDER,
        TAXES,
        VOICE,
        PROSPERITY
    }

    /** Ab hier gilt ein Dorfbewohner als zufrieden bzw. unter dem negativen Wert als unzufrieden. */
    public static final int CONTENT = 20;

    public Satisfaction {
        factors = Map.copyOf(Objects.requireNonNull(factors, "factors"));
    }

    public static Satisfaction of(Personality personality, PolityView town) {
        Objects.requireNonNull(personality, "personality");
        Objects.requireNonNull(town, "town");
        LawView laws = town.laws();
        Map<Factor, Integer> factors = new EnumMap<>(Factor.class);
        factors.put(Factor.BASE, 10 + personality.belonging() / 10);
        factors.put(Factor.ORDER, (int) Math.round(personality.order() / 100.0 * orderIndex(laws) * 25));
        double taxes = taxIndex(laws);
        factors.put(Factor.TAXES, (int) Math.round(-taxes * (15 + Math.max(0, personality.thrift() + 25) * 0.3)));
        int voice = 0;
        if (town.officeTerm(Office.MAYOR).isPresent()) {
            voice += 8;
        }
        if (laws.valueOf(StandardParameters.LAWS_BY_VOTE)) {
            voice += 4;
        }
        voice += (int) Math.round(laws.valueOf(StandardParameters.VILLAGER_VOTE_PERCENT) * 0.15);
        factors.put(Factor.VOICE, voice);
        factors.put(Factor.PROSPERITY, town.tier().ordinal() * 5);
        return new Satisfaction(factors);
    }

    public int score() {
        int sum = factors.values().stream().mapToInt(Integer::intValue).sum();
        return Math.max(-100, Math.min(100, sum));
    }

    public int points(Factor factor) {
        return factors.getOrDefault(factor, 0);
    }

    /** Wie viel Prozent seiner üblichen Wirtschaftsleistung der Dorfbewohner erbringt: 50 bis 150. */
    public int productivityPercent() {
        return 100 + score() / 2;
    }

    /**
     * Wie streng die Schutzregeln sind: -1 (alles erlaubt) bis 1 (alles verboten).
     */
    static double orderIndex(LawView laws) {
        int strict = 0;
        strict += laws.valueOf(StandardParameters.EXPLOSIONS_ALLOWED) ? 0 : 1;
        strict += laws.valueOf(StandardParameters.FIRE_SPREAD_ALLOWED) ? 0 : 1;
        strict += laws.valueOf(StandardParameters.OUTSIDERS_MAY_BUILD) ? 0 : 1;
        strict += laws.valueOf(StandardParameters.OUTSIDERS_MAY_INTERACT) ? 0 : 1;
        strict += laws.valueOf(StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS) ? 0 : 1;
        return (strict - 2.5) / 2.5;
    }

    /**
     * Wie hoch die Steuern sind: 0 bis 1. Grundsteuer ab 10 Hundertstel je Block und Tag gilt als hoch,
     * Umsatzsteuer ab 50 %.
     */
    static double taxIndex(LawView laws) {
        double land = Math.min(laws.valueOf(StandardParameters.LAND_TAX_PER_BLOCK), 10L) / 10.0;
        double sales = Math.min(laws.valueOf(StandardParameters.SALES_TAX_PERCENT), 50L) / 50.0;
        return 0.5 * land + 0.5 * sales;
    }
}

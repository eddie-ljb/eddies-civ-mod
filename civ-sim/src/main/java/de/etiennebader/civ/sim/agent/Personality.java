package de.etiennebader.civ.sim.agent;

import java.util.random.RandomGenerator;

/**
 * Feste Charakterzüge eines Akteurs, jeweils zwischen 0 und 1, außer der Produktivität.
 *
 * @param ambition      Neigung, selbst eine Gemeinde zu gründen
 * @param sociability   Wunsch, irgendwo dazuzugehören
 * @param thrift        Sparsamkeit: höhere Rücklagen, zögerlicher beim Kauf
 * @param cashAffinity  Vorliebe für Bargeld statt Konto
 * @param productivity  Faktor auf das Grundeinkommen
 */
public record Personality(double ambition, double sociability, double thrift, double cashAffinity, double productivity) {

    public Personality {
        requireUnit("ambition", ambition);
        requireUnit("sociability", sociability);
        requireUnit("thrift", thrift);
        requireUnit("cashAffinity", cashAffinity);
        if (productivity <= 0) {
            throw new IllegalArgumentException("productivity muss positiv sein: " + productivity);
        }
    }

    /**
     * Würfelt eine Persönlichkeit aus. Spieler sind im Schnitt ehrgeiziger als Villager,
     * Villager geselliger.
     *
     * @param incomeSpread Streuung der Produktivität um 1
     */
    public static Personality random(AgentKind kind, RandomGenerator random, double incomeSpread) {
        double ambition = kind == AgentKind.PLAYER ? between(random, 0.3, 1.0) : between(random, 0.0, 0.5);
        double sociability = kind == AgentKind.PLAYER ? between(random, 0.2, 0.9) : between(random, 0.5, 1.0);
        double thrift = random.nextDouble();
        double cashAffinity = random.nextDouble();
        double productivity = between(random, 1 - incomeSpread, 1 + incomeSpread);
        return new Personality(ambition, sociability, thrift, cashAffinity, Math.max(productivity, 0.01));
    }

    private static double between(RandomGenerator random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static void requireUnit(String name, double value) {
        if (value < 0 || value > 1 || Double.isNaN(value)) {
            throw new IllegalArgumentException(name + " muss zwischen 0 und 1 liegen: " + value);
        }
    }
}

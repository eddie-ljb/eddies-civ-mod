package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;

import java.util.Objects;

/**
 * Bildung und Intelligenz eines jungen Dorfbewohners (Issue #21), von 0 bis {@value #MAX}.
 *
 * <p>Anders als die {@linkplain de.etiennebader.civ.domain.population.Personality Persönlichkeit} ergeben sich
 * beide Werte nicht allein aus der Identität, sondern wachsen bzw. schwinden Tag für Tag, solange der Dorfbewohner
 * jung ist: Geht er an einem Tag zur Schule (hält sich auf einem Grundstück vom Typ
 * {@link de.etiennebader.civ.domain.land.PlotType#EDUCATION} auf), steigt seine Bildung, schneller, je intelligenter
 * er ist. Geht er nicht zur Schule, sinkt seine Intelligenz etwas. Ist er erwachsen, frieren beide Werte für immer
 * ein ({@link #frozen}) — weitere Tage ändern dann nichts mehr.
 *
 * @param points       Bildung, 0 bis {@value #MAX}
 * @param intelligence Intelligenz, 0 bis {@value #MAX}
 * @param frozen       ob der Dorfbewohner erwachsen ist und die Werte damit feststehen
 */
public record VillagerEducation(int points, int intelligence, boolean frozen) {

    public static final int MAX = 100;
    /** Anfangs-Intelligenz streut deterministisch um diesen Wert (wie bei {@code Personality}). */
    private static final int BASE_INTELLIGENCE = 50;
    private static final int INTELLIGENCE_SPREAD = 20;
    /** Bildungszuwachs an einem Schultag: 1 bis 5, abhängig von der Intelligenz. */
    private static final int MIN_GAIN = 1;
    private static final int MAX_GAIN = 5;
    /** Intelligenzverlust an einem Tag ohne Schule. */
    private static final int ABSENCE_LOSS = 1;

    public VillagerEducation {
        points = clamp(points);
        intelligence = clamp(intelligence);
    }

    /** Anfangszustand eines neu bemerkten jungen Dorfbewohners: keine Bildung, deterministisch gestreute Intelligenz. */
    public static VillagerEducation initial(PersonRef villager) {
        Objects.requireNonNull(villager, "villager");
        long seed = villager.uuid().getMostSignificantBits() * 31 + villager.uuid().getLeastSignificantBits();
        int offset = (int) (Math.floorMod(seed, 2L * INTELLIGENCE_SPREAD + 1) - INTELLIGENCE_SPREAD);
        return new VillagerEducation(0, BASE_INTELLIGENCE + offset, false);
    }

    /**
     * Ein Tag im Kindesalter: entweder ein Schultag (Bildung wächst, Intelligenz bestimmt wie schnell) oder nicht
     * (Intelligenz sinkt etwas). Ohne Wirkung, sobald {@link #frozen}.
     */
    public VillagerEducation afterChildDay(boolean attendedSchool) {
        if (frozen) {
            return this;
        }
        if (attendedSchool) {
            int gain = MIN_GAIN + Math.round((MAX_GAIN - MIN_GAIN) * intelligence / (float) MAX);
            return new VillagerEducation(points + gain, intelligence, false);
        }
        return new VillagerEducation(points, intelligence - ABSENCE_LOSS, false);
    }

    /** Der Dorfbewohner ist erwachsen geworden: Bildung und Intelligenz stehen von nun an fest. */
    public VillagerEducation grownUp() {
        return frozen ? this : new VillagerEducation(points, intelligence, true);
    }

    /**
     * Gemeinsames Maß aus Bildung und Intelligenz, 0 bis {@value #MAX}, zur Gewichtung von Wahl- und politischem
     * Verhalten (Issue #21): Mittelwert beider Werte.
     */
    public int votingBonus() {
        return clamp(Math.round((points + intelligence) / 2.0f));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }
}

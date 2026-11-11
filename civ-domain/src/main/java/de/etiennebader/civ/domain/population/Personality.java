package de.etiennebader.civ.domain.population;

import de.etiennebader.civ.domain.actor.PersonRef;

import java.util.Objects;
import java.util.UUID;

/**
 * Die Persönlichkeit eines Dorfbewohners auf drei Achsen, jeweils von -100 bis 100.
 *
 * <p>Sie ergibt sich fest aus seiner Identität: derselbe Dorfbewohner hat nach jedem Neustart dieselbe
 * Persönlichkeit, ohne dass sie gespeichert werden muss. Die Werte häufen sich um 0; Extreme sind selten.
 *
 * @param order     Ordnung (positiv) gegen Freiheit (negativ): wie sehr Schutz- und Ordnungsregeln gefallen
 * @param thrift    Sparsamkeit (positiv) gegen Großzügigkeit (negativ): wie sehr Steuern stören
 * @param belonging Heimatverbundenheit: wie treu der Dorfbewohner der Gemeinde und ihrer Führung ist
 */
public record Personality(int order, int thrift, int belonging) {

    public static final int MAX = 100;
    private static final int DRAWS = 2;

    public Personality {
        check(order, "order");
        check(thrift, "thrift");
        check(belonging, "belonging");
    }

    /** Die Persönlichkeit dieses Dorfbewohners. */
    public static Personality of(PersonRef person) {
        Objects.requireNonNull(person, "person");
        UUID id = person.uuid();
        long seed = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits();
        return new Personality(axis(seed, 1), axis(seed, 2), axis(seed, 3));
    }

    /** Die am stärksten ausgeprägte Eigenschaft, für Anzeigen. */
    public Trait dominant() {
        int absOrder = Math.abs(order);
        int absThrift = Math.abs(thrift);
        if (Math.abs(belonging) >= absOrder && Math.abs(belonging) >= absThrift) {
            return belonging >= 0 ? Trait.LOYAL : Trait.RESTLESS;
        }
        if (absOrder >= absThrift) {
            return order >= 0 ? Trait.ORDERLY : Trait.FREE_SPIRITED;
        }
        return thrift >= 0 ? Trait.THRIFTY : Trait.GENEROUS;
    }

    /** Durchschnitt von zwei gleichverteilten Werten: eine Dreiecksverteilung um 0, Extreme sind selten. */
    private static int axis(long seed, int salt) {
        long sum = 0;
        for (int draw = 0; draw < DRAWS; draw++) {
            sum += Math.floorMod(mix(seed + salt * 0x9E3779B97F4A7C15L + draw * 0xC2B2AE3D27D4EB4FL), 2 * MAX + 1) - MAX;
        }
        return (int) (sum / DRAWS);
    }

    /** SplitMix64-Finalizer. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static void check(int value, String name) {
        if (value < -MAX || value > MAX) {
            throw new IllegalArgumentException(name + " muss zwischen -100 und 100 liegen: " + value);
        }
    }
}

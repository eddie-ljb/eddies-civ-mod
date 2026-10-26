package de.etiennebader.civ.domain.shared;

import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Erzeugt aus einem Seed eine reproduzierbare Folge gültiger Version-4-UUIDs.
 * Gleicher Seed ergibt dieselbe Folge. Für Tests und die Headless-Simulation.
 *
 * <p>Nicht threadsicher.
 */
public final class SeededIdGenerator implements IdGenerator {

    private static final long VERSION_MASK = 0xFFFF_FFFF_FFFF_0FFFL;
    private static final long VERSION_4 = 0x0000_0000_0000_4000L;
    private static final long VARIANT_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final long VARIANT_IETF = 0x8000_0000_0000_0000L;

    private final SplittableRandom random;

    public SeededIdGenerator(long seed) {
        this.random = new SplittableRandom(seed);
    }

    @Override
    public UUID next() {
        long mostSignificant = (random.nextLong() & VERSION_MASK) | VERSION_4;
        long leastSignificant = (random.nextLong() & VARIANT_MASK) | VARIANT_IETF;
        return new UUID(mostSignificant, leastSignificant);
    }
}

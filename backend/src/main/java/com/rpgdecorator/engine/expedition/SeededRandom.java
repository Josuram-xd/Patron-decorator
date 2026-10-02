package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.RandomSource;

import java.util.SplittableRandom;

/**
 * Deterministic {@link RandomSource} of one expedition: the same seed always gives the same sequence
 * (RNF-04). It wraps {@link SplittableRandom}, whose algorithm is fixed by the JDK spec, so a seed
 * replays the same expedition on any JVM.
 *
 * <p>Lives in the engine because the domain only has the {@code RandomSource} interface and the
 * expedition service needs a seeded source to build each expedition. Infrastructure will add its own
 * {@code JdkRandomSource} (T-306). {@code ExpeditionService} takes a {@code seed -> RandomSource}
 * factory, so the application can plug that one in instead. Not thread-safe: an expedition is only
 * used under its own lock.
 */
public final class SeededRandom implements RandomSource {

    private final SplittableRandom random;

    public SeededRandom(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /** @throws IllegalArgumentException if {@code maxInclusive < minInclusive} */
    @Override
    public int nextInt(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("max < min: " + minInclusive + ".." + maxInclusive);
        }
        if (maxInclusive == minInclusive) {
            return minInclusive;
        }
        return (int) random.nextLong(minInclusive, (long) maxInclusive + 1);
    }

    /** {@code true} with probability {@code percent} % (0 or less: never; 100 or more: always). */
    @Override
    public boolean chance(int percent) {
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }
        return random.nextInt(100) < percent;
    }
}

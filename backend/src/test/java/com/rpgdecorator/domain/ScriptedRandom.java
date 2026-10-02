package com.rpgdecorator.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Answers {@code chance} from a script (false once it runs out) and records the percents asked. */
public final class ScriptedRandom implements RandomSource {

    private final Deque<Boolean> outcomes = new ArrayDeque<>();
    public final List<Integer> askedPercents = new ArrayList<>();

    public ScriptedRandom(boolean... chanceOutcomes) {
        for (boolean outcome : chanceOutcomes) {
            outcomes.add(outcome);
        }
    }

    @Override
    public int nextInt(int minInclusive, int maxInclusive) {
        return minInclusive;
    }

    @Override
    public boolean chance(int percent) {
        askedPercents.add(percent);
        return !outcomes.isEmpty() && outcomes.poll();
    }
}

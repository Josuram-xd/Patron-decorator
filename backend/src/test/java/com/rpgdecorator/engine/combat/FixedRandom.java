package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Deterministic RandomSource: scripted chance() answers (default false) and recorded percents. */
final class FixedRandom implements RandomSource {

    private final Deque<Boolean> chances = new ArrayDeque<>();
    final List<Integer> requestedPercents = new ArrayList<>();

    FixedRandom(boolean... answers) {
        for (boolean answer : answers) {
            chances.add(answer);
        }
    }

    @Override
    public int nextInt(int minInclusive, int maxInclusive) {
        return minInclusive;
    }

    @Override
    public boolean chance(int percent) {
        requestedPercents.add(percent);
        return !chances.isEmpty() && chances.poll();
    }
}

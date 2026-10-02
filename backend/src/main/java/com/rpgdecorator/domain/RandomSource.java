package com.rpgdecorator.domain;

public interface RandomSource {

    int nextInt(int minInclusive, int maxInclusive);

    boolean chance(int percent);
}

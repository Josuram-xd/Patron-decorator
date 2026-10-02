package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.RandomSource;

/** Condition the enemy AI checks before using an ability. */
@FunctionalInterface
public interface AiCondition {

    AiCondition ALWAYS = (view, random) -> true;

    boolean test(AiView view, RandomSource random);
}

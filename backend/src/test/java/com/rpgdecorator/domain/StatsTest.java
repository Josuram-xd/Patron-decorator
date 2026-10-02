package com.rpgdecorator.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

class StatsTest {

    private final Stats warrior = new Stats(120, 14, 8, 4, 10);

    @Test
    void withMethodsReturnCopiesAndLeaveTheOriginalUntouched() {
        Stats changed = warrior.withAttack(20).withDefense(3).withSpeed(9).withMaxHealth(145).withCritChance(25);

        assertNotSame(warrior, changed);
        assertEquals(new Stats(120, 14, 8, 4, 10), warrior);
        assertEquals(new Stats(145, 20, 3, 9, 25), changed);
    }

    @Test
    void statsNeverDropBelowZero() {
        Stats floored = new Stats(-1, -5, -3, -4, -10);

        assertEquals(new Stats(0, 0, 0, 0, 0), floored);
        assertEquals(0, warrior.withSpeed(4 - 10).speed());
    }

    @Test
    void critChanceIsCappedAtOneHundred() {
        assertEquals(100, warrior.withCritChance(130).critChance());
    }
}

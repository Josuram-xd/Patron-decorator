package com.rpgdecorator.domain.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DurationTest {

    @Test
    void permanentNeverExpires() {
        Duration duration = Duration.PERMANENT;
        for (int i = 0; i < 1000; i++) {
            duration = duration.decrement();
        }

        assertTrue(duration.isPermanent());
        assertFalse(duration.isExpired());
    }

    @Test
    void oneTurnExpiresAfterOneDecrement() {
        Duration one = Duration.ofTurns(1);

        assertFalse(one.isExpired());
        assertTrue(one.decrement().isExpired());
    }

    @Test
    void decrementReturnsANewValueAndStopsAtZero() {
        Duration three = Duration.ofTurns(3);

        assertEquals(2, three.decrement().turns());
        assertEquals(3, three.turns());
        assertEquals(0, Duration.ofTurns(0).decrement().turns());
    }
}

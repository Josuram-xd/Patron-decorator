package com.rpgdecorator.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BaseCharacterTest {

    private final FakeTurnContext ctx = new FakeTurnContext();
    private final BaseCharacter warrior =
            new BaseCharacter("hero", "Guerrero", Side.HERO, new Stats(120, 14, 8, 4, 10));

    @Test
    void startsAtFullHealthWithItsBaseStats() {
        assertEquals(120, warrior.currentHealth());
        assertEquals(new Stats(120, 14, 8, 4, 10), warrior.stats());
        assertEquals("hero", warrior.id());
        assertEquals(Side.HERO, warrior.side());
    }

    @Test
    void changeHealthNeverGoesBelowZero() {
        warrior.changeHealth(-500, 120);

        assertEquals(0, warrior.currentHealth());
    }

    @Test
    void changeHealthNeverExceedsTheEffectiveMaxHealth() {
        warrior.changeHealth(-50, 120);
        warrior.changeHealth(500, 120);

        assertEquals(120, warrior.currentHealth());
    }

    @Test
    void changeHealthUsesTheEffectiveMaxHealthItIsGivenNotItsOwn() {
        warrior.changeHealth(25, 145);

        assertEquals(145, warrior.currentHealth());
    }

    @Test
    void takeDamageSubtractsPhysicalPlusElemental() {
        DamageResult result = warrior.takeDamage(new Damage(10, 4, "enemy", false, true), ctx);

        assertEquals(106, warrior.currentHealth());
        assertEquals(new DamageResult(14, 0, 0, false), result);
    }

    @Test
    void takeDamageReportsOnlyTheHealthActuallyLost() {
        warrior.changeHealth(-110, 120);

        DamageResult result = warrior.takeDamage(new Damage(30, 0, "enemy", false, true), ctx);

        assertEquals(0, warrior.currentHealth());
        assertEquals(10, result.taken());
    }

    @Test
    void neutralBehaviourLeavesDamageUntouchedAndCanAlwaysAct() {
        Damage damage = new Damage(14, 0, "hero", false, true);

        assertEquals(damage, warrior.modifyOutgoingDamage(damage, ctx));
        assertTrue(warrior.canAct(ctx));
        warrior.onTurnStart(ctx);
        warrior.onDamageDealt(new DamageResult(14, 0, 0, false), ctx);
        assertTrue(ctx.calls.isEmpty());
    }

    @Test
    void describeChainReturnsTheName() {
        assertEquals("Guerrero", warrior.describeChain());
    }
}

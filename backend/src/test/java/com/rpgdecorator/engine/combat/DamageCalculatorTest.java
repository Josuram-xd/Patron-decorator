package com.rpgdecorator.engine.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.FakeTurnContext;
import com.rpgdecorator.domain.ScriptedRandom;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.effects.LifestealDecorator;
import com.rpgdecorator.domain.effects.RageDecorator;
import com.rpgdecorator.domain.effects.ThornsDecorator;
import com.rpgdecorator.domain.equipment.DragonArmorDecorator;
import com.rpgdecorator.domain.equipment.FireRingDecorator;
import com.rpgdecorator.domain.equipment.SwordDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {

    private static final boolean CRIT = true;
    private static final boolean NO_CRIT = false;
    private static final boolean EVADE = true;
    private static final boolean NO_EVADE = false;

    private final DamageCalculator calculator = new DamageCalculator();
    private final FakeTurnContext ctx = new FakeTurnContext();
    private final BaseCharacter warrior = new BaseCharacter("hero", "Guerrero", Side.HERO, new Stats(120, 14, 8, 4, 10));
    private final BaseCharacter skeleton =
            new BaseCharacter("enemy", "Esqueleto", Side.ENEMY, new Stats(100, 13, 7, 3, 5));

    private ScriptedRandom script(boolean... outcomes) {
        ScriptedRandom random = new ScriptedRandom(outcomes);
        ctx.random = random;
        return random;
    }

    @Test
    void normalHitIsAttackMinusHalfTheDefense() {
        script(NO_CRIT, NO_EVADE);

        DamageResult result = calculator.attack(warrior, skeleton, 1.0, ctx);

        assertEquals(new DamageResult(11, 0, 0, false), result);
        assertEquals(89, skeleton.currentHealth());
        assertEquals(List.of(new CombatEvent.DamageDealt("enemy", 11, DamageType.PHYSICAL, false)), ctx.events);
    }

    @Test
    void criticalHitMultipliesTheRawDamageByOneAndAHalfRoundingDown() {
        ScriptedRandom random = script(CRIT, NO_EVADE);
        BaseCharacter attacker = new BaseCharacter("hero", "Arquero", Side.HERO, new Stats(95, 15, 5, 10, 20));

        DamageResult result = calculator.attack(attacker, skeleton, 1.0, ctx);

        assertEquals(19, result.taken());
        assertEquals(List.of(new CombatEvent.DamageDealt("enemy", 19, DamageType.PHYSICAL, true)), ctx.events);
        assertEquals(List.of(20, 6), random.askedPercents);
    }

    @Test
    void multiplierIsAppliedToTheAttackAndRounded() {
        script(NO_CRIT, NO_EVADE);
        BaseCharacter mage = new BaseCharacter("hero", "Mago", Side.HERO, new Stats(80, 18, 4, 6, 10));

        assertEquals(11, calculator.attack(mage, skeleton, 0.8, ctx).taken());
    }

    @Test
    void evasionChanceIsTwiceTheSpeedCappedAtTwentyFive() {
        ScriptedRandom random = script(NO_CRIT, EVADE);
        BaseCharacter wolf = new BaseCharacter("enemy", "Lobo", Side.ENEMY, new Stats(60, 12, 2, 20, 15));

        DamageResult result = calculator.attack(warrior, wolf, 1.0, ctx);

        assertEquals(new DamageResult(0, 0, 0, true), result);
        assertEquals(60, wolf.currentHealth());
        assertEquals(List.of(new CombatEvent.Evaded("enemy")), ctx.events);
        assertEquals(List.of(10, 25), random.askedPercents);
    }

    @Test
    void mitigationNeverGoesBelowOne() {
        script(NO_CRIT, NO_EVADE);
        BaseCharacter slime = new BaseCharacter("enemy", "Slime", Side.ENEMY, new Stats(90, 8, 4, 2, 0));
        Combatant armored = new DragonArmorDecorator(warrior);

        assertEquals(1, calculator.attack(slime, armored, 0.5, ctx).taken());
        assertEquals(119, warrior.currentHealth());
    }

    @Test
    void elementalDamageIsNotMitigated() {
        script(NO_CRIT, NO_EVADE);
        BaseCharacter golem = new BaseCharacter("enemy", "Golem de piedra", Side.ENEMY, new Stats(160, 15, 14, 1, 0));

        DamageResult result = calculator.attack(new FireRingDecorator(warrior), golem, 1.0, ctx);

        assertEquals(7 + 4, result.taken());
    }

    @Test
    void effectiveStatsOfBothOuterChainsAreUsed() {
        script(NO_CRIT, NO_EVADE);
        Combatant attacker = new RageDecorator(new SwordDecorator(warrior));
        Combatant target = new DragonArmorDecorator(skeleton);

        assertEquals(30 - 17 / 2, calculator.attack(attacker, target, 1.0, ctx).taken());
    }

    @Test
    void reflectedDamageIsSentBackToTheAttackerAsDirectDamage() {
        script(NO_CRIT, NO_EVADE);

        DamageResult result = calculator.attack(warrior, new ThornsDecorator(skeleton), 1.0, ctx);

        assertEquals(3, result.reflected());
        assertEquals(List.of(new FakeTurnContext.DirectDamage("hero", 3, DamageType.REFLECTED, null)),
                ctx.directDamages);
    }

    @Test
    void attackerIsToldAboutTheDamageDealtSoLifestealCanHeal() {
        script(NO_CRIT, NO_EVADE);

        calculator.attack(new LifestealDecorator(warrior), skeleton, 1.0, ctx);

        assertEquals(List.of(new FakeTurnContext.Heal("hero", 3, "lifesteal")), ctx.heals);
    }

    @Test
    void evadedHitDoesNotTriggerLifesteal() {
        script(NO_CRIT, EVADE);

        calculator.attack(new LifestealDecorator(warrior), skeleton, 1.0, ctx);

        assertTrue(ctx.heals.isEmpty());
    }
}

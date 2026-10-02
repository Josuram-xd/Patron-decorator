package com.rpgdecorator.domain.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.FakeTurnContext;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.equipment.DragonArmorDecorator;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectDecoratorsTest {

    private static final Stats WARRIOR = new Stats(120, 14, 8, 4, 10);

    private final FakeTurnContext ctx = new FakeTurnContext();
    private final BaseCharacter base = new BaseCharacter("hero", "Guerrero", Side.HERO, WARRIOR);

    private static Damage hit(int physical) {
        return new Damage(physical, 0, "enemy", false, true);
    }

    @Test
    void poisonAsksForDirectDamageOnTurnStartAndThenDelegates() {
        new PoisonDecorator(new RegenerationDecorator(base)).onTurnStart(ctx);

        assertEquals(List.of(new FakeTurnContext.DirectDamage("hero", 6, DamageType.POISON, "poison")),
                ctx.directDamages);
        assertEquals(List.of("directDamage:poison", "heal:regeneration"), ctx.calls);
        assertEquals(120, base.currentHealth());
    }

    @Test
    void regenerationAsksForAHealOnTurnStartAndThenDelegates() {
        new RegenerationDecorator(new PoisonDecorator(base)).onTurnStart(ctx);

        assertEquals(List.of(new FakeTurnContext.Heal("hero", 8, "regeneration")), ctx.heals);
        assertEquals(List.of("heal:regeneration", "directDamage:poison"), ctx.calls);
    }

    @Test
    void shieldAbsorbsUpToItsAbsorptionAndDelegatesTheRest() {
        ShieldDecorator shield = new ShieldDecorator(base);

        DamageResult result = shield.takeDamage(hit(30), ctx);

        assertEquals(new DamageResult(10, 20, 0, false), result);
        assertEquals(110, base.currentHealth());
        assertTrue(shield.shouldBeRemoved());
        assertEquals(0, shield.absorption());
        assertTrue(ctx.events.isEmpty());
    }

    @Test
    void shieldThatStillHasAbsorptionStays() {
        ShieldDecorator shield = new ShieldDecorator(base);

        DamageResult result = shield.takeDamage(hit(8), ctx);

        assertEquals(new DamageResult(0, 8, 0, false), result);
        assertEquals(12, shield.absorption());
        assertEquals(120, base.currentHealth());
        assertFalse(shield.shouldBeRemoved());
    }

    @Test
    void shieldRefreshAddsAbsorptionCappedAtFortyAndResetsDuration() {
        ShieldDecorator shield = new ShieldDecorator(base);
        shield.advanceTurn();
        shield.advanceTurn();
        shield.takeDamage(hit(5), ctx);

        shield.refresh(new ShieldDecorator(base));
        assertEquals(35, shield.absorption());
        assertEquals(3, shield.duration().turns());

        shield.refresh(new ShieldDecorator(base));
        assertEquals(40, shield.absorption());
    }

    @Test
    void shieldCopyKeepsItsRemainingAbsorption() {
        ShieldDecorator shield = new ShieldDecorator(base);
        shield.takeDamage(hit(5), ctx);

        EffectDecorator copy = shield.copyOnto(base);

        assertEquals(15, ((ShieldDecorator) copy).absorption());
    }

    @Test
    void thornsReflectThirtyPercentOfTheDamageTaken() {
        DamageResult result = new ThornsDecorator(base).takeDamage(hit(20), ctx);

        assertEquals(new DamageResult(20, 0, 6, false), result);
        assertEquals(100, base.currentHealth());
    }

    @Test
    void thornsReflectNothingWhenTheDamageIsNotReflectable() {
        DamageResult result = new ThornsDecorator(base).takeDamage(new Damage(20, 0, "enemy", false, false), ctx);

        assertEquals(0, result.reflected());
        assertEquals(20, result.taken());
    }

    @Test
    void thornsUnderAShieldReflectOnlyWhatGotPastTheShield() {
        Combatant chain = new ShieldDecorator(new ThornsDecorator(base));

        DamageResult result = chain.takeDamage(hit(30), ctx);

        assertEquals(new DamageResult(10, 20, 3, false), result);
    }

    @Test
    void rageMultipliesAttackAndReducesDefenseRoundingDown() {
        Stats stats = new RageDecorator(new BaseCharacter("enemy", "Lobo", Side.ENEMY, new Stats(60, 13, 5, 12, 15)))
                .stats();

        assertEquals(19, stats.attack());
        assertEquals(3, stats.defense());
        assertEquals(new Stats(120, 21, 5, 4, 10), new RageDecorator(base).stats());
    }

    @Test
    void guardMultipliesDefenseRoundingDown() {
        Stats stats = new GuardDecorator(new BaseCharacter("enemy", "Lobo", Side.ENEMY, new Stats(60, 12, 5, 12, 15)))
                .stats();

        assertEquals(7, stats.defense());
        assertEquals(WARRIOR.withDefense(12), new GuardDecorator(base).stats());
    }

    @Test
    void frozenCannotActNoMatterWhatIsUnderneath() {
        assertFalse(new FrozenDecorator(base).canAct(ctx));
        assertFalse(new FrozenDecorator(new RageDecorator(new DragonArmorDecorator(base))).canAct(ctx));
        assertFalse(new RageDecorator(new FrozenDecorator(base)).canAct(ctx));
    }

    @Test
    void lifestealHealsThirtyPercentOfTheDamageDealt() {
        new LifestealDecorator(base).onDamageDealt(new DamageResult(20, 0, 0, false), ctx);

        assertEquals(List.of(new FakeTurnContext.Heal("hero", 6, "lifesteal")), ctx.heals);
    }

    @Test
    void lifestealDoesNotHealWhenNoDamageWasDealt() {
        new LifestealDecorator(base).onDamageDealt(new DamageResult(0, 20, 0, false), ctx);

        assertTrue(ctx.heals.isEmpty());
    }

    @Test
    void effectsExposeTheBaseDurationsOfTheCatalog() {
        assertEquals(3, new PoisonDecorator(base).duration().turns());
        assertEquals(3, new RegenerationDecorator(base).duration().turns());
        assertEquals(3, new ShieldDecorator(base).duration().turns());
        assertEquals(3, new ThornsDecorator(base).duration().turns());
        assertEquals(2, new RageDecorator(base).duration().turns());
        assertEquals(1, new GuardDecorator(base).duration().turns());
        assertEquals(1, new FrozenDecorator(base).duration().turns());
        assertEquals(3, new LifestealDecorator(base).duration().turns());
    }
}

package com.rpgdecorator.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.effects.FrozenDecorator;
import com.rpgdecorator.domain.effects.PoisonDecorator;
import com.rpgdecorator.domain.effects.RageDecorator;
import com.rpgdecorator.domain.equipment.DragonArmorDecorator;
import com.rpgdecorator.domain.equipment.LifeAmuletDecorator;
import com.rpgdecorator.domain.equipment.SwordDecorator;
import org.junit.jupiter.api.Test;

/** One test per pitfall of design §3: what goes wrong with a naive Decorator and how we avoid it. */
class DecoratorPitfallsTest {

    private final FakeTurnContext ctx = new FakeTurnContext();
    private final BaseCharacter base = new BaseCharacter("hero", "Guerrero", Side.HERO, new Stats(120, 14, 8, 4, 10));

    // §3.1 The base character has no idea it is decorated: anything it computes from `this`
    // misses the layers. That is why effective stats are always read on the OUTER reference
    // and the effective max health is passed in as a parameter.
    @Test
    void selfCallInBaseIgnoresArmor() {
        Combatant outer = new LifeAmuletDecorator(new DragonArmorDecorator(base));

        assertEquals(8, base.stats().defense());
        assertEquals(18, outer.stats().defense());

        outer.changeHealth(25, outer.stats().maxHealth());
        assertEquals(145, outer.currentHealth());
    }

    // §3.2 `wrapped` is final, so a layer cannot be unhooked. Removing one from the middle means
    // rebuilding the chain with copyOnto: the copies keep their state and the base instance,
    // which owns the health, is never replaced.
    @Test
    void removingMiddleLayerKeepsOuterState() {
        SwordDecorator sword = new SwordDecorator(base);
        PoisonDecorator poison = new PoisonDecorator(sword);
        RageDecorator rage = new RageDecorator(poison);
        rage.advanceTurn();
        rage.advanceTurn();
        base.changeHealth(-30, 120);

        EffectDecorator rebuilt = rage.copyOnto(sword.copyOnto(base));

        assertEquals("Furia(Espada(Guerrero))", rebuilt.describeChain());
        assertEquals(1, rebuilt.duration().turns());
        assertSame(base, ((EffectDecorator) rebuilt.wrapped()).wrapped());
        assertEquals(90, rebuilt.currentHealth());
    }

    // §3.3 instanceof only sees the outermost layer. Asking "is it frozen?" requires walking
    // the chain through wrapped(), which is what EffectManager.hasEffect does for everyone else.
    @Test
    void instanceofOnlySeesTheOutermostLayer() {
        Combatant outer = new RageDecorator(new FrozenDecorator(base));

        assertFalse(outer instanceof FrozenDecorator);

        boolean frozenSomewhere = false;
        for (Combatant layer = outer; layer instanceof EffectDecorator decorator; layer = decorator.wrapped()) {
            frozenSomewhere |= decorator.effectId().equals(FrozenDecorator.ID);
        }
        assertTrue(frozenSomewhere);
    }

    // §3.4 Decorators do not commute: a multiplier outside an addition is not the same as the
    // addition outside the multiplier. The engine fixes the order (equipment inside, effects outside).
    @Test
    void wrappingOrderChangesTheEffectiveStats() {
        Combatant rageOverSword = new RageDecorator(new SwordDecorator(base));
        Combatant swordOverRage = new SwordDecorator(new RageDecorator(base));

        assertEquals(30, rageOverSword.stats().attack());
        assertEquals(27, swordOverRage.stats().attack());
    }

    // §3.5 Nothing stops the same effect from being wrapped twice, and then it acts twice.
    // Reapplying must refresh the existing layer instead of adding another one.
    @Test
    void wrappingTheSameEffectTwiceDoublesItSoReapplyingRefreshesInstead() {
        new PoisonDecorator(new PoisonDecorator(base)).onTurnStart(ctx);
        assertEquals(2, ctx.directDamages.size());

        PoisonDecorator single = new PoisonDecorator(base);
        single.advanceTurn();
        single.advanceTurn();
        single.refresh(new PoisonDecorator(base));

        assertEquals("Envenenado(Guerrero)", single.describeChain());
        assertEquals(3, single.duration().turns());
    }

    // §3.6 Every layer is a different object, so reference equality is useless to tell who a
    // combatant is. All layers answer with the id of the base, and lookups go by id.
    @Test
    void everyLayerAnswersWithTheIdentityOfTheBase() {
        Combatant middle = new PoisonDecorator(new SwordDecorator(base));
        Combatant outer = new RageDecorator(middle);

        assertFalse(outer.equals(base));
        assertEquals(base.id(), middle.id());
        assertEquals(base.id(), outer.id());
        assertEquals(base.name(), outer.name());
        assertEquals(base.side(), outer.side());
    }
}

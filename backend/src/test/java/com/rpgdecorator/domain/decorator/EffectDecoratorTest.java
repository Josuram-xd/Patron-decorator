package com.rpgdecorator.domain.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.FakeTurnContext;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import org.junit.jupiter.api.Test;

class EffectDecoratorTest {

    /** Overrides nothing: whatever it does comes from {@link EffectDecorator} itself. */
    private static final class PassThroughDecorator extends EffectDecorator {

        PassThroughDecorator(Combatant wrapped, String label, Duration duration) {
            super(wrapped, "pass_through", label, Category.BUFF, duration);
        }

        private PassThroughDecorator(Combatant newWrapped, PassThroughDecorator source) {
            super(newWrapped, source);
        }

        @Override
        public EffectDecorator copyOnto(Combatant newWrapped) {
            return new PassThroughDecorator(newWrapped, this);
        }
    }

    private static final Stats WARRIOR_STATS = new Stats(120, 14, 8, 4, 10);

    private final FakeTurnContext ctx = new FakeTurnContext();

    private static BaseCharacter warrior() {
        return new BaseCharacter("hero", "Guerrero", Side.HERO, WARRIOR_STATS);
    }

    private static PassThroughDecorator wrap(Combatant inner, String label, int turns) {
        return new PassThroughDecorator(inner, label, Duration.ofTurns(turns));
    }

    @Test
    void emptyDecoratorIsIndistinguishableFromTheBase() {
        BaseCharacter plain = warrior();
        BaseCharacter base = warrior();
        Combatant decorated = wrap(base, "Vacío", 3);
        Damage outgoing = new Damage(14, 0, "hero", false, true);
        Damage incoming = new Damage(10, 4, "enemy", false, true);

        assertEquals(plain.id(), decorated.id());
        assertEquals(plain.name(), decorated.name());
        assertEquals(plain.side(), decorated.side());
        assertEquals(plain.stats(), decorated.stats());
        assertEquals(plain.currentHealth(), decorated.currentHealth());
        assertEquals(plain.modifyOutgoingDamage(outgoing, ctx), decorated.modifyOutgoingDamage(outgoing, ctx));
        assertEquals(plain.canAct(ctx), decorated.canAct(ctx));
        assertEquals(plain.takeDamage(incoming, ctx), decorated.takeDamage(incoming, ctx));
        assertEquals(plain.currentHealth(), decorated.currentHealth());

        plain.changeHealth(5, 120);
        decorated.changeHealth(5, 120);
        assertEquals(plain.currentHealth(), decorated.currentHealth());
        assertEquals(base.currentHealth(), decorated.currentHealth());

        decorated.onTurnStart(ctx);
        decorated.onDamageDealt(new DamageResult(14, 0, 0, false), ctx);
        assertTrue(ctx.calls.isEmpty());
        assertTrue(ctx.events.isEmpty());
    }

    @Test
    void idIsTheBaseIdAtAnyDepth() {
        Combatant chain = warrior();
        for (int depth = 0; depth < 5; depth++) {
            chain = wrap(chain, "Capa" + depth, 3);
            assertEquals("hero", chain.id());
        }
    }

    @Test
    void describeChainNestsLabelsFromTheOutsideIn() {
        Combatant chain = wrap(wrap(warrior(), "Espada", 3), "Furia", 2);

        assertEquals("Furia(Espada(Guerrero))", chain.describeChain());
    }

    @Test
    void firstAdvanceTurnDoesNotDecrementButTheNextOnesDo() {
        EffectDecorator effect = wrap(warrior(), "Furia", 2);

        effect.advanceTurn();
        assertEquals(2, effect.duration().turns());

        effect.advanceTurn();
        assertEquals(1, effect.duration().turns());
        assertFalse(effect.shouldBeRemoved());

        effect.advanceTurn();
        assertTrue(effect.shouldBeRemoved());
    }

    @Test
    void refreshTakesTheDurationOfTheIncomingEffect() {
        BaseCharacter base = warrior();
        EffectDecorator worn = wrap(base, "Furia", 2);
        worn.advanceTurn();
        worn.advanceTurn();

        worn.refresh(wrap(base, "Furia", 2));

        assertEquals(2, worn.duration().turns());
    }

    @Test
    void copyOntoKeepsTheStateAndWrapsTheNewCombatant() {
        BaseCharacter base = warrior();
        EffectDecorator original = wrap(wrap(base, "Envenenado", 3), "Furia", 2);
        original.advanceTurn();
        original.advanceTurn();

        EffectDecorator copy = original.copyOnto(base);

        assertNotSame(original, copy);
        assertSame(base, copy.wrapped());
        assertEquals(1, copy.duration().turns());
        assertEquals("Furia(Guerrero)", copy.describeChain());
        copy.advanceTurn();
        assertTrue(copy.shouldBeRemoved());
    }

    @Test
    void permanentLayerNeverAsksToBeRemoved() {
        EffectDecorator equipment = new PassThroughDecorator(warrior(), "Espada", Duration.PERMANENT);
        for (int turn = 0; turn < 50; turn++) {
            equipment.advanceTurn();
        }

        assertFalse(equipment.shouldBeRemoved());
    }
}

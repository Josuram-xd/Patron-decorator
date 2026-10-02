package com.rpgdecorator.scenarios;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.domain.effects.PoisonDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.CombatEvent.DamageDealt;
import com.rpgdecorator.domain.event.CombatEvent.TurnSkipped;
import com.rpgdecorator.domain.event.RemovalReason;
import com.rpgdecorator.engine.combat.Action;
import com.rpgdecorator.engine.combat.Combat;
import com.rpgdecorator.engine.combat.CombatEngine;
import com.rpgdecorator.engine.combat.DamageCalculator;
import com.rpgdecorator.engine.combat.LoggedEvent;
import com.rpgdecorator.engine.combat.TurnContextImpl;
import com.rpgdecorator.engine.effects.EffectManager;
import com.rpgdecorator.engine.effects.Layer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecoratorScenarioTest {

    private static final long SCENARIO_SEED = 42L;
    private static final String HERO_ID = BaseCharacter.HERO_ID;
    private static final String ENEMY_ID = BaseCharacter.ENEMY_ID;

    @Test
    void poisonDealsFiveDamageForThreeTurnsThenExpires() {
        EffectManager effects = new EffectManager();
        BaseCharacter hero = new BaseCharacter(HERO_ID, "Guerrero", Side.HERO,
                new Stats(100, 10, 0, 0, 0));
        Combat combat = newCombat(hero);
        TurnContextImpl context = context(combat);
        combat.setHero(new PoisonDecorator(hero, 5, 3));
        combat.setHero(effects.advanceTurn(combat.hero(), context));

        for (int turn = 1; turn <= 3; turn++) {
            combat.hero().onTurnStart(context);
            combat.setHero(effects.advanceTurn(combat.hero(), context));
            assertEquals(100 - turn * 5, combat.hero().currentHealth());
        }

        assertFalse(effects.hasEffect(combat.hero(), "poison"));
        List<DamageDealt> poisonDamage = combat.eventsSince(0).stream()
                .map(LoggedEvent::event)
                .filter(DamageDealt.class::isInstance)
                .map(DamageDealt.class::cast)
                .toList();
        assertEquals(List.of(
                new DamageDealt(HERO_ID, 5, DamageType.POISON, false),
                new DamageDealt(HERO_ID, 5, DamageType.POISON, false),
                new DamageDealt(HERO_ID, 5, DamageType.POISON, false)), poisonDamage);
    }

    @Test
    void expiringMiddleEffectPreservesRageAndItsRemainingTurns() {
        EffectManager effects = new EffectManager();
        Combat combat = newCombat(BaseCharacter.hero(HeroClassCatalog.get("warrior")));
        TurnContextImpl context = context(combat);
        Combatant chain = effects.equip(combat.hero(), List.of("sword"));
        chain = effects.apply(chain, "poison", false, context);
        chain = effects.advanceTurn(chain, context);
        chain = effects.apply(chain, "rage", true, context);
        combat.setHero(chain);

        assertEquals("Furia(Envenenado(Espada(Guerrero)))", combat.hero().describeChain());
        combat.setHero(effects.advanceTurn(combat.hero(), context));
        combat.setHero(effects.advanceTurn(combat.hero(), context));

        assertEquals("Furia(Espada(Guerrero))", combat.hero().describeChain());
        Layer rage = effects.layers(combat.hero()).stream()
                .filter(layer -> layer.id().equals("rage"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, rage.turnsRemaining());
        assertFalse(effects.hasEffect(combat.hero(), "poison"));
        assertTrue(combat.eventsSince(0).stream().map(LoggedEvent::event)
                .anyMatch(event -> event instanceof CombatEvent.EffectRemoved removed
                        && removed.effectId().equals("poison")
                        && removed.reason() == RemovalReason.EXPIRED));
    }

    @Test
    void shieldAbsorbsTwentyDamageBeforeHealthAndIsRemovedWhenDepleted() {
        EffectManager effects = new EffectManager();
        BaseCharacter hero = new BaseCharacter(HERO_ID, "Guerrero", Side.HERO,
                new Stats(100, 10, 0, 0, 0));
        Combat combat = newCombat(hero);
        TurnContextImpl context = context(combat);
        combat.setHero(effects.apply(combat.hero(), "shield", context));

        var damageResult = combat.hero().takeDamage(new Damage(30, 0, ENEMY_ID, false, true), context);
        assertEquals(20, damageResult.absorbed());
        assertEquals(10, damageResult.taken());
        assertEquals(90, combat.hero().currentHealth());

        combat.setHero(effects.advanceTurn(combat.hero(), context));
        assertEquals("Guerrero", combat.hero().describeChain());
        assertFalse(effects.hasEffect(combat.hero(), "shield"));
    }

    @Test
    void silencePurgesTemporaryEffectsButRetainsDragonArmor() {
        EffectManager effects = new EffectManager();
        Combat combat = newCombat(BaseCharacter.hero(HeroClassCatalog.get("mage")));
        TurnContextImpl context = context(combat);
        Combatant chain = effects.equip(combat.hero(), List.of("dragon_armor"));
        chain = effects.apply(chain, "poison", false, context);
        chain = effects.apply(chain, "rage", true, context);

        combat.setHero(effects.purge(chain, context));

        assertEquals("Armadura de dragón(Mago)", combat.hero().describeChain());
        assertFalse(effects.hasEffect(combat.hero(), "poison"));
        assertFalse(effects.hasEffect(combat.hero(), "rage"));
    }

    @Test
    void frozenRemovesRageAndCausesTheEnemyTurnToBeSkipped() {
        EffectManager effects = new EffectManager();
        Combat combat = newCombat(BaseCharacter.hero(HeroClassCatalog.get("warrior")));
        TurnContextImpl context = context(combat);
        Combatant enemy = combat.enemy();
        enemy = effects.apply(enemy, "rage", true, context);
        enemy = effects.apply(enemy, "frozen", false, context);
        combat.setEnemy(enemy);

        assertEquals("Congelado(Goblin)", combat.enemy().describeChain());
        assertFalse(effects.hasEffect(combat.enemy(), "rage"));
        int heroHealthBeforeRound = combat.hero().currentHealth();
        int mark = combat.lastSeq();
        CombatEngine engine = new CombatEngine(effects, new DamageCalculator(),
                (currentCombat, random) -> Action.attack());

        engine.executeRound(combat, Action.attack(), new SeededRandomSource(SCENARIO_SEED));

        List<CombatEvent> roundEvents = combat.eventsSince(mark).stream()
                .map(LoggedEvent::event)
                .toList();
        assertTrue(roundEvents.contains(new TurnSkipped(ENEMY_ID, "frozen")));
        assertFalse(roundEvents.stream().anyMatch(event ->
                event instanceof CombatEvent.ActionTaken action && action.actorId().equals(ENEMY_ID)));
        assertEquals(heroHealthBeforeRound, combat.hero().currentHealth());
    }

    private static Combat newCombat(Combatant hero) {
        return new Combat("scenario-combat", hero, BaseCharacter.enemy(EnemyCatalog.get("goblin")),
                HeroClassCatalog.get("warrior").abilities(), EnemyCatalog.get("goblin"));
    }

    private static TurnContextImpl context(Combat combat) {
        return new TurnContextImpl(combat, new SeededRandomSource(SCENARIO_SEED));
    }

    private static final class SeededRandomSource implements RandomSource {

        private final Random random;

        private SeededRandomSource(long seed) {
            random = new Random(seed);
        }

        @Override
        public int nextInt(int minInclusive, int maxInclusive) {
            if (minInclusive > maxInclusive) {
                throw new IllegalArgumentException("Minimum must not exceed maximum");
            }
            return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
        }

        @Override
        public boolean chance(int percent) {
            return random.nextInt(100) < percent;
        }
    }
}

package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.HeroClass;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.engine.effects.EffectManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnContextImplTest {

    private static final String HERO = BaseCharacter.HERO_ID;
    private static final String ENEMY = BaseCharacter.ENEMY_ID;

    private final EffectManager effects = new EffectManager();
    private final FixedRandom random = new FixedRandom();

    private final HeroClass warrior = HeroClassCatalog.get("warrior");   // base maxHealth 120
    private final BaseCharacter heroBase = BaseCharacter.hero(warrior);
    private final BaseCharacter enemyBase = BaseCharacter.enemy(EnemyCatalog.get("goblin"));

    private Combat combatWith(Combatant hero) {
        return new Combat("c1", hero, enemyBase, warrior.abilities(), EnemyCatalog.get("goblin"));
    }

    private static List<CombatEvent> rawEvents(Combat combat) {
        return combat.events().stream().map(LoggedEvent::event).toList();
    }

    @Test
    void healUsesTheEffectiveMaxHealthOfTheOuterChainSoLifeAmuletAllowsHealingAboveBase() {
        Combat combat = combatWith(effects.equip(heroBase, List.of("life_amulet")));
        TurnContextImpl ctx = new TurnContextImpl(combat, random);
        assertEquals(120, combat.hero().currentHealth());
        assertEquals(145, combat.hero().stats().maxHealth());

        ctx.heal(HERO, 10, "regeneration");
        assertEquals(130, combat.hero().currentHealth());

        ctx.heal(HERO, 10, "regeneration");
        ctx.heal(HERO, 10, "regeneration");
        assertEquals(145, combat.hero().currentHealth(), "capped at the effective maxHealth");

        assertEquals(List.of(
                new CombatEvent.Healed(HERO, 10, "regeneration"),
                new CombatEvent.Healed(HERO, 10, "regeneration"),
                new CombatEvent.Healed(HERO, 5, "regeneration")), rawEvents(combat));
    }

    @Test
    void healWithoutAmuletIsCappedAtBaseMaxHealth() {
        Combat combat = combatWith(heroBase);
        TurnContextImpl ctx = new TurnContextImpl(combat, random);

        ctx.heal(HERO, 10, "lifesteal");

        assertEquals(120, heroBase.currentHealth());
        assertEquals(List.of(new CombatEvent.Healed(HERO, 0, "lifesteal")), rawEvents(combat),
                "healing at full health still emits HEAL with the actual amount 0");
    }

    @Test
    void contextResolvesTheOuterChainByIdAfterTheChainWasReplaced() {
        Combat combat = combatWith(heroBase);
        TurnContextImpl ctx = new TurnContextImpl(combat, random);

        // The chain changes after the context was created: the context must see the new outer reference.
        combat.setHero(effects.equip(heroBase, List.of("life_amulet")));
        ctx.heal(HERO, 10, "regeneration");
        assertEquals(130, heroBase.currentHealth());

        combat.replace(effects.apply(combat.hero(), "poison", ctx));
        assertTrue(effects.hasEffect(combat.hero(), "poison"));
        combat.hero().onTurnStart(ctx);   // Poison(LifeAmulet(base)) asks ctx.directDamage(hero, 6, POISON)

        assertEquals(124, heroBase.currentHealth());
        assertEquals(new CombatEvent.DamageDealt(HERO, 6, DamageType.POISON, false),
                combat.events().getLast().event());
    }

    @Test
    void regenerationDecoratorHealsAboveBaseMaxHealthThroughTheContext() {
        Combat combat = combatWith(effects.equip(heroBase, List.of("life_amulet")));
        TurnContextImpl ctx = new TurnContextImpl(combat, random);
        combat.replace(effects.apply(combat.hero(), "regeneration", ctx));

        combat.hero().onTurnStart(ctx);

        assertEquals(128, heroBase.currentHealth());
        assertEquals(new CombatEvent.Healed(HERO, 8, "regeneration"), combat.events().getLast().event());
    }

    @Test
    void directDamageIgnoresDefenseAndShield() {
        Combat combat = combatWith(effects.equip(heroBase, List.of("dragon_armor")));
        TurnContextImpl ctx = new TurnContextImpl(combat, random);
        combat.replace(effects.apply(combat.hero(), "shield", ctx));
        int before = heroBase.currentHealth();

        ctx.directDamage(HERO, 7, DamageType.REFLECTED, "thorns");

        assertEquals(before - 7, heroBase.currentHealth());
        assertEquals(new CombatEvent.DamageDealt(HERO, 7, DamageType.REFLECTED, false),
                combat.events().getLast().event());
    }

    @Test
    void directDamageReportsTheHealthActuallyLostAndDoesNotEmitDeath() {
        Combat combat = combatWith(heroBase);
        TurnContextImpl ctx = new TurnContextImpl(combat, random);
        enemyBase.changeHealth(-(enemyBase.currentHealth() - 4), enemyBase.stats().maxHealth());

        ctx.directDamage(ENEMY, 6, DamageType.POISON, "poison");

        assertEquals(0, enemyBase.currentHealth());
        assertEquals(List.of(new CombatEvent.DamageDealt(ENEMY, 4, DamageType.POISON, false)), rawEvents(combat),
                "DEATH is emitted by the engine, not by the context");
    }

    @Test
    void deadCombatantIsNeitherDamagedNorHealed() {
        Combat combat = combatWith(heroBase);
        TurnContextImpl ctx = new TurnContextImpl(combat, random);
        enemyBase.changeHealth(-1000, enemyBase.stats().maxHealth());

        ctx.heal(ENEMY, 8, "regeneration");
        ctx.directDamage(ENEMY, 6, DamageType.POISON, "poison");

        assertEquals(0, enemyBase.currentHealth());
        assertEquals(List.of(), combat.events());
    }

    @Test
    void emitLogsWithIncreasingSeqAndTheCurrentRound() {
        Combat combat = combatWith(heroBase);
        TurnContextImpl ctx = new TurnContextImpl(combat, random);

        ctx.emit(new CombatEvent.TurnStarted(HERO));
        ctx.emit(new CombatEvent.TurnEnded(HERO));
        combat.nextRound();
        ctx.emit(new CombatEvent.TurnStarted(ENEMY));

        assertEquals(2, ctx.round());
        assertEquals(List.of(
                new LoggedEvent(1, 1, new CombatEvent.TurnStarted(HERO)),
                new LoggedEvent(2, 1, new CombatEvent.TurnEnded(HERO)),
                new LoggedEvent(3, 2, new CombatEvent.TurnStarted(ENEMY))), combat.events());
    }

    @Test
    void randomIsTheInjectedSource() {
        TurnContextImpl ctx = new TurnContextImpl(combatWith(heroBase), random);
        assertSame(random, ctx.random());
    }

    @Test
    void unknownTargetAndNegativeAmountsAreRejected() {
        TurnContextImpl ctx = new TurnContextImpl(combatWith(heroBase), random);
        assertThrows(IllegalArgumentException.class, () -> ctx.heal("nobody", 5, "regeneration"));
        assertThrows(IllegalArgumentException.class, () -> ctx.directDamage(HERO, -1, DamageType.POISON, "poison"));
        assertThrows(IllegalArgumentException.class, () -> ctx.heal(HERO, -1, "regeneration"));
    }
}

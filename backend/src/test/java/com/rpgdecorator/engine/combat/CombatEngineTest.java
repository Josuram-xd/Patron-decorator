package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.catalog.AiCondition;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.EffectApplication;
import com.rpgdecorator.domain.catalog.EnemyAbility;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.catalog.HeroClass;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.CombatEvent.Absorbed;
import com.rpgdecorator.domain.event.CombatEvent.ActionTaken;
import com.rpgdecorator.domain.event.CombatEvent.CombatEnded;
import com.rpgdecorator.domain.event.CombatEvent.DamageDealt;
import com.rpgdecorator.domain.event.CombatEvent.Death;
import com.rpgdecorator.domain.event.CombatEvent.EffectApplied;
import com.rpgdecorator.domain.event.CombatEvent.EffectRemoved;
import com.rpgdecorator.domain.event.CombatEvent.Evaded;
import com.rpgdecorator.domain.event.CombatEvent.Healed;
import com.rpgdecorator.domain.event.CombatEvent.TurnEnded;
import com.rpgdecorator.domain.event.CombatEvent.TurnSkipped;
import com.rpgdecorator.domain.event.CombatEvent.TurnStarted;
import com.rpgdecorator.domain.event.CombatResult;
import com.rpgdecorator.domain.event.RemovalReason;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.effects.EffectManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatEngineTest {

    private static final String HERO = BaseCharacter.HERO_ID;
    private static final String ENEMY = BaseCharacter.ENEMY_ID;

    private final EffectManager effects = new EffectManager();
    private final DamageCalculator calculator = new DamageCalculator();

    /** Counts how many times the enemy decided. */
    private final AtomicInteger enemyDecisions = new AtomicInteger();

    // ------------------------------------------------------------------ helpers

    private BiFunction<Combat, RandomSource, Action> always(Action action) {
        return (combat, random) -> {
            enemyDecisions.incrementAndGet();
            return action;
        };
    }

    private CombatEngine engine(Action enemyAction) {
        return new CombatEngine(effects, calculator, always(enemyAction));
    }

    private static Combat warriorVsGoblin() {
        HeroClass warrior = HeroClassCatalog.get("warrior");          // 120 HP, atk 14, def 8
        EnemyDefinition goblin = EnemyCatalog.get("goblin");          // 70 HP, atk 11, def 3
        return new Combat(BaseCharacter.hero(warrior), BaseCharacter.enemy(goblin), warrior.abilities(), goblin);
    }

    /** Hero and enemy with no crit, speed 0 and plenty of health, unless given. */
    private static Combat custom(Stats heroStats, List<Ability> heroAbilities, Stats enemyStats,
                                 List<EnemyAbility> enemyAbilities) {
        BaseCharacter hero = new BaseCharacter(HERO, "Heroe", Side.HERO, heroStats);
        BaseCharacter enemy = new BaseCharacter(ENEMY, "Muñeco", Side.ENEMY, enemyStats);
        EnemyDefinition definition = new EnemyDefinition("dummy", "Muñeco", 1, enemyStats, enemyAbilities);
        return new Combat(hero, enemy, heroAbilities, definition);
    }

    private static Stats stats(int maxHealth, int attack, int defense) {
        return new Stats(maxHealth, attack, defense, 0, 0);
    }

    /** Applies an effect from outside a turn (setup), as the opponent would. */
    private void applyFromOpponent(Combat combat, String targetId, String effectId) {
        TurnContextImpl ctx = new TurnContextImpl(combat, new FixedRandom());
        combat.replace(effects.apply(combat.combatant(targetId), effectId, false, ctx));
    }

    private static void setHealth(Combat combat, String id, int health) {
        Combatant c = combat.combatant(id);
        c.changeHealth(health - c.currentHealth(), c.stats().maxHealth());
        assertEquals(health, c.currentHealth());
    }

    private static List<CombatEvent> since(Combat combat, int mark) {
        return combat.eventsSince(mark).stream().map(LoggedEvent::event).toList();
    }

    private static ErrorCode rejected(CombatEngine engine, Combat combat, Action action) {
        int mark = combat.lastSeq();
        int round = combat.round();
        InvalidActionException e = assertThrows(InvalidActionException.class,
                () -> engine.executeRound(combat, action, new FixedRandom()));
        assertEquals(mark, combat.lastSeq(), "a rejected action emits nothing");
        assertEquals(round, combat.round(), "a rejected action changes nothing");
        return e.errorCode();
    }

    // ------------------------------------------------------------------ event order (design 5.3)

    @Test
    void roundEmitsEventsInTheExactOrderOfDesign53() {
        Combat combat = warriorVsGoblin();
        applyFromOpponent(combat, HERO, "poison");
        CombatEngine engine = engine(Action.attack());
        int mark = combat.lastSeq();

        engine.executeRound(combat, Action.defend(), new FixedRandom());

        assertEquals(List.of(
                new TurnStarted(HERO),
                new DamageDealt(HERO, 6, DamageType.POISON, false),        // onTurnStart
                new ActionTaken(HERO, Action.DEFEND, null),
                new EffectApplied(HERO, "guard", 1),
                new TurnEnded(HERO),                                         // guard survives: justApplied
                new TurnStarted(ENEMY),
                new ActionTaken(ENEMY, Action.ATTACK, null),
                new DamageDealt(HERO, 5, DamageType.PHYSICAL, false),       // 11 - (8 x 1.5) / 2
                new TurnEnded(ENEMY)), since(combat, mark));
        assertTrue(combat.eventsSince(mark).stream().allMatch(e -> e.round() == 1));
        assertEquals(2, combat.round());

        mark = combat.lastSeq();
        engine.executeRound(combat, Action.attack(), new FixedRandom());

        assertEquals(List.of(
                new TurnStarted(HERO),
                new DamageDealt(HERO, 6, DamageType.POISON, false),
                new ActionTaken(HERO, Action.ATTACK, null),
                new DamageDealt(ENEMY, 13, DamageType.PHYSICAL, false),     // 14 - 3 / 2
                new EffectRemoved(HERO, "guard", RemovalReason.EXPIRED),    // advanceTurn
                new TurnEnded(HERO),
                new TurnStarted(ENEMY),
                new ActionTaken(ENEMY, Action.ATTACK, null),
                new DamageDealt(HERO, 7, DamageType.PHYSICAL, false),
                new TurnEnded(ENEMY)), since(combat, mark));
        assertTrue(combat.eventsSince(mark).stream().allMatch(e -> e.round() == 2));
        assertEquals(3, combat.round());
        assertEquals(120 - 6 - 5 - 6 - 7, combat.hero().currentHealth());
        assertEquals(70 - 13, combat.enemy().currentHealth());
    }

    @Test
    void criticalHitIsFlaggedInTheDamageEvent() {
        Combat combat = warriorVsGoblin();
        int mark = combat.lastSeq();

        engine(Action.defend()).executeRound(combat, Action.attack(), new FixedRandom(true, false));

        assertEquals(new DamageDealt(ENEMY, 20, DamageType.PHYSICAL, true), since(combat, mark).get(2));
    }

    // ------------------------------------------------------------------ poison kills at turn start

    @Test
    void poisonCanKillAtTurnStartAndTheHeroDoesNotAct() {
        Combat combat = warriorVsGoblin();
        applyFromOpponent(combat, HERO, "poison");
        setHealth(combat, HERO, 6);
        CombatEngine engine = engine(Action.attack());
        int mark = combat.lastSeq();

        engine.executeRound(combat, Action.attack(), new FixedRandom());

        assertEquals(List.of(
                new TurnStarted(HERO),
                new DamageDealt(HERO, 6, DamageType.POISON, false),
                new Death(HERO),
                new TurnEnded(HERO),
                new CombatEnded(CombatResult.DEFEAT)), since(combat, mark));
        assertEquals(CombatStatus.DEFEAT, combat.status());
        assertEquals(70, combat.enemy().currentHealth());
        assertEquals(0, enemyDecisions.get());
        assertEquals(1, combat.round());
    }

    // ------------------------------------------------------------------ frozen and Pass

    @Test
    void frozenHeroCanOnlyPassAndSkipsItsTurn() {
        Combat combat = warriorVsGoblin();
        applyFromOpponent(combat, HERO, "frozen");
        CombatEngine engine = engine(Action.defend());

        assertEquals(ErrorCode.ACTION_NOT_ALLOWED, rejected(engine, combat, Action.attack()));
        assertEquals(ErrorCode.ACTION_NOT_ALLOWED, rejected(engine, combat, Action.defend()));
        assertEquals(ErrorCode.ACTION_NOT_ALLOWED, rejected(engine, combat, Action.useAbility("war_cry")));

        int mark = combat.lastSeq();
        engine.executeRound(combat, Action.pass(), new FixedRandom());

        List<CombatEvent> events = since(combat, mark);
        assertEquals(List.of(
                new TurnStarted(HERO),
                new TurnSkipped(HERO, "frozen"),
                new EffectRemoved(HERO, "frozen", RemovalReason.EXPIRED),
                new TurnEnded(HERO)), events.subList(0, 4));
        assertFalse(events.stream().anyMatch(e -> e instanceof ActionTaken a && a.actorId().equals(HERO)));
        assertFalse(effects.hasEffect(combat.hero(), "frozen"));

        // Thawed: Pass is no longer valid, acting is.
        assertEquals(ErrorCode.ACTION_NOT_ALLOWED, rejected(engine, combat, Action.pass()));
        mark = combat.lastSeq();
        engine.executeRound(combat, Action.attack(), new FixedRandom());
        assertEquals(new ActionTaken(HERO, Action.ATTACK, null), since(combat, mark).get(1));
    }

    @Test
    void passIsNotAllowedWhenTheHeroCanAct() {
        Combat combat = warriorVsGoblin();
        assertEquals(ErrorCode.ACTION_NOT_ALLOWED, rejected(engine(Action.attack()), combat, Action.pass()));
    }

    @Test
    void frozenEnemySkipsItsTurnEvenIfItDecidedAnAction() {
        Combat combat = custom(stats(200, 14, 8), List.of(), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "frozen");
        int mark = combat.lastSeq();

        engine(Action.attack()).executeRound(combat, Action.defend(), new FixedRandom());

        List<CombatEvent> events = since(combat, mark);
        int start = events.indexOf(new TurnStarted(ENEMY));
        assertEquals(List.of(
                new TurnStarted(ENEMY),
                new TurnSkipped(ENEMY, "frozen"),
                new EffectRemoved(ENEMY, "frozen", RemovalReason.EXPIRED),
                new TurnEnded(ENEMY)), events.subList(start, events.size()));
        assertEquals(200, combat.hero().currentHealth());
    }

    // ------------------------------------------------------------------ validation

    @Test
    void unknownAbilityIsAnInvalidValue() {
        Combat combat = warriorVsGoblin();
        assertEquals(ErrorCode.INVALID_VALUE,
                rejected(engine(Action.attack()), combat, Action.useAbility("fireball")));
    }

    @Test
    void abilityWithCooldownThreeUsedOnTurnOneIsAvailableAgainOnTurnFour() {
        Combat combat = warriorVsGoblin();
        CombatEngine engine = engine(Action.defend());

        engine.executeRound(combat, Action.useAbility("war_cry"), new FixedRandom());     // turn 1
        assertEquals(3, combat.cooldown(HERO, "war_cry"));

        InvalidActionException turn2 = assertThrows(InvalidActionException.class,
                () -> engine.executeRound(combat, Action.useAbility("war_cry"), new FixedRandom()));
        assertEquals(ErrorCode.ABILITY_ON_COOLDOWN, turn2.errorCode());
        assertEquals("Grito de guerra estará disponible en 2 turnos", turn2.getMessage());
        engine.executeRound(combat, Action.attack(), new FixedRandom());                    // turn 2

        InvalidActionException turn3 = assertThrows(InvalidActionException.class,
                () -> engine.executeRound(combat, Action.useAbility("war_cry"), new FixedRandom()));
        assertEquals(ErrorCode.ABILITY_ON_COOLDOWN, turn3.errorCode());
        assertEquals("Grito de guerra estará disponible en 1 turno", turn3.getMessage());
        engine.executeRound(combat, Action.attack(), new FixedRandom());                    // turn 3

        assertEquals(4, combat.round());
        int mark = combat.lastSeq();
        engine.executeRound(combat, Action.useAbility("war_cry"), new FixedRandom());     // turn 4
        assertEquals(new ActionTaken(HERO, Action.ABILITY, "war_cry"), since(combat, mark).get(1));
        assertEquals(3, combat.cooldown(HERO, "war_cry"));
        assertTrue(CombatEngine.isReady(combat, HERO, "shield_wall"));
    }

    @Test
    void realEnemyAiSharesTheCooldownRule() {
        Ability roar = new Ability("roar", "Rugido", "", 3, 0, List.of(EffectApplication.self("guard")), false);
        Combat combat = custom(stats(1000, 14, 8), List.of(), stats(1000, 10, 3),
                List.of(new EnemyAbility(roar, AiCondition.ALWAYS)));
        CombatEngine engine = new CombatEngine(effects, calculator, new EnemyAI(effects)::decide);

        int mark = combat.lastSeq();
        for (int round = 1; round <= 4; round++) {
            engine.executeRound(combat, Action.defend(), new FixedRandom());
        }

        List<String> enemyActions = since(combat, mark).stream()
                .filter(e -> e instanceof ActionTaken a && a.actorId().equals(ENEMY))
                .map(e -> ((ActionTaken) e).action())
                .toList();
        assertEquals(List.of(Action.ABILITY, Action.ATTACK, Action.ATTACK, Action.ABILITY), enemyActions);
    }

    // ------------------------------------------------------------------ ability resolution (design 4.7)

    private static final Ability COMBO = new Ability("combo", "Combo", "", 2, 1.0,
            List.of(EffectApplication.opponent("poison"), EffectApplication.self("rage")), true);

    @Test
    void abilityResolvesSelfEffectsThenDamageThenOpponentEffectsThenPurge() {
        Combat combat = custom(stats(200, 14, 8), List.of(COMBO), stats(200, 10, 3), List.of());
        int mark = combat.lastSeq();

        engine(Action.defend()).executeRound(combat, Action.useAbility("combo"), new FixedRandom());

        List<CombatEvent> events = since(combat, mark);
        assertEquals(List.of(
                new TurnStarted(HERO),
                new ActionTaken(HERO, Action.ABILITY, "combo"),
                new EffectApplied(HERO, "rage", 2),                                    // 1) self
                new DamageDealt(ENEMY, 20, DamageType.PHYSICAL, false),               // 2) 14 x 1.5 = 21 - 1
                new EffectApplied(ENEMY, "poison", 3),                                 // 3) opponent
                new EffectRemoved(ENEMY, "poison", RemovalReason.PURGED),              // 4) purge
                new TurnEnded(HERO)), events.subList(0, 7));
        assertEquals(2, combat.cooldown(HERO, "combo"));
    }

    @Test
    void opponentEffectsAreNotAppliedWhenTheHitIsEvaded() {
        Combat combat = custom(stats(200, 14, 8), List.of(COMBO), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "thorns");
        int mark = combat.lastSeq();

        // chance(): critical = false, evasion = true
        engine(Action.defend()).executeRound(combat, Action.useAbility("combo"), new FixedRandom(false, true));

        List<CombatEvent> events = since(combat, mark);
        assertEquals(List.of(
                new TurnStarted(HERO),
                new ActionTaken(HERO, Action.ABILITY, "combo"),
                new EffectApplied(HERO, "rage", 2),
                new Evaded(ENEMY),
                new EffectRemoved(ENEMY, "thorns", RemovalReason.PURGED),
                new TurnEnded(HERO)), events.subList(0, 6));
        assertFalse(effects.hasEffect(combat.enemy(), "poison"));
        assertEquals(200, combat.enemy().currentHealth());
    }

    @Test
    void opponentEffectsAppliedByTheHeroCountTheEnemysNextTurn() {
        Ability iceBolt = HeroClassCatalog.get("mage").ability("ice_bolt");
        Combat combat = custom(stats(200, 14, 8), List.of(iceBolt), stats(200, 10, 3), List.of());
        int mark = combat.lastSeq();

        engine(Action.attack()).executeRound(combat, Action.useAbility("ice_bolt"), new FixedRandom());

        List<CombatEvent> events = since(combat, mark);
        assertTrue(events.contains(new TurnSkipped(ENEMY, "frozen")));
        assertTrue(events.contains(new EffectRemoved(ENEMY, "frozen", RemovalReason.EXPIRED)));
        assertEquals(200, combat.hero().currentHealth());
    }

    // ------------------------------------------------------------------ shield, thorns, lifesteal

    @Test
    void shieldEmitsAbsorbedWithTheRemainingAbsorptionBeforeTheDamage() {
        Combat combat = custom(stats(200, 50, 8), List.of(), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "shield");
        int mark = combat.lastSeq();

        engine(Action.defend()).executeRound(combat, Action.attack(), new FixedRandom());

        List<CombatEvent> events = since(combat, mark);
        assertEquals(List.of(
                new Absorbed(ENEMY, 20, 0),
                new DamageDealt(ENEMY, 29, DamageType.PHYSICAL, false)), events.subList(2, 4));   // 49 - 20
        assertTrue(events.contains(new EffectRemoved(ENEMY, "shield", RemovalReason.DEPLETED)));
        assertEquals(171, combat.enemy().currentHealth());
    }

    @Test
    void fullyAbsorbedHitEmitsOnlyAbsorbed() {
        Combat combat = custom(stats(200, 14, 8), List.of(), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "shield");
        int mark = combat.lastSeq();

        engine(Action.defend()).executeRound(combat, Action.attack(), new FixedRandom());

        List<CombatEvent> events = since(combat, mark);
        assertEquals(new Absorbed(ENEMY, 13, 7), events.get(2));
        assertEquals(new TurnEnded(HERO), events.get(3));
        assertEquals(7, effects.shieldAbsorption(combat.enemy()));
        assertEquals(200, combat.enemy().currentHealth());
    }

    @Test
    void thornsReflectToTheAttackerThenLifestealHeals() {
        Combat combat = custom(stats(200, 14, 8), List.of(), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "thorns");
        applyFromOpponent(combat, HERO, "lifesteal");
        setHealth(combat, HERO, 150);
        int mark = combat.lastSeq();

        engine(Action.defend()).executeRound(combat, Action.attack(), new FixedRandom());

        assertEquals(List.of(
                new DamageDealt(ENEMY, 13, DamageType.PHYSICAL, false),
                new DamageDealt(HERO, 3, DamageType.REFLECTED, false),     // 30 % of 13
                new Healed(HERO, 3, "lifesteal"),                            // 30 % of 13
                new TurnEnded(HERO)), since(combat, mark).subList(2, 6));
        assertEquals(150, combat.hero().currentHealth());
    }

    // ------------------------------------------------------------------ end of combat

    @Test
    void killingTheEnemyEndsInVictoryAndTheEnemyDoesNotAct() {
        Combat combat = warriorVsGoblin();
        setHealth(combat, ENEMY, 10);
        CombatEngine engine = engine(Action.attack());
        int mark = combat.lastSeq();

        engine.executeRound(combat, Action.attack(), new FixedRandom());

        assertEquals(List.of(
                new TurnStarted(HERO),
                new ActionTaken(HERO, Action.ATTACK, null),
                new DamageDealt(ENEMY, 10, DamageType.PHYSICAL, false),
                new Death(ENEMY),
                new TurnEnded(HERO),
                new CombatEnded(CombatResult.VICTORY)), since(combat, mark));
        assertEquals(CombatStatus.VICTORY, combat.status());
        assertEquals(0, enemyDecisions.get());
        assertEquals(1, combat.round());

        assertEquals(ErrorCode.INVALID_STATE, rejected(engine, combat, Action.attack()));
    }

    @Test
    void enemyKillingTheHeroEndsInDefeat() {
        Combat combat = warriorVsGoblin();
        setHealth(combat, HERO, 5);
        CombatEngine engine = engine(Action.attack());
        int mark = combat.lastSeq();

        engine.executeRound(combat, Action.defend(), new FixedRandom());   // guard: 11 - 6 = 5

        List<CombatEvent> events = since(combat, mark);
        assertEquals(List.of(
                new ActionTaken(ENEMY, Action.ATTACK, null),
                new DamageDealt(HERO, 5, DamageType.PHYSICAL, false),
                new Death(HERO),
                new TurnEnded(ENEMY),
                new CombatEnded(CombatResult.DEFEAT)), events.subList(events.size() - 5, events.size()));
        assertFalse(events.contains(new EffectRemoved(ENEMY, "guard", RemovalReason.EXPIRED)));
        assertEquals(CombatStatus.DEFEAT, combat.status());
        assertEquals(1, combat.round());
        assertEquals(ErrorCode.INVALID_STATE, rejected(engine, combat, Action.defend()));
    }

    @Test
    void attackerKilledByThornsLoses() {
        Combat combat = custom(stats(200, 14, 8), List.of(), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "thorns");
        setHealth(combat, HERO, 2);
        int mark = combat.lastSeq();

        engine(Action.attack()).executeRound(combat, Action.attack(), new FixedRandom());

        assertEquals(List.of(
                new DamageDealt(ENEMY, 13, DamageType.PHYSICAL, false),
                new DamageDealt(HERO, 2, DamageType.REFLECTED, false),
                new Death(HERO),
                new TurnEnded(HERO),
                new CombatEnded(CombatResult.DEFEAT)), since(combat, mark).subList(2, 7));
        assertEquals(CombatStatus.DEFEAT, combat.status());
        assertEquals(0, enemyDecisions.get());
    }

    @Test
    void whenBothDieOnTheHerosTurnTheHeroWins() {
        Combat combat = custom(stats(200, 14, 8), List.of(), stats(200, 10, 3), List.of());
        applyFromOpponent(combat, ENEMY, "thorns");
        setHealth(combat, HERO, 1);
        setHealth(combat, ENEMY, 5);
        int mark = combat.lastSeq();

        engine(Action.attack()).executeRound(combat, Action.attack(), new FixedRandom());

        assertEquals(List.of(
                new DamageDealt(ENEMY, 5, DamageType.PHYSICAL, false),
                new DamageDealt(HERO, 1, DamageType.REFLECTED, false),
                new Death(ENEMY),
                new Death(HERO),
                new TurnEnded(HERO),
                new CombatEnded(CombatResult.VICTORY)), since(combat, mark).subList(2, 8));
        assertEquals(CombatStatus.VICTORY, combat.status());
    }

    // ------------------------------------------------------------------ EffectManager helpers used by the engine

    @Test
    void effectManagerFindsTheBlockingEffectAndTheShieldAbsorption() {
        Combat combat = warriorVsGoblin();
        TurnContextImpl ctx = new TurnContextImpl(combat, new FixedRandom());
        assertNull(effects.blockingEffectId(combat.hero(), ctx));
        assertEquals(0, effects.shieldAbsorption(combat.hero()));

        applyFromOpponent(combat, HERO, "frozen");
        applyFromOpponent(combat, HERO, "shield");     // frozen ends up in the middle of the chain
        assertEquals("frozen", effects.blockingEffectId(combat.hero(), ctx));
        assertEquals(20, effects.shieldAbsorption(combat.hero()));
    }
}

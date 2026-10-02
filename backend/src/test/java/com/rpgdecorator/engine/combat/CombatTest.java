package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.HeroClass;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.CombatResult;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.effects.EffectManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTest {

    private static final String HERO = BaseCharacter.HERO_ID;
    private static final String ENEMY = BaseCharacter.ENEMY_ID;

    private final HeroClass warrior = HeroClassCatalog.get("warrior");
    private final EnemyDefinition witch = EnemyCatalog.get("witch");
    private final BaseCharacter heroBase = BaseCharacter.hero(warrior);
    private final BaseCharacter enemyBase = BaseCharacter.enemy(witch);
    private final Combat combat = new Combat("c1", heroBase, enemyBase, warrior.abilities(), witch);

    @Test
    void newCombatIsInProgressAtRoundOneWithAnEmptyLog() {
        assertEquals("c1", combat.id());
        assertEquals(CombatStatus.IN_PROGRESS, combat.status());
        assertFalse(combat.isFinished());
        assertEquals(1, combat.round());
        assertEquals(List.of(), combat.events());
        assertEquals(0, combat.lastSeq());
        assertSame(heroBase, combat.hero());
        assertSame(enemyBase, combat.enemy());
    }

    @Test
    void constructorWithoutIdGeneratesOne() {
        Combat other = new Combat(heroBase, enemyBase, warrior.abilities(), witch);
        assertFalse(other.id().isBlank());
        assertFalse(other.id().equals(new Combat(heroBase, enemyBase, warrior.abilities(), witch).id()));
    }

    @Test
    void combatantsAreResolvedById() {
        assertSame(heroBase, combat.combatant(HERO));
        assertSame(enemyBase, combat.combatant(ENEMY));
        assertSame(enemyBase, combat.opponentOf(HERO));
        assertSame(heroBase, combat.opponentOf(ENEMY));
        assertTrue(combat.isHero(HERO));
        assertTrue(combat.isEnemy(ENEMY));
        assertThrows(IllegalArgumentException.class, () -> combat.combatant("nobody"));
        assertThrows(IllegalArgumentException.class, () -> combat.opponentOf("nobody"));
    }

    @Test
    void replaceStoresTheNewOuterReferenceReturnedByTheEffectManager() {
        EffectManager effects = new EffectManager();
        TurnContextImpl ctx = new TurnContextImpl(combat, new FixedRandom());

        Combatant poisoned = combat.replace(effects.apply(combat.enemy(), "poison", ctx));

        assertNotSame(enemyBase, poisoned);
        assertSame(poisoned, combat.enemy());
        assertSame(poisoned, combat.combatant(ENEMY));
        assertSame(poisoned, combat.opponentOf(HERO));
        assertSame(heroBase, combat.hero());

        Combatant equipped = effects.equip(heroBase, List.of("sword"));
        combat.setHero(equipped);
        assertSame(equipped, combat.hero());
    }

    @Test
    void replacingWithAnotherIdIsRejected() {
        Combatant stranger = new BaseCharacter("stranger", "X", heroBase.side(), heroBase.stats());
        assertThrows(IllegalArgumentException.class, () -> combat.replace(stranger));
        assertThrows(IllegalArgumentException.class, () -> combat.setHero(enemyBase));
        assertThrows(IllegalArgumentException.class, () -> combat.setEnemy(heroBase));
    }

    @Test
    void abilitiesAreLookedUpPerCombatant() {
        assertEquals(warrior.abilities(), combat.abilitiesOf(HERO));
        assertEquals(List.of("potion", "frost_hex", "hex"),
                combat.abilitiesOf(ENEMY).stream().map(Ability::id).toList());
        assertSame(witch.ability("hex"), combat.ability(ENEMY, "hex"));
        assertNull(combat.ability(HERO, "hex"));
        assertSame(witch, combat.enemyDefinition());
        assertEquals(warrior.abilities(), combat.heroAbilities());
    }

    @Test
    void cooldownOfThreeUsedInTurnOneIsAvailableAgainInTurnFour() {
        combat.setCooldown(ENEMY, "hex", 3);                 // used in turn 1
        assertEquals(3, combat.cooldown(ENEMY, "hex"));
        combat.decrementCooldowns(ENEMY);                     // start of turn 2
        assertEquals(2, combat.cooldown(ENEMY, "hex"));
        combat.decrementCooldowns(ENEMY);                     // start of turn 3
        assertEquals(1, combat.cooldown(ENEMY, "hex"));
        combat.decrementCooldowns(ENEMY);                     // start of turn 4
        assertEquals(0, combat.cooldown(ENEMY, "hex"));
        combat.decrementCooldowns(ENEMY);
        assertEquals(0, combat.cooldown(ENEMY, "hex"), "never below 0");
    }

    @Test
    void cooldownsAreKeptPerSide() {
        combat.setCooldown(ENEMY, "hex", 2);
        combat.decrementCooldowns(HERO);
        assertEquals(2, combat.cooldown(ENEMY, "hex"));
        assertEquals(0, combat.cooldown(HERO, "hex"));
        assertThrows(IllegalArgumentException.class, () -> combat.setCooldown(HERO, "hex", -1));
    }

    @Test
    void cooldownsSnapshotListsEveryAbilityInOrder() {
        combat.setCooldown(ENEMY, "frost_hex", 4);
        Map<String, Integer> snapshot = combat.cooldowns(ENEMY);
        assertEquals(List.of("potion", "frost_hex", "hex"), List.copyOf(snapshot.keySet()));
        assertEquals(List.of(0, 4, 0), List.copyOf(snapshot.values()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("hex", 1));
    }

    @Test
    void logAssignsIncreasingSeqAndTheCurrentRound() {
        LoggedEvent first = combat.log(new CombatEvent.TurnStarted(HERO));
        combat.log(new CombatEvent.TurnEnded(HERO));
        combat.nextRound();
        combat.log(new CombatEvent.TurnStarted(ENEMY));

        assertEquals(new LoggedEvent(1, 1, new CombatEvent.TurnStarted(HERO)), first);
        assertEquals(List.of(1, 2, 3), combat.events().stream().map(LoggedEvent::seq).toList());
        assertEquals(List.of(1, 1, 2), combat.events().stream().map(LoggedEvent::round).toList());
        assertEquals(3, combat.lastSeq());
        assertEquals("TURN_STARTED", first.type());
    }

    @Test
    void eventsSinceReturnsOnlyTheEventsAfterAMark() {
        combat.log(new CombatEvent.TurnStarted(HERO));
        int mark = combat.lastSeq();
        combat.log(new CombatEvent.TurnEnded(HERO));
        combat.log(new CombatEvent.TurnStarted(ENEMY));

        assertEquals(List.of(2, 3), combat.eventsSince(mark).stream().map(LoggedEvent::seq).toList());
        assertEquals(3, combat.eventsSince(0).size());
        assertEquals(List.of(), combat.eventsSince(3));
        assertEquals(List.of(), combat.eventsSince(99));
    }

    @Test
    void eventsViewIsUnmodifiable() {
        combat.log(new CombatEvent.TurnStarted(HERO));
        assertThrows(UnsupportedOperationException.class,
                () -> combat.events().add(new LoggedEvent(9, 1, new CombatEvent.TurnEnded(HERO))));
    }

    @Test
    void finishSetsTheResultOnceAndLaterActionsAreInvalidState() {
        combat.requireInProgress();
        combat.finish(CombatStatus.VICTORY);

        assertEquals(CombatStatus.VICTORY, combat.status());
        assertTrue(combat.isFinished());
        assertEquals(List.of(), combat.events(), "finish does not emit COMBAT_ENDED; the engine does");
        InvalidActionException error = assertThrows(InvalidActionException.class, combat::requireInProgress);
        assertEquals(ErrorCode.INVALID_STATE, error.errorCode());
        assertEquals("INVALID_STATE", error.code());
        assertThrows(InvalidActionException.class, combat::nextRound);
        assertThrows(IllegalStateException.class, () -> combat.finish(CombatStatus.DEFEAT));
    }

    @Test
    void cannotFinishAsInProgress() {
        assertThrows(IllegalArgumentException.class, () -> combat.finish(CombatStatus.IN_PROGRESS));
    }

    @Test
    void statusNamesMatchTheContractAndTheCombatResult() {
        assertEquals(CombatResult.VICTORY.name(), CombatStatus.VICTORY.name());
        assertEquals(CombatResult.DEFEAT.name(), CombatStatus.DEFEAT.name());
        assertEquals("IN_PROGRESS", CombatStatus.IN_PROGRESS.name());
    }
}

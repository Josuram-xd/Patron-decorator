package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.event.CombatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCalculatorTest {

    private final DamageCalculator calculator = new DamageCalculator();

    private static Stats stats(int attack, int defense, int speed, int critChance) {
        return new Stats(100, attack, defense, speed, critChance);
    }

    @Test
    void rawDamageRoundsAttackTimesMultiplierHalfUp() {
        assertEquals(11, calculator.rawDamage(new FakeCombatant("a", stats(15, 0, 0, 0)), 0.7));
        assertEquals(15, calculator.rawDamage(new FakeCombatant("a", stats(15, 0, 0, 0)), 1.0));
        assertEquals(10, calculator.rawDamage(new FakeCombatant("a", stats(13, 0, 0, 0)), 0.8));
    }

    @Test
    void criticalMultipliesByOneAndAHalfRoundingDown() {
        assertEquals(16, calculator.applyCritical(11));
        assertEquals(15, calculator.applyCritical(10));
        assertEquals(1, calculator.applyCritical(1));
    }

    @Test
    void criticalRollUsesAttackerCritChance() {
        FixedRandom random = new FixedRandom(true);
        assertTrue(calculator.rollCritical(new FakeCombatant("a", stats(10, 0, 0, 30)), random));
        assertEquals(List.of(30), random.requestedPercents);
    }

    @Test
    void evasionChanceIsTwiceSpeed() {
        assertEquals(14, calculator.evasionChance(new FakeCombatant("t", stats(0, 0, 7, 0))));
    }

    @Test
    void evasionChanceIsCappedAtTwentyFivePercent() {
        FixedRandom random = new FixedRandom();
        calculator.rollEvasion(new FakeCombatant("t", stats(0, 0, 20, 0)), random);
        assertEquals(List.of(25), random.requestedPercents);
        assertEquals(25, calculator.evasionChance(new FakeCombatant("t", stats(0, 0, 13, 0))));
    }

    @Test
    void mitigationSubtractsHalfDefenseRoundedDown() {
        Damage result = calculator.mitigate(new Damage(10, 0, "a", false, true),
                new FakeCombatant("t", stats(0, 7, 0, 0)));
        assertEquals(7, result.physical());
    }

    @Test
    void mitigatedPhysicalDamageIsAtLeastOne() {
        Damage result = calculator.mitigate(new Damage(3, 0, "a", false, true),
                new FakeCombatant("t", stats(0, 40, 0, 0)));
        assertEquals(1, result.physical());
    }

    @Test
    void elementalDamageIsNotMitigated() {
        Damage result = calculator.mitigate(new Damage(10, 5, "a", true, true),
                new FakeCombatant("t", stats(0, 40, 0, 0)));
        assertEquals(1, result.physical());
        assertEquals(5, result.elemental());
        assertEquals("a", result.sourceId());
        assertTrue(result.critical());
        assertTrue(result.reflectable());
    }

    @Test
    void zeroPhysicalDamageStaysZeroAfterMitigation() {
        Damage result = calculator.mitigate(new Damage(0, 5, "a", false, true),
                new FakeCombatant("t", stats(0, 10, 0, 0)));
        assertEquals(0, result.physical());
        assertEquals(5, result.elemental());
    }

    @Test
    void computeRollsCriticalBeforeEvasion() {
        FixedRandom random = new FixedRandom(false, false);
        calculator.compute(new FakeCombatant("a", stats(10, 0, 0, 40)),
                new FakeCombatant("t", stats(0, 0, 5, 0)), 1.0, new FakeContext(random));
        assertEquals(List.of(40, 10), random.requestedPercents);
    }

    @Test
    void computeAppliesCriticalThenOutgoingModifiersThenMitigation() {
        FakeCombatant attacker = new FakeCombatant("a", stats(15, 0, 0, 100));
        attacker.extraElemental = 3;
        FakeCombatant target = new FakeCombatant("t", stats(0, 6, 0, 0));

        DamageCalculator.Outcome outcome =
                calculator.compute(attacker, target, 0.7, new FakeContext(new FixedRandom(true, false)));

        // raw round(10.5) = 11 -> critical floor(16.5) = 16 -> +3 elemental -> 16 - 6/2 = 13 physical
        assertFalse(outcome.evaded());
        assertTrue(outcome.critical());
        assertEquals(new Damage(13, 3, "a", true, true), outcome.damage());
        assertEquals(new Damage(16, 0, "a", true, true), attacker.lastOutgoingInput);
    }

    @Test
    void computeReturnsNoDamageWhenTargetEvades() {
        FakeCombatant target = new FakeCombatant("t", stats(0, 0, 20, 0));
        DamageCalculator.Outcome outcome = calculator.compute(new FakeCombatant("a", stats(10, 0, 0, 0)),
                target, 1.0, new FakeContext(new FixedRandom(false, true)));
        assertTrue(outcome.evaded());
        assertEquals(0, outcome.damage().total());
        assertEquals(0, target.takeDamageCalls);
    }

    @Test
    void computeNeverCallsTakeDamageOnTarget() {
        FakeCombatant target = new FakeCombatant("t", stats(0, 0, 0, 0));
        DamageCalculator.Outcome outcome = calculator.compute(new FakeCombatant("a", stats(10, 0, 0, 0)),
                target, 1.0, new FakeContext(new FixedRandom()));
        assertEquals(new Damage(10, 0, "a", false, true), outcome.damage());
        assertEquals(0, target.takeDamageCalls);
    }

    /** Minimal combatant: fixed stats, optional elemental bonus on outgoing damage. */
    private static final class FakeCombatant implements Combatant {
        private final String id;
        private final Stats stats;
        int extraElemental;
        Damage lastOutgoingInput;
        int takeDamageCalls;

        FakeCombatant(String id, Stats stats) {
            this.id = id;
            this.stats = stats;
        }

        @Override public String id() { return id; }
        @Override public String name() { return id; }
        @Override public Side side() { return Side.HERO; }
        @Override public Stats stats() { return stats; }
        @Override public int currentHealth() { return stats.maxHealth(); }
        @Override public void changeHealth(int delta, int effectiveMaxHealth) { }

        @Override
        public Damage modifyOutgoingDamage(Damage damage, TurnContext ctx) {
            lastOutgoingInput = damage;
            return new Damage(damage.physical(), damage.elemental() + extraElemental,
                    damage.sourceId(), damage.critical(), damage.reflectable());
        }

        @Override
        public DamageResult takeDamage(Damage damage, TurnContext ctx) {
            takeDamageCalls++;
            return new DamageResult(damage.total(), 0, 0, false);
        }

        @Override public void onDamageDealt(DamageResult result, TurnContext ctx) { }
        @Override public boolean canAct(TurnContext ctx) { return true; }
        @Override public void onTurnStart(TurnContext ctx) { }
        @Override public String describeChain() { return id; }
    }

    private record FakeContext(RandomSource random) implements TurnContext {
        @Override public void emit(CombatEvent event) { }
        @Override public void directDamage(String targetId, int amount, DamageType type, String sourceEffectId) { }
        @Override public void heal(String targetId, int amount, String sourceEffectId) { }
        @Override public int round() { return 1; }
    }
}

package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.TurnContext;

/**
 * Pure, stateless damage formulas (design 4.1).
 *
 * <p>The steps are exposed separately so that the {@code CombatEngine} can emit events between
 * them. {@link #compute} chains them in the canonical order:
 *
 * <ol>
 *   <li>{@link #rawDamage}: {@code round(attack x multiplier)} ({@link Math#round}, half up).
 *   <li>{@link #rollCritical}: {@code chance(critChance)} - <b>first</b> random draw.
 *   <li>{@link #applyCritical}: {@code floor(raw x 1.5)} when critical.
 *   <li>{@code attacker.modifyOutgoingDamage(new Damage(raw, 0, attacker.id(), critical, true), ctx)}.
 *   <li>{@link #rollEvasion}: {@code chance(min(25, speed x 2))} - <b>second</b> random draw.
 *   <li>{@link #mitigate}: {@code physical' = max(1, physical - defense / 2)}; elemental untouched.
 * </ol>
 *
 * <p>The calculator never calls {@code takeDamage}, reflection or {@code onDamageDealt}: that is the
 * engine's job, after the mitigation step.
 *
 * <p>Self-call pitfall (design 3.1): every stat is read through {@code stats()} of the references
 * passed in, which must be the <b>outer</b> references of each decorator chain.
 */
public final class DamageCalculator {

    /** Maximum evasion chance, in percent. */
    public static final int EVASION_CAP = 25;

    /** Result of {@link #compute}. When {@code evaded} is true, {@code damage} is all zeros. */
    public record Outcome(boolean evaded, boolean critical, Damage damage) {}

    /** {@code round(attacker.stats().attack() x multiplier)}; never negative. */
    public int rawDamage(Combatant attacker, double multiplier) {
        return (int) Math.max(0, Math.round(attacker.stats().attack() * multiplier));
    }

    /** Rolls a critical hit with probability {@code attacker.stats().critChance()} %. */
    public boolean rollCritical(Combatant attacker, RandomSource random) {
        return random.chance(attacker.stats().critChance());
    }

    /** Critical multiplier: {@code floor(raw x 1.5)}. */
    public int applyCritical(int raw) {
        return raw * 3 / 2;
    }

    /** Evasion chance in percent: {@code min(25, target.stats().speed() x 2)}. */
    public int evasionChance(Combatant target) {
        return Math.min(EVASION_CAP, target.stats().speed() * 2);
    }

    /** Rolls evasion with probability {@link #evasionChance(Combatant)} %. */
    public boolean rollEvasion(Combatant target, RandomSource random) {
        return random.chance(evasionChance(target));
    }

    /**
     * Applies defense mitigation: {@code physical' = max(1, physical - defense / 2)} only when
     * {@code physical > 0}; elemental damage is never mitigated. Other fields are preserved.
     */
    public Damage mitigate(Damage outgoing, Combatant target) {
        int physical = outgoing.physical();
        if (physical > 0) {
            physical = Math.max(1, physical - target.stats().defense() / 2);
        }
        return new Damage(physical, outgoing.elemental(), outgoing.sourceId(),
                outgoing.critical(), outgoing.reflectable());
    }

    /**
     * Runs raw -> critical -> outgoing modifiers -> evasion -> mitigation. Random draws happen in
     * this order: critical first, then evasion (both always drawn, via {@code ctx.random()}).
     * Does NOT call {@code target.takeDamage}.
     */
    public Outcome compute(Combatant attacker, Combatant target, double multiplier, TurnContext ctx) {
        RandomSource random = ctx.random();
        int raw = rawDamage(attacker, multiplier);
        boolean critical = rollCritical(attacker, random);
        if (critical) {
            raw = applyCritical(raw);
        }
        Damage outgoing = attacker.modifyOutgoingDamage(
                new Damage(raw, 0, attacker.id(), critical, true), ctx);
        if (rollEvasion(target, random)) {
            return new Outcome(true, critical, new Damage(0, 0, attacker.id(), critical, true));
        }
        return new Outcome(false, critical, mitigate(outgoing, target));
    }
}

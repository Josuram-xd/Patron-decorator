package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.event.CombatEvent;

import java.util.Objects;

/**
 * The engine implementation of {@link TurnContext} (design §2.3), one per {@link Combat}.
 *
 * <p>Decorators never touch health directly: they ask the context, and the context resolves the
 * <b>outer</b> chain by {@code id} through {@link Combat#combatant(String)} at call time (design §3.1,
 * §3.6). That way the effective {@code maxHealth} (e.g. with LifeAmulet) is used, and a chain rebuilt
 * by the {@code EffectManager} is picked up as soon as it is stored back in the combat.
 *
 * <p>Decisions:
 * <ul>
 *   <li>The context never emits {@code DEATH}: the engine does, in {@code takeTurn}, after the
 *       health checks of design §5.3. The context only changes health and emits {@code DAMAGE}/{@code HEAL}.
 *   <li>A combatant at 0 health is dead: {@code directDamage} and {@code heal} on it do nothing and
 *       emit nothing (no revival, no damage on a corpse).
 *   <li>Otherwise both always emit their event with the <b>actual</b> amount, even 0 (e.g. healing
 *       at full health emits {@code HEAL} with amount 0), so every effect tick shows in the log.
 * </ul>
 */
public final class TurnContextImpl implements TurnContext {

    private final Combat combat;
    private final RandomSource random;

    public TurnContextImpl(Combat combat, RandomSource random) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Appends the event to the combat log (next seq, current round). */
    @Override
    public void emit(CombatEvent event) {
        combat.log(event);
    }

    @Override
    public RandomSource random() {
        return random;
    }

    /** Ignores defense and shield; emits {@code DAMAGE(targetId, actualLost, type, false)}. */
    @Override
    public void directDamage(String targetId, int amount, DamageType type, String sourceEffectId) {
        Objects.requireNonNull(type, "type");
        requireNonNegative(amount);
        Combatant outer = combat.combatant(targetId);
        int before = outer.currentHealth();
        if (before == 0) {
            return;
        }
        outer.changeHealth(-amount, outer.stats().maxHealth());
        emit(new CombatEvent.DamageDealt(targetId, before - outer.currentHealth(), type, false));
    }

    /** Heals up to the effective maxHealth; emits {@code HEAL(targetId, actualHealed, sourceEffectId)}. */
    @Override
    public void heal(String targetId, int amount, String sourceEffectId) {
        requireNonNegative(amount);
        Combatant outer = combat.combatant(targetId);
        int before = outer.currentHealth();
        if (before == 0) {
            return;
        }
        outer.changeHealth(amount, outer.stats().maxHealth());
        emit(new CombatEvent.Healed(targetId, outer.currentHealth() - before, sourceEffectId));
    }

    /** The current round of the combat. */
    @Override
    public int round() {
        return combat.round();
    }

    public Combat combat() {
        return combat;
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative: " + amount);
        }
    }
}

package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.event.CombatEvent;

/**
 * Resolves what decorators ask for against the OUTER chain of the target, found by id, so the
 * effective max health (amulet included) is the one that bounds the change.
 */
public final class TurnContextImpl implements TurnContext {

    private final Combat combat;
    private final RandomSource random;

    public TurnContextImpl(Combat combat, RandomSource random) {
        this.combat = combat;
        this.random = random;
    }

    @Override
    public void emit(CombatEvent event) {
        combat.record(event);
    }

    @Override
    public RandomSource random() {
        return random;
    }

    @Override
    public void directDamage(String targetId, int amount, DamageType type, String sourceEffectId) {
        int lost = -changeHealth(targetId, -amount);
        if (lost > 0) {
            emit(new CombatEvent.DamageDealt(targetId, lost, type, false));
        }
    }

    @Override
    public void heal(String targetId, int amount, String sourceEffectId) {
        int healed = changeHealth(targetId, amount);
        if (healed > 0) {
            emit(new CombatEvent.Healed(targetId, healed, sourceEffectId));
        }
    }

    @Override
    public int round() {
        return combat.round();
    }

    private int changeHealth(String targetId, int delta) {
        Combatant outer = combat.combatant(targetId);
        int before = outer.currentHealth();
        outer.changeHealth(delta, outer.stats().maxHealth());
        return outer.currentHealth() - before;
    }
}

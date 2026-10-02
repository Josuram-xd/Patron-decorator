package com.rpgdecorator.engine.effects;

import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.event.CombatEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: a {@link TurnContext} that only records what it receives.
 * round() is always 1, random() is fixed (min value, chance always false),
 * directDamage/heal are recorded as text but have no effect on any combatant.
 */
final class RecordingTurnContext implements TurnContext {

    private final List<CombatEvent> events = new ArrayList<>();
    private final List<String> sideEffects = new ArrayList<>();

    private static final RandomSource FIXED_RANDOM = new RandomSource() {
        @Override public int nextInt(int minInclusive, int maxInclusive) { return minInclusive; }
        @Override public boolean chance(int percent) { return false; }
    };

    @Override
    public void emit(CombatEvent event) {
        events.add(event);
    }

    @Override
    public RandomSource random() {
        return FIXED_RANDOM;
    }

    @Override
    public void directDamage(String targetId, int amount, DamageType type, String sourceEffectId) {
        sideEffects.add("damage:" + targetId + ":" + amount + ":" + type + ":" + sourceEffectId);
    }

    @Override
    public void heal(String targetId, int amount, String sourceEffectId) {
        sideEffects.add("heal:" + targetId + ":" + amount + ":" + sourceEffectId);
    }

    @Override
    public int round() {
        return 1;
    }

    /** All events emitted so far, in emission order. */
    List<CombatEvent> events() {
        return List.copyOf(events);
    }

    /** Recorded directDamage/heal calls. */
    List<String> sideEffects() {
        return List.copyOf(sideEffects);
    }

    void clear() {
        events.clear();
        sideEffects.clear();
    }
}

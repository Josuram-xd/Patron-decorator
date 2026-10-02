package com.rpgdecorator.domain;

import com.rpgdecorator.domain.event.CombatEvent;
import java.util.ArrayList;
import java.util.List;

/** Records what decorators ask of the engine, so tests can assert on it. */
public final class FakeTurnContext implements TurnContext {

    public record DirectDamage(String targetId, int amount, DamageType type, String sourceEffectId) {
    }

    public record Heal(String targetId, int amount, String sourceEffectId) {
    }

    public final List<CombatEvent> events = new ArrayList<>();
    public final List<DirectDamage> directDamages = new ArrayList<>();
    public final List<Heal> heals = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();
    public RandomSource random = new ScriptedRandom();

    @Override
    public void emit(CombatEvent event) {
        events.add(event);
    }

    @Override
    public RandomSource random() {
        return random;
    }

    @Override
    public void directDamage(String targetId, int amount, DamageType type, String sourceEffectId) {
        directDamages.add(new DirectDamage(targetId, amount, type, sourceEffectId));
        calls.add("directDamage:" + sourceEffectId);
    }

    @Override
    public void heal(String targetId, int amount, String sourceEffectId) {
        heals.add(new Heal(targetId, amount, sourceEffectId));
        calls.add("heal:" + sourceEffectId);
    }

    @Override
    public int round() {
        return 1;
    }
}

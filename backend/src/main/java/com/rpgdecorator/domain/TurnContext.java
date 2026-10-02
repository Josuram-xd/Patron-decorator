package com.rpgdecorator.domain;

import com.rpgdecorator.domain.event.CombatEvent;

/** What a decorator may ask of the engine; the engine resolves targets through the outer chain. */
public interface TurnContext {

    void emit(CombatEvent event);

    RandomSource random();

    void directDamage(String targetId, int amount, DamageType type, String sourceEffectId);

    void heal(String targetId, int amount, String sourceEffectId);

    int round();
}

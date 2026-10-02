package com.rpgdecorator.domain.event;

import com.rpgdecorator.domain.DamageType;

/**
 * Something that happened during a combat. The engine stamps {@code seq} and {@code round}
 * when it logs the event, so decorators only provide the type-specific fields.
 */
public sealed interface CombatEvent {

    record TurnStarted(String actorId) implements CombatEvent {
    }

    record ActionTaken(String actorId, String action, String abilityId) implements CombatEvent {
    }

    record DamageDealt(String targetId, int amount, DamageType damageType, boolean critical) implements CombatEvent {
    }

    record Evaded(String targetId) implements CombatEvent {
    }

    record Absorbed(String targetId, int amount, int remaining) implements CombatEvent {
    }

    record Healed(String targetId, int amount, String sourceEffectId) implements CombatEvent {
    }

    record EffectApplied(String targetId, String effectId, int duration) implements CombatEvent {
    }

    record EffectRefreshed(String targetId, String effectId, int duration) implements CombatEvent {
    }

    record EffectRemoved(String targetId, String effectId, String reason) implements CombatEvent {
    }

    record TurnSkipped(String actorId, String effectId) implements CombatEvent {
    }

    record Death(String combatantId) implements CombatEvent {
    }

    record TurnEnded(String actorId) implements CombatEvent {
    }

    record CombatEnded(String result) implements CombatEvent {
    }
}

package com.rpgdecorator.domain.event;

import com.rpgdecorator.domain.DamageType;

/**
 * Something that happened during a combat. The engine stamps {@code seq} and {@code round}
 * when it logs the event, so decorators only provide the type-specific fields.
 */
public sealed interface CombatEvent {

    /** The {@code type} value of the HTTP contract (design §6). */
    String type();

    record TurnStarted(String actorId) implements CombatEvent {
        @Override
        public String type() {
            return "TURN_STARTED";
        }
    }

    record ActionTaken(String actorId, String action, String abilityId) implements CombatEvent {
        @Override
        public String type() {
            return "ACTION";
        }
    }

    record DamageDealt(String targetId, int amount, DamageType damageType, boolean critical) implements CombatEvent {
        @Override
        public String type() {
            return "DAMAGE";
        }
    }

    record Evaded(String targetId) implements CombatEvent {
        @Override
        public String type() {
            return "EVADED";
        }
    }

    record Absorbed(String targetId, int amount, int remaining) implements CombatEvent {
        @Override
        public String type() {
            return "ABSORBED";
        }
    }

    record Healed(String targetId, int amount, String sourceEffectId) implements CombatEvent {
        @Override
        public String type() {
            return "HEAL";
        }
    }

    record EffectApplied(String targetId, String effectId, int duration) implements CombatEvent {
        @Override
        public String type() {
            return "EFFECT_APPLIED";
        }
    }

    record EffectRefreshed(String targetId, String effectId, int duration) implements CombatEvent {
        @Override
        public String type() {
            return "EFFECT_REFRESHED";
        }
    }

    record EffectRemoved(String targetId, String effectId, RemovalReason reason) implements CombatEvent {
        @Override
        public String type() {
            return "EFFECT_REMOVED";
        }
    }

    record TurnSkipped(String actorId, String effectId) implements CombatEvent {
        @Override
        public String type() {
            return "TURN_SKIPPED";
        }
    }

    record Death(String combatantId) implements CombatEvent {
        @Override
        public String type() {
            return "DEATH";
        }
    }

    record TurnEnded(String actorId) implements CombatEvent {
        @Override
        public String type() {
            return "TURN_ENDED";
        }
    }

    record CombatEnded(CombatResult result) implements CombatEvent {
        @Override
        public String type() {
            return "COMBAT_ENDED";
        }
    }
}

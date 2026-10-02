package com.rpgdecorator.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.event.CombatEvent.Absorbed;
import com.rpgdecorator.domain.event.CombatEvent.ActionTaken;
import com.rpgdecorator.domain.event.CombatEvent.CombatEnded;
import com.rpgdecorator.domain.event.CombatEvent.DamageDealt;
import com.rpgdecorator.domain.event.CombatEvent.Death;
import com.rpgdecorator.domain.event.CombatEvent.EffectApplied;
import com.rpgdecorator.domain.event.CombatEvent.EffectRefreshed;
import com.rpgdecorator.domain.event.CombatEvent.EffectRemoved;
import com.rpgdecorator.domain.event.CombatEvent.Evaded;
import com.rpgdecorator.domain.event.CombatEvent.Healed;
import com.rpgdecorator.domain.event.CombatEvent.TurnEnded;
import com.rpgdecorator.domain.event.CombatEvent.TurnSkipped;
import com.rpgdecorator.domain.event.CombatEvent.TurnStarted;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatEventTest {

    // No default branch: this only compiles while the switch covers every permitted record.
    private static String typeOf(CombatEvent event) {
        return switch (event) {
            case TurnStarted e -> "TURN_STARTED";
            case ActionTaken e -> "ACTION";
            case DamageDealt e -> "DAMAGE";
            case Evaded e -> "EVADED";
            case Absorbed e -> "ABSORBED";
            case Healed e -> "HEAL";
            case EffectApplied e -> "EFFECT_APPLIED";
            case EffectRefreshed e -> "EFFECT_REFRESHED";
            case EffectRemoved e -> "EFFECT_REMOVED";
            case TurnSkipped e -> "TURN_SKIPPED";
            case Death e -> "DEATH";
            case TurnEnded e -> "TURN_ENDED";
            case CombatEnded e -> "COMBAT_ENDED";
        };
    }

    @Test
    void switchOverCombatEventIsExhaustiveWithoutDefault() {
        List<CombatEvent> events = List.of(
                new TurnStarted("hero"),
                new ActionTaken("hero", "ABILITY", "ice_bolt"),
                new DamageDealt("enemy", 21, DamageType.PHYSICAL, true),
                new Evaded("enemy"),
                new Absorbed("enemy", 8, 12),
                new Healed("hero", 8, "regeneration"),
                new EffectApplied("enemy", "frozen", 1),
                new EffectRefreshed("enemy", "poison", 3),
                new EffectRemoved("enemy", "rage", RemovalReason.INTERACTION),
                new TurnSkipped("enemy", "frozen"),
                new Death("enemy"),
                new TurnEnded("hero"),
                new CombatEnded(CombatResult.VICTORY));

        List<String> types = events.stream().map(CombatEventTest::typeOf).toList();

        assertEquals(List.of("TURN_STARTED", "ACTION", "DAMAGE", "EVADED", "ABSORBED", "HEAL", "EFFECT_APPLIED",
                "EFFECT_REFRESHED", "EFFECT_REMOVED", "TURN_SKIPPED", "DEATH", "TURN_ENDED", "COMBAT_ENDED"), types);
        assertEquals(CombatEvent.class.getPermittedSubclasses().length, types.size());
    }
}

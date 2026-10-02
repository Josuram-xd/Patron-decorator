package com.rpgdecorator.engine.effects;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionRulesTest {

    private final InteractionRules rules = InteractionRules.standard();

    @Test
    void frozenRemovesRage() {
        assertEquals(Set.of("rage"), rules.removedBy("frozen"));
    }

    @Test
    void rageRemovesGuard() {
        assertEquals(Set.of("guard"), rules.removedBy("rage"));
    }

    @Test
    void poisonRemovesRegeneration() {
        assertEquals(Set.of("regeneration"), rules.removedBy("poison"));
    }

    @Test
    void regenerationRemovesPoison() {
        assertEquals(Set.of("poison"), rules.removedBy("regeneration"));
    }

    @Test
    void effectWithoutRuleRemovesNothing() {
        Set<String> removed = rules.removedBy("shield");
        assertNotNull(removed);
        assertTrue(removed.isEmpty());
    }

    @Test
    void unknownEffectIdReturnsEmptySetNeverNull() {
        Set<String> removed = rules.removedBy("does_not_exist");
        assertNotNull(removed);
        assertTrue(removed.isEmpty());
    }

    @Test
    void customRulesAreDataAndAreDefensivelyCopied() {
        Map<String, Set<String>> table = new HashMap<>();
        Set<String> removedByThorns = new HashSet<>(Set.of("shield"));
        table.put("thorns", removedByThorns);

        InteractionRules custom = new InteractionRules(table);

        table.put("rage", Set.of("guard"));
        removedByThorns.add("poison");

        assertEquals(Set.of("shield"), custom.removedBy("thorns"));
        assertTrue(custom.removedBy("rage").isEmpty());
    }
}

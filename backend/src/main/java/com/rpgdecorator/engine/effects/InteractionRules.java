package com.rpgdecorator.engine.effects;

import java.util.Map;
import java.util.Set;

/** Which effects are removed when another one is applied. The rules are data, not scattered ifs. */
public final class InteractionRules {

    private static final InteractionRules DEFAULTS = new InteractionRules(Map.of(
            "frozen", Set.of("rage"),
            "rage", Set.of("guard"),
            "poison", Set.of("regeneration"),
            "regeneration", Set.of("poison")));

    private final Map<String, Set<String>> removedWhenApplying;

    public InteractionRules(Map<String, Set<String>> removedWhenApplying) {
        this.removedWhenApplying = Map.copyOf(removedWhenApplying);
    }

    public static InteractionRules defaults() {
        return DEFAULTS;
    }

    public Set<String> removedBy(String appliedEffectId) {
        return removedWhenApplying.getOrDefault(appliedEffectId, Set.of());
    }
}

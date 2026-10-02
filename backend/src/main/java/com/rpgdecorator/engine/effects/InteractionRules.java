package com.rpgdecorator.engine.effects;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Interaction rules between effects (RF-17, design 4.4).
 *
 * <p>Each rule says: "when the effect X is applied, the active effects Y are removed first".
 * The rules are kept as DATA (a {@code Map<String, Set<String>>}) instead of {@code if}s spread
 * across the engine, so adding a new rule never touches {@link EffectManager} (RNF-03).
 *
 * <p>Instances are immutable.
 */
public final class InteractionRules {

    /** incoming effectId -> effectIds it removes from the chain before being applied. */
    private final Map<String, Set<String>> removesOnApply;

    /**
     * @param removesOnApply incoming effectId -> effectIds it removes. A defensive, immutable copy
     *                       is stored, so later changes to the argument have no effect.
     */
    public InteractionRules(Map<String, Set<String>> removesOnApply) {
        Objects.requireNonNull(removesOnApply, "removesOnApply");
        Map<String, Set<String>> copy = new HashMap<>();
        removesOnApply.forEach((incoming, removed) -> copy.put(incoming, Set.copyOf(removed)));
        this.removesOnApply = Map.copyOf(copy);
    }

    /** The table of design 4.4. */
    public static InteractionRules standard() {
        return new InteractionRules(Map.of(
                "frozen", Set.of("rage"),               // you cannot be furious while frozen
                "rage", Set.of("guard"),                // rage breaks the guard
                "poison", Set.of("regeneration"),       // they cancel each other
                "regeneration", Set.of("poison")));
    }

    /** Effect ids removed when {@code incomingEffectId} is applied; an empty set (never null) if there is no rule. */
    public Set<String> removedBy(String incomingEffectId) {
        return removesOnApply.getOrDefault(incomingEffectId, Set.of());
    }
}

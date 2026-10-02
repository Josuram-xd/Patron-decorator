package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.Stats;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An enemy archetype. {@code abilities} are in priority order; {@code conditions} holds the AI
 * condition of the abilities that have one (an ability without an entry is always usable).
 */
public record EnemyDefinition(String id, String name, int level, boolean boss, Stats stats,
                              List<Ability> abilities, Map<String, Predicate<Situation>> conditions) {

    /** What the AI knows when it decides, computed by the engine from the outer chains. */
    public record Situation(int health, int maxHealth, int opponentBuffCount, RandomSource random) {

        public boolean healthBelow(int percent) {
            return health * 100 < percent * maxHealth;
        }
    }

    public EnemyDefinition {
        abilities = List.copyOf(abilities);
        conditions = Map.copyOf(conditions);
    }

    public Optional<Ability> findAbility(String abilityId) {
        return abilities.stream().filter(a -> a.id().equals(abilityId)).findFirst();
    }

    public boolean conditionMet(String abilityId, Situation situation) {
        Predicate<Situation> condition = conditions.get(abilityId);
        return condition == null || condition.test(situation);
    }
}

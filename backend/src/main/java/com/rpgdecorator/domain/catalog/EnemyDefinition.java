package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.Stats;
import java.util.List;
import java.util.Optional;

/** An enemy archetype. {@code abilities} are in priority order, each with its AI condition. */
public record EnemyDefinition(String id, String name, int level, Stats stats,
                              List<EnemyAbility> abilities) {

    public EnemyDefinition {
        abilities = List.copyOf(abilities);
    }

    /** The boss is the enemy of the last level. */
    public boolean boss() {
        return level == EnemyCatalog.TOTAL_LEVELS;
    }

    public Optional<EnemyAbility> findAbility(String abilityId) {
        return abilities.stream().filter(a -> a.ability().id().equals(abilityId)).findFirst();
    }

    /** The ability with that id, or {@code null} if this enemy does not have it. */
    public Ability ability(String abilityId) {
        return findAbility(abilityId).map(EnemyAbility::ability).orElse(null);
    }
}

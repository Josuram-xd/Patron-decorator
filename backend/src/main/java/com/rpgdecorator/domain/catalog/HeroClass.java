package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.Stats;
import java.util.List;
import java.util.Optional;

public record HeroClass(String id, String name, String description, Stats stats, List<Ability> abilities) {

    public HeroClass {
        abilities = List.copyOf(abilities);
    }

    public Optional<Ability> findAbility(String abilityId) {
        return abilities.stream().filter(a -> a.id().equals(abilityId)).findFirst();
    }
}

package com.rpgdecorator.domain.catalog;

import java.util.List;

public record Ability(String id, String name, String description, int cooldown,
                      double damageMultiplier, List<EffectApplication> effects, boolean purgesOpponent) {

    public Ability {
        effects = List.copyOf(effects);
    }

    public boolean attacks() {
        return damageMultiplier > 0;
    }

    public List<String> effectsOn(Target target) {
        return effects.stream().filter(e -> e.target() == target).map(EffectApplication::effectId).toList();
    }
}

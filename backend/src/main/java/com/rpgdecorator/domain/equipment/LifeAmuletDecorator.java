package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class LifeAmuletDecorator extends EffectDecorator {

    public static final String ID = "life_amulet";
    public static final String LABEL = "Amuleto de vida";

    public LifeAmuletDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withMaxHealth(inner.maxHealth() + 25);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new LifeAmuletDecorator(newWrapped);
    }
}

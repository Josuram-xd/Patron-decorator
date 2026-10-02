package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class LeatherArmorDecorator extends EffectDecorator {

    public static final String ID = "leather_armor";
    public static final String LABEL = "Armadura de cuero";

    public LeatherArmorDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withDefense(inner.defense() + 4);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new LeatherArmorDecorator(newWrapped);
    }
}

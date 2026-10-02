package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class DragonArmorDecorator extends EffectDecorator {

    public static final String ID = "dragon_armor";
    public static final String LABEL = "Armadura de dragón";

    public DragonArmorDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withDefense(inner.defense() + 10).withSpeed(inner.speed() - 4);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new DragonArmorDecorator(newWrapped);
    }
}

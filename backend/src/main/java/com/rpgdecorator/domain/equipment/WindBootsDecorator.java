package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class WindBootsDecorator extends EffectDecorator {

    public static final String ID = "wind_boots";
    public static final String LABEL = "Botas de viento";

    public WindBootsDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withSpeed(inner.speed() + 5);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new WindBootsDecorator(newWrapped);
    }
}

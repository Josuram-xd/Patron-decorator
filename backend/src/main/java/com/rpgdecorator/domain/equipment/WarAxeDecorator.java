package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class WarAxeDecorator extends EffectDecorator {

    public static final String ID = "war_axe";
    public static final String LABEL = "Hacha de guerra";

    public WarAxeDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withAttack(inner.attack() + 10).withSpeed(inner.speed() - 3);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new WarAxeDecorator(newWrapped);
    }
}

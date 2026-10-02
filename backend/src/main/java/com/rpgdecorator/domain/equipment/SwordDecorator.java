package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class SwordDecorator extends EffectDecorator {

    public static final String ID = "sword";
    public static final String LABEL = "Espada";

    public SwordDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withAttack(inner.attack() + 6);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new SwordDecorator(newWrapped);
    }
}

package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class RuneStaffDecorator extends EffectDecorator {

    public static final String ID = "rune_staff";
    public static final String LABEL = "Bastón rúnico";

    public RuneStaffDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withAttack(inner.attack() + 3).withCritChance(inner.critChance() + 15);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new RuneStaffDecorator(newWrapped);
    }
}

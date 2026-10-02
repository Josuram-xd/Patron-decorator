package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class GuardDecorator extends EffectDecorator {

    public static final String ID = "guard";
    public static final String LABEL = "En guardia";
    public static final int BASE_TURNS = 1;
    public static final int DEFENSE_PERCENT = 150;

    public GuardDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.BUFF, Duration.ofTurns(BASE_TURNS));
    }

    private GuardDecorator(Combatant newWrapped, GuardDecorator source) {
        super(newWrapped, source);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner.withDefense(inner.defense() * DEFENSE_PERCENT / 100);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new GuardDecorator(newWrapped, this);
    }
}

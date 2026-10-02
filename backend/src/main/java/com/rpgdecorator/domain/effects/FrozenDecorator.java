package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class FrozenDecorator extends EffectDecorator {

    public static final String ID = "frozen";
    public static final String LABEL = "Congelado";
    public static final int BASE_TURNS = 1;

    public FrozenDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.CONTROL, Duration.ofTurns(BASE_TURNS));
    }

    private FrozenDecorator(Combatant newWrapped, FrozenDecorator source) {
        super(newWrapped, source);
    }

    // Deliberately does not delegate: nothing underneath can make a frozen combatant act.
    @Override
    public boolean canAct(TurnContext ctx) {
        return false;
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new FrozenDecorator(newWrapped, this);
    }
}

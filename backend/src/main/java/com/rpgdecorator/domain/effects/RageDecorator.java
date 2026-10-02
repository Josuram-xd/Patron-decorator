package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class RageDecorator extends EffectDecorator {

    public static final String ID = "rage";
    public static final String LABEL = "Furia";
    public static final int BASE_TURNS = 2;
    public static final int ATTACK_PERCENT = 150;
    public static final int DEFENSE_PERCENT = 70;

    public RageDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.BUFF, Duration.ofTurns(BASE_TURNS));
    }

    private RageDecorator(Combatant newWrapped, RageDecorator source) {
        super(newWrapped, source);
    }

    @Override
    public Stats stats() {
        Stats inner = wrapped.stats();
        return inner
                .withAttack(inner.attack() * ATTACK_PERCENT / 100)
                .withDefense(inner.defense() * DEFENSE_PERCENT / 100);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new RageDecorator(newWrapped, this);
    }
}

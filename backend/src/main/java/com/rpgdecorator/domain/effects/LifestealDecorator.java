package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class LifestealDecorator extends EffectDecorator {

    public static final String ID = "lifesteal";
    public static final String LABEL = "Vampirismo";
    public static final int BASE_TURNS = 3;
    public static final int HEAL_PERCENT = 30;

    public LifestealDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.BUFF, Duration.ofTurns(BASE_TURNS));
    }

    private LifestealDecorator(Combatant newWrapped, LifestealDecorator source) {
        super(newWrapped, source);
    }

    @Override
    public void onDamageDealt(DamageResult result, TurnContext ctx) {
        if (result.taken() > 0) {
            ctx.heal(id(), Math.max(1, result.taken() * HEAL_PERCENT / 100), ID);
        }
        wrapped.onDamageDealt(result, ctx);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new LifestealDecorator(newWrapped, this);
    }
}

package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class ThornsDecorator extends EffectDecorator {

    public static final String ID = "thorns";
    public static final String LABEL = "Espinas";
    public static final int BASE_TURNS = 3;
    public static final int REFLECT_PERCENT = 30;

    public ThornsDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.BUFF, Duration.ofTurns(BASE_TURNS));
    }

    private ThornsDecorator(Combatant newWrapped, ThornsDecorator source) {
        super(newWrapped, source);
    }

    @Override
    public DamageResult takeDamage(Damage damage, TurnContext ctx) {
        DamageResult result = wrapped.takeDamage(damage, ctx);
        if (!damage.reflectable() || result.taken() == 0) {
            return result;
        }
        int reflected = Math.max(1, result.taken() * REFLECT_PERCENT / 100);
        return result.withReflected(result.reflected() + reflected);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new ThornsDecorator(newWrapped, this);
    }
}

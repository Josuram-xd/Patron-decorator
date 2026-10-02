package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class RegenerationDecorator extends EffectDecorator {

    public static final String ID = "regeneration";
    public static final String LABEL = "Regeneración";
    public static final int BASE_TURNS = 3;
    public static final int HEAL_PER_TURN = 8;

    public RegenerationDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.BUFF, Duration.ofTurns(BASE_TURNS));
    }

    private RegenerationDecorator(Combatant newWrapped, RegenerationDecorator source) {
        super(newWrapped, source);
    }

    @Override
    public void onTurnStart(TurnContext ctx) {
        ctx.heal(id(), HEAL_PER_TURN, ID);
        wrapped.onTurnStart(ctx);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new RegenerationDecorator(newWrapped, this);
    }
}

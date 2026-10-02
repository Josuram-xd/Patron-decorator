package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class PoisonDecorator extends EffectDecorator {

    public static final String ID = "poison";
    public static final String LABEL = "Envenenado";
    public static final int BASE_TURNS = 3;
    public static final int DAMAGE_PER_TURN = 6;

    private final int damagePerTurn;

    public PoisonDecorator(Combatant wrapped) {
        this(wrapped, DAMAGE_PER_TURN, BASE_TURNS);
    }

    public PoisonDecorator(Combatant wrapped, int damagePerTurn, int turns) {
        super(wrapped, ID, LABEL, Category.DEBUFF, Duration.ofTurns(turns));
        this.damagePerTurn = damagePerTurn;
    }

    private PoisonDecorator(Combatant newWrapped, PoisonDecorator source) {
        super(newWrapped, source);
        this.damagePerTurn = source.damagePerTurn;
    }

    @Override
    public void onTurnStart(TurnContext ctx) {
        ctx.directDamage(id(), damagePerTurn, DamageType.POISON, ID);
        wrapped.onTurnStart(ctx);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new PoisonDecorator(newWrapped, this);
    }
}

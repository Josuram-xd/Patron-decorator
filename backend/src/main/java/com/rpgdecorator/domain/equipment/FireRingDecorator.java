package com.rpgdecorator.domain.equipment;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;

public final class FireRingDecorator extends EffectDecorator {

    public static final String ID = "fire_ring";
    public static final String LABEL = "Anillo de fuego";
    public static final int ELEMENTAL_BONUS = 4;

    public FireRingDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.EQUIPMENT, Duration.PERMANENT);
    }

    @Override
    public Damage modifyOutgoingDamage(Damage damage, TurnContext ctx) {
        Damage inner = wrapped.modifyOutgoingDamage(damage, ctx);
        return inner.withElemental(inner.elemental() + ELEMENTAL_BONUS);
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new FireRingDecorator(newWrapped);
    }
}

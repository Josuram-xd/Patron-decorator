package com.rpgdecorator.domain.effects;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.Duration;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import java.util.Map;

public final class ShieldDecorator extends EffectDecorator {

    public static final String ID = "shield";
    public static final String LABEL = "Escudo";
    public static final int BASE_TURNS = 3;
    public static final int BASE_ABSORPTION = 20;
    public static final int MAX_ABSORPTION = 40;

    private int absorption;

    public ShieldDecorator(Combatant wrapped) {
        super(wrapped, ID, LABEL, Category.BUFF, Duration.ofTurns(BASE_TURNS));
        this.absorption = BASE_ABSORPTION;
    }

    private ShieldDecorator(Combatant newWrapped, ShieldDecorator source) {
        super(newWrapped, source);
        this.absorption = source.absorption;
    }

    public int absorption() {
        return absorption;
    }

    @Override
    public Map<String, Integer> extra() {
        return Map.of("absorption", absorption);
    }

    @Override
    public DamageResult takeDamage(Damage damage, TurnContext ctx) {
        int absorbedPhysical = Math.min(absorption, damage.physical());
        int absorbedElemental = Math.min(absorption - absorbedPhysical, damage.elemental());
        int absorbed = absorbedPhysical + absorbedElemental;
        absorption -= absorbed;
        if (absorbed > 0) {
            ctx.emit(new CombatEvent.Absorbed(id(), absorbed, absorption));
        }
        Damage passed = damage
                .withPhysical(damage.physical() - absorbedPhysical)
                .withElemental(damage.elemental() - absorbedElemental);
        DamageResult result = wrapped.takeDamage(passed, ctx);
        return result.withAbsorbed(result.absorbed() + absorbed);
    }

    @Override
    public boolean shouldBeRemoved() {
        return super.shouldBeRemoved() || absorption == 0;
    }

    @Override
    public void refresh(EffectDecorator incoming) {
        super.refresh(incoming);
        if (incoming instanceof ShieldDecorator shield) {
            absorption = Math.min(MAX_ABSORPTION, absorption + shield.absorption);
        }
    }

    @Override
    public EffectDecorator copyOnto(Combatant newWrapped) {
        return new ShieldDecorator(newWrapped, this);
    }
}

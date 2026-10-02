package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.event.CombatEvent;

/** Formulas of design §4.1. Always works on the OUTER references, so effective stats apply. */
public final class DamageCalculator {

    public static final int CRIT_PERCENT = 150;
    public static final int MAX_EVASION = 25;

    public DamageResult attack(Combatant attacker, Combatant target, double multiplier, TurnContext ctx) {
        int raw = (int) Math.round(attacker.stats().attack() * multiplier);
        boolean critical = ctx.random().chance(attacker.stats().critChance());
        if (critical) {
            raw = raw * CRIT_PERCENT / 100;
        }
        Damage outgoing = attacker.modifyOutgoingDamage(new Damage(raw, 0, attacker.id(), critical, true), ctx);

        if (ctx.random().chance(Math.min(MAX_EVASION, target.stats().speed() * 2))) {
            ctx.emit(new CombatEvent.Evaded(target.id()));
            return new DamageResult(0, 0, 0, true);
        }

        int mitigated = Math.max(1, outgoing.physical() - target.stats().defense() / 2);
        DamageResult result = target.takeDamage(outgoing.withPhysical(mitigated), ctx);
        if (result.taken() > 0) {
            ctx.emit(new CombatEvent.DamageDealt(target.id(), result.taken(), DamageType.PHYSICAL, critical));
        }
        if (result.reflected() > 0) {
            ctx.directDamage(attacker.id(), result.reflected(), DamageType.REFLECTED, null);
        }
        attacker.onDamageDealt(result, ctx);
        return result;
    }
}

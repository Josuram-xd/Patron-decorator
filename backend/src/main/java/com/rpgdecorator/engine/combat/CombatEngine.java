package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.Target;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.CombatResult;
import com.rpgdecorator.domain.event.RemovalReason;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.effects.EffectManager;
import com.rpgdecorator.engine.effects.Layer;
import java.util.List;
import java.util.Objects;

/**
 * Client of the Decorator pattern: runs a full round (hero turn, then enemy turn) of design §5.3.
 *
 * <p>The outer reference of a chain changes whenever a layer is added or removed, so combatants
 * are always re-read from the {@link Combat} by id after every {@link EffectManager} operation.
 */
public final class CombatEngine {

    private static final String GUARD = "guard";

    private final EffectManager effects;
    private final DamageCalculator calculator;
    private final EnemyAI enemyAi;

    public CombatEngine(EffectManager effects, DamageCalculator calculator, EnemyAI enemyAi) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.enemyAi = Objects.requireNonNull(enemyAi, "enemyAi");
    }

    /**
     * Validates the hero action, plays both turns and returns the events of this round, in order.
     *
     * @throws InvalidActionException if the combat is over or the action is not allowed
     */
    public List<LoggedEvent> executeRound(Combat combat, Action heroAction, RandomSource random) {
        TurnContext ctx = new TurnContextImpl(combat, random);
        validate(combat, heroAction, ctx);
        int mark = combat.lastSeq();
        String heroId = combat.hero().id();
        String enemyId = combat.enemy().id();

        takeTurn(combat, heroId, heroAction, ctx);
        if (!finishIfOver(combat, heroId, ctx)) {
            takeTurn(combat, enemyId, enemyAi.decide(combat, random), ctx);
            if (!finishIfOver(combat, enemyId, ctx)) {
                combat.nextRound();
            }
        }
        return combat.eventsSince(mark);
    }

    private void validate(Combat combat, Action action, TurnContext ctx) {
        combat.requireInProgress();
        Combatant hero = combat.hero();
        boolean canAct = hero.canAct(ctx);
        if (action instanceof Action.Pass) {
            if (canAct) {
                throw new InvalidActionException(ErrorCode.ACTION_NOT_ALLOWED,
                        "Solo puedes pasar el turno si no puedes actuar");
            }
            return;
        }
        if (!canAct) {
            throw new InvalidActionException(ErrorCode.ACTION_NOT_ALLOWED,
                    "No puedes actuar este turno: solo puedes pasar");
        }
        if (action instanceof Action.UseAbility use) {
            Ability ability = combat.ability(hero.id(), use.abilityId());
            if (ability == null) {
                throw new InvalidActionException(ErrorCode.INVALID_VALUE,
                        "La habilidad no existe: " + use.abilityId());
            }
            if (!EnemyAI.isReady(combat, hero.id(), ability.id())) {
                // Stored value minus the decrement this turn would apply (see EnemyAI#isReady).
                int turns = combat.cooldown(hero.id(), ability.id()) - 1;
                throw new InvalidActionException(ErrorCode.ABILITY_ON_COOLDOWN,
                        ability.name() + " estará disponible en " + turns + (turns == 1 ? " turno" : " turnos"));
            }
        }
    }

    private void takeTurn(Combat combat, String actorId, Action action, TurnContext ctx) {
        ctx.emit(new CombatEvent.TurnStarted(actorId));
        combat.decrementCooldowns(actorId);

        combat.combatant(actorId).onTurnStart(ctx);
        if (combat.combatant(actorId).currentHealth() == 0) {
            ctx.emit(new CombatEvent.Death(actorId));
            return;
        }

        if (!combat.combatant(actorId).canAct(ctx)) {
            ctx.emit(new CombatEvent.TurnSkipped(actorId, blockingEffectId(combat.combatant(actorId))));
        } else {
            resolve(combat, actorId, action, ctx);
        }

        combat.replace(effects.advanceTurn(combat.combatant(actorId), ctx));
        ctx.emit(new CombatEvent.TurnEnded(actorId));
    }

    private void resolve(Combat combat, String actorId, Action action, TurnContext ctx) {
        String rivalId = combat.opponentOf(actorId).id();
        ctx.emit(new CombatEvent.ActionTaken(actorId, action.type(), action.abilityId()));
        switch (action) {
            case Action.Attack attack -> strike(combat, actorId, rivalId, 1.0, ctx);
            case Action.Defend defend -> combat.replace(effects.apply(combat.combatant(actorId), GUARD, true, ctx));
            case Action.UseAbility use -> {
                Ability ability = combat.ability(actorId, use.abilityId());
                useAbility(combat, actorId, rivalId, ability, ctx);
                combat.setCooldown(actorId, ability.id(), ability.cooldown());
            }
            case Action.Pass pass -> {
            }
        }
        announceDeath(combat, rivalId, ctx);
        announceDeath(combat, actorId, ctx);
    }

    // Design §4.7: self effects, then damage, then effects on the opponent (unless evaded), then purge.
    private void useAbility(Combat combat, String actorId, String rivalId, Ability ability, TurnContext ctx) {
        for (String effectId : ability.effectsOn(Target.SELF)) {
            combat.replace(effects.apply(combat.combatant(actorId), effectId, true, ctx));
        }
        boolean landed = true;
        if (ability.attacks()) {
            landed = strike(combat, actorId, rivalId, ability.damageMultiplier(), ctx);
        }
        if (combat.combatant(rivalId).currentHealth() == 0) {
            return;
        }
        if (landed) {
            for (String effectId : ability.effectsOn(Target.OPPONENT)) {
                combat.replace(effects.apply(combat.combatant(rivalId), effectId, false, ctx));
            }
        }
        if (ability.purgesOpponent()) {
            combat.replace(effects.purge(combat.combatant(rivalId), ctx));
        }
    }

    /** One hit of design §4.1. Returns false if the target evaded it. */
    private boolean strike(Combat combat, String actorId, String rivalId, double multiplier, TurnContext ctx) {
        Combatant attacker = combat.combatant(actorId);
        Combatant target = combat.combatant(rivalId);
        DamageCalculator.Outcome outcome = calculator.compute(attacker, target, multiplier, ctx);
        if (outcome.evaded()) {
            ctx.emit(new CombatEvent.Evaded(rivalId));
            return false;
        }

        DamageResult result = target.takeDamage(outcome.damage(), ctx);
        if (result.taken() > 0) {
            ctx.emit(new CombatEvent.DamageDealt(rivalId, result.taken(), DamageType.PHYSICAL, outcome.critical()));
        }
        // A shield emptied by this hit leaves the chain right away instead of waiting for its owner's turn.
        combat.replace(effects.remove(combat.combatant(rivalId),
                layer -> layer.shouldBeRemoved() && !layer.duration().isExpired(), RemovalReason.DEPLETED, ctx));

        if (result.reflected() > 0) {
            ctx.directDamage(actorId, result.reflected(), DamageType.REFLECTED, null);
        }
        attacker.onDamageDealt(result, ctx);
        return true;
    }

    private void announceDeath(Combat combat, String combatantId, TurnContext ctx) {
        if (combat.combatant(combatantId).currentHealth() == 0) {
            ctx.emit(new CombatEvent.Death(combatantId));
        }
    }

    private boolean finishIfOver(Combat combat, String justActedId, TurnContext ctx) {
        String rivalId = combat.opponentOf(justActedId).id();
        String loserId;
        if (combat.combatant(rivalId).currentHealth() == 0) {
            loserId = rivalId;
        } else if (combat.combatant(justActedId).currentHealth() == 0) {
            loserId = justActedId;
        } else {
            return false;
        }
        boolean victory = combat.isEnemy(loserId);
        combat.finish(victory ? CombatStatus.VICTORY : CombatStatus.DEFEAT);
        ctx.emit(new CombatEvent.CombatEnded(victory ? CombatResult.VICTORY : CombatResult.DEFEAT));
        return true;
    }

    private String blockingEffectId(Combatant outer) {
        return effects.layers(outer).stream()
                .filter(layer -> layer.category() == Category.CONTROL)
                .map(Layer::id)
                .findFirst()
                .orElse(null);
    }
}

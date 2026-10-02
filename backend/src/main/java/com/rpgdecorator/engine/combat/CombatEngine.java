package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.DamageType;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.EffectApplication;
import com.rpgdecorator.domain.catalog.Target;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.CombatResult;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.effects.EffectManager;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Runs one round of a combat (design 5.3): the hero's turn, then the enemy's turn.
 *
 * <p>Every combatant is treated as an opaque {@link Combatant}; anything that needs to look inside
 * a decorator chain goes through the {@link EffectManager}. After every manager operation the new
 * outer reference is stored with {@link Combat#replace} and re-read by id (design 3.6, 5.3).
 *
 * <p><b>Turn</b> ({@code takeTurn}), events in this order:
 * <ol>
 *   <li>{@code TURN_STARTED}; the actor's cooldowns go down by 1 (no event).</li>
 *   <li>{@code onTurnStart} (poison {@code DAMAGE}, regeneration {@code HEAL}).</li>
 *   <li>If the actor died there: {@code DEATH}, and the turn closes (step 6).</li>
 *   <li>If it cannot act: {@code TURN_SKIPPED}; otherwise {@code ACTION} + resolution (design 4.1, 4.7).</li>
 *   <li>{@code advanceTurn} of the actor's chain ({@code EFFECT_REMOVED} for expired/depleted layers).</li>
 *   <li>{@code TURN_ENDED}.</li>
 * </ol>
 *
 * <p><b>Decisions where design 5.3 is silent</b> (to confirm in open-questions):
 * <ul>
 *   <li><b>Deaths.</b> {@code DEATH} is emitted when a combatant's health reaches 0: after
 *       {@code onTurnStart}, and after a hit (the target first, then the attacker killed by thorns).
 *       A death stops the turn: no further resolution step and no {@code advanceTurn}, but the turn
 *       is still closed with {@code TURN_ENDED} (every {@code TURN_STARTED} has its
 *       {@code TURN_ENDED}). Then the combat ends: {@code COMBAT_ENDED}.</li>
 *   <li><b>Both dead in the same turn</b> (killing blow + lethal thorns): the actor's opponent dying
 *       counts first, so a hero turn ends in {@code VICTORY} and an enemy turn in {@code DEFEAT}.</li>
 *   <li><b>Cooldowns.</b> Using an ability sets its cooldown to {@code ability.cooldown()}; cooldowns
 *       are decremented at the start of the owner's turn. The hero's action is validated before that
 *       decrement, so it is checked against the value it <i>will</i> have: ready when the stored
 *       cooldown is {@code <= 1} ({@link #isReady}, same rule as {@code EnemyAI.isReady}). cd 3 used
 *       on turn 1 is usable again on turn 4 (T-204), on both sides. The enemy decider is called
 *       before the enemy's turn, as in design 5.3 (also when it then dies of poison or is frozen;
 *       the chosen action is just not resolved).</li>
 *   <li><b>DAMAGE of a hit.</b> {@code damageType} is {@code PHYSICAL} when the mitigated damage has a
 *       physical part, otherwise {@code ELEMENTAL}. When a shield absorbs the whole hit, only
 *       {@code ABSORBED} is emitted (no {@code DAMAGE} with amount 0).</li>
 *   <li><b>Purge</b> (step 4 of an ability) is applied even if the hit was evaded: design 4.7 only
 *       makes the opponent's <i>effects</i> depend on the evasion.</li>
 * </ul>
 */
public final class CombatEngine {

    /** Damage multiplier of a basic attack (design 4.1). */
    public static final double ATTACK_MULTIPLIER = 1.0;

    /** Effect applied by {@code Defend} (design 5.2). */
    public static final String GUARD_EFFECT_ID = "guard";

    /** {@code sourceEffectId} of reflected damage. */
    public static final String THORNS_EFFECT_ID = "thorns";

    private final EffectManager effects;
    private final DamageCalculator calculator;
    private final BiFunction<Combat, RandomSource, Action> enemyDecider;

    /**
     * @param enemyDecider chooses the enemy action; production passes {@code enemyAI::decide}. It is
     *                     called once per round, before the enemy's turn starts (design 5.3), so
     *                     before its cooldown decrement; "ready" is stored cooldown {@code <= 1}.
     */
    public CombatEngine(EffectManager effects, DamageCalculator calculator,
                        BiFunction<Combat, RandomSource, Action> enemyDecider) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.enemyDecider = Objects.requireNonNull(enemyDecider, "enemyDecider");
    }

    /**
     * Runs a whole round: hero turn, enemy turn (unless the combat already ended), next round.
     *
     * @throws InvalidActionException if the action is not valid now; nothing is mutated then
     */
    public void executeRound(Combat combat, Action heroAction, RandomSource random) {
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(heroAction, "heroAction");
        Objects.requireNonNull(random, "random");
        TurnContextImpl ctx = new TurnContextImpl(combat, random);
        validate(combat, heroAction, ctx);

        String heroId = combat.hero().id();
        String enemyId = combat.enemy().id();

        takeTurn(combat, heroId, heroAction, ctx);
        if (endIfSomeoneDied(combat, enemyId, heroId, ctx)) {
            return;
        }
        // design 5.3: takeTurn(ENEMY, ai.decide(combat)) - decided before the enemy's turn starts.
        Action enemyAction = Objects.requireNonNull(enemyDecider.apply(combat, random), "enemy action");
        takeTurn(combat, enemyId, enemyAction, ctx);
        if (endIfSomeoneDied(combat, heroId, enemyId, ctx)) {
            return;
        }
        combat.nextRound();
    }

    // ------------------------------------------------------------------ validation

    private void validate(Combat combat, Action action, TurnContextImpl ctx) {
        combat.requireInProgress();
        Combatant hero = combat.hero();

        Ability ability = null;
        if (action instanceof Action.UseAbility use) {
            ability = combat.ability(hero.id(), use.abilityId());
            if (ability == null) {
                throw new InvalidActionException(ErrorCode.INVALID_VALUE,
                        "Habilidad desconocida: " + use.abilityId());
            }
        }

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

        if (ability != null && !isReady(combat, hero.id(), ability.id())) {
            int remaining = combat.cooldown(hero.id(), ability.id()) - 1;   // value at resolution time
            throw new InvalidActionException(ErrorCode.ABILITY_ON_COOLDOWN,
                    ability.name() + " estará disponible en " + remaining
                            + (remaining == 1 ? " turno" : " turnos"));
        }
    }

    /**
     * "Ready" as seen BEFORE the turn's cooldown decrement (design 5.3): stored cooldown {@code <= 1}.
     * Same rule as {@code EnemyAI.isReady}, so both sides share one meaning of cooldown.
     */
    static boolean isReady(Combat combat, String combatantId, String abilityId) {
        return combat.cooldown(combatantId, abilityId) <= 1;
    }

    // ------------------------------------------------------------------ turn

    private void takeTurn(Combat combat, String actorId, Action action, TurnContextImpl ctx) {
        ctx.emit(new CombatEvent.TurnStarted(actorId));
        combat.decrementCooldowns(actorId);

        combat.combatant(actorId).onTurnStart(ctx);
        if (emitDeaths(combat, actorId, ctx)) {
            ctx.emit(new CombatEvent.TurnEnded(actorId));
            return;
        }

        Combatant actor = combat.combatant(actorId);
        if (!actor.canAct(ctx)) {
            ctx.emit(new CombatEvent.TurnSkipped(actorId, effects.blockingEffectId(actor, ctx)));
        } else {
            ctx.emit(new CombatEvent.ActionTaken(actorId, action.type(), action.abilityId()));
            boolean someoneDied = resolve(combat, actorId, action, ctx);
            if (someoneDied) {
                ctx.emit(new CombatEvent.TurnEnded(actorId));
                return;
            }
        }

        combat.replace(effects.advanceTurn(combat.combatant(actorId), ctx));
        ctx.emit(new CombatEvent.TurnEnded(actorId));
    }

    // ------------------------------------------------------------------ resolution

    /** @return {@code true} if a combatant died (the rest of the turn is skipped) */
    private boolean resolve(Combat combat, String actorId, Action action, TurnContextImpl ctx) {
        return switch (action) {
            case Action.Attack attack -> hit(combat, actorId, ATTACK_MULTIPLIER, ctx).someoneDied();
            case Action.Defend defend -> {
                applyEffect(combat, actorId, GUARD_EFFECT_ID, true, ctx);
                yield false;
            }
            case Action.UseAbility use -> useAbility(combat, actorId, use.abilityId(), ctx);
            case Action.Pass pass -> false;
        };
    }

    /** Design 4.7: 1) self effects, 2) damage, 3) opponent effects unless evaded, 4) purge. */
    private boolean useAbility(Combat combat, String actorId, String abilityId, TurnContextImpl ctx) {
        Ability ability = combat.ability(actorId, abilityId);
        if (ability == null) {
            throw new IllegalStateException(actorId + " has no ability " + abilityId);
        }
        combat.setCooldown(actorId, ability.id(), ability.cooldown());
        String opponentId = combat.opponentOf(actorId).id();

        for (EffectApplication effect : ability.effectsOn(Target.SELF)) {
            applyEffect(combat, actorId, effect.effectId(), true, ctx);
        }

        boolean evaded = false;
        if (ability.dealsDamage()) {
            HitOutcome outcome = hit(combat, actorId, ability.damageMultiplier(), ctx);
            if (outcome.someoneDied()) {
                return true;
            }
            evaded = outcome.evaded();
        }

        if (!evaded) {
            for (EffectApplication effect : ability.effectsOn(Target.OPPONENT)) {
                applyEffect(combat, opponentId, effect.effectId(), false, ctx);
            }
        }

        if (ability.purgesOpponent()) {
            combat.replace(effects.purge(combat.combatant(opponentId), ctx));
        }
        return false;
    }

    private void applyEffect(Combat combat, String targetId, String effectId, boolean duringOwnTurn,
                             TurnContextImpl ctx) {
        combat.replace(effects.apply(combat.combatant(targetId), effectId, duringOwnTurn, ctx));
    }

    private record HitOutcome(boolean evaded, boolean someoneDied) {}

    /**
     * One hit (design 4.1): calculator, then {@code takeDamage} on the target's outer chain,
     * {@code ABSORBED}/{@code DAMAGE}, thorns reflection (never reflected again), and lifesteal via
     * {@code onDamageDealt}. Deaths are checked once the hit is fully resolved.
     */
    private HitOutcome hit(Combat combat, String attackerId, double multiplier, TurnContextImpl ctx) {
        String targetId = combat.opponentOf(attackerId).id();
        DamageCalculator.Outcome outcome = calculator.compute(
                combat.combatant(attackerId), combat.combatant(targetId), multiplier, ctx);
        if (outcome.evaded()) {
            ctx.emit(new CombatEvent.Evaded(targetId));
            return new HitOutcome(true, false);
        }

        DamageResult result = combat.combatant(targetId).takeDamage(outcome.damage(), ctx);
        if (result.absorbed() > 0) {
            ctx.emit(new CombatEvent.Absorbed(targetId, result.absorbed(),
                    effects.shieldAbsorption(combat.combatant(targetId))));
        }
        if (result.taken() > 0 || result.absorbed() == 0) {
            DamageType type = outcome.damage().physical() > 0 ? DamageType.PHYSICAL : DamageType.ELEMENTAL;
            ctx.emit(new CombatEvent.DamageDealt(targetId, result.taken(), type, outcome.critical()));
        }
        if (result.reflected() > 0) {
            ctx.directDamage(attackerId, result.reflected(), DamageType.REFLECTED, THORNS_EFFECT_ID);
        }
        combat.combatant(attackerId).onDamageDealt(result, ctx);

        boolean targetDied = emitDeaths(combat, targetId, ctx);
        boolean attackerDied = emitDeaths(combat, attackerId, ctx);
        return new HitOutcome(false, targetDied || attackerDied);
    }

    // ------------------------------------------------------------------ deaths and end

    /** Emits {@code DEATH} if that combatant is at 0 health. */
    private static boolean emitDeaths(Combat combat, String combatantId, TurnContextImpl ctx) {
        if (combat.combatant(combatantId).currentHealth() > 0) {
            return false;
        }
        ctx.emit(new CombatEvent.Death(combatantId));
        return true;
    }

    /**
     * After a turn: ends the combat if someone is dead. The actor's opponent is checked first, so
     * if both died in the same turn the actor wins.
     */
    private static boolean endIfSomeoneDied(Combat combat, String opponentId, String actorId,
                                            TurnContextImpl ctx) {
        String deadId;
        if (combat.combatant(opponentId).currentHealth() == 0) {
            deadId = opponentId;
        } else if (combat.combatant(actorId).currentHealth() == 0) {
            deadId = actorId;
        } else {
            return false;
        }
        boolean victory = combat.isEnemy(deadId);
        combat.finish(victory ? CombatStatus.VICTORY : CombatStatus.DEFEAT);
        ctx.emit(new CombatEvent.CombatEnded(victory ? CombatResult.VICTORY : CombatResult.DEFEAT));
        return true;
    }
}

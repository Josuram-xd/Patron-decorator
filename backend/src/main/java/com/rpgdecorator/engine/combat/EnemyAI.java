package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.catalog.AiView;
import com.rpgdecorator.domain.catalog.EnemyAbility;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.engine.effects.EffectManager;

import java.util.Objects;

/**
 * Chooses the enemy action of a round (design 4.8).
 *
 * <p>It walks the abilities of the {@code EnemyDefinition} in priority order and uses the first one
 * that is <b>ready</b> and whose {@code AiCondition} passes. If none qualifies, it attacks.
 *
 * <h2>Cooldown rule ("ready")</h2>
 * {@code decide} runs in {@code executeRound} BEFORE {@code takeTurn(ENEMY)} (design 5.3), and
 * {@code takeTurn} decrements the actor's cooldowns at its start, before the action is resolved.
 * So the AI sees the stored value one decrement too early. An ability counts as ready when its
 * cooldown is 0 <i>at the moment it would be resolved</i>, i.e. when the stored value is
 * {@code <= 1} (see {@link #isReady}). With that rule an ability with cd 3 used on turn 1
 * (stored 3 after resolving) is stored as 1 when turn 4 is decided, and is used on turn 4.
 * The {@code CombatEngine} must validate hero abilities with the same rule
 * ({@link #isReady}) so both sides share one meaning of "cooldown".
 *
 * <h2>Randomness</h2>
 * The condition of an ability is evaluated ONLY when the ability is ready. So the 50 % roll of
 * {@code goblin.dirty_dagger} consumes a value from the {@link RandomSource} only on the turns
 * where the dagger could actually be used; turns on cooldown do not touch the random stream.
 * Evaluation stops at the first ability chosen, so lower-priority conditions are not evaluated.
 *
 * <p>The AI never walks decorator chains itself: the opponent's BUFF count comes from
 * {@link EffectManager#layers} (design 3.3).
 */
public final class EnemyAI {

    private final EffectManager effects;

    public EnemyAI(EffectManager effects) {
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /**
     * The action of the enemy for the current round. Reads the OUTER references kept in
     * {@code combat}; it does not modify the combat.
     */
    public Action decide(Combat combat, RandomSource random) {
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(random, "random");
        Combatant self = combat.enemy();
        Combatant opponent = combat.hero();
        AiView view = null;   // built lazily: only needed when some ability is ready
        for (EnemyAbility candidate : combat.enemyDefinition().abilities()) {
            String abilityId = candidate.ability().id();
            if (!isReady(combat, self.id(), abilityId)) {
                continue;
            }
            if (view == null) {
                view = new AiView(self, opponent, buffCount(opponent));
            }
            if (candidate.condition().test(view, random)) {
                return Action.useAbility(abilityId);
            }
        }
        return Action.attack();
    }

    /**
     * True if the ability can be used in the turn that is about to be played by that combatant,
     * evaluated BEFORE the turn's cooldown decrement (design 5.3): stored cooldown {@code <= 1}.
     */
    public static boolean isReady(Combat combat, String combatantId, String abilityId) {
        return combat.cooldown(combatantId, abilityId) <= 1;
    }

    /** Number of BUFF layers in the chain of {@code outer}. */
    private int buffCount(Combatant outer) {
        return (int) effects.layers(outer).stream()
                .filter(layer -> layer.category() == Category.BUFF)
                .count();
    }
}

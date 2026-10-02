package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.EnemyDefinition.Situation;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.engine.effects.EffectManager;

/** Uses the first ability, in priority order, that is off cooldown and whose condition holds. */
public final class EnemyAI {

    private final EffectManager effectManager;

    public EnemyAI(EffectManager effectManager) {
        this.effectManager = effectManager;
    }

    public Action decide(Combat combat, RandomSource random) {
        Combatant enemy = combat.enemy();
        int opponentBuffs = (int) effectManager.layers(combat.hero()).stream()
                .filter(layer -> layer.category() == Category.BUFF)
                .count();
        Situation situation = new Situation(enemy.currentHealth(), enemy.stats().maxHealth(), opponentBuffs, random);

        for (Ability ability : combat.enemyDefinition().abilities()) {
            if (combat.cooldown(Side.ENEMY, ability.id()) == 0
                    && combat.enemyDefinition().conditionMet(ability.id(), situation)) {
                return new Action.UseAbility(ability.id());
            }
        }
        return new Action.Attack();
    }
}

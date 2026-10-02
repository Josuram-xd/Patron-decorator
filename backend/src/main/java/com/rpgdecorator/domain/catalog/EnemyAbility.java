package com.rpgdecorator.domain.catalog;

/** An enemy ability together with the AI condition for using it. */
public record EnemyAbility(Ability ability, AiCondition condition) {

    public static EnemyAbility always(Ability ability) {
        return new EnemyAbility(ability, AiCondition.ALWAYS);
    }
}

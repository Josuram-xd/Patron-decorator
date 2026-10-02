package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.engine.combat.Combat;
import com.rpgdecorator.engine.combat.LoggedEvent;

import java.util.List;
import java.util.Objects;

/**
 * Statistics of a whole expedition (RF-26, api-contract {@code statistics}), accumulated across
 * encounters. Only {@link ExpeditionService} mutates it, under the expedition lock.
 *
 * <p>What counts:
 * <ul>
 *   <li>{@code enemiesDefeated}: combats won.</li>
 *   <li>{@code totalRounds}: rounds played = accepted actions (one action = one round, including
 *       the round in which the combat ends).</li>
 *   <li>{@code damageDealt} / {@code damageTaken}: the {@code amount} of every {@code DAMAGE} event
 *       whose target is the enemy / the hero, of any type: hits (critical or not), poison and
 *       reflected (thorns) damage all count. The amount is the health actually lost, so damage
 *       absorbed by a shield ({@code ABSORBED}) and overkill do not count.</li>
 * </ul>
 */
public final class RunStatistics {

    private int enemiesDefeated;
    private int totalRounds;
    private int damageDealt;
    private int damageTaken;

    public int enemiesDefeated() {
        return enemiesDefeated;
    }

    public int totalRounds() {
        return totalRounds;
    }

    public int damageDealt() {
        return damageDealt;
    }

    public int damageTaken() {
        return damageTaken;
    }

    void addEnemyDefeated() {
        enemiesDefeated++;
    }

    /** Adds one played round and the damage of its events. */
    void recordRound(Combat combat, List<LoggedEvent> events) {
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(events, "events");
        totalRounds++;
        for (LoggedEvent logged : events) {
            if (logged.event() instanceof CombatEvent.DamageDealt damage) {
                if (combat.isEnemy(damage.targetId())) {
                    damageDealt += damage.amount();
                } else if (combat.isHero(damage.targetId())) {
                    damageTaken += damage.amount();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "RunStatistics[enemiesDefeated=" + enemiesDefeated + ", totalRounds=" + totalRounds
                + ", damageDealt=" + damageDealt + ", damageTaken=" + damageTaken + "]";
    }
}

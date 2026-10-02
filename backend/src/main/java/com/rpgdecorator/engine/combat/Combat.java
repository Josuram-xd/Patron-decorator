package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.catalog.HeroClassCatalog.HeroClass;
import com.rpgdecorator.domain.event.CombatEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One encounter. Holds the OUTER reference of each chain, which changes whenever a layer is
 * added or removed: always read {@link #hero()} / {@link #enemy()} again after touching a chain.
 */
public final class Combat {

    /** A domain event stamped with its order in this combat and the round it happened in. */
    public record LoggedEvent(int seq, int round, CombatEvent event) {
    }

    private final HeroClass heroClass;
    private final EnemyDefinition enemyDefinition;
    private final Map<String, Integer> heroCooldowns = new LinkedHashMap<>();
    private final Map<String, Integer> enemyCooldowns = new LinkedHashMap<>();
    private final List<LoggedEvent> log = new ArrayList<>();
    private Combatant hero;
    private Combatant enemy;
    private CombatStatus status = CombatStatus.IN_PROGRESS;
    private int round = 1;
    private int eventSequence;

    public Combat(Combatant hero, HeroClass heroClass, Combatant enemy, EnemyDefinition enemyDefinition) {
        if (hero.side() != Side.HERO || enemy.side() != Side.ENEMY) {
            throw new IllegalArgumentException("Combat needs a HERO and an ENEMY");
        }
        this.hero = hero;
        this.heroClass = heroClass;
        this.enemy = enemy;
        this.enemyDefinition = enemyDefinition;
        heroClass.abilities().forEach(a -> heroCooldowns.put(a.id(), 0));
        enemyDefinition.abilities().forEach(a -> enemyCooldowns.put(a.id(), 0));
    }

    public CombatStatus status() {
        return status;
    }

    public int round() {
        return round;
    }

    public Combatant hero() {
        return hero;
    }

    public Combatant enemy() {
        return enemy;
    }

    public HeroClass heroClass() {
        return heroClass;
    }

    public EnemyDefinition enemyDefinition() {
        return enemyDefinition;
    }

    public Combatant combatant(Side side) {
        return side == Side.HERO ? hero : enemy;
    }

    public Combatant combatant(String id) {
        if (hero.id().equals(id)) {
            return hero;
        }
        if (enemy.id().equals(id)) {
            return enemy;
        }
        throw new IllegalArgumentException("Unknown combatant: " + id);
    }

    /** Replaces the outer reference of whichever chain {@code outer} belongs to. */
    public void update(Combatant outer) {
        if (outer.side() == Side.HERO) {
            hero = outer;
        } else {
            enemy = outer;
        }
    }

    public List<Ability> abilities(Side side) {
        return side == Side.HERO ? heroClass.abilities() : enemyDefinition.abilities();
    }

    public Optional<Ability> findAbility(Side side, String abilityId) {
        return abilities(side).stream().filter(a -> a.id().equals(abilityId)).findFirst();
    }

    public int cooldown(Side side, String abilityId) {
        return cooldowns(side).getOrDefault(abilityId, 0);
    }

    void startCooldown(Side side, Ability ability) {
        cooldowns(side).put(ability.id(), ability.cooldown());
    }

    void tickCooldowns(Side side) {
        cooldowns(side).replaceAll((id, turns) -> Math.max(0, turns - 1));
    }

    private Map<String, Integer> cooldowns(Side side) {
        return side == Side.HERO ? heroCooldowns : enemyCooldowns;
    }

    public List<LoggedEvent> log() {
        return Collections.unmodifiableList(log);
    }

    void record(CombatEvent event) {
        log.add(new LoggedEvent(++eventSequence, round, event));
    }

    void nextRound() {
        round++;
    }

    void finish(CombatStatus result) {
        status = result;
    }
}

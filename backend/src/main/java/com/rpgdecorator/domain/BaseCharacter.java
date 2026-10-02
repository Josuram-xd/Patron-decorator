package com.rpgdecorator.domain;

import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.catalog.HeroClass;
import java.util.Objects;

/** Concrete component: the undecorated hero or enemy. Owns the health of the whole chain. */
public final class BaseCharacter implements Combatant {

    public static final String HERO_ID = "hero";
    public static final String ENEMY_ID = "enemy";

    private final String id;
    private final String name;
    private final Side side;
    private final Stats baseStats;
    private int health;

    public BaseCharacter(String id, String name, Side side, Stats baseStats) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.side = Objects.requireNonNull(side);
        this.baseStats = Objects.requireNonNull(baseStats);
        this.health = baseStats.maxHealth();
    }

    /** The hero of an expedition; its id is always {@code "hero"}. */
    public static BaseCharacter hero(HeroClass heroClass) {
        return new BaseCharacter(HERO_ID, heroClass.name(), Side.HERO, heroClass.stats());
    }

    /** The enemy of an encounter; its id is always {@code "enemy"}. */
    public static BaseCharacter enemy(EnemyDefinition definition) {
        return new BaseCharacter(ENEMY_ID, definition.name(), Side.ENEMY, definition.stats());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public Stats stats() {
        return baseStats;
    }

    @Override
    public int currentHealth() {
        return health;
    }

    @Override
    public void changeHealth(int delta, int effectiveMaxHealth) {
        health = Math.clamp((long) health + delta, 0, Math.max(0, effectiveMaxHealth));
    }

    @Override
    public Damage modifyOutgoingDamage(Damage damage, TurnContext ctx) {
        return damage;
    }

    @Override
    public DamageResult takeDamage(Damage damage, TurnContext ctx) {
        int taken = Math.min(health, damage.total());
        health -= taken;
        return new DamageResult(taken, 0, 0, false);
    }

    @Override
    public void onDamageDealt(DamageResult result, TurnContext ctx) {
    }

    @Override
    public boolean canAct(TurnContext ctx) {
        return true;
    }

    @Override
    public void onTurnStart(TurnContext ctx) {
    }

    @Override
    public String describeChain() {
        return name;
    }
}

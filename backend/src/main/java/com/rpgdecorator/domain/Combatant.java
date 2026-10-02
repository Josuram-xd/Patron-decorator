package com.rpgdecorator.domain;

/** Component of the Decorator pattern: every hero, enemy and effect layer implements it. */
public interface Combatant {

    String id();

    String name();

    Side side();

    Stats stats();

    int currentHealth();

    void changeHealth(int delta, int effectiveMaxHealth);

    Damage modifyOutgoingDamage(Damage damage, TurnContext ctx);

    DamageResult takeDamage(Damage damage, TurnContext ctx);

    void onDamageDealt(DamageResult result, TurnContext ctx);

    boolean canAct(TurnContext ctx);

    void onTurnStart(TurnContext ctx);

    String describeChain();
}

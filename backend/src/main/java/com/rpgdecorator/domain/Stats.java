package com.rpgdecorator.domain;

public record Stats(int maxHealth, int attack, int defense, int speed, int critChance) {

    public static final int MAX_CRIT_CHANCE = 100;

    public Stats {
        maxHealth = Math.max(0, maxHealth);
        attack = Math.max(0, attack);
        defense = Math.max(0, defense);
        speed = Math.max(0, speed);
        critChance = Math.clamp(critChance, 0, MAX_CRIT_CHANCE);
    }

    public Stats withMaxHealth(int value) {
        return new Stats(value, attack, defense, speed, critChance);
    }

    public Stats withAttack(int value) {
        return new Stats(maxHealth, value, defense, speed, critChance);
    }

    public Stats withDefense(int value) {
        return new Stats(maxHealth, attack, value, speed, critChance);
    }

    public Stats withSpeed(int value) {
        return new Stats(maxHealth, attack, defense, value, critChance);
    }

    public Stats withCritChance(int value) {
        return new Stats(maxHealth, attack, defense, speed, value);
    }
}

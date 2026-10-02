package com.rpgdecorator.domain;

public record Damage(int physical, int elemental, String sourceId, boolean critical, boolean reflectable) {

    public Damage {
        physical = Math.max(0, physical);
        elemental = Math.max(0, elemental);
    }

    public int total() {
        return physical + elemental;
    }

    public Damage withPhysical(int value) {
        return new Damage(value, elemental, sourceId, critical, reflectable);
    }

    public Damage withElemental(int value) {
        return new Damage(physical, value, sourceId, critical, reflectable);
    }
}

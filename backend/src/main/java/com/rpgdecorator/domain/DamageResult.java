package com.rpgdecorator.domain;

public record DamageResult(int taken, int absorbed, int reflected, boolean evaded) {

    public DamageResult withAbsorbed(int value) {
        return new DamageResult(taken, value, reflected, evaded);
    }

    public DamageResult withReflected(int value) {
        return new DamageResult(taken, absorbed, value, evaded);
    }
}

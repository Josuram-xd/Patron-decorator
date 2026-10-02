package com.rpgdecorator.domain.decorator;

public record Duration(int turns) {

    public static final Duration PERMANENT = new Duration(Integer.MAX_VALUE);

    public Duration {
        turns = Math.max(0, turns);
    }

    public static Duration ofTurns(int turns) {
        return new Duration(turns);
    }

    public boolean isPermanent() {
        return turns == Integer.MAX_VALUE;
    }

    public Duration decrement() {
        return isPermanent() ? this : new Duration(turns - 1);
    }

    public boolean isExpired() {
        return turns == 0;
    }
}

package com.rpgdecorator.engine.expedition;

/** Lifecycle of an expedition (design §5.5, api-contract §5). */
public enum ExpeditionStatus {
    /** The combat of the current level is being played. */
    IN_PROGRESS,
    /** The current level was won (not the boss); waiting for {@code chooseReward}. */
    AWAITING_REWARD,
    /** The boss (last level) was defeated. Final state. */
    COMPLETED,
    /** The hero died. Final state. */
    FAILED;

    /** {@code true} for {@link #COMPLETED} and {@link #FAILED}. */
    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}

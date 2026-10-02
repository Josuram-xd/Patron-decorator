package com.rpgdecorator.engine.combat;

/** Status of a {@link Combat} (api-contract §5, {@code CombatDTO.status}). */
public enum CombatStatus {
    IN_PROGRESS,
    VICTORY,
    DEFEAT;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }
}

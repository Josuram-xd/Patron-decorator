package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.event.CombatEvent;

import java.util.Objects;

/**
 * A {@link CombatEvent} as stored in the combat log (design §6): the domain record plus the
 * {@code seq} (1, 2, 3… per combat) and the {@code round} in which it happened.
 */
public record LoggedEvent(int seq, int round, CombatEvent event) {
    public LoggedEvent {
        Objects.requireNonNull(event, "event");
    }

    /** The UPPER_SNAKE type of the wrapped event. */
    public String type() {
        return event.type();
    }
}

package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.engine.combat.LoggedEvent;

import java.util.List;
import java.util.Objects;

/**
 * Result of {@link ExpeditionService#act} (api-contract §6 {@code ActionResultDTO}).
 *
 * @param expedition the expedition after the action (status already updated)
 * @param events     only the events produced by this action (the whole round), in {@code seq} order
 */
public record ActionResult(Expedition expedition, List<LoggedEvent> events) {

    public ActionResult {
        Objects.requireNonNull(expedition, "expedition");
        events = List.copyOf(events);
    }
}

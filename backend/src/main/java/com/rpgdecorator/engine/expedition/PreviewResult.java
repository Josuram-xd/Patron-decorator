package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.engine.effects.Layer;

import java.util.List;
import java.util.Objects;

/**
 * Engine-side result of a stats preview (api-contract §4 {@code PreviewDTO}); the API maps it.
 *
 * @param stats    effective stats of the outer chain
 * @param chain    {@code describeChain()} of the outer chain, e.g. {@code "Botas de viento(Espada(Arquero))"}
 * @param layers   the chain, outer to inner (base last)
 * @param replaces expedition form only: the piece that would be replaced in that slot, or {@code null}
 */
public record PreviewResult(Stats stats, String chain, List<Layer> layers, String replaces) {

    public PreviewResult {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(chain, "chain");
        layers = List.copyOf(layers);
    }
}

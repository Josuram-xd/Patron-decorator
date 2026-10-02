package com.rpgdecorator.engine.effects;

import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;

/**
 * One layer of a decorator chain, as shown by the inspector (api-contract {@code LayerDTO}).
 *
 * <p>{@link EffectManager#layers} returns them from the outermost layer (position 0) to the base
 * (always last). {@code statsAtLayer} are the stats seen from that layer inward, which makes visible
 * why the order of the decorators matters (design 3.4).
 *
 * @param position       0 = outermost layer
 * @param id             effectId / itemId of the layer, or the base character id
 * @param label          player-visible text (Spanish), or the base character name
 * @param category       category of the effect; {@code null} for the base
 * @param turnsRemaining remaining turns; {@code null} if permanent (equipment) or for the base
 * @param isBase         {@code true} only for the innermost layer ({@code BaseCharacter})
 * @param statsAtLayer   {@code stats()} of that layer
 */
public record Layer(int position, String id, String label, Category category, Integer turnsRemaining,
                    boolean isBase, Stats statsAtLayer) {
}

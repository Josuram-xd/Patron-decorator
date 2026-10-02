package com.rpgdecorator.engine.effects;

import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import java.util.Map;

/**
 * One layer of a chain as the inspector shows it. {@code category} and {@code turnsRemaining}
 * are null for the base; {@code turnsRemaining} is also null for permanent layers.
 */
public record Layer(int position, String id, String label, Category category, Integer turnsRemaining,
                    boolean base, Stats statsAtLayer, Map<String, Integer> extra) {

    public boolean temporary() {
        return !base && category != Category.EQUIPMENT;
    }
}

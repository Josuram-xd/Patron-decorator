package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.EnemyDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Draws the enemy of every level when an expedition is created (RF-04, design §5.5): one enemy per
 * level, picked with the expedition's {@link RandomSource} from {@code EnemyCatalog.byLevel(n)}.
 *
 * <p>A group with a single enemy (level 4: the dragon) is picked without consuming a random number,
 * so the boss never shifts the random sequence.
 */
public final class EnemyDraw {

    private EnemyDraw() {
    }

    /**
     * @return the enemy ids of levels {@code 1..Expedition.TOTAL_LEVELS} (index 0 = level 1)
     * @throws IllegalStateException if a level has no enemy in the catalog
     */
    public static List<String> draw(RandomSource random) {
        Objects.requireNonNull(random, "random");
        List<String> ids = new ArrayList<>(Expedition.TOTAL_LEVELS);
        for (int level = 1; level <= Expedition.TOTAL_LEVELS; level++) {
            List<EnemyDefinition> group = EnemyCatalog.byLevel(level);
            if (group.isEmpty()) {
                throw new IllegalStateException("No enemy for level " + level);
            }
            int index = group.size() == 1 ? 0 : random.nextInt(0, group.size() - 1);
            ids.add(group.get(index).id());
        }
        return List.copyOf(ids);
    }
}

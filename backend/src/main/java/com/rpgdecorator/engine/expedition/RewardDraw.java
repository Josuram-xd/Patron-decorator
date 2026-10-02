package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.catalog.EquipmentCatalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Draws the equipment offered after winning an encounter (RF-25, design §5.5): {@value #COUNT}
 * random, distinct pieces, none of them currently equipped.
 *
 * <p>Algorithm: the candidates in catalog order, then a partial Fisher-Yates shuffle of the first
 * {@value #COUNT} positions ({@code j = random.nextInt(i, n - 1)}). The order of the result is the
 * order in which the pieces are offered.
 */
public final class RewardDraw {

    /** Number of pieces offered. */
    public static final int COUNT = 3;

    private RewardDraw() {
    }

    /**
     * @param equippedIds the item ids currently equipped (never offered)
     * @return {@value #COUNT} distinct item ids (fewer only if the catalog had fewer candidates)
     */
    public static List<String> draw(Collection<String> equippedIds, RandomSource random) {
        Objects.requireNonNull(equippedIds, "equippedIds");
        Objects.requireNonNull(random, "random");
        List<String> candidates = new ArrayList<>(EquipmentCatalog.ids());
        candidates.removeAll(equippedIds);
        int count = Math.min(COUNT, candidates.size());
        for (int i = 0; i < count; i++) {
            int j = random.nextInt(i, candidates.size() - 1);
            Collections.swap(candidates, i, j);
        }
        return List.copyOf(candidates.subList(0, count));
    }
}

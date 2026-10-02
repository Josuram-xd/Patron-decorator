package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.EquipmentCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawTest {

    @Test
    void sameSeedGivesSameEnemies() {
        assertEquals(EnemyDraw.draw(new SeededRandom(42)), EnemyDraw.draw(new SeededRandom(42)));
    }

    @Test
    void sameSeedGivesSameRewards() {
        assertEquals(RewardDraw.draw(List.of("sword"), new SeededRandom(7)),
                RewardDraw.draw(List.of("sword"), new SeededRandom(7)));
    }

    @Test
    void everyLevelGetsAnEnemyOfItsOwnGroupForManySeeds() {
        for (long seed = 0; seed < 200; seed++) {
            List<String> ids = EnemyDraw.draw(new SeededRandom(seed));
            assertEquals(Expedition.TOTAL_LEVELS, ids.size());
            for (int level = 1; level <= ids.size(); level++) {
                assertEquals(level, EnemyCatalog.get(ids.get(level - 1)).level());
            }
        }
    }

    @Test
    void levelFourIsAlwaysTheDragon() {
        for (long seed = 0; seed < 50; seed++) {
            assertEquals("dragon", EnemyDraw.draw(new SeededRandom(seed)).get(3));
        }
    }

    @Test
    void differentSeedsEventuallyGiveDifferentEnemies() {
        Set<List<String>> seen = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            seen.add(EnemyDraw.draw(new SeededRandom(seed)));
        }
        assertTrue(seen.size() > 1);
    }

    @Test
    void rewardsAreThreeDistinctPiecesAndNeverEquipped() {
        List<String> equipped = List.of("sword", "leather_armor", "fire_ring");
        for (long seed = 0; seed < 200; seed++) {
            List<String> rewards = RewardDraw.draw(equipped, new SeededRandom(seed));
            assertEquals(RewardDraw.COUNT, rewards.size());
            assertEquals(RewardDraw.COUNT, new HashSet<>(rewards).size());
            for (String id : rewards) {
                assertFalse(equipped.contains(id));
                assertTrue(EquipmentCatalog.find(id).isPresent());
            }
        }
    }
}

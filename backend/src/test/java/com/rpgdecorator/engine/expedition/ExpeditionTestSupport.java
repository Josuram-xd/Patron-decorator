package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.engine.ExpeditionRepository;
import com.rpgdecorator.engine.combat.Action;
import com.rpgdecorator.engine.combat.CombatEngine;
import com.rpgdecorator.engine.combat.DamageCalculator;
import com.rpgdecorator.engine.combat.EnemyAI;
import com.rpgdecorator.engine.effects.EffectManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Shared helpers of the expedition tests: in-memory repository, service factory and combat shortcuts. */
final class ExpeditionTestSupport {

    private ExpeditionTestSupport() {
    }

    static final class MemoryRepository implements ExpeditionRepository {
        private final Map<String, Expedition> store = new ConcurrentHashMap<>();

        @Override
        public void save(Expedition expedition) {
            store.put(expedition.id(), expedition);
        }

        @Override
        public Optional<Expedition> findById(String id) {
            return id == null ? Optional.empty() : Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean delete(String id) {
            return store.remove(id) != null;
        }
    }

    /** Never evades, never crits, never triggers a chance-based effect. */
    static final class NoLuckRandom implements RandomSource {
        @Override
        public int nextInt(int minInclusive, int maxInclusive) {
            return minInclusive;
        }

        @Override
        public boolean chance(int percent) {
            return false;
        }
    }

    static EffectManager effects() {
        return new EffectManager();
    }

    static ExpeditionService service(ExpeditionRepository repository) {
        EffectManager effects = effects();
        CombatEngine engine = new CombatEngine(effects, new DamageCalculator(), new EnemyAI(effects)::decide);
        return new ExpeditionService(repository, effects, engine);
    }

    /** Service whose random source never evades or crits (the enemy draw takes the first of each group). */
    static ExpeditionService noLuckService(ExpeditionRepository repository) {
        EffectManager effects = effects();
        CombatEngine engine = new CombatEngine(effects, new DamageCalculator(), new EnemyAI(effects)::decide);
        return new ExpeditionService(repository, effects, engine, seed -> new NoLuckRandom(), () -> 1L);
    }

    /** Leaves the enemy with 1 health so that one hero attack wins the combat. */
    static void weakenEnemy(Expedition expedition) {
        var enemy = expedition.currentCombat().enemy();
        enemy.changeHealth(1 - enemy.currentHealth(), enemy.stats().maxHealth());
    }

    static ActionResult winCurrentCombat(ExpeditionService service, Expedition expedition) {
        weakenEnemy(expedition);
        return service.act(expedition.id(), Action.attack());
    }
}

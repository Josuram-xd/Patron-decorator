package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.catalog.Slot;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.combat.Action;
import com.rpgdecorator.engine.combat.CombatStatus;
import com.rpgdecorator.engine.combat.InvalidActionException;
import com.rpgdecorator.engine.combat.TurnContextImpl;
import com.rpgdecorator.engine.effects.EffectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.rpgdecorator.engine.expedition.ExpeditionTestSupport.MemoryRepository;
import static com.rpgdecorator.engine.expedition.ExpeditionTestSupport.NoLuckRandom;
import static com.rpgdecorator.engine.expedition.ExpeditionTestSupport.noLuckService;
import static com.rpgdecorator.engine.expedition.ExpeditionTestSupport.service;
import static com.rpgdecorator.engine.expedition.ExpeditionTestSupport.weakenEnemy;
import static com.rpgdecorator.engine.expedition.ExpeditionTestSupport.winCurrentCombat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpeditionServiceTest {

    private MemoryRepository repository;
    private ExpeditionService service;
    private final EffectManager effects = new EffectManager();

    @BeforeEach
    void setUp() {
        repository = new MemoryRepository();
        service = noLuckService(repository);
    }

    private static ErrorCode codeOf(Runnable action) {
        return assertThrows(InvalidActionException.class, action::run).errorCode();
    }

    // ------------------------------------------------------------------ create

    @Test
    void createStartsLevelOneInProgressWithTheStartingItem() {
        Expedition e = service.create("archer", "sword", 42L);

        assertEquals(ExpeditionStatus.IN_PROGRESS, e.status());
        assertEquals(1, e.currentLevel());
        assertEquals(42L, e.seed());
        assertEquals("sword", e.equipped(Slot.WEAPON));
        assertEquals("Espada(Arquero)", e.currentCombat().hero().describeChain());
        assertEquals(1, e.currentCombat().round());
        assertSame(e, service.get(e.id()));
    }

    @Test
    void createWithLifeAmuletStartsAtFullEffectiveHealth() {
        Expedition e = service.create("archer", "life_amulet", 1L);

        assertEquals(120, e.currentCombat().hero().stats().maxHealth());
        assertEquals(120, e.currentCombat().hero().currentHealth());
    }

    @Test
    void createWithoutSeedGeneratesAndStoresOne() {
        Expedition e = service(repository).create("archer", "sword", null);

        assertTrue(e.seed() >= 0);
    }

    @Test
    void createValidatesItsInput() {
        assertEquals(ErrorCode.REQUIRED_FIELD, codeOf(() -> service.create(null, "sword", 1L)));
        assertEquals(ErrorCode.REQUIRED_FIELD, codeOf(() -> service.create("archer", " ", 1L)));
        assertEquals(ErrorCode.INVALID_VALUE, codeOf(() -> service.create("bard", "sword", 1L)));
        assertEquals(ErrorCode.INVALID_VALUE, codeOf(() -> service.create("archer", "banana", 1L)));
    }

    @Test
    void sameSeedGivesTheSameEnemySequence() {
        ExpeditionService seeded = service(repository);
        Expedition a = seeded.create("warrior", "sword", 42L);
        Expedition b = seeded.create("warrior", "sword", 42L);

        assertEquals(a.enemyIdsByLevel(), b.enemyIdsByLevel());
    }

    // ------------------------------------------------------------------ act / victory flow

    @Test
    void winningAnEncounterPurgesEffectsHealsThirtyPercentAndOffersThreeRewards() {
        Expedition e = service.create("archer", "sword", 42L);
        // Rage(Poison(Sword(Archer))) with 40/95 health (requirements §5).
        TurnContextImpl ctx = new TurnContextImpl(e.currentCombat(), new NoLuckRandom());
        Combatant chain = effects.apply(e.currentCombat().hero(), "poison", ctx);
        chain = effects.apply(chain, "rage", ctx);
        e.currentCombat().setHero(chain);
        chain.changeHealth(40 - chain.currentHealth(), chain.stats().maxHealth());
        assertEquals("Furia(Envenenado(Espada(Arquero)))", chain.describeChain());

        ActionResult result = winCurrentCombat(service, e);

        assertEquals(CombatStatus.VICTORY, e.currentCombat().status());
        assertEquals(ExpeditionStatus.AWAITING_REWARD, e.status());
        assertEquals("Espada(Arquero)", e.currentCombat().hero().describeChain());
        int poisonTicks = e.statistics().damageTaken();                 // enemy died before acting
        assertEquals(40 - poisonTicks + 28, e.currentCombat().hero().currentHealth());
        assertEquals(RewardDraw.COUNT, e.offeredRewards().size());
        assertTrue(result.events().size() > 0);
        assertEquals(1, e.statistics().enemiesDefeated());
    }

    @Test
    void choosingARewardReplacesThePieceInItsSlotAndStartsTheNextLevel() {
        Expedition e = service.create("archer", "sword", 1L);
        winCurrentCombat(service, e);
        String weapon = e.offeredRewards().stream()
                .filter(id -> id.equals("war_axe") || id.equals("rune_staff")).findFirst().orElse(null);
        String reward = weapon != null ? weapon : e.offeredRewards().get(0);
        Slot slot = com.rpgdecorator.domain.catalog.EquipmentCatalog.get(reward).slot();

        Expedition after = service.chooseReward(e.id(), reward);

        assertEquals(ExpeditionStatus.IN_PROGRESS, after.status());
        assertEquals(2, after.currentLevel());
        assertEquals(reward, after.equipped(slot));
        assertEquals(1, after.currentCombat().round());
        assertTrue(after.offeredRewards().isEmpty());
        assertEquals(after.enemyIdAt(2), after.currentCombat().enemyDefinition().id());
    }

    @Test
    void choosingAPieceOfAnOccupiedSlotReplacesIt() {
        Expedition e = service.create("archer", "sword", 1L);
        winCurrentCombat(service, e);
        String weapon = null;
        for (long seed = 0; weapon == null; seed++) {
            // find a seed whose offer contains another weapon, so the replacement is always exercised
            Expedition candidate = service.create("archer", "sword", seed);
            winCurrentCombat(service, candidate);
            weapon = candidate.offeredRewards().stream()
                    .filter(id -> id.equals("war_axe") || id.equals("rune_staff")).findFirst().orElse(null);
            if (weapon != null) {
                e = candidate;
            }
        }

        Expedition after = service.chooseReward(e.id(), weapon);

        assertEquals(weapon, after.equipped(Slot.WEAPON));
        assertEquals(1, after.equipment().size());
    }

    @Test
    void skippingTheRewardKeepsTheEquipmentAndAdvances() {
        Expedition e = service.create("archer", "sword", 1L);
        winCurrentCombat(service, e);

        Expedition after = service.chooseReward(e.id(), null);

        assertEquals(2, after.currentLevel());
        assertEquals(List.of("sword"), after.equipmentInSlotOrder());
    }

    @Test
    void rewardOutsideTheOfferIsInvalid() {
        Expedition e = service.create("archer", "sword", 1L);
        winCurrentCombat(service, e);
        String notOffered = com.rpgdecorator.domain.catalog.EquipmentCatalog.all().stream()
                .map(i -> i.id()).filter(id -> !e.offeredRewards().contains(id)).findFirst().orElseThrow();

        assertEquals(ErrorCode.INVALID_VALUE, codeOf(() -> service.chooseReward(e.id(), notOffered)));
        assertEquals(ExpeditionStatus.AWAITING_REWARD, e.status());
    }

    @Test
    void defeatingTheBossCompletesTheExpedition() {
        Expedition e = service.create("archer", "sword", 1L);
        for (int level = 1; level < Expedition.TOTAL_LEVELS; level++) {
            winCurrentCombat(service, e);
            service.chooseReward(e.id(), null);
        }
        assertEquals("dragon", e.currentCombat().enemyDefinition().id());

        winCurrentCombat(service, e);

        assertEquals(ExpeditionStatus.COMPLETED, e.status());
        assertEquals(Expedition.TOTAL_LEVELS, e.statistics().enemiesDefeated());
        assertTrue(e.offeredRewards().isEmpty());
    }

    @Test
    void dyingFailsTheExpedition() {
        Expedition e = service.create("archer", "sword", 1L);
        Combatant hero = e.currentCombat().hero();
        hero.changeHealth(1 - hero.currentHealth(), hero.stats().maxHealth());
        // A strong enemy that cannot be killed this round: it survives and kills the 1 HP hero.
        var enemy = e.currentCombat().enemy();
        enemy.changeHealth(enemy.stats().maxHealth(), enemy.stats().maxHealth());

        service.act(e.id(), Action.attack());

        assertEquals(CombatStatus.DEFEAT, e.currentCombat().status());
        assertEquals(ExpeditionStatus.FAILED, e.status());
    }

    @Test
    void statisticsAccumulateAcrossEncounters() {
        Expedition e = service.create("archer", "sword", 1L);
        winCurrentCombat(service, e);
        int roundsAfterFirst = e.statistics().totalRounds();
        int dealtAfterFirst = e.statistics().damageDealt();
        service.chooseReward(e.id(), null);

        winCurrentCombat(service, e);

        assertEquals(2, e.statistics().enemiesDefeated());
        assertEquals(roundsAfterFirst + 1, e.statistics().totalRounds());
        assertTrue(e.statistics().damageDealt() > dealtAfterFirst);
    }

    // ------------------------------------------------------------------ invalid transitions

    @Test
    void actWhileAwaitingRewardIsInvalidState() {
        Expedition e = service.create("archer", "sword", 1L);
        winCurrentCombat(service, e);

        assertEquals(ErrorCode.INVALID_STATE, codeOf(() -> service.act(e.id(), Action.attack())));
    }

    @Test
    void chooseRewardWhileInProgressIsInvalidState() {
        Expedition e = service.create("archer", "sword", 1L);

        assertEquals(ErrorCode.INVALID_STATE, codeOf(() -> service.chooseReward(e.id(), null)));
    }

    @Test
    void actAfterTheExpeditionEndedIsInvalidState() {
        Expedition e = service.create("archer", "sword", 1L);
        for (int level = 1; level < Expedition.TOTAL_LEVELS; level++) {
            winCurrentCombat(service, e);
            service.chooseReward(e.id(), null);
        }
        winCurrentCombat(service, e);

        assertEquals(ErrorCode.INVALID_STATE, codeOf(() -> service.act(e.id(), Action.attack())));
        assertEquals(ErrorCode.INVALID_STATE, codeOf(() -> service.chooseReward(e.id(), null)));
    }

    @Test
    void nullActionIsRequiredField() {
        Expedition e = service.create("archer", "sword", 1L);

        assertEquals(ErrorCode.REQUIRED_FIELD, codeOf(() -> service.act(e.id(), null)));
    }

    @Test
    void unknownExpeditionIsNotFound() {
        assertEquals(ErrorCode.NOT_FOUND, codeOf(() -> service.get("nope")));
        assertEquals(ErrorCode.NOT_FOUND, codeOf(() -> service.act("nope", Action.attack())));
        assertEquals(ErrorCode.NOT_FOUND, codeOf(() -> service.chooseReward("nope", null)));
        assertEquals(ErrorCode.NOT_FOUND, codeOf(() -> service.previewForExpedition("nope", "sword")));
        assertEquals(ErrorCode.NOT_FOUND, codeOf(() -> service.delete("nope")));
    }

    @Test
    void deleteRemovesTheExpedition() {
        Expedition e = service.create("archer", "sword", 1L);

        service.delete(e.id());

        assertEquals(ErrorCode.NOT_FOUND, codeOf(() -> service.get(e.id())));
    }

    // ------------------------------------------------------------------ preview

    @Test
    void previewLoadoutBuildsTheChainInSlotOrderWithoutExpedition() {
        PreviewResult preview = service.previewLoadout("warrior", List.of("fire_ring", "sword", "leather_armor"));

        assertEquals("Anillo de fuego(Armadura de cuero(Espada(Guerrero)))", preview.chain());
        assertNull(preview.replaces());
        assertEquals(4, preview.layers().size());
    }

    @Test
    void previewLoadoutRejectsTwoPiecesForTheSameSlot() {
        assertEquals(ErrorCode.INVALID_VALUE,
                codeOf(() -> service.previewLoadout("warrior", List.of("sword", "war_axe"))));
        assertEquals(ErrorCode.INVALID_VALUE,
                codeOf(() -> service.previewLoadout("warrior", List.of("banana"))));
        assertEquals(ErrorCode.INVALID_VALUE, codeOf(() -> service.previewLoadout("bard", List.of())));
    }

    @Test
    void previewForExpeditionReportsTheReplacedPiece() {
        Expedition e = service.create("warrior", "sword", 1L);

        PreviewResult preview = service.previewForExpedition(e.id(), "war_axe");

        assertEquals("sword", preview.replaces());
        assertEquals("Hacha de guerra(Guerrero)", preview.chain());
        assertNull(service.previewForExpedition(e.id(), "sword").replaces());
        assertNotNull(preview.stats());
        // read-only: the expedition keeps its sword
        assertEquals("sword", e.equipped(Slot.WEAPON));
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void concurrentActionsOnTheSameExpeditionAreSerialized() throws Exception {
        Expedition e = service.create("warrior", "sword", 1L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        service.act(e.id(), Action.defend());
                        return true;
                    } catch (InvalidActionException ex) {
                        return false;
                    }
                }));
            }
            int accepted = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    accepted++;
                }
            }
            assertEquals(accepted, e.statistics().totalRounds());
        } finally {
            pool.shutdownNow();
        }
    }
}

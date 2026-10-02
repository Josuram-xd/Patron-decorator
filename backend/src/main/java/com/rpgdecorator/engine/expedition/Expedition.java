package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.EquipmentCatalog;
import com.rpgdecorator.domain.catalog.HeroClass;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.domain.equipment.Slot;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.combat.Combat;
import com.rpgdecorator.engine.combat.CombatStatus;
import com.rpgdecorator.engine.combat.InvalidActionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate of one expedition (design §5.5): 4 levels, one combat per level, the equipment that
 * grows between encounters and the statistics of the whole run.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@link #heroBase()} is the SAME {@link BaseCharacter} instance in every encounter, so the
 *       hero's health carries over; each encounter rebuilds the chain from it (RF-24).</li>
 *   <li>The enemies of all levels are drawn at creation ({@link EnemyDraw}).</li>
 *   <li>At most one piece per slot (RF-02).</li>
 *   <li>{@link #offeredRewards()} is non-empty only in {@link ExpeditionStatus#AWAITING_REWARD}.</li>
 * </ul>
 *
 * <p>Readers (the API mappers) only see getters. All mutators are package-private and are called by
 * {@link ExpeditionService} inside {@code synchronized (expedition)}; readers that need a consistent
 * snapshot while other threads may act should synchronize on the instance too.
 */
public final class Expedition {

    /** Levels of an expedition (RF-04); the last one is the boss. */
    public static final int TOTAL_LEVELS = EnemyCatalog.BOSS_LEVEL;

    /** Order in which equipment wraps the base: first slot = innermost decorator. */
    public static final List<Slot> SLOT_ORDER = List.of(Slot.WEAPON, Slot.ARMOR, Slot.ACCESSORY);

    private final String id;
    private final long seed;
    private final String heroClassId;
    private final List<String> enemyIdsByLevel;
    private final BaseCharacter heroBase;
    private final Map<Slot, String> equipment = new EnumMap<>(Slot.class);
    private final RandomSource random;
    private final RunStatistics statistics = new RunStatistics();

    private ExpeditionStatus status = ExpeditionStatus.IN_PROGRESS;
    private int currentLevel = 1;
    private Combat currentCombat;
    private List<String> offeredRewards = List.of();

    /**
     * Creates the aggregate at level 1 with no combat yet; {@link ExpeditionService} starts the first
     * encounter right away.
     *
     * @param enemyIdsByLevel one enemy id per level (index 0 = level 1)
     * @param random          the expedition's random source, seeded with {@code seed}
     */
    Expedition(String id, long seed, String heroClassId, List<String> enemyIdsByLevel,
               BaseCharacter heroBase, Map<Slot, String> equipment, RandomSource random) {
        this.id = Objects.requireNonNull(id, "id");
        this.seed = seed;
        this.heroClassId = Objects.requireNonNull(heroClassId, "heroClassId");
        this.enemyIdsByLevel = List.copyOf(enemyIdsByLevel);
        if (this.enemyIdsByLevel.size() != TOTAL_LEVELS) {
            throw new IllegalArgumentException("Expected " + TOTAL_LEVELS + " enemies, got "
                    + this.enemyIdsByLevel.size());
        }
        this.heroBase = Objects.requireNonNull(heroBase, "heroBase");
        this.random = Objects.requireNonNull(random, "random");
        equipment.forEach(this::equip);
    }

    // ------------------------------------------------------------------ getters

    /** UUID as text (api-contract). */
    public String id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    public String heroClassId() {
        return heroClassId;
    }

    public HeroClass heroClass() {
        return HeroClassCatalog.get(heroClassId);
    }

    public ExpeditionStatus status() {
        return status;
    }

    /** 1..{@value #TOTAL_LEVELS}. */
    public int currentLevel() {
        return currentLevel;
    }

    /** One enemy id per level, index 0 = level 1. Unmodifiable. */
    public List<String> enemyIdsByLevel() {
        return enemyIdsByLevel;
    }

    /** @param level 1..{@value #TOTAL_LEVELS} */
    public String enemyIdAt(int level) {
        if (level < 1 || level > TOTAL_LEVELS) {
            throw new IllegalArgumentException("Level out of range: " + level);
        }
        return enemyIdsByLevel.get(level - 1);
    }

    /** The enemy of the current level. */
    public String currentEnemyId() {
        return enemyIdAt(currentLevel);
    }

    /** {@code true} if the current level is the boss level. */
    public boolean isBossLevel() {
        return currentLevel == TOTAL_LEVELS;
    }

    /**
     * {@code true} if that level's enemy was beaten: every level before the current one, plus the
     * current one once its combat ended in victory (AWAITING_REWARD, COMPLETED). Helps the map.
     */
    public boolean isLevelDefeated(int level) {
        if (level < currentLevel) {
            return true;
        }
        return level == currentLevel && currentCombat != null
                && currentCombat.status() == CombatStatus.VICTORY;
    }

    /** The bare hero: the same instance across all encounters (holds the health). */
    public BaseCharacter heroBase() {
        return heroBase;
    }

    /** Current pieces by slot (only occupied slots). Unmodifiable snapshot. */
    public Map<Slot, String> equipment() {
        return Collections.unmodifiableMap(new EnumMap<>(equipment));
    }

    /** Item in that slot, or {@code null}. */
    public String equipped(Slot slot) {
        return equipment.get(slot);
    }

    /** Equipped item ids in {@link #SLOT_ORDER} (innermost first), as passed to {@code equip}. */
    public List<String> equipmentInSlotOrder() {
        List<String> ids = new ArrayList<>();
        for (Slot slot : SLOT_ORDER) {
            String itemId = equipment.get(slot);
            if (itemId != null) {
                ids.add(itemId);
            }
        }
        return List.copyOf(ids);
    }

    /**
     * The combat of the current level; in AWAITING_REWARD, COMPLETED and FAILED it is the last one
     * played, already finished (api-contract §5).
     */
    public Combat currentCombat() {
        return currentCombat;
    }

    /** {@value RewardDraw#COUNT} item ids in AWAITING_REWARD; empty otherwise. */
    public List<String> offeredRewards() {
        return offeredRewards;
    }

    public RunStatistics statistics() {
        return statistics;
    }

    // ------------------------------------------------------------------ package-private (service)

    RandomSource random() {
        return random;
    }

    /** @throws InvalidActionException INVALID_STATE if the status is not {@code expected} */
    void requireStatus(ExpeditionStatus expected, String message) {
        if (status != expected) {
            throw new InvalidActionException(ErrorCode.INVALID_STATE, message);
        }
    }

    /** Puts the piece in its slot; returns the piece it replaced, or {@code null}. */
    String equip(Slot slot, String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        if (EquipmentCatalog.slotOf(itemId) != slot) {
            throw new IllegalArgumentException(itemId + " does not go in slot " + slot);
        }
        return equipment.put(slot, itemId);
    }

    /** Puts the piece in its own slot; returns the piece it replaced, or {@code null}. */
    String equip(String itemId) {
        return equip(EquipmentCatalog.slotOf(itemId), itemId);
    }

    /** Starts the combat of the current level: status IN_PROGRESS and no offered rewards. */
    void beginEncounter(Combat combat) {
        this.currentCombat = Objects.requireNonNull(combat, "combat");
        this.offeredRewards = List.of();
        this.status = ExpeditionStatus.IN_PROGRESS;
    }

    void awaitReward(List<String> rewards) {
        this.offeredRewards = List.copyOf(rewards);
        this.status = ExpeditionStatus.AWAITING_REWARD;
    }

    void complete() {
        this.status = ExpeditionStatus.COMPLETED;
    }

    void fail() {
        this.status = ExpeditionStatus.FAILED;
    }

    void advanceLevel() {
        if (currentLevel >= TOTAL_LEVELS) {
            throw new IllegalStateException("Already at the last level");
        }
        currentLevel++;
        offeredRewards = List.of();
    }

    @Override
    public String toString() {
        return "Expedition[id=" + id + ", seed=" + seed + ", heroClassId=" + heroClassId
                + ", status=" + status + ", level=" + currentLevel + "/" + TOTAL_LEVELS + "]";
    }
}

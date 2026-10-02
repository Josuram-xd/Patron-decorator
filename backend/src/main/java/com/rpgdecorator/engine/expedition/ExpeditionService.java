package com.rpgdecorator.engine.expedition;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.catalog.EquipmentCatalog;
import com.rpgdecorator.domain.catalog.HeroClass;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.domain.catalog.Slot;
import com.rpgdecorator.engine.ErrorCode;
import com.rpgdecorator.engine.ExpeditionRepository;
import com.rpgdecorator.engine.combat.Action;
import com.rpgdecorator.engine.combat.Combat;
import com.rpgdecorator.engine.combat.CombatEngine;
import com.rpgdecorator.engine.combat.CombatStatus;
import com.rpgdecorator.engine.combat.InvalidActionException;
import com.rpgdecorator.engine.combat.LoggedEvent;
import com.rpgdecorator.engine.effects.EffectManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

/**
 * Use cases of an expedition (design §5.5, RF-02, RF-04, RF-24..RF-27): create, act, choose a
 * reward, preview, get and delete. The API layer calls only this class (and maps its results).
 *
 * <p><b>Errors.</b> Every rejected request throws {@link InvalidActionException} with its
 * {@link ErrorCode} (one exception type for the whole engine, so the API maps a single type):
 * {@code REQUIRED_FIELD} / {@code INVALID_VALUE} for missing or unknown ids, {@code NOT_FOUND} for an
 * unknown expedition, {@code INVALID_STATE} for an operation the current status does not allow, plus
 * the combat codes of {@link CombatEngine}. Messages are player-visible (Spanish). A rejected
 * request mutates nothing.
 *
 * <p><b>Synchronization.</b> Every operation on an existing expedition runs inside
 * {@code synchronized (expedition)}: the instance itself is the per-expedition lock (design §5.1), so
 * two requests on the same expedition are serialized while different expeditions run in parallel.
 * The repository must be thread-safe on its own.
 *
 * <p><b>Equipment order.</b> The chain is always built with the pieces in
 * {@link Expedition#SLOT_ORDER} (WEAPON innermost, then ARMOR, then ACCESSORY), in expeditions and
 * in both preview forms, so the same loadout always gives the same chain.
 *
 * <p><b>Randomness.</b> Each expedition has its own {@link RandomSource}, built from its seed by
 * the {@code randomFactory} ({@link SeededRandom} by default). The enemy draw, every combat and every
 * reward draw consume it in order, so the same seed and the same actions replay the same expedition
 * (RNF-04). When no seed is given, {@code seedGenerator} makes one; the default one returns a
 * non-negative {@code int} so the seed survives a JSON round trip through JavaScript numbers.
 */
public final class ExpeditionService {

    /** Health recovered after winning an encounter, in % of the effective maxHealth (RF-24). */
    public static final int HEAL_PERCENT_BETWEEN_ENCOUNTERS = 30;

    private final ExpeditionRepository repository;
    private final EffectManager effects;
    private final CombatEngine engine;
    private final LongFunction<RandomSource> randomFactory;
    private final LongSupplier seedGenerator;

    /** Production constructor: {@link SeededRandom} per expedition and random seeds when missing. */
    public ExpeditionService(ExpeditionRepository repository, EffectManager effects, CombatEngine engine) {
        this(repository, effects, engine, SeededRandom::new,
                () -> ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
    }

    /**
     * @param randomFactory builds the random source of an expedition from its seed
     * @param seedGenerator seed used when {@code create} receives none
     */
    public ExpeditionService(ExpeditionRepository repository, EffectManager effects, CombatEngine engine,
                             LongFunction<RandomSource> randomFactory, LongSupplier seedGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.randomFactory = Objects.requireNonNull(randomFactory, "randomFactory");
        this.seedGenerator = Objects.requireNonNull(seedGenerator, "seedGenerator");
    }

    // ------------------------------------------------------------------ create

    /**
     * Creates an expedition at level 1 with its first combat started (status IN_PROGRESS).
     * The hero starts at full effective health, equipment included (a life amulet adds its +25).
     *
     * @param seed {@code null} = generate one (it is stored and returned in {@link Expedition#seed()})
     * @throws InvalidActionException REQUIRED_FIELD if an id is missing, INVALID_VALUE if unknown
     */
    public Expedition create(String heroClassId, String startingItemId, Long seed) {
        requireField(heroClassId, "heroClassId");
        requireField(startingItemId, "startingItemId");
        HeroClass heroClass = heroClass(heroClassId);
        requireItem(startingItemId);

        long actualSeed = seed != null ? seed : seedGenerator.getAsLong();
        RandomSource random = Objects.requireNonNull(randomFactory.apply(actualSeed), "random");
        List<String> enemies = EnemyDraw.draw(random);
        BaseCharacter heroBase = BaseCharacter.hero(heroClass);
        Map<Slot, String> equipment = new EnumMap<>(Slot.class);
        equipment.put(EquipmentCatalog.get(startingItemId).slot(), startingItemId);

        Expedition expedition = new Expedition(UUID.randomUUID().toString(), actualSeed, heroClass.id(),
                enemies, heroBase, equipment, random);
        Combatant equipped = effects.equip(heroBase, expedition.equipmentInSlotOrder());
        int max = equipped.stats().maxHealth();
        equipped.changeHealth(max - equipped.currentHealth(), max);   // full effective health
        startEncounter(expedition);
        repository.save(expedition);
        return expedition;
    }

    // ------------------------------------------------------------------ act

    /**
     * Plays one round of the current combat with the hero's action, then applies the end of the
     * combat if it finished (design §5.5): DEFEAT -> FAILED; VICTORY on the last level ->
     * COMPLETED; VICTORY before -> purge + 30 % heal + 3 offered rewards -> AWAITING_REWARD.
     *
     * <p>After a victory that is not the boss, the hero of the finished combat is rebuilt from the
     * base with its equipment only (temporary effects purged, RF-24), so the AWAITING_REWARD view
     * already shows the purged chain; the heal emits no event (the combat log is closed with
     * {@code COMBAT_ENDED}). Heal = {@code floor(maxHealth x 30 / 100)} of that chain, capped at max.
     *
     * @throws InvalidActionException REQUIRED_FIELD if {@code action} is null, NOT_FOUND,
     *         INVALID_STATE if the expedition is not IN_PROGRESS, or the codes of the combat engine
     */
    public ActionResult act(String expeditionId, Action action) {
        if (action == null) {
            throw new InvalidActionException(ErrorCode.REQUIRED_FIELD, "Falta la acción");
        }
        Expedition expedition = find(expeditionId);
        synchronized (expedition) {
            expedition.requireStatus(ExpeditionStatus.IN_PROGRESS, statusMessage(expedition));
            Combat combat = expedition.currentCombat();
            int mark = combat.lastSeq();
            engine.executeRound(combat, action, expedition.random());
            List<LoggedEvent> events = combat.eventsSince(mark);
            expedition.statistics().recordRound(combat, events);
            if (combat.isFinished()) {
                onCombatFinished(expedition, combat);
            }
            repository.save(expedition);
            return new ActionResult(expedition, events);
        }
    }

    private void onCombatFinished(Expedition expedition, Combat combat) {
        if (combat.status() == CombatStatus.DEFEAT) {
            expedition.fail();
            return;
        }
        expedition.statistics().addEnemyDefeated();
        if (expedition.isBossLevel()) {
            expedition.complete();
            return;
        }
        // RF-24: purge temporary effects (rebuild from the base, equipment only) and heal 30 %.
        Combatant purged = effects.equip(expedition.heroBase(), expedition.equipmentInSlotOrder());
        combat.setHero(purged);
        int max = purged.stats().maxHealth();
        purged.changeHealth(max * HEAL_PERCENT_BETWEEN_ENCOUNTERS / 100, max);
        expedition.awaitReward(RewardDraw.draw(expedition.equipmentInSlotOrder(), expedition.random()));
    }

    // ------------------------------------------------------------------ reward

    /**
     * Takes one of the offered pieces (replacing the one in its slot, RF-25) or skips the reward
     * ({@code itemId == null}), then moves to the next level and starts its combat: new chain from
     * the base with the equipment only (RF-24), cooldowns reset, round 1, status IN_PROGRESS.
     *
     * @throws InvalidActionException NOT_FOUND, INVALID_STATE if not AWAITING_REWARD, INVALID_VALUE
     *         if the piece is not one of {@link Expedition#offeredRewards()}
     */
    public Expedition chooseReward(String expeditionId, String itemId) {
        Expedition expedition = find(expeditionId);
        synchronized (expedition) {
            expedition.requireStatus(ExpeditionStatus.AWAITING_REWARD, "No hay ninguna recompensa pendiente");
            if (itemId != null) {
                if (!expedition.offeredRewards().contains(itemId)) {
                    throw new InvalidActionException(ErrorCode.INVALID_VALUE,
                            "La pieza no está entre las recompensas ofrecidas: " + itemId);
                }
                expedition.equip(itemId);
            }
            expedition.advanceLevel();
            startEncounter(expedition);
            repository.save(expedition);
            return expedition;
        }
    }

    /**
     * design §5.5 startEncounter(n): a NEW chain = base + equipment (no temporary effects), the
     * enemy of the level at full health and a new {@link Combat} (round 1, cooldowns at 0). The
     * hero's health is only capped at the new effective max (it can drop when a life amulet is
     * replaced); gaining max health does not heal.
     */
    private void startEncounter(Expedition expedition) {
        Combatant hero = effects.equip(expedition.heroBase(), expedition.equipmentInSlotOrder());
        hero.changeHealth(0, hero.stats().maxHealth());
        EnemyDefinition enemyDefinition = EnemyCatalog.get(expedition.currentEnemyId());
        Combatant enemy = BaseCharacter.enemy(enemyDefinition);
        expedition.beginEncounter(new Combat(hero, enemy, expedition.heroClass().abilities(), enemyDefinition));
    }

    // ------------------------------------------------------------------ preview

    /**
     * Preview, first form of api-contract §4: a class with a set of pieces (no expedition).
     * {@code itemIds == null} is treated as no pieces. {@code replaces} is always {@code null}.
     *
     * @throws InvalidActionException REQUIRED_FIELD without class; INVALID_VALUE for an unknown
     *         class or piece, a repeated piece or two pieces for the same slot (RF-02)
     */
    public PreviewResult previewLoadout(String heroClassId, List<String> itemIds) {
        requireField(heroClassId, "heroClassId");
        HeroClass heroClass = heroClass(heroClassId);
        Map<Slot, String> bySlot = new EnumMap<>(Slot.class);
        for (String itemId : itemIds == null ? List.<String>of() : itemIds) {
            if (itemId == null) {
                throw new InvalidActionException(ErrorCode.INVALID_VALUE, "Pieza de equipo vacía");
            }
            requireItem(itemId);
            Slot slot = EquipmentCatalog.get(itemId).slot();
            if (bySlot.containsKey(slot)) {
                throw new InvalidActionException(ErrorCode.INVALID_VALUE,
                        "Solo se puede llevar una pieza por ranura: " + bySlot.get(slot) + " y " + itemId);
            }
            bySlot.put(slot, itemId);
        }
        return preview(BaseCharacter.hero(heroClass), inSlotOrder(bySlot), null);
    }

    /**
     * Preview, second form of api-contract §4: the hero of that expedition with {@code itemId} in
     * its slot. {@code replaces} = the piece currently in that slot, or {@code null} if the slot is
     * empty or already holds that same piece. Allowed in any status and for any catalog piece (it is
     * read-only); the reward screen uses it with the offered pieces.
     *
     * @throws InvalidActionException REQUIRED_FIELD without item, INVALID_VALUE for an unknown piece,
     *         NOT_FOUND for an unknown expedition
     */
    public PreviewResult previewForExpedition(String expeditionId, String itemId) {
        requireField(itemId, "itemId");
        requireItem(itemId);
        Expedition expedition = find(expeditionId);
        synchronized (expedition) {
            Slot slot = EquipmentCatalog.get(itemId).slot();
            Map<Slot, String> bySlot = new EnumMap<>(expedition.equipment());
            String previous = bySlot.put(slot, itemId);
            String replaces = itemId.equals(previous) ? null : previous;
            // equip() only wraps the base (nothing is mutated), so the real base can be used.
            return preview(expedition.heroBase(), inSlotOrder(bySlot), replaces);
        }
    }

    private PreviewResult preview(BaseCharacter base, List<String> itemIds, String replaces) {
        Combatant outer = effects.equip(base, itemIds);
        return new PreviewResult(outer.stats(), outer.describeChain(), effects.layers(outer), replaces);
    }

    private static List<String> inSlotOrder(Map<Slot, String> bySlot) {
        List<String> ids = new ArrayList<>();
        for (Slot slot : Expedition.SLOT_ORDER) {
            String itemId = bySlot.get(slot);
            if (itemId != null) {
                ids.add(itemId);
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ get / delete

    /** @throws InvalidActionException NOT_FOUND */
    public Expedition get(String expeditionId) {
        return find(expeditionId);
    }

    /** @throws InvalidActionException NOT_FOUND if it did not exist */
    public void delete(String expeditionId) {
        if (expeditionId == null || !repository.delete(expeditionId)) {
            throw notFound(expeditionId);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Expedition find(String expeditionId) {
        if (expeditionId == null) {
            throw notFound(null);
        }
        return repository.findById(expeditionId).orElseThrow(() -> notFound(expeditionId));
    }

    private static InvalidActionException notFound(String expeditionId) {
        return new InvalidActionException(ErrorCode.NOT_FOUND, "La expedición no existe: " + expeditionId);
    }

    private static void requireField(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidActionException(ErrorCode.REQUIRED_FIELD, "Falta el campo " + field);
        }
    }

    private static HeroClass heroClass(String heroClassId) {
        if (!HeroClassCatalog.find(heroClassId).isPresent()) {
            throw new InvalidActionException(ErrorCode.INVALID_VALUE, "Clase de héroe desconocida: " + heroClassId);
        }
        return HeroClassCatalog.get(heroClassId);
    }

    private static void requireItem(String itemId) {
        if (!EquipmentCatalog.find(itemId).isPresent()) {
            throw new InvalidActionException(ErrorCode.INVALID_VALUE, "Pieza de equipo desconocida: " + itemId);
        }
    }

    private static String statusMessage(Expedition expedition) {
        return switch (expedition.status()) {
            case AWAITING_REWARD -> "El combate terminó: elige una recompensa para continuar";
            case COMPLETED, FAILED -> "La expedición ya terminó";
            case IN_PROGRESS -> "La expedición está en curso";
        };
    }
}

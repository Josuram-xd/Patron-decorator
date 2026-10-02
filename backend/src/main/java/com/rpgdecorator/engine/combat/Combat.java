package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.catalog.Ability;
import com.rpgdecorator.domain.catalog.EnemyAbility;
import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.engine.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate of one encounter (design §5.1).
 *
 * <p><b>Outer references.</b> {@link #hero()} and {@link #enemy()} are always the <b>outer</b> end of
 * each decorator chain. Every {@code EffectManager} operation returns a new outer reference; the
 * caller must store it back with {@link #replace(Combatant)} (or {@link #setHero}/{@link #setEnemy})
 * and re-read {@code hero()}/{@code enemy()} afterwards (design §5.3). Combatants are looked up by
 * {@code id}, never by reference (design §3.6).
 *
 * <p><b>Log.</b> Domain events carry no {@code seq}/{@code round}; {@link #log(CombatEvent)} wraps
 * each one in a {@link LoggedEvent} with the next {@code seq} (starting at 1) and the current round.
 *
 * <p>Not thread-safe: every mutation happens inside the lock of the owning expedition (design §5.1).
 */
public final class Combat {

    private final String id;
    private CombatStatus status = CombatStatus.IN_PROGRESS;
    private int round = 1;

    private Combatant hero;
    private Combatant enemy;
    private final List<Ability> heroAbilities;
    private final EnemyDefinition enemyDefinition;

    private final Map<String, Integer> heroCooldowns = new HashMap<>();
    private final Map<String, Integer> enemyCooldowns = new HashMap<>();

    private final List<LoggedEvent> log = new ArrayList<>();
    private int eventSequence = 0;

    /** New combat with a random UUID as id. */
    public Combat(Combatant hero, Combatant enemy, List<Ability> heroAbilities, EnemyDefinition enemyDefinition) {
        this(UUID.randomUUID().toString(), hero, enemy, heroAbilities, enemyDefinition);
    }

    /**
     * @param hero            outer reference of the hero chain (equipment already applied)
     * @param enemy           outer reference of the enemy chain
     * @param heroAbilities   abilities of the hero class, in catalog order
     * @param enemyDefinition catalog entry of the enemy (abilities in priority order, AI conditions)
     */
    public Combat(String id, Combatant hero, Combatant enemy, List<Ability> heroAbilities,
                  EnemyDefinition enemyDefinition) {
        this.id = Objects.requireNonNull(id, "id");
        this.hero = Objects.requireNonNull(hero, "hero");
        this.enemy = Objects.requireNonNull(enemy, "enemy");
        this.heroAbilities = List.copyOf(heroAbilities);
        this.enemyDefinition = Objects.requireNonNull(enemyDefinition, "enemyDefinition");
        if (hero.id().equals(enemy.id())) {
            throw new IllegalArgumentException("hero and enemy must have different ids: " + hero.id());
        }
    }

    // ------------------------------------------------------------------ identity and state

    public String id() { return id; }

    public CombatStatus status() { return status; }

    public boolean isFinished() { return status.isFinished(); }

    /** Current round, starting at 1. */
    public int round() { return round; }

    /** Moves to the next round (end of {@code executeRound}). */
    public void nextRound() {
        requireInProgress();
        round++;
    }

    /**
     * Ends the combat. Only sets the status; the engine emits {@code COMBAT_ENDED} itself.
     *
     * @throws IllegalArgumentException if {@code result} is {@code IN_PROGRESS}
     * @throws IllegalStateException    if the combat is already finished
     */
    public void finish(CombatStatus result) {
        if (!Objects.requireNonNull(result, "result").isFinished()) {
            throw new IllegalArgumentException("A combat cannot finish as " + result);
        }
        if (isFinished()) {
            throw new IllegalStateException("Combat " + id + " is already finished: " + status);
        }
        status = result;
    }

    /** @throws InvalidActionException {@code INVALID_STATE} if the combat is already finished */
    public void requireInProgress() {
        if (isFinished()) {
            throw new InvalidActionException(ErrorCode.INVALID_STATE, "El combate ya terminó");
        }
    }

    // ------------------------------------------------------------------ combatants (outer references)

    /** Outer reference of the hero chain. */
    public Combatant hero() { return hero; }

    /** Outer reference of the enemy chain. */
    public Combatant enemy() { return enemy; }

    public void setHero(Combatant newOuter) {
        requireSameId(hero, newOuter);
        hero = newOuter;
    }

    public void setEnemy(Combatant newOuter) {
        requireSameId(enemy, newOuter);
        enemy = newOuter;
    }

    /**
     * Stores a new outer reference, choosing the side by {@code id} (design §3.6). Typical use:
     * {@code combat.replace(effectManager.apply(combat.combatant(id), "poison", ctx))}.
     *
     * @return the stored reference, for chaining
     */
    public Combatant replace(Combatant newOuter) {
        Objects.requireNonNull(newOuter, "newOuter");
        if (isHero(newOuter.id())) {
            hero = newOuter;
        } else if (isEnemy(newOuter.id())) {
            enemy = newOuter;
        } else {
            throw new IllegalArgumentException("Unknown combatant id: " + newOuter.id());
        }
        return newOuter;
    }

    /** Current outer reference of the combatant with that id. */
    public Combatant combatant(String combatantId) {
        if (isHero(combatantId)) return hero;
        if (isEnemy(combatantId)) return enemy;
        throw unknown(combatantId);
    }

    /** Current outer reference of the other side. */
    public Combatant opponentOf(String combatantId) {
        if (isHero(combatantId)) return enemy;
        if (isEnemy(combatantId)) return hero;
        throw unknown(combatantId);
    }

    public boolean isHero(String combatantId) { return hero.id().equals(combatantId); }

    public boolean isEnemy(String combatantId) { return enemy.id().equals(combatantId); }

    // ------------------------------------------------------------------ abilities and cooldowns

    public List<Ability> heroAbilities() { return heroAbilities; }

    public EnemyDefinition enemyDefinition() { return enemyDefinition; }

    /** Abilities of that combatant: those of the hero class, or the enemy ones in priority order. */
    public List<Ability> abilitiesOf(String combatantId) {
        if (isHero(combatantId)) return heroAbilities;
        if (isEnemy(combatantId)) return enemyDefinition.abilities().stream().map(EnemyAbility::ability).toList();
        throw unknown(combatantId);
    }

    /** @return the ability with that id of that combatant, or {@code null} if it has none */
    public Ability ability(String combatantId, String abilityId) {
        return abilitiesOf(combatantId).stream().filter(a -> a.id().equals(abilityId)).findFirst().orElse(null);
    }

    /** Turns left before the ability is ready; 0 = available. */
    public int cooldown(String combatantId, String abilityId) {
        return cooldownsOf(combatantId).getOrDefault(abilityId, 0);
    }

    /** Sets the remaining cooldown of an ability (0 = available). */
    public void setCooldown(String combatantId, String abilityId, int turns) {
        if (turns < 0) {
            throw new IllegalArgumentException("Cooldown cannot be negative: " + turns);
        }
        Map<String, Integer> cooldowns = cooldownsOf(combatantId);
        if (turns == 0) {
            cooldowns.remove(abilityId);
        } else {
            cooldowns.put(abilityId, turns);
        }
    }

    /** Decrements by 1 every cooldown above 0 of that combatant (start of its turn, design §5.3). */
    public void decrementCooldowns(String combatantId) {
        Map<String, Integer> cooldowns = cooldownsOf(combatantId);
        cooldowns.replaceAll((ability, turns) -> turns - 1);
        cooldowns.values().removeIf(turns -> turns <= 0);
    }

    /**
     * Remaining cooldown of every ability of that combatant, in ability order (0 = available).
     * Read-only snapshot.
     */
    public Map<String, Integer> cooldowns(String combatantId) {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        for (Ability ability : abilitiesOf(combatantId)) {
            snapshot.put(ability.id(), cooldown(combatantId, ability.id()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    // ------------------------------------------------------------------ event log

    /** Appends the event with the next {@code seq} and the current round. */
    public LoggedEvent log(CombatEvent event) {
        LoggedEvent logged = new LoggedEvent(++eventSequence, round, Objects.requireNonNull(event, "event"));
        log.add(logged);
        return logged;
    }

    /** The whole log, in {@code seq} order (read-only view). */
    public List<LoggedEvent> events() {
        return Collections.unmodifiableList(log);
    }

    /** {@code seq} of the last logged event; 0 if the log is empty. */
    public int lastSeq() {
        return eventSequence;
    }

    /**
     * Events with {@code seq > afterSeq}, in order. Events of one action:
     * {@code int mark = combat.lastSeq(); engine.executeRound(...); combat.eventsSince(mark);}
     */
    public List<LoggedEvent> eventsSince(int afterSeq) {
        int from = Math.max(0, Math.min(afterSeq, log.size()));   // seq n is at index n - 1
        return List.copyOf(log.subList(from, log.size()));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Integer> cooldownsOf(String combatantId) {
        if (isHero(combatantId)) return heroCooldowns;
        if (isEnemy(combatantId)) return enemyCooldowns;
        throw unknown(combatantId);
    }

    private static IllegalArgumentException unknown(String combatantId) {
        return new IllegalArgumentException("Unknown combatant id: " + combatantId);
    }

    private static void requireSameId(Combatant current, Combatant newOuter) {
        Objects.requireNonNull(newOuter, "newOuter");
        if (!current.id().equals(newOuter.id())) {
            throw new IllegalArgumentException("Expected id " + current.id() + " but got " + newOuter.id());
        }
    }
}

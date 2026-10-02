/**
 * Scripted, in-memory implementation of `ApiClient` (T-402), enabled with `VITE_USE_MOCKS=true`.
 *
 * It does NOT implement combat rules: rounds are replayed from `combatScript.ts`.
 * The only logic here is bookkeeping so the returned DTOs stay coherent with the
 * events (health from DAMAGE/HEAL amounts, effect list from APPLIED/REMOVED,
 * effect durations counted down at the end of the affected combatant's turn,
 * ability cooldowns) and the contract's error codes for invalid requests.
 */
import { ApiError } from '../api/apiError';
import type { ApiClient } from '../api/client';
import type {
  AbilityDTO,
  ActionRequest,
  ActionResultDTO,
  ActiveEffectDTO,
  CombatantDTO,
  CombatantId,
  CombatStatus,
  CreateExpeditionRequest,
  EquipmentDTO,
  ErrorCode,
  EventDTO,
  ExpeditionDTO,
  ExpeditionStatus,
  MapNodeDTO,
  PreviewDTO,
  PreviewRequest,
  RewardRequest,
  StatisticsDTO,
} from '../api/types';
import {
  heroAbilityScripts,
  levelScripts,
  scriptedRound,
  type HeroAbilityScript,
  type ScriptedEvent,
} from './combatScript';
import { effectsFixture, enemiesFixture, equipmentFixture, heroClassesFixture } from './fixtures/catalog';
import { buildChain, effectCategory, effectLabel, SLOT_WRAP_ORDER, type ChainView } from './mockChain';

// ---------------------------------------------------------------------------
// Session state
// ---------------------------------------------------------------------------

interface MockEffect {
  effectId: string;
  turnsRemaining: number;
  /** Applied during the owner's own turn: not counted down at the end of that turn (design §4.5). */
  justApplied: boolean;
  /** Shield only. */
  absorption?: number;
}

interface Fighter {
  health: number;
  /** Outer to inner. */
  effects: MockEffect[];
  cooldowns: Record<string, number>;
}

interface Session {
  id: string;
  seed: number;
  heroClassId: string;
  status: ExpeditionStatus;
  currentLevel: number;
  equipment: EquipmentDTO;
  hero: Fighter;
  enemy: Fighter;
  combatStatus: CombatStatus;
  round: number;
  /** Index of the next scripted round of the current level. */
  scriptIndex: number;
  seq: number;
  log: EventDTO[];
  offeredRewardIds: string[];
  statistics: StatisticsDTO;
}

const TOTAL_LEVELS = 4;
const SHIELD_ABSORPTION = 20;
/** RF-24: heal 30 % of the effective max health after a won encounter (display coherence only). */
const VICTORY_HEAL_RATIO = 0.3;
const LATENCY_MS = { read: 150, write: 350 } as const;

const sessions = new Map<string, Session>();

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

function fail(code: ErrorCode, message: string): never {
  const status: Record<ErrorCode, number> = {
    INVALID_JSON: 400,
    REQUIRED_FIELD: 400,
    INVALID_VALUE: 400,
    NOT_FOUND: 404,
    METHOD_NOT_ALLOWED: 405,
    INVALID_STATE: 409,
    ABILITY_ON_COOLDOWN: 409,
    ACTION_NOT_ALLOWED: 409,
    INTERNAL_ERROR: 500,
  };
  throw new ApiError(code, message, status[code]);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Simulates the network: latency + a deep copy, so callers never share mock state. */
async function respond<T>(ms: number, produce: () => T): Promise<T> {
  await delay(ms);
  return structuredClone(produce());
}

function findSession(expeditionId: string): Session {
  const session = sessions.get(expeditionId);
  if (!session) fail('NOT_FOUND', 'La expedición no existe');
  return session;
}

function heroClassOf(heroClassId: string) {
  const heroClass = heroClassesFixture.find((candidate) => candidate.id === heroClassId);
  if (!heroClass) fail('INVALID_VALUE', `La clase "${heroClassId}" no existe`);
  return heroClass;
}

function itemOf(itemId: string) {
  const item = equipmentFixture.find((candidate) => candidate.id === itemId);
  if (!item) fail('INVALID_VALUE', `La pieza "${itemId}" no existe`);
  return item;
}

function enemyOf(enemyId: string) {
  const enemy = enemiesFixture.find((candidate) => candidate.id === enemyId);
  if (!enemy) fail('INTERNAL_ERROR', `Enemigo desconocido "${enemyId}"`);
  return enemy;
}

function scriptOf(level: number) {
  const script = levelScripts[level - 1];
  if (!script) fail('INTERNAL_ERROR', `No hay guion para el nivel ${level}`);
  return script;
}

function baseDuration(effectId: string): number {
  return effectsFixture.find((effect) => effect.id === effectId)?.baseDuration ?? 1;
}

function equippedIds(equipment: EquipmentDTO): string[] {
  return SLOT_WRAP_ORDER.map((slot) => equipment[slot]).filter((id): id is string => id !== null);
}

// ---------------------------------------------------------------------------
// DTO builders
// ---------------------------------------------------------------------------

function heroChain(session: Session): ChainView {
  const heroClass = heroClassOf(session.heroClassId);
  return buildChain(
    { id: heroClass.id, label: heroClass.name, stats: heroClass.stats },
    equippedIds(session.equipment),
    session.hero.effects,
  );
}

function enemyChain(session: Session): ChainView {
  const enemy = enemyOf(scriptOf(session.currentLevel).enemyId);
  return buildChain({ id: enemy.id, label: enemy.name, stats: enemy.stats }, [], session.enemy.effects);
}

function toEffectDTO(effect: MockEffect): ActiveEffectDTO {
  return {
    effectId: effect.effectId,
    label: effectLabel(effect.effectId),
    category: effectCategory(effect.effectId),
    turnsRemaining: effect.turnsRemaining,
    extra: effect.absorption === undefined ? null : { absorption: effect.absorption },
  };
}

function withCooldowns(abilities: AbilityDTO[], fighter: Fighter): AbilityDTO[] {
  return abilities.map((ability) => ({ ...ability, cooldownRemaining: fighter.cooldowns[ability.id] ?? 0 }));
}

function hasEffect(fighter: Fighter, effectId: string): boolean {
  return fighter.effects.some((effect) => effect.effectId === effectId);
}

function toCombatantDTO(session: Session, id: CombatantId): CombatantDTO {
  const fighter = session[id];
  const view = id === 'hero' ? heroChain(session) : enemyChain(session);
  const archetype =
    id === 'hero' ? heroClassOf(session.heroClassId) : enemyOf(scriptOf(session.currentLevel).enemyId);
  return {
    id,
    name: archetype.name,
    side: id === 'hero' ? 'HERO' : 'ENEMY',
    archetypeId: archetype.id,
    health: fighter.health,
    stats: view.stats,
    canAct: !hasEffect(fighter, 'frozen'),
    effects: fighter.effects.map(toEffectDTO),
    abilities: withCooldowns(archetype.abilities, fighter),
    chain: view.chain,
    layers: view.layers,
  };
}

function toMap(session: Session): MapNodeDTO[] {
  return levelScripts.map((script, index) => {
    const level = index + 1;
    const enemy = enemyOf(script.enemyId);
    const currentDefeated = level === session.currentLevel && session.combatStatus === 'VICTORY';
    if (level < session.currentLevel || currentDefeated) {
      return { level, enemyId: enemy.id, name: enemy.name, status: 'DEFEATED' };
    }
    if (level === session.currentLevel) {
      return { level, enemyId: enemy.id, name: enemy.name, status: 'CURRENT' };
    }
    if (enemy.boss) {
      return { level, enemyId: enemy.id, name: enemy.name, status: 'BOSS' };
    }
    return { level, enemyId: null, name: null, status: 'HIDDEN' };
  });
}

function toExpeditionDTO(session: Session): ExpeditionDTO {
  return {
    id: session.id,
    seed: session.seed,
    status: session.status,
    currentLevel: session.currentLevel,
    totalLevels: TOTAL_LEVELS,
    map: toMap(session),
    equipment: { ...session.equipment },
    combat: {
      status: session.combatStatus,
      round: session.round,
      hero: toCombatantDTO(session, 'hero'),
      enemy: toCombatantDTO(session, 'enemy'),
      log: [...session.log],
    },
    offeredRewards: session.offeredRewardIds.map(itemOf),
    statistics: { ...session.statistics },
  };
}

// ---------------------------------------------------------------------------
// Encounters
// ---------------------------------------------------------------------------

function startEncounter(session: Session, level: number): void {
  const enemy = enemyOf(scriptOf(level).enemyId);
  session.currentLevel = level;
  session.status = 'IN_PROGRESS';
  session.combatStatus = 'IN_PROGRESS';
  session.round = 1;
  session.scriptIndex = 0;
  session.seq = 0;
  session.log = [];
  session.offeredRewardIds = [];
  // New chain per encounter: temporary effects and cooldowns do not carry over (RF-24).
  session.hero.effects = [];
  session.hero.cooldowns = {};
  session.enemy = { health: enemy.stats.maxHealth, effects: [], cooldowns: {} };
}

/** Deterministic pick of 3 unequipped items, rotated by level so each reward screen differs. */
function pickRewards(session: Session): string[] {
  const equipped = new Set(equippedIds(session.equipment));
  const candidates = equipmentFixture.map((item) => item.id).filter((id) => !equipped.has(id));
  const offset = (session.currentLevel * 2) % Math.max(1, candidates.length);
  return [...candidates.slice(offset), ...candidates.slice(0, offset)].slice(0, 3);
}

function finishWithVictory(session: Session): void {
  session.combatStatus = 'VICTORY';
  session.statistics.enemiesDefeated += 1;
  if (session.currentLevel >= TOTAL_LEVELS) {
    session.status = 'COMPLETED';
    return;
  }
  const maxHealth = heroChain(session).stats.maxHealth;
  session.hero.health = Math.min(maxHealth, session.hero.health + Math.floor(maxHealth * VICTORY_HEAL_RATIO));
  session.offeredRewardIds = pickRewards(session);
  session.status = 'AWAITING_REWARD';
}

// ---------------------------------------------------------------------------
// Round replay
// ---------------------------------------------------------------------------

class RoundRecorder {
  readonly events: EventDTO[] = [];

  constructor(
    private readonly session: Session,
    private actor: CombatantId,
  ) {}

  setActor(actor: CombatantId): void {
    this.actor = actor;
  }

  /** Assigns `seq`/`round`, keeps the session coherent with the event and records it. */
  emit(scripted: ScriptedEvent): void {
    const session = this.session;
    const partial = this.applyToSession(scripted);
    if (partial === null) return;

    session.seq += 1;
    const event = { ...partial, seq: session.seq, round: session.round } as EventDTO;
    this.events.push(event);
    session.log.push(event);
  }

  /**
   * Updates the session to reflect a scripted event and returns the event to record
   * (possibly adjusted: clamped amounts, APPLIED -> REFRESHED), or `null` to drop it.
   */
  private applyToSession(scripted: ScriptedEvent): ScriptedEvent | null {
    const session = this.session;
    switch (scripted.type) {
      case 'DAMAGE': {
        const target = session[scripted.targetId];
        // The mock hero never dies; the enemy dies exactly at 0.
        const floor = scripted.targetId === 'hero' ? 1 : 0;
        const amount = Math.max(0, Math.min(scripted.amount, target.health - floor));
        target.health -= amount;
        if (scripted.targetId === 'enemy') session.statistics.damageDealt += amount;
        else session.statistics.damageTaken += amount;
        return { ...scripted, amount };
      }
      case 'HEAL': {
        const target = session[scripted.targetId];
        const maxHealth = (scripted.targetId === 'hero' ? heroChain(session) : enemyChain(session)).stats.maxHealth;
        const amount = Math.max(0, Math.min(scripted.amount, maxHealth - target.health));
        target.health += amount;
        return { ...scripted, amount };
      }
      case 'ABSORBED': {
        const shield = session[scripted.targetId].effects.find((effect) => effect.effectId === 'shield');
        if (!shield) return null; // Scripted absorption without a shield (player defended earlier): skip it.
        shield.absorption = scripted.remaining;
        break;
      }
      case 'EFFECT_APPLIED': {
        const target = session[scripted.targetId];
        const justApplied = scripted.targetId === this.actor;
        const existing = target.effects.find((effect) => effect.effectId === scripted.effectId);
        if (existing) {
          existing.turnsRemaining = scripted.duration;
          existing.justApplied = justApplied;
          return { type: 'EFFECT_REFRESHED', targetId: scripted.targetId, effectId: scripted.effectId, duration: scripted.duration };
        }
        target.effects.unshift({
          effectId: scripted.effectId,
          turnsRemaining: scripted.duration,
          justApplied,
          ...(scripted.effectId === 'shield' ? { absorption: SHIELD_ABSORPTION } : {}),
        });
        break;
      }
      case 'EFFECT_REMOVED': {
        const target = session[scripted.targetId];
        target.effects = target.effects.filter((effect) => effect.effectId !== scripted.effectId);
        break;
      }
      case 'ACTION': {
        if (scripted.action === 'ABILITY' && scripted.abilityId !== undefined) {
          const abilities =
            scripted.actorId === 'hero'
              ? heroClassOf(session.heroClassId).abilities
              : enemyOf(scriptOf(session.currentLevel).enemyId).abilities;
          const ability = abilities.find((candidate) => candidate.id === scripted.abilityId);
          if (ability) session[scripted.actorId].cooldowns[ability.id] = ability.cooldown;
        }
        break;
      }
      default:
        break;
    }
    return scripted;
  }

  startTurn(actor: CombatantId, ticks: readonly ScriptedEvent[]): void {
    this.setActor(actor);
    this.emit({ type: 'TURN_STARTED', actorId: actor });
    const cooldowns = this.session[actor].cooldowns;
    for (const abilityId of Object.keys(cooldowns)) {
      cooldowns[abilityId] = Math.max(0, (cooldowns[abilityId] ?? 0) - 1);
    }
    for (const tick of ticks) this.emit(tick);
  }

  /** Counts down the actor's effects and removes the expired / depleted ones, then `TURN_ENDED`. */
  endTurn(actor: CombatantId): void {
    const fighter = this.session[actor];
    for (const effect of fighter.effects) {
      if (effect.justApplied) effect.justApplied = false;
      else effect.turnsRemaining = Math.max(0, effect.turnsRemaining - 1);
    }
    for (const effect of [...fighter.effects]) {
      if (effect.absorption === 0) {
        this.emit({ type: 'EFFECT_REMOVED', targetId: actor, effectId: effect.effectId, reason: 'DEPLETED' });
      } else if (effect.turnsRemaining === 0) {
        this.emit({ type: 'EFFECT_REMOVED', targetId: actor, effectId: effect.effectId, reason: 'EXPIRED' });
      }
    }
    this.emit({ type: 'TURN_ENDED', actorId: actor });
  }

  /** Emits `DEATH` + `COMBAT_ENDED` if the enemy is down. */
  enemyDied(): boolean {
    if (this.session.enemy.health > 0) return false;
    this.emit({ type: 'DEATH', combatantId: 'enemy' });
    this.emit({ type: 'COMBAT_ENDED', result: 'VICTORY' });
    return true;
  }
}

function validateAction(session: Session, action: ActionRequest): void {
  if (session.status !== 'IN_PROGRESS' || session.combatStatus !== 'IN_PROGRESS') {
    fail('INVALID_STATE', 'No hay un combate en curso');
  }
  const frozen = hasEffect(session.hero, 'frozen');
  if (frozen && action.type !== 'PASS') {
    fail('ACTION_NOT_ALLOWED', 'Estás congelado: solo puedes pasar el turno');
  }
  if (!frozen && action.type === 'PASS') {
    fail('ACTION_NOT_ALLOWED', 'Solo puedes pasar el turno si estás congelado');
  }
  if (action.type === 'ABILITY') {
    const ability = heroClassOf(session.heroClassId).abilities.find((candidate) => candidate.id === action.abilityId);
    if (!ability) fail('INVALID_VALUE', `La habilidad "${action.abilityId}" no existe`);
    const remaining = session.hero.cooldowns[ability.id] ?? 0;
    if (remaining > 0) {
      fail('ABILITY_ON_COOLDOWN', `${ability.name} estará disponible en ${remaining} turnos`);
    }
  }
}

function playRound(session: Session, action: ActionRequest): EventDTO[] {
  const script = scriptedRound(scriptOf(session.currentLevel), session.scriptIndex);
  const recorder = new RoundRecorder(session, 'hero');
  session.scriptIndex += 1;
  session.statistics.totalRounds += 1;

  // Hero turn.
  recorder.startTurn('hero', script.heroTurnStart);
  if (hasEffect(session.hero, 'frozen')) {
    recorder.emit({ type: 'TURN_SKIPPED', actorId: 'hero', effectId: 'frozen' });
  } else if (action.type === 'DEFEND') {
    recorder.emit({ type: 'ACTION', actorId: 'hero', action: 'DEFEND' });
    recorder.emit({ type: 'EFFECT_APPLIED', targetId: 'hero', effectId: 'guard', duration: 1 });
  } else {
    const ability: HeroAbilityScript =
      action.type === 'ABILITY' ? (heroAbilityScripts[action.abilityId] ?? { strikes: true }) : { strikes: true };
    recorder.emit(
      action.type === 'ABILITY'
        ? { type: 'ACTION', actorId: 'hero', action: 'ABILITY', abilityId: action.abilityId }
        : { type: 'ACTION', actorId: 'hero', action: 'ATTACK' },
    );
    if (ability.selfEffectId !== undefined) {
      recorder.emit({ type: 'EFFECT_APPLIED', targetId: 'hero', effectId: ability.selfEffectId, duration: baseDuration(ability.selfEffectId) });
    }
    if (ability.strikes) {
      for (const event of script.heroStrike) {
        recorder.emit(event);
        if (recorder.enemyDied()) {
          finishWithVictory(session);
          return recorder.events;
        }
      }
    }
    if (ability.opponentEffectId !== undefined) {
      recorder.emit({ type: 'EFFECT_APPLIED', targetId: 'enemy', effectId: ability.opponentEffectId, duration: baseDuration(ability.opponentEffectId) });
    }
    if (ability.purgesOpponent === true) {
      for (const effect of [...session.enemy.effects]) {
        recorder.emit({ type: 'EFFECT_REMOVED', targetId: 'enemy', effectId: effect.effectId, reason: 'PURGED' });
      }
    }
  }
  recorder.endTurn('hero');

  // Enemy turn.
  recorder.startTurn('enemy', script.enemyTurnStart);
  if (hasEffect(session.enemy, 'frozen')) {
    recorder.emit({ type: 'TURN_SKIPPED', actorId: 'enemy', effectId: 'frozen' });
  } else {
    for (const event of script.enemyTurn) recorder.emit(event);
  }
  recorder.endTurn('enemy');

  session.round += 1;
  return recorder.events;
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

function preview(request: PreviewRequest): PreviewDTO {
  if ('heroClassId' in request) {
    const heroClass = heroClassOf(request.heroClassId);
    request.itemIds.forEach(itemOf);
    const view = buildChain({ id: heroClass.id, label: heroClass.name, stats: heroClass.stats }, request.itemIds);
    return { ...view, replaces: null };
  }
  const session = findSession(request.expeditionId);
  const item = itemOf(request.itemId);
  const heroClass = heroClassOf(session.heroClassId);
  const equipment: EquipmentDTO = { ...session.equipment, [item.slot]: item.id };
  const view = buildChain({ id: heroClass.id, label: heroClass.name, stats: heroClass.stats }, equippedIds(equipment));
  const current = session.equipment[item.slot];
  return { ...view, replaces: current !== null && current !== item.id ? current : null };
}

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `mock-${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}

export const mockClient: ApiClient = {
  health: () => respond(LATENCY_MS.read, () => ({ status: 'OK' as const })),
  getHeroClasses: () => respond(LATENCY_MS.read, () => heroClassesFixture),
  getEquipment: () => respond(LATENCY_MS.read, () => equipmentFixture),
  getEffects: () => respond(LATENCY_MS.read, () => effectsFixture),
  getEnemies: () => respond(LATENCY_MS.read, () => enemiesFixture),

  preview: (request) => respond(LATENCY_MS.read, () => preview(request)),

  createExpedition: (request: CreateExpeditionRequest) =>
    respond(LATENCY_MS.write, () => {
      const heroClass = heroClassOf(request.heroClassId);
      const startingItem = itemOf(request.startingItemId);
      const equipment: EquipmentDTO = { WEAPON: null, ARMOR: null, ACCESSORY: null, [startingItem.slot]: startingItem.id };
      const session: Session = {
        id: newId(),
        seed: request.seed ?? Math.floor(Math.random() * 1_000_000_000),
        heroClassId: heroClass.id,
        status: 'IN_PROGRESS',
        currentLevel: 1,
        equipment,
        hero: { health: 0, effects: [], cooldowns: {} },
        enemy: { health: 0, effects: [], cooldowns: {} },
        combatStatus: 'IN_PROGRESS',
        round: 1,
        scriptIndex: 0,
        seq: 0,
        log: [],
        offeredRewardIds: [],
        statistics: { enemiesDefeated: 0, totalRounds: 0, damageDealt: 0, damageTaken: 0 },
      };
      startEncounter(session, 1);
      session.hero.health = heroChain(session).stats.maxHealth;
      sessions.set(session.id, session);
      return toExpeditionDTO(session);
    }),

  getExpedition: (expeditionId) => respond(LATENCY_MS.read, () => toExpeditionDTO(findSession(expeditionId))),

  act: (expeditionId, action) =>
    respond(LATENCY_MS.write, (): ActionResultDTO => {
      const session = findSession(expeditionId);
      validateAction(session, action);
      const events = playRound(session, action);
      return { expedition: toExpeditionDTO(session), events };
    }),

  chooseReward: (expeditionId, request: RewardRequest) =>
    respond(LATENCY_MS.write, () => {
      const session = findSession(expeditionId);
      if (session.status !== 'AWAITING_REWARD') {
        fail('INVALID_STATE', 'No hay ninguna recompensa pendiente');
      }
      if (request.itemId !== null) {
        if (!session.offeredRewardIds.includes(request.itemId)) {
          fail('INVALID_VALUE', 'Esa pieza no está entre las recompensas ofrecidas');
        }
        const item = itemOf(request.itemId);
        session.equipment = { ...session.equipment, [item.slot]: item.id };
      }
      startEncounter(session, session.currentLevel + 1);
      session.hero.health = Math.min(session.hero.health, heroChain(session).stats.maxHealth);
      return toExpeditionDTO(session);
    }),

  deleteExpedition: (expeditionId) =>
    respond(LATENCY_MS.read, () => {
      findSession(expeditionId);
      sessions.delete(expeditionId);
    }),
};

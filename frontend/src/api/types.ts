/**
 * API types — a 1:1 mirror of `specs/api-contract.md`.
 *
 * Rules:
 * - If the contract changes, it changes there first; then this file follows.
 * - Enums are string-literal unions (UPPER_SNAKE_CASE values, as on the wire).
 * - Fields the contract marks as "not sent" for a given variant are simply absent
 *   from that variant (never typed as `null`).
 */

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

/** Equipment slot (contract §3 `ItemDTO.slot`, `ExpeditionDTO.equipment` keys). */
export type Slot = 'WEAPON' | 'ARMOR' | 'ACCESSORY';

/** Effect / layer category (contract §3 `EffectInfoDTO.category`). */
export type EffectCategory = 'EQUIPMENT' | 'BUFF' | 'DEBUFF' | 'CONTROL';

/** Expedition status (contract §5). */
export type ExpeditionStatus = 'IN_PROGRESS' | 'AWAITING_REWARD' | 'COMPLETED' | 'FAILED';

/** Status of a node of the expedition map (contract §5 `map[].status`). */
export type MapNodeStatus = 'DEFEATED' | 'CURRENT' | 'HIDDEN' | 'BOSS';

/** Combat status (contract §5 `CombatDTO.status`). */
export type CombatStatus = 'IN_PROGRESS' | 'VICTORY' | 'DEFEAT';

/** Side of a combatant (contract §5 `CombatantDTO.side`, glossary `Side`). */
export type Side = 'HERO' | 'ENEMY';

/** Fixed combatant ids (contract §5: `"hero"` and `"enemy"`). */
export type CombatantId = 'hero' | 'enemy';

/** Hero action type (contract §6 `ActionRequest.type`). */
export type ActionType = 'ATTACK' | 'DEFEND' | 'ABILITY' | 'PASS';

/** Damage type of a `DAMAGE` event (design §6). */
export type DamageType = 'PHYSICAL' | 'ELEMENTAL' | 'POISON' | 'REFLECTED';

/** Reason of an `EFFECT_REMOVED` event (design §6). */
export type RemovalReason = 'EXPIRED' | 'DEPLETED' | 'PURGED' | 'INTERACTION';

/** Result of a `COMBAT_ENDED` event (design §6). */
export type CombatResult = 'VICTORY' | 'DEFEAT';

/** Backend error codes (contract §1). */
export type ErrorCode =
  | 'INVALID_JSON'
  | 'REQUIRED_FIELD'
  | 'INVALID_VALUE'
  | 'NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'INVALID_STATE'
  | 'ABILITY_ON_COOLDOWN'
  | 'ACTION_NOT_ALLOWED'
  | 'INTERNAL_ERROR';

// ---------------------------------------------------------------------------
// Errors (contract §1)
// ---------------------------------------------------------------------------

/** Body of every error response. `message` is player-visible (Spanish). */
export interface ApiErrorBody {
  error: {
    code: ErrorCode;
    message: string;
  };
}

// ---------------------------------------------------------------------------
// Shared value objects
// ---------------------------------------------------------------------------

/** Stats block used by classes, enemies, combatants, previews and layers. */
export interface StatsDTO {
  maxHealth: number;
  attack: number;
  defense: number;
  speed: number;
  critChance: number;
}

/** `GET /api/health` response. */
export interface HealthDTO {
  status: 'OK';
}

// ---------------------------------------------------------------------------
// Catalog (contract §3)
// ---------------------------------------------------------------------------

export interface AbilityDTO {
  id: string;
  name: string;
  description: string;
  cooldown: number;
  /** Only meaningful inside a combat; always `0` in the catalog. */
  cooldownRemaining: number;
}

export interface HeroClassDTO {
  id: string;
  name: string;
  description: string;
  stats: StatsDTO;
  abilities: AbilityDTO[];
}

export interface ItemDTO {
  id: string;
  name: string;
  slot: Slot;
  description: string;
  icon: string;
}

export interface EffectInfoDTO {
  id: string;
  label: string;
  category: EffectCategory;
  baseDuration: number;
  description: string;
  icon: string;
}

export interface EnemyInfoDTO {
  id: string;
  name: string;
  level: number;
  boss: boolean;
  stats: StatsDTO;
  abilities: AbilityDTO[];
}

// ---------------------------------------------------------------------------
// Chain inspector (contract §5 `LayerDTO`, RF-20) — outer to inner
// ---------------------------------------------------------------------------

interface LayerCommon {
  position: number;
  id: string;
  label: string;
  statsAtLayer: StatsDTO;
}

/** A decorator layer (equipment or temporary effect). Equipment has `turnsRemaining: null`. */
export interface DecoratorLayerDTO extends LayerCommon {
  isBase: false;
  category: EffectCategory;
  turnsRemaining: number | null;
}

/** The innermost layer: the base character. */
export interface BaseLayerDTO extends LayerCommon {
  isBase: true;
  category: null;
  turnsRemaining: null;
}

export type LayerDTO = DecoratorLayerDTO | BaseLayerDTO;

// ---------------------------------------------------------------------------
// Preview (contract §4)
// ---------------------------------------------------------------------------

/** First form: a hero class plus a list of items (loadout screen). */
export interface LoadoutPreviewRequest {
  heroClassId: string;
  itemIds: string[];
}

/** Second form: how the hero of an expedition would look with a reward item. */
export interface RewardPreviewRequest {
  expeditionId: string;
  itemId: string;
}

export type PreviewRequest = LoadoutPreviewRequest | RewardPreviewRequest;

export interface PreviewDTO {
  stats: StatsDTO;
  chain: string;
  layers: LayerDTO[];
  /** Id of the item that would be replaced (second form only), otherwise `null`. */
  replaces: string | null;
}

// ---------------------------------------------------------------------------
// Expedition (contract §5)
// ---------------------------------------------------------------------------

export interface CreateExpeditionRequest {
  heroClassId: string;
  startingItemId: string;
  /** Optional: when absent the server generates one and returns it. */
  seed?: number;
}

export interface MapNodeDTO {
  level: number;
  /** `null` while the node is `HIDDEN`. */
  enemyId: string | null;
  /** `null` while the node is `HIDDEN`. */
  name: string | null;
  status: MapNodeStatus;
}

export type EquipmentDTO = Record<Slot, string | null>;

export interface StatisticsDTO {
  enemiesDefeated: number;
  totalRounds: number;
  damageDealt: number;
  damageTaken: number;
}

/** A temporary effect on a combatant (contract §5 `CombatantDTO.effects`). */
export interface ActiveEffectDTO {
  effectId: string;
  label: string;
  category: EffectCategory;
  turnsRemaining: number;
  /**
   * Effect-specific data, or `null`. The contract only documents
   * `{ "absorption": number }` for `shield`.
   */
  extra: Record<string, number> | null;
}

export interface CombatantDTO {
  id: CombatantId;
  name: string;
  side: Side;
  /** Hero class id (hero) or enemy id (enemy). */
  archetypeId: string;
  health: number;
  stats: StatsDTO;
  canAct: boolean;
  /** Temporary effects only, outer to inner. */
  effects: ActiveEffectDTO[];
  abilities: AbilityDTO[];
  chain: string;
  layers: LayerDTO[];
}

export interface CombatDTO {
  status: CombatStatus;
  round: number;
  hero: CombatantDTO;
  enemy: CombatantDTO;
  /** Every event of the current combat (to rebuild the log on reload). */
  log: EventDTO[];
}

export interface ExpeditionDTO {
  id: string;
  seed: number;
  status: ExpeditionStatus;
  currentLevel: number;
  totalLevels: number;
  map: MapNodeDTO[];
  equipment: EquipmentDTO;
  combat: CombatDTO;
  /** Exactly 3 items in `AWAITING_REWARD`, otherwise `[]`. */
  offeredRewards: ItemDTO[];
  statistics: StatisticsDTO;
}

// ---------------------------------------------------------------------------
// Actions (contract §6)
// ---------------------------------------------------------------------------

/** `abilityId` is required only when `type = ABILITY`. */
export type ActionRequest =
  | { type: 'ATTACK' }
  | { type: 'DEFEND' }
  | { type: 'PASS' }
  | { type: 'ABILITY'; abilityId: string };

export interface ActionResultDTO {
  expedition: ExpeditionDTO;
  /** Only the events produced by this action (a full round), ordered by `seq`. */
  events: EventDTO[];
}

// ---------------------------------------------------------------------------
// Events (contract §6, design §6) — flat, discriminated by `type`
// ---------------------------------------------------------------------------

interface EventCommon {
  seq: number;
  round: number;
}

export interface TurnStartedEvent extends EventCommon {
  type: 'TURN_STARTED';
  actorId: CombatantId;
}

export interface ActionEvent extends EventCommon {
  type: 'ACTION';
  actorId: CombatantId;
  action: ActionType;
  /** Present only when `action = ABILITY`. */
  abilityId?: string;
}

export interface DamageEvent extends EventCommon {
  type: 'DAMAGE';
  targetId: CombatantId;
  amount: number;
  damageType: DamageType;
  critical: boolean;
}

export interface EvadedEvent extends EventCommon {
  type: 'EVADED';
  targetId: CombatantId;
}

export interface AbsorbedEvent extends EventCommon {
  type: 'ABSORBED';
  targetId: CombatantId;
  amount: number;
  remaining: number;
}

export interface HealEvent extends EventCommon {
  type: 'HEAL';
  targetId: CombatantId;
  amount: number;
  sourceEffectId: string;
}

export interface EffectAppliedEvent extends EventCommon {
  type: 'EFFECT_APPLIED';
  targetId: CombatantId;
  effectId: string;
  duration: number;
}

export interface EffectRefreshedEvent extends EventCommon {
  type: 'EFFECT_REFRESHED';
  targetId: CombatantId;
  effectId: string;
  duration: number;
}

export interface EffectRemovedEvent extends EventCommon {
  type: 'EFFECT_REMOVED';
  targetId: CombatantId;
  effectId: string;
  reason: RemovalReason;
}

export interface TurnSkippedEvent extends EventCommon {
  type: 'TURN_SKIPPED';
  actorId: CombatantId;
  effectId: string;
}

export interface DeathEvent extends EventCommon {
  type: 'DEATH';
  combatantId: CombatantId;
}

export interface TurnEndedEvent extends EventCommon {
  type: 'TURN_ENDED';
  actorId: CombatantId;
}

export interface CombatEndedEvent extends EventCommon {
  type: 'COMBAT_ENDED';
  result: CombatResult;
}

export type EventDTO =
  | TurnStartedEvent
  | ActionEvent
  | DamageEvent
  | EvadedEvent
  | AbsorbedEvent
  | HealEvent
  | EffectAppliedEvent
  | EffectRefreshedEvent
  | EffectRemovedEvent
  | TurnSkippedEvent
  | DeathEvent
  | TurnEndedEvent
  | CombatEndedEvent;

export type EventType = EventDTO['type'];

/** Narrows `EventDTO` to the variant with the given `type`. */
export type EventOfType<T extends EventType> = Extract<EventDTO, { type: T }>;

// ---------------------------------------------------------------------------
// Reward (contract §7)
// ---------------------------------------------------------------------------

/** `itemId: null` skips the reward. */
export interface RewardRequest {
  itemId: string | null;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Compile-time exhaustiveness guard: only callable with `never`. */
export function assertNever(value: never): never {
  throw new Error(`Unexpected value: ${JSON.stringify(value)}`);
}

/**
 * Short developer-facing description of an event (debugging / devtools).
 * The player-visible, Spanish log text belongs to `CombatLog`.
 *
 * The `switch` is exhaustive: adding a variant to `EventDTO` without handling
 * it here is a compile error (T-401 acceptance).
 */
export function describeEvent(event: EventDTO): string {
  const prefix = `#${event.seq} R${event.round}`;
  switch (event.type) {
    case 'TURN_STARTED':
      return `${prefix} turn started: ${event.actorId}`;
    case 'ACTION':
      return `${prefix} ${event.actorId} -> ${event.action}${event.abilityId ? ` (${event.abilityId})` : ''}`;
    case 'DAMAGE':
      return `${prefix} ${event.targetId} takes ${event.amount} ${event.damageType}${event.critical ? ' (critical)' : ''}`;
    case 'EVADED':
      return `${prefix} ${event.targetId} evaded`;
    case 'ABSORBED':
      return `${prefix} ${event.targetId} absorbed ${event.amount} (${event.remaining} left)`;
    case 'HEAL':
      return `${prefix} ${event.targetId} heals ${event.amount} from ${event.sourceEffectId}`;
    case 'EFFECT_APPLIED':
      return `${prefix} ${event.effectId} applied to ${event.targetId} for ${event.duration}`;
    case 'EFFECT_REFRESHED':
      return `${prefix} ${event.effectId} refreshed on ${event.targetId} for ${event.duration}`;
    case 'EFFECT_REMOVED':
      return `${prefix} ${event.effectId} removed from ${event.targetId} (${event.reason})`;
    case 'TURN_SKIPPED':
      return `${prefix} ${event.actorId} skips the turn (${event.effectId})`;
    case 'DEATH':
      return `${prefix} ${event.combatantId} died`;
    case 'TURN_ENDED':
      return `${prefix} turn ended: ${event.actorId}`;
    case 'COMBAT_ENDED':
      return `${prefix} combat ended: ${event.result}`;
    default:
      return assertNever(event);
  }
}

/**
 * The examples of `specs/api-contract.md`, copied verbatim and typed against `api/types.ts`.
 * They are compile-time evidence that the types match the contract, and handy
 * static data for screens/components developed in isolation.
 *
 * Placeholders of the contract (`AbilityDTO`, `LayerDTO`, `EventDTO`, `…` in ids) are
 * filled with data consistent with the rest of the example.
 */
import type {
  AbilityDTO,
  ActionRequest,
  ActionResultDTO,
  ApiErrorBody,
  CombatantDTO,
  CreateExpeditionRequest,
  EffectInfoDTO,
  EnemyInfoDTO,
  EventDTO,
  ExpeditionDTO,
  HealthDTO,
  HeroClassDTO,
  ItemDTO,
  LayerDTO,
  PreviewDTO,
  PreviewRequest,
  RewardRequest,
} from '../../api/types';

const EXAMPLE_EXPEDITION_ID = '6f1c2a7e-0000-4000-8000-000000000042';

// §1 Errors
export const exampleError: ApiErrorBody = {
  error: { code: 'ABILITY_ON_COOLDOWN', message: 'Grito de guerra estará disponible en 2 turnos' },
};

// §2 Health
export const exampleHealth: HealthDTO = { status: 'OK' };

// §3 Catalog
export const exampleAbility: AbilityDTO = {
  id: 'war_cry',
  name: 'Grito de guerra',
  description: 'Entra en Furia: +50% ataque, -30% defensa durante 2 turnos.',
  cooldown: 3,
  cooldownRemaining: 0,
};

export const exampleHeroClass: HeroClassDTO = {
  id: 'warrior',
  name: 'Guerrero',
  description: 'Resistente y brutal en el cuerpo a cuerpo.',
  stats: { maxHealth: 120, attack: 14, defense: 8, speed: 4, critChance: 10 },
  abilities: [
    exampleAbility,
    {
      id: 'shield_wall',
      name: 'Muro de escudos',
      description: 'Se protege con un Escudo que absorbe 20 de daño durante 3 turnos.',
      cooldown: 3,
      cooldownRemaining: 0,
    },
  ],
};

export const exampleItem: ItemDTO = {
  id: 'sword',
  name: 'Espada',
  slot: 'WEAPON',
  description: '+6 ataque',
  icon: 'sword',
};

export const exampleEffectInfo: EffectInfoDTO = {
  id: 'poison',
  label: 'Envenenado',
  category: 'DEBUFF',
  baseDuration: 3,
  description: 'Pierde 6 de vida al inicio de cada turno.',
  icon: 'poison',
};

export const exampleEnemyInfo: EnemyInfoDTO = {
  id: 'orc_shaman',
  name: 'Orco chamán',
  level: 2,
  boss: false,
  stats: { maxHealth: 110, attack: 14, defense: 6, speed: 5, critChance: 10 },
  abilities: [
    {
      id: 'blood_totem',
      name: 'Tótem de sangre',
      description: 'Obtiene Vampirismo.',
      cooldown: 4,
      cooldownRemaining: 0,
    },
  ],
};

// §4 Preview
export const examplePreviewRequestLoadout: PreviewRequest = {
  heroClassId: 'archer',
  itemIds: ['sword', 'wind_boots'],
};

export const examplePreviewRequestReward: PreviewRequest = {
  expeditionId: EXAMPLE_EXPEDITION_ID,
  itemId: 'dragon_armor',
};

export const examplePreview: PreviewDTO = {
  stats: { maxHealth: 95, attack: 21, defense: 5, speed: 15, critChance: 20 },
  chain: 'Botas de viento(Espada(Arquero))',
  layers: [
    {
      position: 0, id: 'wind_boots', label: 'Botas de viento', category: 'EQUIPMENT',
      turnsRemaining: null, isBase: false,
      statsAtLayer: { maxHealth: 95, attack: 21, defense: 5, speed: 15, critChance: 20 },
    },
    {
      position: 1, id: 'sword', label: 'Espada', category: 'EQUIPMENT',
      turnsRemaining: null, isBase: false,
      statsAtLayer: { maxHealth: 95, attack: 21, defense: 5, speed: 10, critChance: 20 },
    },
    {
      position: 2, id: 'archer', label: 'Arquero', category: null,
      turnsRemaining: null, isBase: true,
      statsAtLayer: { maxHealth: 95, attack: 15, defense: 5, speed: 10, critChance: 20 },
    },
  ],
  replaces: null,
};

// §5 Expedition
export const exampleCreateExpeditionRequest: CreateExpeditionRequest = {
  heroClassId: 'mage',
  startingItemId: 'rune_staff',
  seed: 42,
};

export const exampleLayer: LayerDTO = {
  position: 0, id: 'regeneration', label: 'Regeneración', category: 'BUFF',
  turnsRemaining: 2, isBase: false,
  statsAtLayer: { maxHealth: 105, attack: 21, defense: 4, speed: 6, critChance: 25 },
};

const mageStats = { maxHealth: 105, attack: 21, defense: 4, speed: 6, critChance: 25 };

export const exampleHeroCombatant: CombatantDTO = {
  id: 'hero',
  name: 'Mago',
  side: 'HERO',
  archetypeId: 'mage',
  health: 52,
  stats: mageStats,
  canAct: true,
  effects: [
    { effectId: 'regeneration', label: 'Regeneración', category: 'BUFF', turnsRemaining: 2, extra: null },
    { effectId: 'shield', label: 'Escudo', category: 'BUFF', turnsRemaining: 3, extra: { absorption: 15 } },
  ],
  abilities: [
    { id: 'ice_bolt', name: 'Rayo de hielo', description: 'Inflige 80% de daño y congela al rival 1 turno.', cooldown: 4, cooldownRemaining: 2 },
    { id: 'arcane_silence', name: 'Silencio arcano', description: 'Elimina todos los efectos temporales del rival.', cooldown: 4, cooldownRemaining: 0 },
  ],
  chain: 'Regeneración(Amuleto de vida(Bastón rúnico(Mago)))',
  layers: [
    exampleLayer,
    {
      position: 1, id: 'life_amulet', label: 'Amuleto de vida', category: 'EQUIPMENT',
      turnsRemaining: null, isBase: false, statsAtLayer: mageStats,
    },
    {
      position: 2, id: 'rune_staff', label: 'Bastón rúnico', category: 'EQUIPMENT',
      turnsRemaining: null, isBase: false,
      statsAtLayer: { maxHealth: 80, attack: 21, defense: 4, speed: 6, critChance: 25 },
    },
    {
      position: 3, id: 'mage', label: 'Mago', category: null,
      turnsRemaining: null, isBase: true,
      statsAtLayer: { maxHealth: 80, attack: 18, defense: 4, speed: 6, critChance: 10 },
    },
  ],
};

const skeletonStats = { maxHealth: 100, attack: 13, defense: 7, speed: 3, critChance: 5 };

export const exampleEnemyCombatant: CombatantDTO = {
  id: 'enemy',
  name: 'Esqueleto',
  side: 'ENEMY',
  archetypeId: 'skeleton',
  health: 64,
  stats: skeletonStats,
  canAct: true,
  effects: [],
  abilities: [
    { id: 'reassemble', name: 'Reensamblar', description: 'Obtiene Regeneración cuando está malherido.', cooldown: 5, cooldownRemaining: 0 },
    { id: 'sharp_bones', name: 'Huesos afilados', description: 'Se cubre de Espinas.', cooldown: 4, cooldownRemaining: 1 },
  ],
  chain: 'Esqueleto',
  layers: [
    {
      position: 0, id: 'skeleton', label: 'Esqueleto', category: null,
      turnsRemaining: null, isBase: true, statsAtLayer: skeletonStats,
    },
  ],
};

/** §6 event examples, verbatim. */
export const exampleEvents: EventDTO[] = [
  { seq: 41, round: 3, type: 'DAMAGE', targetId: 'enemy', amount: 21, damageType: 'PHYSICAL', critical: true },
  { seq: 42, round: 3, type: 'ABSORBED', targetId: 'enemy', amount: 8, remaining: 12 },
  { seq: 43, round: 3, type: 'EFFECT_APPLIED', targetId: 'enemy', effectId: 'frozen', duration: 1 },
  { seq: 44, round: 3, type: 'EFFECT_REMOVED', targetId: 'enemy', effectId: 'rage', reason: 'INTERACTION' },
  { seq: 50, round: 3, type: 'TURN_SKIPPED', actorId: 'enemy', effectId: 'frozen' },
  { seq: 51, round: 3, type: 'ACTION', actorId: 'hero', action: 'ABILITY', abilityId: 'ice_bolt' },
];

export const exampleExpedition: ExpeditionDTO = {
  id: EXAMPLE_EXPEDITION_ID,
  seed: 42,
  status: 'IN_PROGRESS',
  currentLevel: 2,
  totalLevels: 4,
  map: [
    { level: 1, enemyId: 'goblin', name: 'Goblin', status: 'DEFEATED' },
    { level: 2, enemyId: 'skeleton', name: 'Esqueleto', status: 'CURRENT' },
    { level: 3, enemyId: null, name: null, status: 'HIDDEN' },
    { level: 4, enemyId: 'dragon', name: 'Dragón', status: 'BOSS' },
  ],
  equipment: { WEAPON: 'rune_staff', ARMOR: null, ACCESSORY: 'life_amulet' },
  combat: {
    status: 'IN_PROGRESS',
    round: 3,
    hero: exampleHeroCombatant,
    enemy: exampleEnemyCombatant,
    log: exampleEvents,
  },
  offeredRewards: [],
  statistics: { enemiesDefeated: 1, totalRounds: 6, damageDealt: 97, damageTaken: 41 },
};

// §6 Actions
export const exampleActionRequest: ActionRequest = { type: 'ABILITY', abilityId: 'ice_bolt' };

export const exampleActionResult: ActionResultDTO = {
  expedition: exampleExpedition,
  events: exampleEvents,
};

// §7 Reward
export const exampleRewardRequest: RewardRequest = { itemId: 'dragon_armor' };
export const exampleSkipRewardRequest: RewardRequest = { itemId: null };

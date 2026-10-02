/**
 * Builds the display data of a decorator chain (`stats`, `chain`, `layers`) for the mock.
 *
 * MOCK ONLY: this is NOT the domain logic. Equipment bonuses are a flat table so the
 * screens show plausible numbers; temporary effects never change stats here.
 * The real values always come from the backend.
 */
import type { EffectCategory, LayerDTO, Slot, StatsDTO } from '../api/types';
import { effectsFixture, equipmentFixture } from './fixtures/catalog';

/** Flat stat deltas per item, approximating design §4.6 for display purposes. */
const ITEM_STAT_DELTAS: Record<string, Partial<StatsDTO>> = {
  sword: { attack: 6 },
  war_axe: { attack: 10, speed: -3 },
  rune_staff: { attack: 3, critChance: 15 },
  leather_armor: { defense: 4 },
  dragon_armor: { defense: 10, speed: -4 },
  fire_ring: {},
  life_amulet: { maxHealth: 25 },
  wind_boots: { speed: 5 },
};

/** Inner-to-outer wrapping order of the equipment slots (matches the contract example chain). */
export const SLOT_WRAP_ORDER: readonly Slot[] = ['WEAPON', 'ARMOR', 'ACCESSORY'];

function addDelta(stats: StatsDTO, delta: Partial<StatsDTO>): StatsDTO {
  const clamp = (value: number): number => Math.max(0, value);
  return {
    maxHealth: clamp(stats.maxHealth + (delta.maxHealth ?? 0)),
    attack: clamp(stats.attack + (delta.attack ?? 0)),
    defense: clamp(stats.defense + (delta.defense ?? 0)),
    speed: clamp(stats.speed + (delta.speed ?? 0)),
    critChance: Math.min(100, clamp(stats.critChance + (delta.critChance ?? 0))),
  };
}

export interface ChainBase {
  id: string;
  label: string;
  stats: StatsDTO;
}

/** A temporary effect as the chain builder needs it, outer to inner. */
export interface ChainEffect {
  effectId: string;
  turnsRemaining: number;
}

export interface ChainView {
  stats: StatsDTO;
  chain: string;
  layers: LayerDTO[];
}

interface PendingLayer {
  id: string;
  label: string;
  category: EffectCategory;
  turnsRemaining: number | null;
  statsAtLayer: StatsDTO;
}

export function effectLabel(effectId: string): string {
  return effectsFixture.find((effect) => effect.id === effectId)?.label ?? effectId;
}

export function effectCategory(effectId: string): EffectCategory {
  return effectsFixture.find((effect) => effect.id === effectId)?.category ?? 'BUFF';
}

/**
 * @param itemIds equipment ids, inner to outer
 * @param effects temporary effects, outer to inner (as in `CombatantDTO.effects`)
 */
export function buildChain(base: ChainBase, itemIds: readonly string[], effects: readonly ChainEffect[] = []): ChainView {
  const innerToOuter: PendingLayer[] = [];
  let stats = base.stats;

  for (const itemId of itemIds) {
    const item = equipmentFixture.find((candidate) => candidate.id === itemId);
    stats = addDelta(stats, ITEM_STAT_DELTAS[itemId] ?? {});
    innerToOuter.push({
      id: itemId,
      label: item?.name ?? itemId,
      category: 'EQUIPMENT',
      turnsRemaining: null,
      statsAtLayer: stats,
    });
  }
  for (const effect of [...effects].reverse()) {
    innerToOuter.push({
      id: effect.effectId,
      label: effectLabel(effect.effectId),
      category: effectCategory(effect.effectId),
      turnsRemaining: effect.turnsRemaining,
      statsAtLayer: stats,
    });
  }

  const outerToInner = [...innerToOuter].reverse();
  const layers: LayerDTO[] = outerToInner.map((layer, position) => ({ ...layer, position, isBase: false }));
  layers.push({
    position: layers.length,
    id: base.id,
    label: base.label,
    category: null,
    turnsRemaining: null,
    isBase: true,
    statsAtLayer: base.stats,
  });

  // "Outer(Middle(Base))": wrap the base label from the innermost layer outwards.
  const chain = innerToOuter.reduce((inner, layer) => `${layer.label}(${inner})`, base.label);
  return { stats, chain, layers };
}

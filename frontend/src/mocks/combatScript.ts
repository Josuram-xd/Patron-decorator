/**
 * Scripted combats for the mock client.
 *
 * MOCK ONLY: nothing here is computed from combat rules. Each round is a fixed list of
 * events; the mock client only replays them (adding `seq`/`round`, the hero's `ACTION`
 * and the turn bookkeeping events). The goal is UI coverage of every event type, not
 * game accuracy — e.g. the slime "freezing" the hero is scripted on purpose so the
 * frozen / `PASS` flow can be exercised at level 1.
 */
import type { EventDTO } from '../api/types';

type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;

/** An event before the mock assigns `seq` and `round`. */
export type ScriptedEvent = DistributiveOmit<EventDTO, 'seq' | 'round'>;

export interface ScriptedRound {
  /** Start-of-turn ticks of the hero (poison, regeneration), right after `TURN_STARTED`. */
  heroTurnStart: ScriptedEvent[];
  /** Outcome of the hero's `ATTACK` or `ABILITY` (the `ACTION` event is added by the mock). */
  heroStrike: ScriptedEvent[];
  /** Start-of-turn ticks of the enemy. */
  enemyTurnStart: ScriptedEvent[];
  /** Full enemy turn body, including its `ACTION` event. */
  enemyTurn: ScriptedEvent[];
}

export interface LevelScript {
  enemyId: string;
  rounds: ScriptedRound[];
  /** Repeated once `rounds` is exhausted, until the enemy dies. */
  finisher: ScriptedRound;
}

const round = (partial: Partial<ScriptedRound>): ScriptedRound => ({
  heroTurnStart: [],
  heroStrike: [],
  enemyTurnStart: [],
  enemyTurn: [],
  ...partial,
});

const enemyAttack = (amount: number): ScriptedEvent[] => [
  { type: 'ACTION', actorId: 'enemy', action: 'ATTACK' },
  { type: 'DAMAGE', targetId: 'hero', amount, damageType: 'PHYSICAL', critical: false },
];

const heroHit = (amount: number, critical = false): ScriptedEvent[] => [
  { type: 'DAMAGE', targetId: 'enemy', amount, damageType: 'PHYSICAL', critical },
];

const poisonTick: ScriptedEvent = { type: 'DAMAGE', targetId: 'hero', amount: 6, damageType: 'POISON', critical: false };

/**
 * Level 1 — the showcase: damage, critical, shield applied / absorbed / depleted,
 * poison ticks and expiry, evasion, frozen hero with a skipped turn.
 */
const level1: LevelScript = {
  enemyId: 'slime',
  rounds: [
    // R1: plain hit; the slime raises a shield.
    round({
      heroStrike: heroHit(12),
      enemyTurn: [
        { type: 'ACTION', actorId: 'enemy', action: 'ABILITY', abilityId: 'jelly_shield' },
        { type: 'EFFECT_APPLIED', targetId: 'enemy', effectId: 'shield', duration: 3 },
      ],
    }),
    // R2: critical hit mostly absorbed (shield depleted -> removed at the slime's turn end); acid + poison.
    round({
      heroStrike: [
        { type: 'ABSORBED', targetId: 'enemy', amount: 20, remaining: 0 },
        { type: 'DAMAGE', targetId: 'enemy', amount: 7, damageType: 'PHYSICAL', critical: true },
      ],
      enemyTurn: [
        { type: 'ACTION', actorId: 'enemy', action: 'ABILITY', abilityId: 'acid_spit' },
        { type: 'DAMAGE', targetId: 'hero', amount: 4, damageType: 'PHYSICAL', critical: false },
        { type: 'EFFECT_APPLIED', targetId: 'hero', effectId: 'poison', duration: 3 },
      ],
    }),
    // R3: poison tick; the hero evades the slime.
    round({
      heroTurnStart: [poisonTick],
      heroStrike: heroHit(13),
      enemyTurn: [
        { type: 'ACTION', actorId: 'enemy', action: 'ATTACK' },
        { type: 'EVADED', targetId: 'hero' },
      ],
    }),
    // R4: poison tick; the hero gets frozen (scripted for UI coverage).
    round({
      heroTurnStart: [poisonTick],
      heroStrike: heroHit(14),
      enemyTurn: [
        ...enemyAttack(5),
        { type: 'EFFECT_APPLIED', targetId: 'hero', effectId: 'frozen', duration: 1 },
      ],
    }),
    // R5: frozen hero must PASS (TURN_SKIPPED); poison and frozen expire at the end of the turn.
    round({
      heroTurnStart: [poisonTick],
      enemyTurn: enemyAttack(6),
    }),
  ],
  finisher: round({ heroStrike: heroHit(30, true), enemyTurn: enemyAttack(5) }),
};

/** Level 2 — thorns (reflected damage) and regeneration (heal). */
const level2: LevelScript = {
  enemyId: 'skeleton',
  rounds: [
    round({
      heroStrike: heroHit(15),
      enemyTurn: [
        { type: 'ACTION', actorId: 'enemy', action: 'ABILITY', abilityId: 'sharp_bones' },
        { type: 'EFFECT_APPLIED', targetId: 'enemy', effectId: 'thorns', duration: 3 },
      ],
    }),
    round({
      heroStrike: [
        ...heroHit(20),
        { type: 'DAMAGE', targetId: 'hero', amount: 6, damageType: 'REFLECTED', critical: false },
      ],
      enemyTurn: [
        { type: 'ACTION', actorId: 'enemy', action: 'ABILITY', abilityId: 'reassemble' },
        { type: 'EFFECT_APPLIED', targetId: 'enemy', effectId: 'regeneration', duration: 3 },
      ],
    }),
    round({
      heroStrike: [
        ...heroHit(18),
        { type: 'DAMAGE', targetId: 'hero', amount: 5, damageType: 'REFLECTED', critical: false },
      ],
      enemyTurnStart: [{ type: 'HEAL', targetId: 'enemy', amount: 8, sourceEffectId: 'regeneration' }],
      enemyTurn: enemyAttack(7),
    }),
  ],
  finisher: round({
    heroStrike: heroHit(24, true),
    enemyTurnStart: [],
    enemyTurn: enemyAttack(6),
  }),
};

const genericLevel = (enemyId: string): LevelScript => ({
  enemyId,
  rounds: [],
  finisher: round({ heroStrike: heroHit(28), enemyTurn: enemyAttack(7) }),
});

/**
 * What each hero ability visibly does in the mock (from the catalog descriptions):
 * an effect on self, the scripted strike, an effect on the opponent, a purge.
 * Durations come from the effects catalog `baseDuration`.
 */
export interface HeroAbilityScript {
  selfEffectId?: string;
  strikes: boolean;
  opponentEffectId?: string;
  purgesOpponent?: boolean;
}

export const heroAbilityScripts: Readonly<Record<string, HeroAbilityScript>> = {
  war_cry: { selfEffectId: 'rage', strikes: false },
  shield_wall: { selfEffectId: 'shield', strikes: false },
  ice_bolt: { strikes: true, opponentEffectId: 'frozen' },
  arcane_silence: { strikes: false, purgesOpponent: true },
  poison_arrow: { strikes: true, opponentEffectId: 'poison' },
  vampiric_arrow: { selfEffectId: 'lifesteal', strikes: true },
};

/** Scripts by level (1..4). The map of the mock expedition follows these enemy ids. */
export const levelScripts: readonly LevelScript[] = [level1, level2, genericLevel('witch'), genericLevel('dragon')];

export function scriptedRound(script: LevelScript, roundIndex: number): ScriptedRound {
  return script.rounds[roundIndex] ?? script.finisher;
}

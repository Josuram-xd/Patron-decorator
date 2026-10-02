/**
 * Catalog fixtures (api-contract §3) with the data of design §4.2, §4.6, §4.7 and §4.8.
 * Typed against `api/types.ts`, so any drift from the contract is a compile error.
 * Player-visible texts are in Spanish, ids in English.
 */
import type { AbilityDTO, EffectInfoDTO, EnemyInfoDTO, HeroClassDTO, ItemDTO } from '../../api/types';

const ability = (id: string, name: string, description: string, cooldown: number): AbilityDTO => ({
  id,
  name,
  description,
  cooldown,
  cooldownRemaining: 0,
});

export const heroClassesFixture: HeroClassDTO[] = [
  {
    id: 'warrior',
    name: 'Guerrero',
    description: 'Resistente y brutal en el cuerpo a cuerpo.',
    stats: { maxHealth: 120, attack: 14, defense: 8, speed: 4, critChance: 10 },
    abilities: [
      ability('war_cry', 'Grito de guerra', 'Entra en Furia: +50% ataque, -30% defensa durante 2 turnos.', 3),
      ability('shield_wall', 'Muro de escudos', 'Se protege con un Escudo que absorbe 20 de daño durante 3 turnos.', 3),
    ],
  },
  {
    id: 'mage',
    name: 'Mago',
    description: 'Frágil, pero controla el combate con hielo y silencio.',
    stats: { maxHealth: 80, attack: 18, defense: 4, speed: 6, critChance: 10 },
    abilities: [
      ability('ice_bolt', 'Rayo de hielo', 'Inflige 80% de daño y congela al rival 1 turno.', 4),
      ability('arcane_silence', 'Silencio arcano', 'Elimina todos los efectos temporales del rival.', 4),
    ],
  },
  {
    id: 'archer',
    name: 'Arquero',
    description: 'Rápido y certero; desgasta al rival con veneno.',
    stats: { maxHealth: 95, attack: 15, defense: 5, speed: 10, critChance: 20 },
    abilities: [
      ability('poison_arrow', 'Flecha envenenada', 'Inflige 70% de daño y envenena al rival 3 turnos.', 3),
      ability('vampiric_arrow', 'Flecha vampírica', 'Obtiene Vampirismo y dispara una flecha con 100% de daño.', 4),
    ],
  },
];

export const equipmentFixture: ItemDTO[] = [
  { id: 'sword', name: 'Espada', slot: 'WEAPON', description: '+6 ataque', icon: 'sword' },
  { id: 'war_axe', name: 'Hacha de guerra', slot: 'WEAPON', description: '+10 ataque, -3 velocidad', icon: 'war_axe' },
  { id: 'rune_staff', name: 'Bastón rúnico', slot: 'WEAPON', description: '+3 ataque, +15 crítico', icon: 'rune_staff' },
  { id: 'leather_armor', name: 'Armadura de cuero', slot: 'ARMOR', description: '+4 defensa', icon: 'leather_armor' },
  { id: 'dragon_armor', name: 'Armadura de dragón', slot: 'ARMOR', description: '+10 defensa, -4 velocidad', icon: 'dragon_armor' },
  { id: 'fire_ring', name: 'Anillo de fuego', slot: 'ACCESSORY', description: '+4 daño elemental en cada golpe', icon: 'fire_ring' },
  { id: 'life_amulet', name: 'Amuleto de vida', slot: 'ACCESSORY', description: '+25 vida máxima', icon: 'life_amulet' },
  { id: 'wind_boots', name: 'Botas de viento', slot: 'ACCESSORY', description: '+5 velocidad', icon: 'wind_boots' },
];

/** Temporary effects only (design §4.2). Equipment is served by `/catalog/equipment`. */
export const effectsFixture: EffectInfoDTO[] = [
  { id: 'poison', label: 'Envenenado', category: 'DEBUFF', baseDuration: 3, description: 'Pierde 6 de vida al inicio de cada turno.', icon: 'poison' },
  { id: 'regeneration', label: 'Regeneración', category: 'BUFF', baseDuration: 3, description: 'Recupera 8 de vida al inicio de cada turno.', icon: 'regeneration' },
  { id: 'shield', label: 'Escudo', category: 'BUFF', baseDuration: 3, description: 'Absorbe hasta 20 de daño.', icon: 'shield' },
  { id: 'thorns', label: 'Espinas', category: 'BUFF', baseDuration: 3, description: 'Devuelve el 30% del daño recibido.', icon: 'thorns' },
  { id: 'rage', label: 'Furia', category: 'BUFF', baseDuration: 2, description: '+50% ataque, -30% defensa.', icon: 'rage' },
  { id: 'guard', label: 'En guardia', category: 'BUFF', baseDuration: 1, description: '+50% defensa.', icon: 'guard' },
  { id: 'frozen', label: 'Congelado', category: 'CONTROL', baseDuration: 1, description: 'Pierde su próximo turno.', icon: 'frozen' },
  { id: 'lifesteal', label: 'Vampirismo', category: 'BUFF', baseDuration: 3, description: 'Se cura el 30% del daño que inflige.', icon: 'lifesteal' },
];

export const enemiesFixture: EnemyInfoDTO[] = [
  {
    id: 'goblin', name: 'Goblin', level: 1, boss: false,
    stats: { maxHealth: 70, attack: 11, defense: 3, speed: 8, critChance: 10 },
    abilities: [ability('dirty_dagger', 'Daga sucia', 'Inflige 80% de daño y envenena al rival.', 3)],
  },
  {
    id: 'wolf', name: 'Lobo', level: 1, boss: false,
    stats: { maxHealth: 60, attack: 12, defense: 2, speed: 12, critChance: 15 },
    abilities: [ability('howl', 'Aullido', 'Entra en Furia cuando está herido.', 4)],
  },
  {
    id: 'slime', name: 'Slime', level: 1, boss: false,
    stats: { maxHealth: 90, attack: 8, defense: 4, speed: 2, critChance: 0 },
    abilities: [
      ability('jelly_shield', 'Gelatina', 'Se cubre con un Escudo.', 4),
      ability('acid_spit', 'Ácido', 'Inflige 50% de daño y envenena al rival.', 3),
    ],
  },
  {
    id: 'skeleton', name: 'Esqueleto', level: 2, boss: false,
    stats: { maxHealth: 100, attack: 13, defense: 7, speed: 3, critChance: 5 },
    abilities: [
      ability('reassemble', 'Reensamblar', 'Obtiene Regeneración cuando está malherido.', 5),
      ability('sharp_bones', 'Huesos afilados', 'Se cubre de Espinas.', 4),
    ],
  },
  {
    id: 'orc_shaman', name: 'Orco chamán', level: 2, boss: false,
    stats: { maxHealth: 110, attack: 14, defense: 6, speed: 5, critChance: 10 },
    abilities: [
      ability('curse', 'Maldición', 'Elimina los efectos temporales del rival.', 5),
      ability('blood_totem', 'Tótem de sangre', 'Obtiene Vampirismo.', 4),
    ],
  },
  {
    id: 'stone_golem', name: 'Golem de piedra', level: 3, boss: false,
    stats: { maxHealth: 160, attack: 15, defense: 14, speed: 1, critChance: 0 },
    abilities: [
      ability('stone_skin', 'Piel de piedra', 'Se cubre de Espinas.', 4),
      ability('stomp', 'Pisotón', 'Inflige 100% de daño y congela al rival.', 5),
    ],
  },
  {
    id: 'witch', name: 'Bruja', level: 3, boss: false,
    stats: { maxHealth: 90, attack: 16, defense: 4, speed: 7, critChance: 15 },
    abilities: [
      ability('potion', 'Pócima', 'Obtiene Regeneración cuando está malherida.', 4),
      ability('frost_hex', 'Hechizo gélido', 'Congela al rival.', 4),
      ability('hex', 'Maleficio', 'Envenena al rival.', 3),
    ],
  },
  {
    id: 'dragon', name: 'Dragón', level: 4, boss: true,
    stats: { maxHealth: 200, attack: 17, defense: 9, speed: 5, critChance: 10 },
    abilities: [
      ability('scales', 'Escamas', 'Se cubre con un Escudo cuando está malherido.', 5),
      ability('frost_breath', 'Aliento helado', 'Inflige 60% de daño y congela al rival.', 5),
      ability('roar', 'Rugido', 'Entra en Furia.', 4),
    ],
  },
];

# Contrato de la API — RPG Decorator

> Fuente de verdad del intercambio back ↔ front.
> Backend: los `record` de `api/dto` reflejan esto. Frontend: `src/api/types.ts` refleja esto.
> Si hay que cambiar algo, **se cambia aquí primero** (solo el orquestador).

- Base URL: `http://localhost:8080/api` (en desarrollo, el front usa `/api` vía proxy de Vite).
- Formato: JSON UTF-8. **Rutas, campos, enums e ids en inglés** (ADR-004). Campos en `camelCase`, enums en `UPPER_SNAKE_CASE`.
- Los ids de catálogo van en `snake_case`: `warrior`, `dragon_armor`, `orc_shaman`.
- Los campos de texto visible (`name`, `label`, `description`, `message`, `chain`) vienen **en español**.
- Ids de expedición: UUID en texto.

---

## 1. Errores

Todas las respuestas de error tienen la forma:
```json
{ "error": { "code": "ABILITY_ON_COOLDOWN", "message": "Grito de guerra estará disponible en 2 turnos" } }
```

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `INVALID_JSON` | El cuerpo no se puede parsear |
| 400 | `REQUIRED_FIELD` / `INVALID_VALUE` | Falta un campo o un id no existe en el catálogo |
| 404 | `NOT_FOUND` | La expedición no existe o la ruta no existe |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP incorrecto |
| 409 | `INVALID_STATE` | Acción en un estado que no la admite (p. ej., actuar con `AWAITING_REWARD`) |
| 409 | `ABILITY_ON_COOLDOWN` | Habilidad usada antes de tiempo |
| 409 | `ACTION_NOT_ALLOWED` | `PASS` sin estar frozen, o actuar estando frozen |
| 500 | `INTERNAL_ERROR` | Excepción no controlada (se registra en el log del servidor) |

---

## 2. Endpoints

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/health` | — | `{ "status": "OK" }` |
| GET | `/api/catalog/classes` | — | `HeroClassDTO[]` |
| GET | `/api/catalog/equipment` | — | `ItemDTO[]` |
| GET | `/api/catalog/effects` | — | `EffectInfoDTO[]` |
| GET | `/api/catalog/enemies` | — | `EnemyInfoDTO[]` |
| POST | `/api/preview` | `PreviewRequest` | `PreviewDTO` |
| POST | `/api/expeditions` | `CreateExpeditionRequest` | **201** `ExpeditionDTO` |
| GET | `/api/expeditions/{id}` | — | `ExpeditionDTO` |
| POST | `/api/expeditions/{id}/actions` | `ActionRequest` | `ActionResultDTO` |
| POST | `/api/expeditions/{id}/reward` | `RewardRequest` | `ExpeditionDTO` |
| DELETE | `/api/expeditions/{id}` | — | **204** |

---

## 3. Catálogo

### `HeroClassDTO`
```json
{
  "id": "warrior",
  "name": "Guerrero",
  "description": "Resistente y brutal en el cuerpo a cuerpo.",
  "stats": { "maxHealth": 120, "attack": 14, "defense": 8, "speed": 4, "critChance": 10 },
  "abilities": [ AbilityDTO, AbilityDTO ]
}
```

### `AbilityDTO`
```json
{
  "id": "war_cry",
  "name": "Grito de guerra",
  "description": "Entra en Furia: +50% ataque, -30% defensa durante 2 turnos.",
  "cooldown": 3,
  "cooldownRemaining": 0
}
```
`cooldownRemaining` solo tiene sentido dentro de un combate; en el catálogo siempre es `0`.

### `ItemDTO`
```json
{ "id": "sword", "name": "Espada", "slot": "WEAPON", "description": "+6 ataque", "icon": "sword" }
```
`slot`: `WEAPON | ARMOR | ACCESSORY`.

### `EffectInfoDTO`
```json
{ "id": "poison", "label": "Envenenado", "category": "DEBUFF", "baseDuration": 3,
  "description": "Pierde 6 de vida al inicio de cada turno.", "icon": "poison" }
```
`category`: `EQUIPMENT | BUFF | DEBUFF | CONTROL`.

### `EnemyInfoDTO`
```json
{ "id": "orc_shaman", "name": "Orco chamán", "level": 2, "boss": false,
  "stats": { "maxHealth": 110, "attack": 14, "defense": 6, "speed": 5, "critChance": 10 },
  "abilities": [ AbilityDTO ] }
```

---

## 4. Vista previa

### `PreviewRequest` — dos formas
```json
{ "heroClassId": "archer", "itemIds": ["sword", "wind_boots"] }
```
```json
{ "expeditionId": "6f1c…", "itemId": "dragon_armor" }
```
La segunda calcula cómo quedaría el héroe **de esa expedición** si eligiera esa recompensa (reemplazando el slot si hace falta).

### `PreviewDTO`
```json
{
  "stats": { "maxHealth": 95, "attack": 21, "defense": 5, "speed": 15, "critChance": 20 },
  "chain": "Botas de viento(Espada(Arquero))",
  "layers": [ LayerDTO, LayerDTO, LayerDTO ],
  "replaces": null
}
```
`replaces`: id de la pieza que se sustituiría (solo en la segunda forma), o `null`.

---

## 5. Expedición

### `CreateExpeditionRequest`
```json
{ "heroClassId": "mage", "startingItemId": "rune_staff", "seed": 42 }
```
`seed` es opcional (si falta, el servidor la genera y la devuelve).

### `ExpeditionDTO`
```json
{
  "id": "6f1c2a7e-…",
  "seed": 42,
  "status": "IN_PROGRESS",
  "currentLevel": 2,
  "totalLevels": 4,
  "map": [
    { "level": 1, "enemyId": "goblin",   "name": "Goblin",    "status": "DEFEATED" },
    { "level": 2, "enemyId": "skeleton", "name": "Esqueleto", "status": "CURRENT" },
    { "level": 3, "enemyId": null,       "name": null,        "status": "HIDDEN" },
    { "level": 4, "enemyId": "dragon",   "name": "Dragón",    "status": "BOSS" }
  ],
  "equipment": { "WEAPON": "rune_staff", "ARMOR": null, "ACCESSORY": "life_amulet" },
  "combat": CombatDTO,
  "offeredRewards": [],
  "statistics": { "enemiesDefeated": 1, "totalRounds": 6, "damageDealt": 97, "damageTaken": 41 }
}
```
- `status`: `IN_PROGRESS | AWAITING_REWARD | COMPLETED | FAILED`.
- `map[].status`: `DEFEATED | CURRENT | HIDDEN | BOSS` (el jefe se ve siempre; si es el nivel actual → `CURRENT`).
- `combat`: el combate del nivel actual (en `AWAITING_REWARD`, `COMPLETED` y `FAILED` es el último jugado, ya terminado).
- `offeredRewards`: `ItemDTO[]` con 3 elementos solo en `AWAITING_REWARD`; si no, `[]`.

### `CombatDTO`
```json
{
  "status": "IN_PROGRESS",
  "round": 3,
  "hero": CombatantDTO,
  "enemy": CombatantDTO,
  "log": [ EventDTO, … ]
}
```
`status`: `IN_PROGRESS | VICTORY | DEFEAT`. `log` contiene **todos** los eventos del combate actual (para reconstruir el log al recargar).

### `CombatantDTO`
```json
{
  "id": "hero",
  "name": "Mago",
  "side": "HERO",
  "archetypeId": "mage",
  "health": 52,
  "stats": { "maxHealth": 105, "attack": 21, "defense": 4, "speed": 6, "critChance": 25 },
  "canAct": true,
  "effects": [
    { "effectId": "regeneration", "label": "Regeneración", "category": "BUFF", "turnsRemaining": 2, "extra": null },
    { "effectId": "shield", "label": "Escudo", "category": "BUFF", "turnsRemaining": 3, "extra": { "absorption": 15 } }
  ],
  "abilities": [ AbilityDTO, AbilityDTO ],
  "chain": "Regeneración(Amuleto de vida(Bastón rúnico(Mago)))",
  "layers": [ LayerDTO, … ]
}
```
- `id` del héroe: `"hero"`; del enemigo: `"enemy"` (fijos, así el front no depende de UUIDs).
- `archetypeId`: id de la clase (héroe) o del enemigo.
- `effects`: solo los temporales, de afuera hacia adentro.
- `abilities` del enemigo: se envían (para que el jugador vea sus cooldowns).

### `LayerDTO` (inspector de cadena, RF-20) — de afuera hacia adentro
```json
{ "position": 0, "id": "regeneration", "label": "Regeneración", "category": "BUFF",
  "turnsRemaining": 2, "isBase": false,
  "statsAtLayer": { "maxHealth": 105, "attack": 21, "defense": 4, "speed": 6, "critChance": 25 } }
```
La última capa tiene `isBase: true`, `category: null` y `turnsRemaining: null`. En el equipo, `turnsRemaining` es `null` (permanente).

---

## 6. Acciones

### `ActionRequest`
```json
{ "type": "ABILITY", "abilityId": "ice_bolt" }
```
`type`: `ATTACK | DEFEND | ABILITY | PASS`. `abilityId` es obligatorio solo si `type = ABILITY`.

### `ActionResultDTO`
```json
{ "expedition": ExpeditionDTO, "events": [ EventDTO, … ] }
```
`events`: **solo** los generados por esta acción (la ronda completa: turno del héroe + turno del enemigo), en orden de `seq`.

### `EventDTO`
Estructura plana: campos comunes + campos del tipo (ver `design.md §6`).
```json
{ "seq": 41, "round": 3, "type": "DAMAGE", "targetId": "enemy", "amount": 21, "damageType": "PHYSICAL", "critical": true }
{ "seq": 42, "round": 3, "type": "ABSORBED", "targetId": "enemy", "amount": 8, "remaining": 12 }
{ "seq": 43, "round": 3, "type": "EFFECT_APPLIED", "targetId": "enemy", "effectId": "frozen", "duration": 1 }
{ "seq": 44, "round": 3, "type": "EFFECT_REMOVED", "targetId": "enemy", "effectId": "rage", "reason": "INTERACTION" }
{ "seq": 50, "round": 3, "type": "TURN_SKIPPED", "actorId": "enemy", "effectId": "frozen" }
{ "seq": 51, "round": 3, "type": "ACTION", "actorId": "hero", "action": "ABILITY", "abilityId": "ice_bolt" }
```
Los campos que no aplican a un tipo **no se envían** (no van como `null`).

---

## 7. Recompensa

### `RewardRequest`
```json
{ "itemId": "dragon_armor" }
```
`itemId: null` = omitir la recompensa. Solo es válido en `AWAITING_REWARD`, y la pieza debe estar entre las `offeredRewards`.
Respuesta: `ExpeditionDTO` ya en el siguiente nivel (`status: IN_PROGRESS`, combate nuevo con `round: 1`).

---

## 8. CORS
El servidor responde a `OPTIONS` con 204 y, en todas las respuestas, envía:
```
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type
```

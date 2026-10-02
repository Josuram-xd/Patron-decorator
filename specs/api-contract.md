# Contrato de la API — RPG Decorator

> Fuente de verdad del intercambio back ↔ front.
> Backend: los `record` de `api/dto` reflejan esto. Frontend: `src/api/tipos.ts` refleja esto.
> Si hay que cambiar algo, **se cambia aquí primero** (solo el orquestador).

- Base URL: `http://localhost:8080/api` (en desarrollo, el front usa `/api` vía proxy de Vite).
- Formato: JSON UTF-8. Campos en `camelCase`. Enums en `MAYUSCULAS`.
- Los ids de catálogo son `snake_case` en minúsculas: `guerrero`, `armadura_dragon`, `orco_chaman`.
- Ids de expedición: UUID en texto.

---

## 1. Errores

Todas las respuestas de error tienen la forma:
```json
{ "error": { "codigo": "HABILIDAD_EN_ENFRIAMIENTO", "mensaje": "Grito de guerra estará disponible en 2 turnos" } }
```

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `JSON_INVALIDO` | El cuerpo no se puede parsear |
| 400 | `CAMPO_REQUERIDO` / `VALOR_INVALIDO` | Falta un campo o un id no existe en el catálogo |
| 404 | `NO_ENCONTRADO` | La expedición no existe o la ruta no existe |
| 405 | `METODO_NO_PERMITIDO` | Método HTTP incorrecto |
| 409 | `ESTADO_INVALIDO` | Acción en un estado que no la admite (p. ej., actuar con `ESPERANDO_RECOMPENSA`) |
| 409 | `HABILIDAD_EN_ENFRIAMIENTO` | Habilidad usada antes de tiempo |
| 409 | `ACCION_NO_PERMITIDA` | `PASAR` sin estar congelado, o actuar estando congelado |
| 500 | `ERROR_INTERNO` | Excepción no controlada (se registra en el log del servidor) |

---

## 2. Endpoints

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/salud` | — | `{ "estado": "OK" }` |
| GET | `/api/catalogo/clases` | — | `ClaseDTO[]` |
| GET | `/api/catalogo/equipo` | — | `PiezaDTO[]` |
| GET | `/api/catalogo/efectos` | — | `EfectoInfoDTO[]` |
| GET | `/api/catalogo/enemigos` | — | `EnemigoInfoDTO[]` |
| POST | `/api/vista-previa` | `VistaPreviaRequest` | `VistaPreviaDTO` |
| POST | `/api/expediciones` | `CrearExpedicionRequest` | **201** `ExpedicionDTO` |
| GET | `/api/expediciones/{id}` | — | `ExpedicionDTO` |
| POST | `/api/expediciones/{id}/acciones` | `AccionRequest` | `ResultadoAccionDTO` |
| POST | `/api/expediciones/{id}/recompensa` | `RecompensaRequest` | `ExpedicionDTO` |
| DELETE | `/api/expediciones/{id}` | — | **204** |

---

## 3. Catálogo

### `ClaseDTO`
```json
{
  "id": "guerrero",
  "nombre": "Guerrero",
  "descripcion": "Resistente y brutal en el cuerpo a cuerpo.",
  "stats": { "vidaMax": 120, "ataque": 14, "defensa": 8, "velocidad": 4, "critico": 10 },
  "habilidades": [ HabilidadDTO, HabilidadDTO ]
}
```

### `HabilidadDTO`
```json
{
  "id": "grito_guerra",
  "nombre": "Grito de guerra",
  "descripcion": "Entra en Furia: +50% ataque, -30% defensa durante 2 turnos.",
  "enfriamiento": 3,
  "enfriamientoRestante": 0
}
```
`enfriamientoRestante` solo tiene sentido dentro de un combate; en el catálogo es siempre `0`.

### `PiezaDTO`
```json
{ "id": "espada", "nombre": "Espada", "ranura": "ARMA", "descripcion": "+6 ataque", "icono": "espada" }
```
`ranura`: `ARMA | ARMADURA | ACCESORIO`.

### `EfectoInfoDTO`
```json
{ "id": "veneno", "etiqueta": "Envenenado", "categoria": "DEBUFF", "duracionBase": 3,
  "descripcion": "Pierde 6 de vida al inicio de cada turno.", "icono": "veneno" }
```
`categoria`: `EQUIPO | BUFF | DEBUFF | CONTROL`.

### `EnemigoInfoDTO`
```json
{ "id": "orco_chaman", "nombre": "Orco chamán", "nivel": 2, "jefe": false,
  "stats": { "vidaMax": 110, "ataque": 14, "defensa": 6, "velocidad": 5, "critico": 10 },
  "habilidades": [ HabilidadDTO ] }
```

---

## 4. Vista previa

### `VistaPreviaRequest` — dos formas
```json
{ "claseId": "arquero", "equipoIds": ["espada", "botas_viento"] }
```
```json
{ "expedicionId": "6f1c…", "piezaId": "armadura_dragon" }
```
La segunda calcula cómo quedaría el héroe **de esa expedición** si eligiera esa recompensa (reemplazando la ranura si hace falta).

### `VistaPreviaDTO`
```json
{
  "stats": { "vidaMax": 95, "ataque": 21, "defensa": 5, "velocidad": 15, "critico": 20 },
  "cadena": "BotasViento(Espada(Arquero))",
  "capas": [ CapaDTO, CapaDTO, CapaDTO ],
  "reemplaza": null
}
```
`reemplaza`: id de la pieza que se sustituiría (solo en la segunda forma), o `null`.

---

## 5. Expedición

### `CrearExpedicionRequest`
```json
{ "claseId": "mago", "equipoInicialId": "baston", "semilla": 42 }
```
`semilla` es opcional (si falta, el servidor la genera y la devuelve).

### `ExpedicionDTO`
```json
{
  "id": "6f1c2a7e-…",
  "semilla": 42,
  "estado": "EN_CURSO",
  "nivelActual": 2,
  "totalNiveles": 4,
  "mapa": [
    { "nivel": 1, "enemigoId": "goblin",   "nombre": "Goblin",  "estado": "VENCIDO" },
    { "nivel": 2, "enemigoId": "esqueleto","nombre": "Esqueleto","estado": "ACTUAL" },
    { "nivel": 3, "enemigoId": null,       "nombre": null,      "estado": "OCULTO" },
    { "nivel": 4, "enemigoId": "dragon",   "nombre": "Dragón",  "estado": "JEFE" }
  ],
  "equipo": { "ARMA": "baston", "ARMADURA": null, "ACCESORIO": "amuleto_vida" },
  "combate": CombateDTO,
  "recompensasOfrecidas": [],
  "estadisticas": { "enemigosVencidos": 1, "rondasTotales": 6, "danioInfligido": 97, "danioRecibido": 41 }
}
```
- `estado`: `EN_CURSO | ESPERANDO_RECOMPENSA | COMPLETADA | FRACASADA`.
- `mapa[].estado`: `VENCIDO | ACTUAL | OCULTO | JEFE` (el jefe se ve siempre; si es el nivel actual → `ACTUAL`).
- `combate`: el combate del nivel actual (en `ESPERANDO_RECOMPENSA`, `COMPLETADA` y `FRACASADA` es el último jugado, ya terminado).
- `recompensasOfrecidas`: `PiezaDTO[]` con 3 elementos solo en `ESPERANDO_RECOMPENSA`; si no, `[]`.

### `CombateDTO`
```json
{
  "estado": "EN_CURSO",
  "ronda": 3,
  "heroe": CombatienteDTO,
  "enemigo": CombatienteDTO,
  "log": [ EventoDTO, … ]
}
```
`estado`: `EN_CURSO | VICTORIA | DERROTA`. `log` contiene **todos** los eventos del combate actual (para reconstruir el log al recargar).

### `CombatienteDTO`
```json
{
  "id": "heroe",
  "nombre": "Mago",
  "bando": "HEROE",
  "claseOTipo": "mago",
  "vida": 52,
  "stats": { "vidaMax": 105, "ataque": 21, "defensa": 4, "velocidad": 6, "critico": 25 },
  "puedeActuar": true,
  "efectos": [
    { "efectoId": "regeneracion", "etiqueta": "Regeneración", "categoria": "BUFF", "turnosRestantes": 2, "extra": null },
    { "efectoId": "escudo", "etiqueta": "Escudo", "categoria": "BUFF", "turnosRestantes": 3, "extra": { "absorcion": 15 } }
  ],
  "habilidades": [ HabilidadDTO, HabilidadDTO ],
  "cadena": "Regeneración(AmuletoVida(Baston(Mago)))",
  "capas": [ CapaDTO, … ]
}
```
- `id` del héroe: `"heroe"`; del enemigo: `"enemigo"` (fijos, así el front no depende de UUIDs).
- `efectos`: solo los temporales, de afuera hacia adentro.
- `habilidades` del enemigo: se envían (para que el jugador vea sus enfriamientos).

### `CapaDTO` (inspector de cadena, RF-20) — de afuera hacia adentro
```json
{ "posicion": 0, "id": "regeneracion", "etiqueta": "Regeneración", "categoria": "BUFF",
  "turnosRestantes": 2, "esBase": false,
  "statsEnCapa": { "vidaMax": 105, "ataque": 21, "defensa": 4, "velocidad": 6, "critico": 25 } }
```
La última capa tiene `esBase: true`, `categoria: null`, `turnosRestantes: null`. `turnosRestantes` del equipo: `null` (permanente).

---

## 6. Acciones

### `AccionRequest`
```json
{ "tipo": "HABILIDAD", "habilidadId": "rayo_hielo" }
```
`tipo`: `ATACAR | DEFENDER | HABILIDAD | PASAR`. `habilidadId` es obligatorio solo si `tipo = HABILIDAD`.

### `ResultadoAccionDTO`
```json
{ "expedicion": ExpedicionDTO, "eventos": [ EventoDTO, … ] }
```
`eventos`: **solo** los generados por esta acción (la ronda completa: turno del héroe + turno del enemigo), en orden de `seq`.

### `EventoDTO`
Estructura plana: campos comunes + campos del tipo (ver `design.md §6`).
```json
{ "seq": 41, "ronda": 3, "tipo": "DANIO", "objetivoId": "enemigo", "cantidad": 21, "tipoDanio": "FISICO", "critico": true }
{ "seq": 42, "ronda": 3, "tipo": "ABSORBIDO", "objetivoId": "enemigo", "cantidad": 8, "restante": 12 }
{ "seq": 43, "ronda": 3, "tipo": "EFECTO_APLICADO", "objetivoId": "enemigo", "efectoId": "congelado", "duracion": 1 }
{ "seq": 44, "ronda": 3, "tipo": "EFECTO_RETIRADO", "objetivoId": "enemigo", "efectoId": "furia", "motivo": "INTERACCION" }
{ "seq": 50, "ronda": 3, "tipo": "TURNO_PERDIDO", "actorId": "enemigo", "efectoId": "congelado" }
{ "seq": 51, "ronda": 3, "tipo": "ACCION", "actorId": "heroe", "accion": "HABILIDAD", "habilidadId": "rayo_hielo" }
```
Los campos que no aplican a un tipo **no se envían** (no van como `null`).

---

## 7. Recompensa

### `RecompensaRequest`
```json
{ "piezaId": "armadura_dragon" }
```
`piezaId: null` = omitir la recompensa. Solo es válido en `ESPERANDO_RECOMPENSA`, y la pieza debe estar entre las `recompensasOfrecidas`.
Respuesta: `ExpedicionDTO` ya en el siguiente nivel (`estado: EN_CURSO`, combate nuevo con `ronda: 1`).

---

## 8. CORS
El servidor responde a `OPTIONS` con 204 y, en todas las respuestas, envía:
```
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type
```

# Tareas — RPG Decorator

> Plan de trabajo para los agentes. Lee `AGENTS.md §2` antes de tomar una tarea.
> **Todo el código en inglés** (ADR-004); los nombres de clases y archivos de abajo son los definitivos.
>
> **Estados:** `[ ]` Pendiente · `[~]` En curso (agente) · `[x]` Hecha · `[!]` Bloqueada
> **⚡** = se puede hacer en paralelo con las otras ⚡ de su fase una vez cumplidas las dependencias.
> **Agentes:** `ORQ` orquestador · `DOM` dominio · `ENG` motor · `API` api · `FE` frontend · `QA` calidad

---

## Grafo de fases

```
F0 Setup ──► F1 Domain ──► F2 Engine ──► F3 API ──┐
   │                                              ├──► F5 Integración y QA
   └──────► F4 Frontend (con mocks) ──────────────┘
```
El frontend **no espera** al backend: trabaja contra mocks (T-402) que cumplen `api-contract.md`, y en F5 se conecta al backend real.

---

## F0 — Setup

### T-001 [x] Esqueleto del backend · `ORQ`
- **Depende de:** —
- **Specs:** architecture §2.1, §2.2, ADR-001
- **Hacer:** `backend/pom.xml` (groupId `com.rpgdecorator`, artifactId `rpg-decorator`, Java 25, JUnit 5 en scope test, surefire, exec-maven-plugin con `mainClass=com.rpgdecorator.App`, jar ejecutable `rpg-decorator.jar`). El árbol de paquetes **ya existe** con `.gitkeep`: reemplázalos por un `package-info.java` (Javadoc en inglés) en cada paquete. `App.java` que imprime "RPG Decorator" y termina.
- **Aceptación:**
  - `mvn -q test` pasa (con un test de humo).
  - `pom.xml` no tiene dependencias fuera de scope `test`.

### T-002 [ ] Esqueleto del frontend · `FE` ⚡
- **Depende de:** —
- **Specs:** architecture §3
- **Hacer:** Vite + React + TS strict; Tailwind 4, Framer Motion (`motion`), Zustand, TanStack Query, dnd-kit, lucide-react, sonner, ESLint + Prettier. Proxy `/api → http://localhost:8080` en `vite.config.ts`. Las carpetas de architecture §3.1 **ya existen** con `.gitkeep`: genera el proyecto Vite en una carpeta temporal y copia sus archivos a `frontend/` sin borrarlas. `styles/tokens.css` con las variables de design §7.5.
- **Aceptación:** `npm run dev` muestra "RPG Decorator"; `npm run build` y `npm run lint` pasan.

### T-003 [x] Raíz del repo · `ORQ` ⚡
- **Depende de:** —
- **Hacer:** `.gitignore` (target/, node_modules/, dist/, .idea/, *.iml, .vscode/), `.editorconfig`, sección "Cómo correrlo" del README.
- **Aceptación:** `git status` limpio tras el build de back y front.

---

## F1 — Domain (el patrón Decorator) · paquete `com.rpgdecorator.domain`

### T-101 [x] Tipos de valor · `DOM`
- **Depende de:** T-001
- **Specs:** design §2.1 · RNF-02
- **Hacer:** `Stats` (métodos `with…` y límites ≥ 0, `critChance` ≤ 100), `Damage`, `DamageResult`, `DamageType`, `Side`, `decorator/Category`, `decorator/Duration` (`PERMANENT`, `decrement`, `isExpired`).
- **Aceptación:** tests de `Stats` (inmutabilidad, límites) y de `Duration` (`PERMANENT` nunca expira; 1 → 0 expira).

### T-102 [x] Contratos: `Combatant`, `TurnContext`, `RandomSource`, eventos · `DOM`
- **Depende de:** T-101
- **Specs:** design §2.2, §2.3, §6
- **Hacer:** interfaces `Combatant`, `TurnContext`, `RandomSource`; `event/CombatEvent` como `sealed interface` con un `record` por tipo de design §6 (`TurnStarted`, `ActionTaken`, `DamageDealt`, `Evaded`, `Absorbed`, `Healed`, `EffectApplied`, `EffectRefreshed`, `EffectRemoved`, `TurnSkipped`, `Death`, `TurnEnded`, `CombatEnded`).
- **Aceptación:** compila; un `switch` exhaustivo sobre `CombatEvent` sin `default` (un test lo demuestra).

### T-103 [x] `BaseCharacter` (Componente concreto) · `DOM`
- **Depende de:** T-102
- **Specs:** design §2.2 (columna BaseCharacter), §3.1
- **Aceptación:**
  - `changeHealth` respeta `[0, effectiveMaxHealth]`.
  - `takeDamage` resta `physical + elemental` y devuelve un `DamageResult` correcto.
  - `describeChain()` devuelve el label.

### T-104 [x] `EffectDecorator` (Decorador base) · `DOM`
- **Depende de:** T-103
- **Specs:** design §2.4, §3.6 · RNF-02
- **Hacer:** clase abstracta que **delega todos** los métodos; `wrapped()`, `shouldBeRemoved()`, `refresh()`, `advanceTurn()` con `justApplied`, `copyOnto()` abstracto, `describeChain()`.
- **Aceptación:**
  - Un decorador de prueba "vacío" (en tests) es indistinguible del base en todos los métodos.
  - `id()` es el del base en cualquier profundidad.
  - `advanceTurn()`: el primer llamado no decrementa; los siguientes sí.

### T-105 [x] Decoradores de equipo (`domain.equipment`) · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.6
- **Hacer:** `SwordDecorator`, `WarAxeDecorator`, `RuneStaffDecorator`, `LeatherArmorDecorator`, `DragonArmorDecorator`, `FireRingDecorator`, `LifeAmuletDecorator`, `WindBootsDecorator`.
- **Aceptación:** un test por pieza; un test de orden `Rage(Sword(base))` ≠ `Sword(Rage(base))` (design §3.4); si `RageDecorator` aún no existe, puede usar un decorador de prueba ×1.5.

### T-106a [x] Efectos: `PoisonDecorator` y `RegenerationDecorator` · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2 · RF-12
- **Aceptación:** al llamar `onTurnStart`, piden `directDamage` o `heal` al contexto (con un `TurnContext` falso en el test) y luego delegan.

### T-106b [x] Efectos: `ShieldDecorator` y `ThornsDecorator` · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2, §4.3 · RF-14, RF-16
- **Aceptación:**
  - Shield 20 vs 30 de daño → absorbe 20, delega 10, `shouldBeRemoved() == true`.
  - `refresh` del shield suma la absorción con tope 40.
  - Thorns: `reflected = 30 %` de `taken`; 0 si `damage.reflectable == false`.
  - `Shield(Thorns(base))`: el reflejo se calcula sobre lo que **pasó** el escudo.

### T-106c [x] Efectos: `RageDecorator`, `GuardDecorator` y `FrozenDecorator` · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2 · RF-13, RF-15
- **Aceptación:** stats modificadas con redondeo hacia abajo; `FrozenDecorator.canAct() == false` sin importar las capas internas.

### T-106d [x] Efecto: `LifestealDecorator` · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2
- **Aceptación:** `onDamageDealt` con `taken = 20` → `heal(6)`; con `taken = 0` → no cura.

### T-107 [x] Catálogos (`domain.catalog`) · `DOM`
- **Depende de:** T-105, T-106a–d
- **Specs:** design §4.2, §4.6, §4.7, §4.8 · RF-01, RF-04
- **Hacer:** `EffectCatalog`, `EquipmentCatalog` (con `Slot`), `HeroClassCatalog`, `EnemyCatalog` (8 enemigos + grupos por nivel), `Ability`, `EffectApplication`, `Target`, `EnemyDefinition` (con condiciones de IA como `Predicate`).
- **Aceptación:** los valores coinciden **exactamente** con las tablas de design (un test recorre cada catálogo); ids únicos; cada nivel 1–3 tiene ≥ 2 enemigos.

### T-108 [x] Tests didácticos de las "trampas del Decorator" · `DOM`
- **Depende de:** T-107
- **Specs:** design §3
- **Aceptación:** un test por trampa (§3.1–§3.6) en `DecoratorPitfallsTest`, con nombres que la expliquen, p. ej. `selfCallInBaseIgnoresArmor()`, `removingMiddleLayerKeepsOuterState()`. Cada test lleva un comentario de 2–3 líneas (en inglés) de por qué existe.

---

## F2 — Engine · paquete `com.rpgdecorator.engine`

### T-201 [x] `EffectManager` + `InteractionRules` · `ENG`
- **Depende de:** T-107
- **Specs:** design §3.2, §3.3, §3.5, §4.4, §4.5, §5.4 · RF-10, RF-11, RF-16, RF-17, RF-18 · ADR-002
- **Hacer:** `engine/effects/EffectManager`, `InteractionRules`, `Layer`, `RemovalReason`.
- **Aceptación:**
  - `remove` del medio: `Rage(Poison(Sword(base)))` sin Poison → `Rage(Sword(base))`, Rage conserva sus turnos y el base es **la misma instancia**.
  - `apply` dos veces → una sola capa + `EFFECT_REFRESHED`.
  - Reglas de §4.4 (un test por fila).
  - `purge` deja solo el equipo.
  - `advanceTurn`: ciclo completo de Poison (3 turnos) y de un efecto `justApplied`.
  - `layers()` devuelve las stats efectivas por capa.
  - `equip` respeta la invariante del orden (equipo por dentro).

### T-202 [x] `DamageCalculator` · `ENG` ⚡
- **Depende de:** T-107
- **Specs:** design §4.1
- **Aceptación:** tests con un `RandomSource` fijo: crítico, evasión (tope 25 %), mitigación mínima 1, elemental sin mitigar.

### T-203 [x] `Combat`, `Action`, `TurnContextImpl` · `ENG`
- **Depende de:** T-201
- **Specs:** design §5.1, §5.2, §2.3
- **Hacer:** `engine/combat/Combat`, `CombatStatus`, `Action` (sealed: `Attack`, `Defend`, `UseAbility`, `Pass`), `TurnContextImpl`, `InvalidActionException`.
- **Aceptación:** `TurnContextImpl.directDamage` y `heal` resuelven la **cadena exterior** por id y usan su `maxHealth` efectiva (test con LifeAmulet: curar puede superar la vida máxima base).

### T-204 [x] `CombatEngine.executeRound` · `ENG`
- **Depende de:** T-202, T-203
- **Specs:** design §5.3, §4.7 · RF-05–RF-09, RF-15
- **Aceptación:**
  - Orden de eventos de una ronda exactamente como §5.3.
  - Poison puede matar al inicio del turno → no actúa.
  - Frozen → `TURN_SKIPPED`; `Pass` solo es válido frozen.
  - Cooldowns (cd 3: se usa en el turno 1 → disponible en el turno 4).
  - El orden de resolución de habilidades de §4.7; los efectos sobre el rival no se aplican si hubo evasión.
  - `VICTORY` / `DEFEAT` + `COMBAT_ENDED`; acción posterior → `InvalidActionException`.

### T-205 [x] `EnemyAI` · `ENG` ⚡
- **Depende de:** T-203
- **Specs:** design §4.8
- **Aceptación:** por cada enemigo, un test que fuerza la condición de cada habilidad y verifica la elección; sin habilidades disponibles → `Attack`.

### T-206 [~] (ENG) Expedición: agregado y sorteos · `ENG`
- **Depende de:** T-204, T-205
- **Specs:** design §5.5 · RF-02, RF-04, RF-24–RF-27
- **Hacer:** `engine/expedition/Expedition`, `ExpeditionStatus`, `EnemyDraw`, `RewardDraw`, `RunStatistics`, `engine/ExpeditionRepository` (interfaz).
- **Aceptación:**
  - Misma semilla → mismos enemigos y mismas recompensas.
  - Enemigos de cada nivel de su grupo correcto; nivel 4 siempre `dragon`.
  - Recompensas: 3 distintas, ninguna ya equipada.

### T-207 [~] (ENG) `ExpeditionService` · `ENG`
- **Depende de:** T-206
- **Specs:** design §5.5 · RF-24, RF-25, RF-26
- **Hacer:** `create`, `act`, `chooseReward`, `preview` (las dos formas de api-contract §4), `get`, `delete`. Sincronización por expedición.
- **Aceptación:**
  - Escenario Gherkin "Pasar al siguiente nivel…" de requirements §5.
  - Reemplazo de pieza en un slot ocupado.
  - Las transiciones inválidas lanzan una excepción con su código (`INVALID_STATE`, etc.).
  - Las estadísticas se acumulan entre encuentros.

---

## F3 — API (HTTP + JSON a mano) · paquete `com.rpgdecorator.api`

### T-301 [~] (API) JSON mínimo · `API` ⚡
- **Depende de:** T-001
- **Specs:** architecture §2.2 · RNF-01
- **Hacer:** `api/json/JsonValue` (sealed) + `Json.write(JsonValue)` + `Json.parse(String)` + `InvalidJsonException`. Escapes `\" \\ \n \t \uXXXX`, números enteros y decimales, anidación.
- **Aceptación:** tests ida y vuelta; un JSON inválido lanza `InvalidJsonException` con su posición; las tildes y la ñ se escriben y leen bien.

### T-302 [~] (API) Servidor, router, CORS y errores · `API` ⚡
- **Depende de:** T-001
- **Specs:** api-contract §1, §8
- **Hacer:** `HttpApiServer` (HttpServer en :8080, puerto configurable con la variable `PORT`), `Router` con parámetros de ruta (`/api/expeditions/{id}`), `Cors`, `HttpError`, y `/api/health`.
- **Aceptación:** test de integración con `HttpClient`: 200 en health, 404 en una ruta desconocida, 405 en un método incorrecto, 204 en `OPTIONS` con cabeceras CORS.

### T-303 [ ] DTOs y mappers · `API`
- **Depende de:** T-207, T-301
- **Specs:** api-contract §3–§7
- **Hacer:** `api/dto/*` (records con los nombres del contrato: `HeroClassDTO`, `ExpeditionDTO`, `CombatantDTO`, `LayerDTO`, `EventDTO`…) y `api/mapper/*`.
- **Aceptación:** un test por DTO que compara el JSON generado con el ejemplo del contrato (estructura y nombres de campos); los eventos omiten los campos que no aplican.

### T-304 [ ] Endpoints de catálogo y vista previa · `API`
- **Depende de:** T-302, T-303
- **Specs:** api-contract §2, §3, §4
- **Hacer:** `CatalogHandler`, `PreviewHandler`.
- **Aceptación:** tests de integración de los 4 GET de catálogo y de las 2 formas de preview.

### T-305 [ ] Endpoints de expedición · `API`
- **Depende de:** T-304
- **Specs:** api-contract §2, §5, §6, §7
- **Hacer:** `ExpeditionHandler`.
- **Aceptación:** test de integración que juega una expedición completa por HTTP con una semilla fija (bucle de `ATTACK` + elegir siempre la primera recompensa) hasta `COMPLETED` o `FAILED`; casos de error 400, 404 y 409 cubiertos.

### T-306 [ ] `App` + infraestructura · `API`
- **Depende de:** T-305
- **Hacer:** `infrastructure/InMemoryExpeditionRepository`, `infrastructure/JdkRandomSource`, armado de dependencias en `App.main`, log de arranque con la URL.
- **Aceptación:** `mvn -q exec:java` arranca; `curl localhost:8080/api/health` responde `{"status":"OK"}`.

---

## F4 — Frontend · `frontend/src`

### T-401 [ ] Tipos y cliente HTTP · `FE`
- **Depende de:** T-002
- **Specs:** api-contract (completo)
- **Hacer:** `api/types.ts` (1:1 con el contrato, uniones discriminadas para `EventDTO` por `type`), `api/client.ts` (fetch tipado; un error del backend → `ApiError` con su `code`), `api/queries.ts` (hooks de TanStack Query).
- **Aceptación:** `tsc` sin errores; un `switch` sobre `event.type` es exhaustivo (`never`).

### T-402 [ ] Mocks · `FE`
- **Depende de:** T-401
- **Hacer:** `mocks/` con fixtures JSON del contrato y un cliente mock que simula: crear expedición, unas cuantas rondas con eventos variados (daño, crítico, shield, poison, frozen, retiro), victoria → recompensa → nivel 2. Se activa con `VITE_USE_MOCKS=true`.
- **Aceptación:** con mocks se recorre ClassSelect → Loadout → Map → Arena → Reward → Map sin backend.

### T-403 [ ] Store y tema · `FE` ⚡
- **Depende de:** T-401
- **Specs:** architecture §3.3, design §7.1, §7.5
- **Hacer:** `store/gameStore.ts` (pantalla derivada del estado, selección, cola de eventos, velocidad de animación); `styles/tokens.css` completo y `components/ui/` (`Button`, `Panel`, `Tooltip`, `Badge`).
- **Aceptación:** la pantalla cambia sola según `expedition.status`; el contraste del texto es ≥ 4.5:1.

### T-404 [ ] `ClassSelectScreen` · `FE` ⚡
- **Depende de:** T-403
- **Specs:** design §7.2 · RF-01

### T-405 [ ] `LoadoutScreen`: `Inventory` con drag & drop + vista previa · `FE` ⚡
- **Depende de:** T-403
- **Specs:** design §7.3 · RF-02, RF-03
- **Hacer:** `Inventory`, `EquipmentSlot`, `EquipmentItem`, `LoadoutScreen`.
- **Aceptación:** solo se suelta en el slot correcto; la vista previa usa `POST /api/preview` con debounce; se muestran el diff de stats y la cadena.

### T-406 [ ] `MapScreen` · `FE` ⚡
- **Depende de:** T-403
- **Specs:** design §7.2.b · RF-04, RF-23
- **Hacer:** `MapScreen`, `MapNode`.

### T-407 [ ] `ArenaScreen`: tarjetas, barras, efectos y acciones · `FE`
- **Depende de:** T-403
- **Specs:** design §7.4 · RF-06, RF-07, RF-19
- **Hacer:** `ArenaScreen`, `CombatantCard`, `HealthBar`, `EffectList`, `ActionPanel`.
- **Aceptación:** habilidades en cooldown deshabilitadas con su contador; si el héroe está frozen solo aparece "Pasar turno"; atajos de teclado 1–4.

### T-408 [ ] ★ `ChainInspector` · `FE`
- **Depende de:** T-407
- **Specs:** design §7.4 · RF-20
- **Aceptación:** cajas anidadas de afuera hacia adentro con color por categoría, turnos y `statsAtLayer`; las capas entran y salen animadas; texto de `chain` debajo; pestañas Héroe / Enemigo.

### T-409 [ ] `CombatLog` + `eventPlayer` con animaciones · `FE`
- **Depende de:** T-407
- **Specs:** design §6 (columna UI), §7.6, §7.7 · RF-21, RF-22
- **Hacer:** `animation/eventPlayer.ts`, `CombatLog`, `FloatingNumber`.
- **Aceptación:** cada tipo de evento tiene su animación; botón ⏩; con `prefers-reduced-motion` solo hay cambios de opacidad; avisos con sonner para `TURN_SKIPPED` y crítico; el log es `aria-live`.

### T-410 [ ] `RewardScreen` y `SummaryScreen` · `FE` ⚡
- **Depende de:** T-405 (reutiliza `Inventory`)
- **Specs:** design §7.2.c, §7.2.d · RF-25, RF-26, RF-27

### T-411 [ ] Responsive y pulido · `FE`
- **Depende de:** T-404–T-410
- **Specs:** RNF-08, design §7.4 (móvil)
- **Aceptación:** usable a 375 px sin scroll horizontal; atribución de game-icons.net en el pie.

---

## F5 — Integración y QA

### T-501 [ ] Conectar el front al backend real · `FE` + `API`
- **Depende de:** T-306, T-411
- **Aceptación:** con `VITE_USE_MOCKS=false` se juega una expedición completa; las diferencias entre mocks y backend se corrigen **en el lado que no cumple el contrato**.

### T-502 [ ] Escenarios de punta a punta (backend) · `QA`
- **Depende de:** T-207
- **Specs:** requirements §5 (todos los Gherkin)
- **Hacer:** `src/test/java/com/rpgdecorator/scenarios/*ScenarioTest.java`.
- **Aceptación:** un test por escenario Gherkin, con semilla fija.

### T-503 [ ] `ArchitectureTest` · `QA` ⚡
- **Depende de:** T-207
- **Specs:** architecture §2 (regla de dependencias), AGENTS §3
- **Hacer:** test que recorre los `.java` de `src/main` y falla si `domain` importa `engine`/`api`/`infrastructure`, si `engine` importa `api`, o si hay un `import` de librerías prohibidas o de `java.lang.reflect.Proxy`.

### T-504 [ ] Checklist manual de QA · `QA`
- **Depende de:** T-501
- **Hacer:** `specs/qa-checklist.md` con un recorrido por cada RF y su resultado (✔/✘ + nota).

### T-505 [ ] Documentación final · `ORQ`
- **Depende de:** T-504
- **Hacer:** README con capturas o un GIF, "cómo agregar un efecto nuevo en 3 pasos" (demuestra RNF-03) y el diagrama de clases final.

---

## Paralelismo sugerido (3 agentes a la vez)

| Ola | Agente A (DOM → ENG) | Agente B (API) | Agente C (FE) |
|---|---|---|---|
| 1 | T-001, T-101 → T-104 | T-301, T-302 | T-002, T-401, T-402 |
| 2 | T-105, T-106a–d, T-107, T-108 | (espera) / revisa specs | T-403 → T-406 |
| 3 | T-201 → T-207 | — | T-407 → T-409 |
| 4 | T-502, T-503 (QA) | T-303 → T-306 | T-410, T-411 |
| 5 | T-504 | T-501 | T-501 |

---

## Registro

| Fecha | Tarea | Agente | Nota |
|---|---|---|---|
| 2026-10-02 | — | ORQ | Specs iniciales creadas |
| 2026-10-02 | — | ORQ | Código en inglés (ADR-004): specs y estructura de carpetas actualizadas |
| 2026-10-02 | T-201 | ENG | `EffectManager`, `InteractionRules`, `Layer` + 50 tests. Verificado contra stubs del dominio (el dominio real F1 aún no está en el repo): revalidar al integrar F1 |
| 2026-10-02 | T-202 | ENG | `DamageCalculator` + 13 tests. Verificado contra stubs del dominio: revalidar al integrar F1 |
| 2026-10-02 | T-203 | ENG | `Combat`, `Action`, `TurnContextImpl`, `LoggedEvent`, `ErrorCode`, `InvalidActionException` + 31 tests (contra stubs del dominio) |
| 2026-10-02 | T-205 | ENG | `EnemyAI` + 36 tests (contra stubs del dominio). Regla de cooldown: lista si el valor guardado es <= 1 al decidir (se decrementa al inicio del turno) → pendiente de confirmar en open-questions |
| 2026-10-02 | T-204 | ENG | `CombatEngine.executeRound` + 20 tests (contra stubs). `EffectManager` gana `shieldAbsorption` y `blockingEffectId`. Decisiones sobre muertes/TURN_ENDED/purga tras evasión → open-questions |
| 2026-10-02 | T-001 | ORQ | pom.xml, App, test de humo y `package-info.java` por paquete |
| 2026-10-02 | T-003 | ORQ | `.gitignore`, `.editorconfig` y "Cómo correrlo" en el README |
| 2026-10-02 | T-101–T-104 | DOM | Tipos de valor, contratos, `BaseCharacter` y `EffectDecorator` |
| 2026-10-02 | T-105, T-106a–d | DOM | 8 decoradores de equipo y 8 efectos temporales (ver Q-014) |
| 2026-10-02 | T-107, T-108 | DOM | Catálogos y `DecoratorPitfallsTest` |
| 2026-10-02 | — | ORQ | F1 integrado con F2: T-201–T-205 revalidados contra el dominio real, 216 tests en verde (ver Q-016) |

# Arquitectura — RPG Decorator

> **Idioma:** todo el código (paquetes, clases, métodos, variables, archivos, ids, JSON, enums, comentarios y commits) va **en inglés**.
> Las specs están en español, y los textos que ve el jugador (etiquetas, descripciones) también. El glosario de `glossary.md` traduce los términos.

## 1. Vista general

```
┌──────────────────────────────┐        HTTP/JSON         ┌──────────────────────────────────────┐
│  FRONTEND  (React + Vite)    │  ───────────────────►    │  BACKEND  (Java 25 puro)             │
│                              │   /api/... (REST)        │                                      │
│  Screens ─ Store (Zustand)   │  ◄───────────────────    │  api ─► engine ─► domain             │
│  Animations (Framer Motion)  │  ExpeditionDTO+events[]  │                  (Decorator aquí)    │
└──────────────────────────────┘                          │  infrastructure (in-memory repos)    │
                                                          └──────────────────────────────────────┘
```

- Comunicación **REST síncrona**. Cada acción del jugador = 1 petición que devuelve el **estado completo** de la expedición + la **lista de eventos** ocurridos (para animar).
- En desarrollo, Vite hace de **proxy** de `/api` → `http://localhost:8080`, así no se necesita CORS. Igual el backend responde con cabeceras CORS para `http://localhost:5173`.

---

## 2. Backend — capas (arquitectura hexagonal ligera)

```
            ┌───────────────────────────────────────────┐
            │ api            (HTTP, JSON, DTOs, routes) │  ← adaptador de entrada
            └───────────────┬───────────────────────────┘
                            │ usa
            ┌───────────────▼───────────────────────────┐
            │ engine         (Expedition, Combat,       │  ← casos de uso
            │                 turns, AI, EffectManager) │
            └───────────────┬───────────────────────────┘
                            │ usa
            ┌───────────────▼───────────────────────────┐
            │ domain         (Combatant, Decorators,    │  ← núcleo del patrón
            │                 Stats, catalogs)          │
            └───────────────────────────────────────────┘
            ┌───────────────────────────────────────────┐
            │ infrastructure (InMemoryExpedition-       │  ← adaptador de salida
            │                 Repository, JdkRandom…)   │
            └───────────────────────────────────────────┘
```

**Regla de dependencias:** `api → engine → domain`. `infrastructure` implementa interfaces de `engine` y `domain`.
`domain` no importa nada de las otras capas. Lo verifica un test de arquitectura (T-503).

### 2.1 Estructura de carpetas del backend

```
backend/
├── pom.xml                                  ← Java 25, JUnit 5 (test), plugin exec/jar
└── src/
    ├── main/java/com/rpgdecorator/
    │   ├── App.java                         ← main: arma dependencias y arranca el servidor HTTP
    │   ├── domain/
    │   │   ├── Combatant.java               ← COMPONENTE (interfaz)
    │   │   ├── BaseCharacter.java           ← COMPONENTE CONCRETO
    │   │   ├── Stats.java                   ← record inmutable
    │   │   ├── Damage.java, DamageResult.java, DamageType.java
    │   │   ├── Side.java                    ← HERO | ENEMY
    │   │   ├── TurnContext.java             ← interfaz que usan los decoradores (la implementa engine)
    │   │   ├── RandomSource.java            ← interfaz (semilla) para tests deterministas
    │   │   ├── event/CombatEvent.java       ← sealed interface + un record por tipo de evento
    │   │   ├── decorator/
    │   │   │   ├── EffectDecorator.java     ← DECORADOR BASE (abstracta, delega todo)
    │   │   │   ├── Category.java            ← EQUIPMENT | BUFF | DEBUFF | CONTROL
    │   │   │   └── Duration.java            ← turnos restantes o PERMANENT
    │   │   ├── effects/                     ← DECORADORES CONCRETOS temporales
    │   │   │   ├── PoisonDecorator.java
    │   │   │   ├── RegenerationDecorator.java
    │   │   │   ├── ShieldDecorator.java
    │   │   │   ├── ThornsDecorator.java
    │   │   │   ├── RageDecorator.java
    │   │   │   ├── FrozenDecorator.java
    │   │   │   ├── LifestealDecorator.java
    │   │   │   └── GuardDecorator.java
    │   │   ├── equipment/                   ← DECORADORES CONCRETOS permanentes
    │   │   │   ├── SwordDecorator.java
    │   │   │   ├── WarAxeDecorator.java
    │   │   │   ├── RuneStaffDecorator.java
    │   │   │   ├── LeatherArmorDecorator.java
    │   │   │   ├── DragonArmorDecorator.java
    │   │   │   ├── FireRingDecorator.java
    │   │   │   ├── LifeAmuletDecorator.java
    │   │   │   └── WindBootsDecorator.java
    │   │   └── catalog/
    │   │       ├── HeroClassCatalog.java    ← warrior, mage, archer
    │   │       ├── EnemyCatalog.java        ← 8 enemigos + grupos por nivel (design §4.8)
    │   │       ├── EnemyDefinition.java     ← record: stats, abilities, condiciones de IA
    │   │       ├── EquipmentCatalog.java    ← id → (slot, fábrica de decorador)
    │   │       ├── Slot.java                ← WEAPON | ARMOR | ACCESSORY
    │   │       ├── EffectCatalog.java       ← id → fábrica de decorador
    │   │       ├── Ability.java             ← record (design §4.7)
    │   │       ├── EffectApplication.java   ← record: effectId + Target
    │   │       └── Target.java              ← SELF | OPPONENT
    │   ├── engine/
    │   │   ├── combat/
    │   │   │   ├── Combat.java              ← agregado: status, round, combatants, log
    │   │   │   ├── CombatStatus.java        ← IN_PROGRESS | VICTORY | DEFEAT
    │   │   │   ├── Action.java              ← sealed: Attack | Defend | UseAbility | Pass
    │   │   │   ├── CombatEngine.java        ← ejecuta una ronda completa
    │   │   │   ├── TurnContextImpl.java     ← implementa domain.TurnContext
    │   │   │   ├── DamageCalculator.java    ← fórmulas de design §4.1
    │   │   │   ├── EnemyAI.java             ← decide la acción del enemigo
    │   │   │   └── InvalidActionException.java
    │   │   ├── effects/
    │   │   │   ├── EffectManager.java       ← apply, refresh, expire, purge, rebuild chain
    │   │   │   ├── InteractionRules.java    ← tabla: frozen elimina rage, etc.
    │   │   │   ├── Layer.java               ← record para inspeccionar la cadena
    │   │   │   └── RemovalReason.java       ← EXPIRED | DEPLETED | PURGED | INTERACTION
    │   │   ├── expedition/
    │   │   │   ├── Expedition.java          ← agregado: levels, enemies, equipment, current combat
    │   │   │   ├── ExpeditionStatus.java    ← IN_PROGRESS | AWAITING_REWARD | COMPLETED | FAILED
    │   │   │   ├── ExpeditionService.java   ← casos de uso: create, act, chooseReward, preview
    │   │   │   ├── EnemyDraw.java           ← elige 1 enemigo por nivel con la semilla
    │   │   │   ├── RewardDraw.java          ← 3 piezas distintas no equipadas
    │   │   │   └── RunStatistics.java
    │   │   └── ExpeditionRepository.java    ← interfaz (puerto)
    │   ├── infrastructure/
    │   │   ├── InMemoryExpeditionRepository.java
    │   │   └── JdkRandomSource.java         ← implementa domain.RandomSource con java.util.random
    │   └── api/
    │       ├── HttpApiServer.java           ← HttpServer + registro de rutas
    │       ├── Router.java                  ← método + patrón de ruta → handler
    │       ├── Cors.java, HttpError.java
    │       ├── json/
    │       │   ├── Json.java                ← writer y parser mínimos (a mano)
    │       │   ├── JsonValue.java           ← sealed: object, array, string, number, bool, null
    │       │   └── InvalidJsonException.java
    │       ├── dto/                         ← records que reflejan api-contract.md
    │       ├── mapper/                      ← domain/engine → DTO
    │       └── handlers/
    │           ├── CatalogHandler.java      ← classes, equipment, effects, enemies
    │           ├── PreviewHandler.java
    │           └── ExpeditionHandler.java   ← create, get, actions, reward
    └── test/java/com/rpgdecorator/          ← mismo árbol de paquetes
        ├── domain/...                       ← tests unitarios por decorador
        ├── engine/...                       ← tests de motor
        ├── api/...                          ← tests de JSON y de integración HTTP
        ├── scenarios/...                    ← escenarios Gherkin de punta a punta (QA)
        └── ArchitectureTest.java            ← verifica la regla de dependencias
```

### 2.2 Stack backend

| Pieza | Elección | Motivo |
|---|---|---|
| Lenguaje | **Java 25** (LTS, instalado) | `record`, `sealed`, pattern matching en `switch` |
| Build | **Maven 3.9** | Estándar; corre los tests y empaqueta un jar ejecutable |
| HTTP | `com.sun.net.httpserver.HttpServer` | Viene en el JDK (módulo `jdk.httpserver`) |
| JSON | Escrito a mano (`api/json`) | Requisito de Java puro; el contrato es pequeño |
| Concurrencia | `ConcurrentHashMap` + `synchronized` por expedición | Suficiente para uso local |
| Tests | **JUnit 5** (solo scope `test`) | Ver ADR-001 |
| HTTP en tests | `java.net.http.HttpClient` (JDK) | Tests de integración sin librerías |

Comandos (definidos en T-001):
```
cd backend
mvn test                          # tests
mvn -q exec:java                  # arranca en :8080   (o: java -jar target/rpg-decorator.jar)
```

---

## 3. Frontend

### 3.1 Estructura de carpetas

```
frontend/
├── package.json, vite.config.ts (proxy /api), tsconfig.json, tailwind config
├── public/icons/                       ← SVG de game-icons.net (CC BY 3.0, con atribución)
└── src/
    ├── main.tsx, App.tsx               ← cambia de pantalla según el estado
    ├── api/
    │   ├── types.ts                    ← tipos TS = api-contract.md
    │   ├── client.ts                   ← fetch tipado + manejo de errores (ApiError)
    │   └── queries.ts                  ← hooks de TanStack Query
    ├── mocks/                          ← fixtures JSON + cliente mock (VITE_USE_MOCKS=true)
    ├── store/
    │   └── gameStore.ts                ← Zustand: screen, selection, event queue, speed
    ├── screens/
    │   ├── ClassSelectScreen.tsx       ← clase
    │   ├── LoadoutScreen.tsx           ← pieza inicial (drag & drop + vista previa)
    │   ├── MapScreen.tsx               ← progreso de la expedición (4 niveles)
    │   ├── ArenaScreen.tsx             ← combate
    │   ├── RewardScreen.tsx            ← elegir 1 de 3 piezas
    │   └── SummaryScreen.tsx           ← COMPLETED / FAILED
    ├── components/
    │   ├── CombatantCard.tsx           ← retrato, barra de vida, stats
    │   ├── HealthBar.tsx
    │   ├── EffectList.tsx              ← íconos con contador de turnos
    │   ├── ChainInspector.tsx          ← ★ pila visual de decoradores
    │   ├── ActionPanel.tsx             ← Attack / Defend / Abilities (cooldowns)
    │   ├── CombatLog.tsx
    │   ├── FloatingNumber.tsx          ← daño o curación animados
    │   ├── Inventory.tsx, EquipmentSlot.tsx, EquipmentItem.tsx
    │   ├── MapNode.tsx                 ← nivel del mapa (defeated / current / hidden / boss)
    │   └── ui/                         ← Button, Panel, Tooltip, Badge
    ├── animation/
    │   └── eventPlayer.ts              ← reproduce events[] en secuencia con delays
    └── styles/tokens.css               ← variables de color (tema oscuro "fantasía")
```

### 3.2 Stack frontend

| Librería | Uso |
|---|---|
| **React 19 + Vite** | Base |
| **TypeScript** (strict) | Tipos del contrato |
| **Tailwind CSS 4** | Estilos |
| **Framer Motion** (`motion`) | Daño flotante, sacudidas, entrada y salida de efectos, capas del inspector |
| **Zustand** | Estado de UI (pantalla actual, selección, cola de animaciones) |
| **TanStack Query** | Llamadas al backend, carga y errores |
| **dnd-kit** | Arrastrar equipo a las ranuras |
| **lucide-react** | Íconos de interfaz |
| **sonner** | Avisos ("¡Congelado! Pierdes el turno") |
| **ESLint + Prettier** | Calidad |

Íconos de efectos y equipo: **game-icons.net** (CC BY 3.0) → atribución en el pie de página.

### 3.3 Flujo de datos en el frontend

```
Click "Atacar"
  └─► mutation POST /api/expeditions/{id}/actions
        └─► respuesta { expedition, events[] }
              ├─► gameStore.enqueueEvents(events)
              │     └─► eventPlayer: anima uno por uno (≈600 ms c/u)
              │            (número flotante, sacudida, ícono de efecto aparece o se va)
              └─► al terminar la cola → se pinta la `expedition` final (fuente de verdad)
                    └─► la pantalla se deriva de expedition.status
                        (IN_PROGRESS → Arena · AWAITING_REWARD → Reward · COMPLETED/FAILED → Summary)
```

El estado que se pinta siempre es el que devuelve el servidor; los eventos **solo** sirven para animar la transición.

---

## 4. Decisiones registradas

| ADR | Decisión |
|---|---|
| [ADR-001](adr/ADR-001-java-puro.md) | Backend en Java puro; JUnit 5 solo para tests |
| [ADR-002](adr/ADR-002-reconstruccion-cadena.md) | Quitar decoradores del medio reconstruyendo la cadena |
| [ADR-003](adr/ADR-003-rest-eventos.md) | REST síncrono que devuelve estado + eventos; sin WebSocket |
| [ADR-004](adr/ADR-004-codigo-en-ingles.md) | Todo el código en inglés; specs y textos de UI en español |

# ⚔️ RPG Decorator — Sistema de combate con efectos temporales

Caso de estudio del **patrón Decorator** aplicado a un combate RPG por turnos.

- **Backend:** Java puro (JDK 25, sin frameworks). El patrón Decorator se implementa a mano.
- **Frontend:** React + Vite + TypeScript, interactivo y animado.

Cada efecto (`PoisonDecorator`, `ShieldDecorator`, `RageDecorator`, `FrozenDecorator`…) y cada pieza de equipo es un **decorador** que envuelve al personaje.

La UI muestra en vivo la pila de decoradores: `Furia(Envenenado(Espada(Guerrero)))`.

> 🗣️ **Código 100 % en inglés**; specs y textos del juego en español (ADR-004).

---

## 📁 Estructura del repositorio

```
Patron-decorator/
├── README.md               ← estás aquí
├── AGENTS.md               ← reglas de trabajo para agentes (LEER PRIMERO)
├── CLAUDE.md               ← puntero a AGENTS.md para Claude Code
├── .claude/agents/         ← subagentes por rol: rpg-domain, rpg-engine, rpg-api, rpg-frontend, rpg-qa
├── specs/                  ← fuente de verdad (spec-driven)
│   ├── requirements.md     ← QUÉ: requisitos funcionales y criterios de aceptación
│   ├── architecture.md     ← CÓMO a nivel macro: capas, módulos, carpetas, stack
│   ├── design.md           ← CÓMO en detalle: decoradores, motor, reglas, UI
│   ├── api-contract.md     ← contrato HTTP/JSON entre back y front
│   ├── tasks.md            ← plan de trabajo: tareas, dependencias, estado
│   ├── glossary.md         ← término en español → nombre en código (inglés)
│   ├── open-questions.md   ← supuestos y dudas pendientes
│   └── adr/                ← decisiones de arquitectura registradas
├── backend/                ← Java puro + Maven (solo carpetas; el código empieza en T-001)
└── frontend/               ← React + Vite + TS (solo carpetas; el código empieza en T-002)
```

## 🎮 El juego en una línea
Expedición **PvE** de 4 niveles: el héroe (Guerrero, Mago o Arquero) enfrenta enemigos distintos sorteados por nivel
(Goblin/Lobo/Slime → Esqueleto/Orco chamán → Golem/Bruja → 🐉 Dragón). Al ganar, elige equipo nuevo: su cadena de decoradores crece.

---

## 📚 Caso de estudio: el patrón Decorator en un combate RPG

### 1. El problema
En un combate un personaje puede estar **envenenado**, **furioso**, **con escudo** y **congelado** a la vez, además de llevar
**espada**, **armadura** y **amuleto**. Cada uno de esos modificadores cambia una parte distinta de su comportamiento:

- **Stats** (Furia: ataque × 1.5 y defensa × 0.7; Espada: ataque +6).
- **Daño recibido** (Escudo absorbe; Espinas devuelve una parte).
- **Daño infligido** (Anillo de fuego suma daño elemental; Vampirismo cura).
- **Inicio de turno** (Veneno hace daño; Regeneración cura).
- **Poder actuar** (Congelado hace perder el turno).

Además, los efectos **aparecen y desaparecen en tiempo de ejecución** (duran N turnos) y se combinan libremente.

### 2. Por qué no basta con herencia o con banderas
- **Herencia:** una subclase por combinación (`PoisonedRagingWarrior`, `ShieldedFrozenMage`…) produce una explosión de clases:
  solo con los 8 efectos temporales ya hay 2⁸ = 256 combinaciones por clase, sin contar el equipo. Y una clase no se puede cambiar en mitad del combate.
- **Banderas en el personaje** (`isPoisoned`, `hasShield`…): todo termina en un `if` gigante dentro de `BaseCharacter`;
  cada efecto nuevo obliga a tocar la clase y el motor (viola abierto/cerrado).

### 3. La solución: cada efecto y cada pieza de equipo es un decorador
Todos los modificadores implementan la misma interfaz que el personaje (`Combatant`), **envuelven** a otro `Combatant`
y le delegan todo, salvo lo que les toca cambiar.

| Rol GoF | En este proyecto |
|---|---|
| **Component** | `Combatant` (interfaz) |
| **ConcreteComponent** | `BaseCharacter`: stats base y vida de la clase o enemigo |
| **Decorator** | `EffectDecorator` (abstracta): guarda `wrapped` y delega todos los métodos |
| **ConcreteDecorator** temporal | `PoisonDecorator`, `ShieldDecorator`, `RageDecorator`, `FrozenDecorator`… |
| **ConcreteDecorator** permanente | `SwordDecorator`, `DragonArmorDecorator`… (equipo) |
| **Client** | `CombatEngine`, `EffectManager` |

Cadena en memoria (de afuera hacia adentro):

```
hero ──► RageDecorator ──► PoisonDecorator ──► SwordDecorator ──► BaseCharacter(warrior)
         (temporal, 2t)    (temporal, 3t)      (equipo, ∞)        (vida, stats base)

describeChain() = "Furia(Envenenado(Espada(Guerrero)))"
```

Cada decorador sobrescribe **solo un aspecto** (esquema ilustrativo; el comportamiento exacto está en `specs/design.md §4`):

```java
// Furia: solo cambia stats(); todo lo demás lo hereda delegado de EffectDecorator
@Override
public Stats stats() {
    Stats s = wrapped.stats();
    return s.withAttack((int) (s.attack() * 1.5)).withDefense((int) (s.defense() * 0.7));
}

// Congelado: canAct() devuelve false sin delegar
@Override
public boolean canAct(TurnContext ctx) { return false; }
```

Equipo y efectos son el **mismo patrón**: lo único que cambia es la duración (∞ para el equipo) y la categoría
(`EQUIPMENT` no se purga con Silencio). **Invariante de orden:** el equipo queda siempre por dentro y los efectos
nuevos se agregan en la capa exterior.

### 4. Las trampas del Decorator (el corazón didáctico)
El proyecto no solo usa el patrón: muestra dónde se rompe y cómo se resuelve (detalle en `specs/design.md §3`, cada una con su test).

| Trampa | Qué pasa | Cómo se resuelve |
|---|---|---|
| **Self-calls** | `BaseCharacter` no sabe que está decorado: `this.stats()` ve la defensa *sin* armadura | El motor calcula todo sobre la **referencia exterior** (`DamageCalculator`, `TurnContext`) |
| **Quitar una capa del medio** | `wrapped` es `final`; no se puede desenganchar | `EffectManager` **reconstruye** la cadena con `copyOnto` (ADR-002) |
| **`instanceof`** | `hero instanceof FrozenDecorator` solo ve la capa exterior | `EffectManager.hasEffect()` / `layers()` recorren la cadena |
| **El orden importa** | `Furia(Espada(base))` → ataque 30; `Espada(Furia(base))` → 27 | Orden fijo por invariante; el inspector muestra las stats capa a capa |
| **Duplicados** | `Poison(Poison(...))` haría doble daño | Reaplicar refresca la duración en vez de añadir capa (RF-16) |
| **Identidad** | Cada capa es un objeto distinto | Todas devuelven el `id()` del base; se busca por `id`, nunca por referencia |

### 5. Qué *no* es un decorador
**Silencio** elimina todos los efectos temporales del objetivo. No modifica el comportamiento de un combatiente: es una
**operación sobre la cadena** (`EffectManager.purge`). Igual que las **reglas de interacción** (Congelado elimina Furia,
Veneno y Regeneración se anulan), que son datos en una tabla y no decoradores. Saber cuándo **no** usar el patrón también es parte del caso.

### 6. Cómo se ve en el juego
La UI tiene un **inspector de cadena** que muestra, para cada combatiente, la pila de decoradores como texto y como capas
visuales con las stats en cada nivel. Turno a turno se ve cómo se apilan, expiran y se reconstruyen las capas.

---

## ▶️ Cómo correrlo

Requisitos: **JDK 25** y **Maven 3.9** (backend); **Node 20+** (frontend, a partir de T-002).

```
cd backend
mvn test                              # tests
mvn -q exec:java                      # arranca la app
mvn -q package                        # genera target/rpg-decorator.jar
java -jar target/rpg-decorator.jar
```

```
cd frontend
npm install
npm run dev                           # http://localhost:5173 (proxy /api → :8080)
```

## 🔁 Flujo spec-driven

```
requirements.md ──► architecture.md / design.md / api-contract.md ──► tasks.md ──► código ──► tests
        ▲                                                                              │
        └──────────────── open-questions.md (si la spec no alcanza) ◄──────────────────┘
```

0. Para arrancar con agentes en Claude Code: *"Usa el agente rpg-domain para hacer T-101"* (ver `specs/tasks.md`, sección de paralelismo).
1. Nada se implementa si no está en una spec.
2. Cada tarea de `tasks.md` referencia los requisitos (`RF-xx`) que cumple.
3. Si el código necesita algo que la spec no dice, **se actualiza la spec primero**.


Cambios hechos por el grupo 2:


William Eduardo Cando Cuarán
Victor Manuel Aguilar Agredo
Samuel Santiago Hurtado Argoti



# ⚔️ RPG Decorator — Sistema de combate con efectos temporales

Caso de estudio del **patrón Decorator** aplicado a un combate RPG por turnos.

- **Backend:** Java puro (JDK 25, sin frameworks). El patrón Decorator se implementa a mano.
- **Frontend:** React + Vite + TypeScript, interactivo y animado.

Cada efecto (Veneno, Escudo, Furia, Congelado…) y cada pieza de equipo es un **decorador** que envuelve al personaje.
La UI muestra en vivo la pila de decoradores: `Furia(Envenenado(Espada(Guerrero)))`.

---

## 📁 Estructura del repositorio

```
Patron-decorator/
├── README.md               ← estás aquí
├── AGENTS.md               ← reglas de trabajo para agentes (LEER PRIMERO)
├── CLAUDE.md               ← puntero a AGENTS.md para Claude Code
├── .claude/agents/         ← definición de subagentes por rol
├── specs/                  ← fuente de verdad (spec-driven)
│   ├── requirements.md     ← QUÉ: requisitos funcionales y criterios de aceptación
│   ├── architecture.md     ← CÓMO a nivel macro: capas, módulos, carpetas, stack
│   ├── design.md           ← CÓMO en detalle: decoradores, motor, reglas, UI
│   ├── api-contract.md     ← contrato HTTP/JSON entre back y front
│   ├── tasks.md            ← plan de trabajo: tareas, dependencias, estado
│   ├── open-questions.md   ← supuestos y dudas pendientes
│   └── adr/                ← decisiones de arquitectura registradas
├── backend/                ← (se crea en T-001) Java puro + Maven
└── frontend/               ← (se crea en T-002) React + Vite + TS
```

## 🔁 Flujo spec-driven

```
requirements.md ──► architecture.md / design.md / api-contract.md ──► tasks.md ──► código ──► tests
        ▲                                                                              │
        └──────────────── open-questions.md (si la spec no alcanza) ◄──────────────────┘
```

1. Nada se implementa si no está en una spec.
2. Cada tarea de `tasks.md` referencia los requisitos (`RF-xx`) que cumple.
3. Si el código necesita algo que la spec no dice, **se actualiza la spec primero**.

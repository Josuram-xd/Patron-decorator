# CLAUDE.md

Este proyecto se trabaja **spec-driven** y **por agentes**.

Antes de cualquier tarea, lee en este orden:
1. `AGENTS.md`: reglas, roles y flujo de trabajo.
2. `specs/tasks.md`: elige o continúa una tarea.
3. Las specs que la tarea referencia (`requirements.md`, `design.md`, `api-contract.md`…).

Reglas críticas (resumen, el detalle está en AGENTS.md):
- Backend en **Java puro**: sin Spring, sin Jackson, sin Lombok. El Decorator se escribe a mano.
- No inventes comportamiento: si la spec no lo cubre, anótalo en `specs/open-questions.md`.
- Al terminar una tarea, actualiza su estado en `specs/tasks.md`.

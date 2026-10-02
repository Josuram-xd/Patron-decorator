# AGENTS.md — Reglas de trabajo para agentes

Este documento define **cómo** trabajan los agentes (humanos o IA) en este repo.
La fuente de verdad son los archivos de `specs/`. El código obedece a la spec, nunca al revés.

---

## 1. Roles

| Rol | Subagente (`.claude/agents/`) | Dueño de | Tareas |
|---|---|---|---|
| **Orquestador / Arquitecto** | (sesión principal) | `specs/**`, `AGENTS.md` | Asigna tareas, resuelve `open-questions.md`, revisa PRs, mantiene la coherencia |
| **Dominio** | `rpg-domain` | `backend/src/main/java/com/rpgdecorator/domain/**` | Fase 1 (T-1xx) |
| **Motor** | `rpg-engine` | `backend/src/main/java/com/rpgdecorator/engine/**` | Fase 2 (T-2xx) |
| **API** | `rpg-api` | `backend/src/main/java/com/rpgdecorator/api/**`, `.../infrastructure/**` | Fase 3 (T-3xx) |
| **Frontend** | `rpg-frontend` | `frontend/**` | Fase 4 (T-4xx) |
| **QA** | `rpg-qa` | `backend/src/test/**/scenarios/**`, `ArchitectureTest`, `specs/qa-checklist.md` | Fase 5 (T-5xx) y revisión cruzada |

> Un agente **solo modifica archivos de su zona**. Si necesita un cambio fuera de ella, lo pide en `open-questions.md` o al orquestador.
> Excepción: cada agente escribe los tests unitarios de su propio código.

---

## 2. Flujo de una tarea

1. **Elegir:** toma una tarea de `specs/tasks.md` en estado `[ ] Pendiente` cuyas dependencias estén todas `[x] Hecha`.
2. **Reclamar:** cambia su estado a `[~] En curso (agente)`.
3. **Leer la spec:** lee los `RF-xx` referenciados y las secciones de `design.md` / `api-contract.md` indicadas.
4. **Implementar:** solo lo que pide la tarea. Sin features extra.
5. **Probar:** todos los criterios de aceptación de la tarea deben tener un test (backend) o una verificación (frontend).
6. **Verificar:** `mvn -q test` (backend) o `npm run build && npm run lint` (frontend) en verde.
7. **Cerrar:** marca `[x] Hecha` y añade una línea en la sección *Registro* al final de `tasks.md`.
8. **Commit:** uno por tarea, en inglés: `feat(T-105): add equipment decorators`.

### Si la spec no alcanza
- **No improvises.** Añade una entrada en `specs/open-questions.md` con: tarea, duda y propuesta.
- Si la duda bloquea, marca la tarea `[!] Bloqueada` y toma otra.
- Solo el orquestador modifica `requirements.md`, `design.md`, `api-contract.md` y los ADR.

---

## 3. Reglas técnicas no negociables

### Backend
- **Java 25**, compilado con Maven. **Cero dependencias de runtime.**
- Única dependencia permitida: **JUnit 5 en scope `test`** (ver ADR-001).
- HTTP con `com.sun.net.httpserver.HttpServer` (JDK). JSON **hecho a mano** (`api/json`).
- **Prohibido:** Spring, Jakarta EE, Jackson/Gson, Lombok, Guava, Apache Commons, cualquier librería de "decorators" o AOP.
- El patrón Decorator se implementa **explícitamente**: interfaz `Combatant` + clase abstracta `EffectDecorator` que **delega** en `wrapped`. Nada de proxies dinámicos (`java.lang.reflect.Proxy`) ni herencia de la clase concreta.
- `domain/` **no conoce** HTTP, JSON ni el motor. `engine/` no conoce HTTP.
- Toda aleatoriedad pasa por `RandomSource` (inyectable, con semilla) para que los tests sean deterministas.

### Frontend
- React + Vite + TypeScript **strict**. Librerías permitidas: las de `architecture.md §4`. Otras requieren un ADR.
- Los tipos TS **reflejan exactamente** `api-contract.md`. Si el contrato cambia, se cambian ahí primero.
- El frontend **no calcula reglas de combate**: solo muestra lo que devuelve el backend y anima los `eventos`.

### Convenciones
- **Todo el código en inglés** (ADR-004): paquetes, clases, métodos, variables, archivos, ids de catálogo, JSON, enums, comentarios, nombres de tests y mensajes de commit. Usa los nombres de `specs/glossary.md`.
- **En español** solo: las specs (`specs/**`, `AGENTS.md`, `README.md`) y los textos visibles para el jugador (`label`, `name`, `description` de los catálogos, textos de la UI, `message` de los errores).
- Clases de efecto con sufijo `Decorator`: `PoisonDecorator`, `ShieldDecorator`.
- Tests: `ClassNameTest`; el nombre del método describe el comportamiento: `poisonExpiresAfterThreeTurns()`.
- Commits: Conventional Commits en inglés con el ID de la tarea: `feat(T-106a): add poison and regeneration decorators`.

---

## 4. Definición de Hecho (DoD)

Una tarea está **Hecha** cuando:
- [ ] Cumple todos sus criterios de aceptación.
- [ ] Tiene tests que pasan (o una verificación manual documentada, en tareas de UI).
- [ ] No rompe el build ni los tests existentes.
- [ ] Respeta la zona de archivos del agente y las reglas técnicas.
- [ ] `tasks.md` está actualizado.

---

## 5. Trabajo en paralelo

- Las tareas sin dependencias entre sí pueden ejecutarse **en paralelo** por distintos agentes (están marcadas con ⚡ en `tasks.md`).
- El frontend puede avanzar **antes que el backend** usando los mocks de `frontend/src/mocks/` (T-402), que siguen `api-contract.md` al pie de la letra.
- Para trabajar aislado, cada agente puede usar un **git worktree** o una rama `agente/T-xxx`.

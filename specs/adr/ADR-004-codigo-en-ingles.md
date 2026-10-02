# ADR-004 — Todo el código en inglés

- **Estado:** Aceptada
- **Fecha:** 2026-10-02

## Contexto
Las specs se escribieron en español, y la primera versión proponía identificadores en español (`Combatiente`, `recibirDanio`). El equipo prefiere el código en inglés: es el estándar de la industria, encaja con la terminología del patrón (GoF: *Component*, *Decorator*) y evita mezclar idiomas con las APIs de Java y React.

## Decisión
**En inglés:**
- Paquetes, clases, interfaces, métodos, variables y constantes.
- Archivos y carpetas (backend y frontend).
- Ids de catálogo (`warrior`, `poison`, `dragon_armor`), enums (`IN_PROGRESS`), rutas HTTP y campos JSON.
- Comentarios, Javadoc, nombres de tests y mensajes de commit.

**En español:**
- Documentación del proyecto: `specs/**`, `AGENTS.md`, `README.md`.
- Textos visibles para el jugador: `name`, `label` y `description` de los catálogos, textos de la UI y `message` de los errores.

La correspondencia entre términos está en [`glossary.md`](../glossary.md); es obligatoria para todos los agentes.

## Consecuencias
- ✅ Código homogéneo y legible para cualquier desarrollador.
- ✅ Los textos del juego quedan como datos: traducir la UI no toca la lógica.
- ❌ Las specs mezclan español (prosa) con nombres en inglés (código); el glosario lo mitiga.

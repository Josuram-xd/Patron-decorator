# ADR-003 — REST síncrono que devuelve estado + eventos

- **Estado:** Aceptada
- **Fecha:** 2026-10-02

## Contexto
El juego es PvE por turnos: solo el jugador inicia acciones y el enemigo responde dentro de la misma ronda. Aun así, la UI necesita animar **paso a paso** lo que ocurrió (daño, escudo, veneno…).

## Decisión
- Cada acción es un `POST` que devuelve `{ expedition, events[] }`:
  - `expedition`: estado completo y final (fuente de verdad).
  - `events`: secuencia ordenada para animar la transición.
- **Sin WebSocket ni SSE.**

## Consecuencias
- ✅ Simple de implementar con `HttpServer` y fácil de mockear en el front.
- ✅ Recargar la página no pierde nada: `GET /api/expeditions/{id}` devuelve el estado y el log completo.
- ❌ Si se añadiera PvP en el futuro, haría falta un canal push (nuevo ADR).

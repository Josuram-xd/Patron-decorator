# ADR-001 — Backend en Java puro, sin frameworks

- **Estado:** Aceptada
- **Fecha:** 2026-10-02

## Contexto
El objetivo del proyecto es **aprender el patrón Decorator implementándolo a mano**. Frameworks como Spring traen sus propios mecanismos de decoración (AOP, proxies, `@Transactional`, filtros) que ocultan el patrón y lo vuelven "magia".

## Decisión
- Backend con **JDK 25 y nada más** en runtime:
  - HTTP: `com.sun.net.httpserver.HttpServer`.
  - JSON: serializador y parser escritos a mano (`api/json`).
  - Persistencia: memoria (`ConcurrentHashMap`).
- **Maven** solo como herramienta de build.
- **JUnit 5** permitido **únicamente en scope `test`**: no forma parte del programa y hace los tests legibles.

## Consecuencias
- ✅ El patrón queda 100 % visible y explicable; cero magia.
- ✅ El jar es pequeño y arranca al instante.
- ❌ Hay que escribir routing, JSON y manejo de errores a mano (tareas T-301 y T-302).
- ❌ Sin validación declarativa: las validaciones se escriben a mano en los handlers.
- Cualquier dependencia nueva requiere un ADR nuevo.

# ADR-002 — Quitar decoradores del medio reconstruyendo la cadena

- **Estado:** Aceptada
- **Fecha:** 2026-10-02

## Contexto
Los efectos temporales expiran en cualquier orden, así que hay que retirar capas que están **en medio** de la cadena (`Furia(Veneno(Espada(base)))` → quitar Veneno). El Decorator clásico no ofrece esta operación.

## Opciones
1. **`setEnvuelto()` mutable:** se re-engancha la capa de afuera a la de adentro.
   Simple, pero rompe la inmutabilidad de la estructura y obliga a recorrer la cadena guardando el "padre".
2. **Decoradores "inactivos":** el efecto expirado se queda en la cadena pero delega sin hacer nada.
   La cadena crece sin límite y el inspector muestra basura.
3. **Reconstrucción:** desenrollar, filtrar y volver a envolver con copias (`copiarSobre`). ✅

## Decisión
Opción 3. `envuelto` es `final`. Cada decorador concreto implementa `copiarSobre(Combatiente)` para clonarse **con su estado** (duración, absorción) sobre otro envuelto. `GestorEfectos` es el único que reconstruye.

## Consecuencias
- ✅ Las cadenas son estructuralmente inmutables; es fácil razonar y testear.
- ✅ Es el mismo mecanismo que se usa para empezar cada encuentro de la expedición (reconstruir desde el base solo con el equipo).
- ❌ Cada decorador concreto debe implementar `copiarSobre` (una línea, pero obligatoria).
- ❌ La referencia exterior cambia: quien la guarda (`Combate`) debe actualizarla siempre con lo que devuelve el gestor.

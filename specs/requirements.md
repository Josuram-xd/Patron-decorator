# Requisitos — RPG Decorator

> **Qué** debe hacer el sistema. El **cómo** está en `architecture.md` y `design.md`.
> Cada requisito tiene un ID (`RF-xx` / `RNF-xx`) que las tareas referencian.
> Los nombres en código (inglés) de cada término están en `glossary.md`.

## 1. Objetivo

Un RPG **PvE** por turnos: el héroe emprende una **expedición** de 4 niveles y en cada uno se enfrenta **1 vs 1** a un enemigo distinto (controlado por la IA), hasta llegar al jefe final.
El comportamiento de cada combatiente se modifica en tiempo de ejecución con **decoradores apilables**:

- **Equipo** → decoradores **permanentes** (Espada, Armadura…).
- **Efectos de estado** → decoradores **temporales** que expiran tras N turnos (Veneno, Furia…).

El objetivo didáctico es **ver el patrón funcionando**: la UI muestra la cadena de decoradores de cada combatiente y cómo cambia turno a turno.

## 2. Glosario

| Término | Significado |
|---|---|
| **Combatiente** (`Combatant`) | Héroe o enemigo. Interfaz que decoran todos los efectos |
| **Personaje base** | Componente concreto: stats base de una clase o enemigo, sin decorar |
| **Efecto** | Decorador temporal con duración en turnos |
| **Equipo** | Decorador permanente (duración infinita) que no se purga con Silencio |
| **Cadena** | Pila de decoradores que envuelve al personaje base, de afuera hacia adentro |
| **Ronda** | El turno del héroe seguido del turno del enemigo |
| **Expedición** | Partida completa: secuencia de 4 encuentros contra enemigos distintos |
| **Encuentro** | Un combate 1 vs 1 dentro de la expedición |
| **Recompensa** | Pieza de equipo elegida (1 de 3) tras ganar un encuentro |
| **Evento** | Hecho atómico del combate (daño, curación, efecto aplicado…) que la UI anima |

---

## 3. Requisitos funcionales

### Preparación
- **RF-01 — Elegir clase.** El jugador elige una clase de héroe: **Guerrero, Mago o Arquero**, cada una con stats base y 2 habilidades propias.
- **RF-02 — Pieza inicial.** El jugador elige **1 pieza de equipo inicial** del catálogo. Hay 3 ranuras: arma, armadura y accesorio, con máximo 1 pieza por ranura. Cada pieza se aplica como un decorador permanente.
- **RF-03 — Vista previa de stats.** Al elegir o cambiar equipo se ven las stats resultantes **calculadas por el backend**.

### Expedición (PvE con enemigos distintos)
- **RF-04 — Niveles.** Una expedición tiene **4 niveles**. En cada nivel aparece un enemigo distinto, sorteado (con la semilla) de su grupo:
  - Nivel 1: Goblin, Lobo o Slime.
  - Nivel 2: Esqueleto u Orco chamán.
  - Nivel 3: Golem de piedra o Bruja.
  - Nivel 4 (jefe): Dragón.
  Al iniciar, el jugador ve el **mapa de la expedición** con los enemigos de los niveles 1–3 ocultos hasta llegar a ellos; el jefe es visible.
- **RF-24 — Entre encuentros.** Tras ganar un encuentro:
  1. Se **purgan todos los efectos temporales** del héroe (el equipo se queda).
  2. El héroe **conserva su vida** y recupera el **30 % de su vida máxima** efectiva.
  3. Se reinician los enfriamientos.
- **RF-25 — Recompensas.** Tras ganar un encuentro (salvo el jefe) se ofrecen **3 piezas de equipo** al azar y el jugador elige 1 (u omite). Si la ranura está ocupada, la nueva **reemplaza** a la anterior. Así la cadena de equipo crece durante la expedición.
- **RF-26 — Fin de la expedición.** Si el héroe muere → expedición `FAILED`. Si vence al Dragón → `COMPLETED`. Se muestra un resumen: enemigos vencidos, rondas totales, daño infligido y recibido, y la cadena final de equipo.
- **RF-27 — Expedición nueva.** Desde el resumen se puede empezar otra expedición (nueva semilla → otros enemigos).

### Combate
- **RF-05 — Iniciar encuentro.** Al entrar a un nivel se crea un combate con estado `IN_PROGRESS`, ronda 1, y el héroe actúa primero.
- **RF-06 — Acciones del héroe.** En su turno el héroe puede: **Atacar**, **Defender** (aplica `Defensa` por 1 turno) o usar una de sus **2 habilidades**.
- **RF-07 — Enfriamiento.** Cada habilidad tiene un enfriamiento en turnos; no se puede usar hasta que llegue a 0.
- **RF-08 — Turno del enemigo.** Después de la acción del héroe, el enemigo actúa automáticamente según su IA (ver `design.md §5.4`).
- **RF-09 — Fin de combate.** El combate termina cuando un combatiente llega a 0 de vida: `VICTORY` o `DEFEAT`. Ya no se aceptan acciones.

### Efectos (decoradores temporales)
- **RF-10 — Aplicar efectos.** Las habilidades aplican efectos sobre uno mismo o sobre el rival. Cada efecto envuelve al combatiente como un nuevo decorador **en la capa exterior**.
- **RF-11 — Duración.** Cada efecto tiene una duración en turnos del combatiente afectado; al llegar a 0 **se quita de la cadena aunque esté en medio**.
- **RF-12 — Efectos por turno.** Algunos efectos actúan al inicio del turno del afectado (Veneno hace daño, Regeneración cura).
- **RF-13 — Modificar stats.** Algunos efectos modifican stats derivadas (Furia: +ataque/−defensa).
- **RF-14 — Interceptar daño.** Algunos efectos interceptan el daño recibido (Escudo absorbe; Espinas devuelve una parte).
- **RF-15 — Bloquear acción.** Congelado hace perder el turno al afectado.
- **RF-16 — Reaplicar.** Reaplicar un efecto que ya está activo **no** crea otra capa: se refresca la duración (salvo el Escudo, que suma absorción; ver `design.md §4.3`).
- **RF-17 — Interacciones.** Existen reglas de interacción entre efectos (p. ej., Congelado elimina Furia). Lista en `design.md §4.4`.
- **RF-18 — Silencio.** Silencio elimina **todos los efectos temporales** del objetivo, pero **no el equipo**.

### Visualización / didáctica
- **RF-19 — Estado visible.** La UI muestra en todo momento: vida, stats actuales, efectos activos con turnos restantes y enfriamientos.
- **RF-20 — Inspector de cadena.** La UI muestra la cadena de decoradores de cada combatiente como texto (`Furia(Veneno(Espada(Guerrero)))`) y como pila visual por capas.
- **RF-21 — Log de combate.** Se muestra un registro cronológico de los eventos de cada ronda.
- **RF-22 — Animaciones.** Cada evento se anima: números de daño o curación flotando, sacudida al recibir un golpe, aparición o desaparición de íconos de efecto.
- **RF-23 — Mapa de progreso.** Durante la expedición se ve el nivel actual, los enemigos vencidos y los pendientes.

---

## 4. Requisitos no funcionales

- **RNF-01 — Java puro.** Backend sin frameworks ni librerías de runtime (ver ADR-001).
- **RNF-02 — Decorator explícito.** El patrón se implementa a mano, con la estructura clásica de GoF (componente, componente concreto, decorador base, decoradores concretos).
- **RNF-03 — Abierto/Cerrado.** Agregar un efecto nuevo = crear **una clase** de decorador y registrarla en el catálogo. Sin tocar el motor ni otros efectos.
- **RNF-04 — Determinismo.** Con la misma semilla, el mismo combate produce los mismos resultados (tests reproducibles).
- **RNF-05 — Cobertura.** Cada decorador tiene tests unitarios; el motor tiene tests de escenario completos.
- **RNF-06 — Persistencia.** En memoria (no hay BD). Un reinicio del servidor borra los combates.
- **RNF-07 — Rendimiento.** Una acción responde en menos de 100 ms en local.
- **RNF-09 — Código en inglés.** Todo identificador, archivo, id, campo JSON, comentario y commit en inglés; los textos visibles para el jugador en español (ADR-004).
- **RNF-08 — Responsive.** La UI se usa en escritorio (≥1024 px) y es legible en móvil (≥375 px).

---

## 5. Criterios de aceptación clave (Gherkin)

```gherkin
Escenario: El veneno hace daño y expira
  Dado un Guerrero con 100 de vida
  Cuando recibe Envenenado (5 de daño, 3 turnos)
  Entonces al inicio de cada uno de sus 3 turnos siguientes pierde 5 de vida
  Y después del tercer turno Envenenado ya no está en su cadena

Escenario: Quitar un decorador del medio de la cadena
  Dado un combatiente con cadena Furia(Envenenado(Espada(Guerrero)))
  Cuando Envenenado expira
  Entonces la cadena es Furia(Espada(Guerrero))
  Y Furia conserva sus turnos restantes

Escenario: El escudo absorbe antes que la vida
  Dado un combatiente con Escudo de 20 de absorción
  Cuando recibe 30 de daño
  Entonces el escudo absorbe 20 y la vida baja 10
  Y el Escudo se elimina de la cadena por quedar en 0

Escenario: Silencio respeta el equipo
  Dado un combatiente con cadena Furia(Veneno(ArmaduraDragon(Mago)))
  Cuando recibe Silencio
  Entonces su cadena es ArmaduraDragon(Mago)

Escenario: Pasar al siguiente nivel conserva el equipo y purga los efectos
  Dado un héroe con cadena Furia(Veneno(Espada(Arquero))) y 40/95 de vida
  Cuando vence al enemigo del nivel 1
  Entonces su cadena es Espada(Arquero)
  Y su vida es 40 + 28 = 68
  Y se le ofrecen 3 piezas de equipo como recompensa

Escenario: Misma semilla, mismos enemigos
  Dadas dos expediciones creadas con la semilla 42
  Entonces ambas tienen la misma secuencia de enemigos

Escenario: Congelado hace perder el turno y anula Furia
  Dado un enemigo con Furia activa
  Cuando recibe Congelado
  Entonces Furia se elimina de su cadena
  Y en su siguiente turno se emite el evento TURNO_PERDIDO y no ataca
```

# Preguntas abiertas y supuestos

> Los agentes añaden aquí sus dudas en lugar de improvisar. El orquestador responde y, si hace falta, actualiza la spec.

## Formato
```
### Q-xxx — título corto
- Tarea: T-xxx · Agente: XXX · Estado: ABIERTA | RESUELTA
- Duda: …
- Propuesta: …
- Resolución: … (la escribe el orquestador)
```

---

## Supuestos tomados al escribir las specs (validar con el usuario)

### Q-001 — JUnit en tests
- Estado: RESUELTA (supuesto)
- Duda: "Java puro", ¿incluye no usar JUnit?
- Resolución: JUnit 5 solo en scope `test` (ADR-001). Si se quiere 0 dependencias también en los tests, reemplazar por un mini *runner* propio (tarea extra).

### Q-002 — Java 25
- Estado: RESUELTA (supuesto)
- Resolución: es el JDK instalado (Temurin 25 LTS). El código no debe usar *preview features*.

### Q-003 — Balance numérico
- Estado: ABIERTA
- Duda: los valores de stats y efectos son una primera propuesta; pueden hacer la expedición demasiado fácil o difícil.
- Propuesta: tras T-502, simular 1000 expediciones por clase con IA "siempre atacar" y ajustar para una tasa de victoria de ~40–60 %.

### Q-004 — Persistencia
- Estado: RESUELTA (supuesto)
- Resolución: en memoria (RNF-06). Una BD queda fuera de alcance.

---

## Dudas de F2 (motor) y F4 (T-401/T-402)

> Escritas por el orquestador a partir de los reportes de los agentes. F2 se implementó contra **stubs del dominio** (F1 aún no estaba en el repo); hay que revalidar al integrar F1.

### Q-005 — Visibilidad de `copyOnto`
- Tarea: T-201 · Agente: ENG · Estado: ABIERTA
- Duda: design §2.4 lo declara `protected abstract`, pero `EffectManager` vive en `engine.effects` y no puede llamarlo.
- Propuesta: `public abstract EffectDecorator copyOnto(Combatant newWrapped)`. El dominio real (T-104) debe declararlo así; corregir design §2.4.

### Q-006 — `RemovalReason`: ¿dominio o motor?
- Tarea: T-201 · Agente: ENG · Estado: ABIERTA
- Duda: tasks T-201 lo pone en `engine/effects`, pero el evento `EffectRemoved` (dominio) tiene el campo `reason` y el dominio no puede importar del motor.
- Propuesta: `domain.event.RemovalReason` (así se implementó).

### Q-007 — `justApplied` y efectos aplicados por el rival
- Tarea: T-201 · Agente: ENG · Estado: ABIERTA
- Duda: si toda capa nueva nace con `justApplied = true`, un Veneno aplicado por el enemigo dañaría 4 turnos en vez de 3 (design §4.5).
- Propuesta (implementada): `EffectManager.apply(outer, effectId, duringOwnTurn, ctx)`; con `duringOwnTurn = false` se limpia el flag al aplicar. Además, `refresh` no reactiva `justApplied`: reaplicar un efecto propio en el propio turno dura un turno menos. Confirmar si se acepta.

### Q-008 — Regla de cooldown
- Tarea: T-204, T-205 · Agente: ENG · Estado: ABIERTA
- Duda: los cooldowns bajan al **inicio** del turno (design §5.3), pero la validación del héroe y la decisión de la IA ocurren **antes**.
- Propuesta (implementada): una habilidad está lista si el valor guardado es `<= 1` al validar/decidir → "cd 3 usado en el turno 1 → disponible en el turno 4" para ambos bandos. Consecuencia: el contador guardado es 1 mayor que "turnos de espera"; el DTO (`cooldownRemaining`) debería enviar `max(0, guardado − 1)` o la spec debe decir otra cosa.

### Q-009 — Muertes y cierre del turno
- Tarea: T-204 · Agente: ENG · Estado: ABIERTA
- Duda: el pseudocódigo de §5.3 hace `emit DEATH; return` sin `TURN_ENDED` al morir por veneno.
- Propuesta (implementada): todo `TURN_STARTED` tiene su `TURN_ENDED`; una muerte salta el resto del turno (y `advanceTurn`) y luego `COMBAT_ENDED`. Si mueren los dos en el mismo turno gana el actor (turno del héroe → VICTORY; del enemigo → DEFEAT). Espinas puede matar al atacante: su `DEATH` va después del del objetivo. El robo de vida se aplica también en el golpe letal.

### Q-010 — Detalles de eventos de daño
- Tarea: T-202, T-204 · Agente: ENG · Estado: ABIERTA
- Propuestas (implementadas):
  - Escudo que absorbe todo: solo `ABSORBED`, sin `DAMAGE` de 0. `ABSORBED` va antes de `DAMAGE`.
  - `damageType` del golpe: `PHYSICAL` si hay parte física; si no, `ELEMENTAL`.
  - Golpe evadido: los modificadores salientes (Anillo de fuego) ya se aplicaron; los efectos sobre el rival no se aplican, pero la **purga sí** (§4.7 solo condiciona los efectos).
  - Con daño bruto 0 igual se tiran crítico y evasión (orden de tiradas fijo: crítico, luego evasión). El mínimo de 1 solo aplica si el físico es > 0.
  - La IA del enemigo decide antes de su turno aunque luego muera o esté congelada (puede consumir una tirada aleatoria).
  - `HEAL` se emite con la cantidad real curada, aunque sea 0; un combatiente con 0 de vida no se cura (sin "revivir" en el mismo tick).

### Q-011 — `Combat` necesita más campos que design §5.5
- Tarea: T-203 · Agente: ENG · Estado: ABIERTA
- Propuesta (implementada): `new Combat(hero, enemy, heroAbilities, enemyDefinition)`; actualizar design §5.1/§5.5.

### Q-012 — Textos de descripción de habilidades y equipo
- Tarea: T-107 · Agente: DOM · Estado: ABIERTA
- Duda: design no da el texto literal de las descripciones (`description`). Los stubs y los mocks usan frases cortas propias ("Daño ×0.8 y envenena al rival.", "Ataque +6").
- Propuesta: definirlas en design §4.6–§4.8.

### Q-013 — Ambigüedades del contrato detectadas en T-401/T-402
- Tarea: T-401, T-402 · Agente: FE · Estado: ABIERTA
- `CombatantDTO.effects[].extra`: solo está documentado `{absorption}`; se tipó como `Record<string, number> | null`.
- Estado del nivel ya vencido en el mapa durante `AWAITING_REWARD`: ¿`CURRENT` o `DEFEATED`? (el mock usa `DEFEATED`).
- Orden de envoltura del equipo: arma por dentro, luego armadura, luego accesorio (lo que sugiere el ejemplo del contrato).
- `/catalog/effects`: ¿incluye el equipo con categoría `EQUIPMENT`? (el mock solo lista los 8 temporales).
- `seed`: si en Java es `long`, en JS pierde precisión por encima de 2^53 → limitarla o enviarla como texto.
- Al ganar vida máxima por una recompensa (Amuleto de vida): ¿la vida actual se mantiene o sube? (el mock la mantiene).
- `PASS`: ¿emite `ACTION`? (el motor emite `ACTION PASS` solo si el enemigo pasa pudiendo actuar; el héroe congelado solo produce `TURN_SKIPPED`).

---

## Dudas de F0/F1 (dominio real) e integración con F2

### Q-014 — Daño del veneno: 5 o 6
- Tarea: T-106a · Agente: DOM · Estado: ABIERTA
- Duda: el Gherkin de requirements §5 usa "Envenenado (5 de daño, 3 turnos)"; design §4.2 y api-contract §3 dicen 6.
- Propuesta: el catálogo usa 6; `PoisonDecorator` acepta daño y turnos por constructor para que el escenario pueda usar 5. Alinear el Gherkin a 6.

### Q-015 — JDK 25 y Maven no están instalados
- Tarea: T-001 · Agente: ORQ · Estado: ABIERTA
- Duda: Q-002 asume Temurin 25, pero esta máquina tiene `JAVA_HOME` en JDK 21 y `mvn` no está en el PATH.
- Propuesta: instalar Temurin 25 y Maven 3.9. Mientras tanto el build se verifica con el JDK 25 (JBR) y el Maven que trae IntelliJ.

### Q-016 — Integración de F1 con F2
- Tarea: T-101–T-108 · Agente: DOM · Estado: RESUELTA
- El dominio real se adaptó a la API contra la que se escribió el motor: `RemovalReason` y `CombatResult` en `domain.event`, `CombatEvent.type()`, `HeroClass`, `EnemyAbility(ability, AiCondition)`, `AiView`, `EffectApplication.self/opponent`, `BaseCharacter.hero/enemy`, `copyOnto` público (Q-005, Q-006).
- Los records de `CombatEvent` no llevan `seq` ni `round`: los pone el motor en `LoggedEvent`. `ShieldDecorator` no emite `ABSORBED`; lo emite el motor a partir del `DamageResult`.
- `changeHealth` es `final` en `EffectDecorator` ("ningún decorador lo sobrescribe", design §2.2).
- Revalidación: los 216 tests (66 de dominio + 150 de motor) pasan contra el dominio real.

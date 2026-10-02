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

### Q-005 — `seq`, `round` y enums en los eventos del dominio
- Tarea: T-102 · Agente: DOM · Estado: RESUELTA (supuesto)
- Duda: design §6 dice que todo evento lleva `seq` y `round`, pero los decoradores emiten eventos (`ShieldDecorator` → `ABSORBED`) y no conocen el `seq`. Además `reason`, `action` y `result` usan enums que viven en `engine` (`RemovalReason`, `Action`, `CombatStatus`), y `domain` no puede importarlos.
- Resolución: los records de `CombatEvent` solo llevan los campos propios del tipo; `engine` les pone `seq` y `round` al registrarlos en el log del combate. `action`, `reason` y `result` son `String` con el nombre del enum.

### Q-006 — Visibilidad de `copyOnto`
- Tarea: T-104 · Agente: DOM · Estado: RESUELTA (supuesto)
- Duda: design §2.4 lo declara `protected`, pero lo llama `EffectManager`, que está en otro paquete (`engine.effects`).
- Resolución: `copyOnto` es `public`. `changeHealth` es `final` en `EffectDecorator` ("ningún decorador lo sobrescribe").

### Q-007 — Daño del veneno: 5 o 6
- Tarea: T-106a · Agente: DOM · Estado: ABIERTA
- Duda: el Gherkin de requirements §5 usa "Envenenado (5 de daño, 3 turnos)"; design §4.2 y api-contract §3 dicen 6.
- Propuesta: el catálogo usa 6; `PoisonDecorator` acepta daño y turnos por constructor para que el escenario pueda usar 5. Alinear el Gherkin a 6.

### Q-008 — JDK 25 y Maven no están instalados
- Tarea: T-001 · Agente: ORQ · Estado: ABIERTA
- Duda: Q-002 asume Temurin 25, pero la máquina tiene `JAVA_HOME` en JDK 21 y `mvn` no está en el PATH.
- Propuesta: instalar Temurin 25 y Maven 3.9. Mientras tanto el build se verifica con el JDK 25 (JBR) y el Maven que trae IntelliJ.

### Q-009 — Entrada de las condiciones de IA
- Tarea: T-107 · Agente: DOM · Estado: RESUELTA (supuesto)
- Duda: design §4.8 pide las condiciones como `Predicate`, pero no dice sobre qué. "El rival tiene ≥ 2 BUFF" necesita recorrer la cadena, y eso es de `EffectManager` (engine).
- Resolución: `Predicate<EnemyDefinition.Situation>`, con `Situation(health, maxHealth, opponentBuffCount, random)`. El motor la calcula con las cadenas exteriores y el dominio no hace `instanceof`.

### Q-010 — `justApplied` cuando el efecto lo aplica el rival
- Tarea: T-201 · Agente: ENG · Estado: RESUELTA (supuesto)
- Duda: `EffectDecorator` nace con `justApplied = true`, pero design §4.5 dice que solo se salta el decremento si el efecto se aplicó en el turno del propio afectado. Con la firma `apply(outer, effectId, ctx)` el gestor no sabe de quién es el turno, y un Poison del rival duraría 4 turnos.
- Resolución: se añade `apply(outer, effectId, ctx, duringOwnTurn)`; la forma de 3 argumentos equivale a `duringOwnTurn = true`. Si es `false`, el gestor consume el `justApplied` al envolver. También se añade `EffectDecorator.extra()` (absorción del Shield) para el `extra` de api-contract §5.

### Q-011 — Tipo de daño del evento `DAMAGE` en un golpe mixto
- Tarea: T-202 · Agente: ENG · Estado: ABIERTA
- Duda: un golpe con FireRing lleva parte física y elemental, pero `DamageResult.taken` es un solo número; no se puede emitir un `DAMAGE` por tipo.
- Propuesta: un solo evento `DAMAGE` con `damageType = PHYSICAL` por golpe (lo implementado). Si se quiere el número naranja, separar `taken` en físico y elemental en `DamageResult`.

### Q-003 — Balance numérico
- Estado: ABIERTA
- Duda: los valores de stats y efectos son una primera propuesta; pueden hacer la expedición demasiado fácil o difícil.
- Propuesta: tras T-502, simular 1000 expediciones por clase con IA "siempre atacar" y ajustar para una tasa de victoria de ~40–60 %.

### Q-004 — Persistencia
- Estado: RESUELTA (supuesto)
- Resolución: en memoria (RNF-06). Una BD queda fuera de alcance.

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

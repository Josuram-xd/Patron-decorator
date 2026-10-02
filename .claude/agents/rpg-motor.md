---
name: rpg-motor
description: Implementa el motor de combate y la expedición (GestorEfectos, MotorCombate, IA, Expedicion, ServicioExpedicion) en Java puro. Usar para las tareas T-2xx de specs/tasks.md.
tools: Read, Write, Edit, Glob, Grep, Bash
---
Eres el agente de MOTOR del proyecto RPG Decorator.

Antes de empezar lee: AGENTS.md, specs/tasks.md (tu tarea), specs/design.md §3–§5, specs/adr/ADR-002 y los RF citados.

Zona de archivos: backend/src/main/java/com/rpgdecorator/motor/** y sus tests.

Reglas:
- Java 25 puro, sin librerías. Depende solo de `dominio`; nunca de `api`.
- GestorEfectos es el ÚNICO que hace instanceof sobre decoradores y el único que reconstruye cadenas.
- Tras cada operación del gestor, actualiza la referencia exterior guardada en Combate.
- Toda aleatoriedad pasa por dominio.Aleatorio; en los tests usa uno con semilla o fijo.
- El orden de eventos de una ronda es el de design §5.3: los tests lo verifican.
- Si la spec no alcanza, escribe en specs/open-questions.md.
- Al terminar: `mvn -q test` en verde y actualiza specs/tasks.md.

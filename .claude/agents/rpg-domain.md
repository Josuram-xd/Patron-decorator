---
name: rpg-domain
description: Implementa el núcleo del patrón Decorator (Combatant, BaseCharacter, EffectDecorator, efectos, equipo y catálogos) en Java puro. Usar para las tareas T-1xx de specs/tasks.md.
tools: Read, Write, Edit, Glob, Grep, Bash
---
Eres el agente de DOMINIO del proyecto RPG Decorator.

Antes de empezar lee: AGENTS.md, specs/tasks.md (tu tarea), specs/design.md §1–§4, specs/glossary.md y los RF que cite la tarea en specs/requirements.md.

Zona de archivos: backend/src/main/java/com/rpgdecorator/domain/** y sus tests en backend/src/test/java/com/rpgdecorator/domain/**.

Reglas:
- TODO el código en inglés: clases, métodos, variables, comentarios, nombres de tests y commits (ADR-004). Solo los textos visibles para el jugador (label, name, description) van en español.
- Java 25 puro. Nada de librerías ni de java.lang.reflect.Proxy. El Decorator se escribe a mano: cada decorador delega en `wrapped`.
- `domain` no importa nada de engine, api ni infrastructure.
- Los decoradores no mutan la vida directamente: usan TurnContext (design §3.1).
- Cada decorador concreto implementa copyOnto (ADR-002).
- Valores numéricos EXACTAMENTE los de las tablas de design §4.
- Si la spec no alcanza, escribe en specs/open-questions.md y no improvises.
- Al terminar: `mvn -q test` en verde, marca la tarea [x] en specs/tasks.md y añade una línea al Registro.

# Glosario — español (specs) → inglés (código)

> Obligatorio para todos los agentes (ADR-004). Si necesitas un término que no está aquí, añádelo en `open-questions.md`.

## Conceptos y clases

| Español (specs / UI) | Código | Tipo |
|---|---|---|
| Combatiente | `Combatant` | interface |
| Personaje base | `BaseCharacter` | class |
| Decorador de efecto (base) | `EffectDecorator` | abstract class |
| Envuelto | `wrapped` | field |
| Stats / estadísticas | `Stats` (`maxHealth`, `attack`, `defense`, `speed`, `critChance`) | record |
| Daño | `Damage` (`physical`, `elemental`, `sourceId`, `critical`, `reflectable`) | record |
| Resultado de daño | `DamageResult` (`taken`, `absorbed`, `reflected`, `evaded`) | record |
| Tipo de daño | `DamageType` (`PHYSICAL`, `ELEMENTAL`, `POISON`, `REFLECTED`) | enum |
| Bando | `Side` (`HERO`, `ENEMY`) | enum |
| Categoría | `Category` (`EQUIPMENT`, `BUFF`, `DEBUFF`, `CONTROL`) | enum |
| Duración | `Duration` (`PERMANENT`) | record |
| Contexto de turno | `TurnContext` / `TurnContextImpl` | interface / class |
| Aleatorio | `RandomSource` / `JdkRandomSource` | interface / class |
| Evento de combate | `CombatEvent` | sealed interface |
| Gestor de efectos | `EffectManager` | class |
| Reglas de interacción | `InteractionRules` | class |
| Capa (inspector) | `Layer` | record |
| Motivo de retiro | `RemovalReason` (`EXPIRED`, `DEPLETED`, `PURGED`, `INTERACTION`) | enum |
| Combate | `Combat` | class |
| Estado del combate | `CombatStatus` (`IN_PROGRESS`, `VICTORY`, `DEFEAT`) | enum |
| Acción | `Action` (`Attack`, `Defend`, `UseAbility`, `Pass`) | sealed interface |
| Motor de combate | `CombatEngine` | class |
| Calculadora de daño | `DamageCalculator` | class |
| IA del enemigo | `EnemyAI` | class |
| Habilidad | `Ability` | record |
| Enfriamiento | `cooldown` | field |
| Aplicación de efecto | `EffectApplication` | record |
| Objetivo (propio / rival) | `Target` (`SELF`, `OPPONENT`) | enum |
| Ranura (arma / armadura / accesorio) | `Slot` (`WEAPON`, `ARMOR`, `ACCESSORY`) | enum |
| Pieza de equipo | item (`ItemDTO`, `itemId`) | — |
| Expedición | `Expedition` | class |
| Estado de la expedición | `ExpeditionStatus` (`IN_PROGRESS`, `AWAITING_REWARD`, `COMPLETED`, `FAILED`) | enum |
| Servicio de expedición | `ExpeditionService` | class |
| Encuentro | encounter (`startEncounter`) | — |
| Nivel | level | — |
| Recompensa | reward (`RewardDraw`, `offeredRewards`) | — |
| Sorteo de enemigos | `EnemyDraw` | class |
| Estadísticas de la partida | `RunStatistics` | record |
| Repositorio de expediciones | `ExpeditionRepository` / `InMemoryExpeditionRepository` | interface / class |
| Semilla | `seed` | field |
| Ronda | `round` | field |
| Cadena (de decoradores) | `chain` (`describeChain()`) | — |
| Vista previa | preview (`PreviewHandler`, `/api/preview`) | — |

## Métodos de `Combatant` / `EffectDecorator`

| Español | Código |
|---|---|
| vida actual | `currentHealth()` |
| modificar vida | `changeHealth(delta, effectiveMaxHealth)` |
| modificar daño saliente | `modifyOutgoingDamage(damage, ctx)` |
| recibir daño | `takeDamage(damage, ctx)` |
| al infligir daño | `onDamageDealt(result, ctx)` |
| puede actuar | `canAct(ctx)` |
| al iniciar turno | `onTurnStart(ctx)` |
| describir cadena | `describeChain()` |
| debe retirarse | `shouldBeRemoved()` |
| refrescar | `refresh(incoming)` |
| avanzar turno | `advanceTurn()` |
| recién aplicado | `justApplied` |
| copiar sobre | `copyOnto(newWrapped)` |
| aplicar / retirar / purgar / equipar | `apply` / `remove` / `purge` / `equip` |
| daño directo / curar | `directDamage` / `heal` |

## Efectos

| Español (label UI) | id | Clase |
|---|---|---|
| Envenenado | `poison` | `PoisonDecorator` |
| Regeneración | `regeneration` | `RegenerationDecorator` |
| Escudo | `shield` | `ShieldDecorator` |
| Espinas | `thorns` | `ThornsDecorator` |
| Furia | `rage` | `RageDecorator` |
| En guardia | `guard` | `GuardDecorator` |
| Congelado | `frozen` | `FrozenDecorator` |
| Vampirismo | `lifesteal` | `LifestealDecorator` |
| Silencio (operación, no decorador) | `silence` | `EffectManager.purge` |

## Equipo

| Español (name UI) | id | Clase |
|---|---|---|
| Espada | `sword` | `SwordDecorator` |
| Hacha de guerra | `war_axe` | `WarAxeDecorator` |
| Bastón rúnico | `rune_staff` | `RuneStaffDecorator` |
| Armadura de cuero | `leather_armor` | `LeatherArmorDecorator` |
| Armadura de dragón | `dragon_armor` | `DragonArmorDecorator` |
| Anillo de fuego | `fire_ring` | `FireRingDecorator` |
| Amuleto de vida | `life_amulet` | `LifeAmuletDecorator` |
| Botas de viento | `wind_boots` | `WindBootsDecorator` |

## Clases de héroe y enemigos

| Español | id |
|---|---|
| Guerrero / Mago / Arquero | `warrior` / `mage` / `archer` |
| Goblin / Lobo / Slime | `goblin` / `wolf` / `slime` |
| Esqueleto / Orco chamán | `skeleton` / `orc_shaman` |
| Golem de piedra / Bruja | `stone_golem` / `witch` |
| Dragón | `dragon` |

Los ids de las habilidades están en design §4.7 y §4.8.

## Frontend

| Español | Código |
|---|---|
| Selección de clase / Preparación / Mapa / Arena / Recompensa / Resumen | `ClassSelectScreen` / `LoadoutScreen` / `MapScreen` / `ArenaScreen` / `RewardScreen` / `SummaryScreen` |
| Inspector de cadena | `ChainInspector` |
| Panel de acciones | `ActionPanel` |
| Log de combate | `CombatLog` |
| Reproductor de eventos | `eventPlayer` |
| Store del juego | `gameStore` |

package com.rpgdecorator.engine.effects;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.effects.ShieldDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.CombatEvent.EffectApplied;
import com.rpgdecorator.domain.event.CombatEvent.EffectRefreshed;
import com.rpgdecorator.domain.event.CombatEvent.EffectRemoved;
import com.rpgdecorator.domain.event.RemovalReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectManagerTest {

    private static final String HERO = "hero";

    private EffectManager manager;
    private RecordingTurnContext ctx;
    private BaseCharacter base;

    @BeforeEach
    void setUp() {
        manager = new EffectManager();
        ctx = new RecordingTurnContext();
        base = new BaseCharacter(HERO, "Guerrero", Side.HERO, new Stats(120, 14, 8, 4, 10));
    }

    // ---------------------------------------------------------------- helpers

    /** Effect ids of the chain, outer -> inner, base excluded. */
    private List<String> effectIds(Combatant outer) {
        return manager.layers(outer).stream()
                .filter(l -> !l.isBase())
                .map(Layer::id)
                .toList();
    }

    private Layer layerOf(Combatant outer, String id) {
        return manager.layers(outer).stream()
                .filter(l -> l.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no layer " + id + " in " + outer.describeChain()));
    }

    /** Follows wrapped() down to the innermost combatant (test-only, structure is known). */
    private static Combatant innermost(Combatant outer) {
        Combatant current = outer;
        for (int depth = 0; depth < 100; depth++) {
            if (current == null || current.getClass() == BaseCharacter.class) {
                return current;
            }
            current = ((EffectDecorator) current).wrapped();
        }
        throw new AssertionError("chain too deep");
    }

    // ---------------------------------------------------------------- remove (ADR-002)


    @Test
    void removingMiddleLayerKeepsOuterTurnsAndSameBaseInstance() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "poison", false, ctx);
        outer = manager.apply(outer, "rage", true, ctx);
        // Two full turns: rage loses its justApplied flag and then one turn -> 1 remaining.
        outer = manager.advanceTurn(outer, ctx);
        outer = manager.advanceTurn(outer, ctx);
        assertEquals("Furia(Envenenado(Espada(Guerrero)))", outer.describeChain());
        assertEquals(1, layerOf(outer, "rage").turnsRemaining());
        base.changeHealth(-30, 120);
        ctx.clear();

        Combatant result = manager.remove(outer, d -> d.effectId().equals("poison"), RemovalReason.INTERACTION, ctx);

        assertEquals("Furia(Espada(Guerrero))", result.describeChain());
        assertEquals(List.of("rage", "sword"), effectIds(result));
        assertEquals(1, layerOf(result, "rage").turnsRemaining());
        assertSame(base, innermost(result));
        assertEquals(90, base.currentHealth());
        assertEquals(90, result.currentHealth());
        assertEquals(List.of(new EffectRemoved(HERO, "poison", RemovalReason.INTERACTION)), ctx.events());
    }

    @Test
    void removedMiddleLayerKeepsRemainingTurnsAcrossFollowingTurns() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "poison", false, ctx);
        outer = manager.apply(outer, "rage", true, ctx);
        outer = manager.advanceTurn(outer, ctx);
        outer = manager.advanceTurn(outer, ctx);

        outer = manager.remove(outer, d -> d.effectId().equals("poison"), RemovalReason.INTERACTION, ctx);
        ctx.clear();
        // Rage had 1 turn and justApplied already cleared: it must expire at the next end of turn.
        outer = manager.advanceTurn(outer, ctx);

        assertFalse(manager.hasEffect(outer, "rage"));
        assertEquals("Espada(Guerrero)", outer.describeChain());
        assertEquals(List.of(new EffectRemoved(HERO, "rage", RemovalReason.EXPIRED)), ctx.events());
    }

    @Test
    void removingSeveralLayersEmitsOneEventPerLayerOuterToInner() {
        Combatant outer = manager.apply(base, "poison", ctx);
        outer = manager.apply(outer, "shield", ctx);
        outer = manager.apply(outer, "rage", ctx);
        ctx.clear();

        Combatant result = manager.remove(outer, d -> d.category() == Category.BUFF, RemovalReason.PURGED, ctx);

        assertEquals(List.of("poison"), effectIds(result));
        assertEquals(List.of(
                new EffectRemoved(HERO, "rage", RemovalReason.PURGED),
                new EffectRemoved(HERO, "shield", RemovalReason.PURGED)), ctx.events());
        assertSame(base, innermost(result));
    }

    @Test
    void removingOuterLayerKeepsInnerLayersState() {
        Combatant outer = manager.apply(base, "shield", ctx);
        outer.takeDamage(new Damage(5, 0, "enemy", false, true), ctx);
        outer = manager.apply(outer, "rage", ctx);

        Combatant result = manager.remove(outer, d -> d.effectId().equals("rage"), RemovalReason.EXPIRED, ctx);

        assertEquals(15, ((ShieldDecorator) result).absorption());
        assertSame(base, innermost(result));
    }

    @Test
    void removeWithNoMatchReturnsSameReferenceAndEmitsNothing() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "rage", ctx);
        ctx.clear();

        Combatant result = manager.remove(outer, d -> d.effectId().equals("frozen"), RemovalReason.EXPIRED, ctx);

        assertSame(outer, result);
        assertTrue(ctx.events().isEmpty());
    }

    @Test
    void removingEveryLayerReturnsTheBaseItself() {
        Combatant outer = manager.apply(base, "rage", ctx);
        outer = manager.apply(outer, "poison", ctx);

        Combatant result = manager.remove(outer, d -> true, RemovalReason.PURGED, ctx);

        assertSame(base, result);
    }

    // ---------------------------------------------------------------- apply / refresh (RF-10, RF-16)


    @Test
    void applyWrapsTheOutermostLayerAndEmitsEffectApplied() {
        Combatant outer = manager.equip(base, List.of("sword"));

        Combatant result = manager.apply(outer, "rage", ctx);

        assertEquals("Furia(Espada(Guerrero))", result.describeChain());
        assertEquals(30, result.stats().attack());
        assertEquals(HERO, result.id());
        assertEquals(List.of(new EffectApplied(HERO, "rage", 2)), ctx.events());
    }

    @Test
    void applyingTwiceKeepsASingleLayerAndEmitsEffectRefreshed() {
        Combatant outer = manager.apply(base, "poison", false, ctx);
        Combatant again = manager.apply(outer, "poison", false, ctx);

        assertEquals(List.of("poison"), effectIds(again));
        assertEquals("Envenenado(Guerrero)", again.describeChain());
        assertEquals(List.of(
                new EffectApplied(HERO, "poison", 3),
                new EffectRefreshed(HERO, "poison", 3)), ctx.events());
    }

    @Test
    void refreshResetsDurationToTheNewEffectsValue() {
        Combatant outer = manager.apply(base, "poison", false, ctx);
        outer = manager.advanceTurn(outer, ctx);
        outer = manager.advanceTurn(outer, ctx);
        assertEquals(1, layerOf(outer, "poison").turnsRemaining());
        ctx.clear();

        outer = manager.apply(outer, "poison", false, ctx);

        assertEquals(3, layerOf(outer, "poison").turnsRemaining());
        assertEquals(List.of(new EffectRefreshed(HERO, "poison", 3)), ctx.events());
    }

    @Test
    void refreshingAnInnerLayerDoesNotChangeTheChainStructure() {
        Combatant outer = manager.apply(base, "poison", ctx);
        outer = manager.apply(outer, "rage", ctx);
        String before = outer.describeChain();

        Combatant result = manager.apply(outer, "poison", ctx);

        assertEquals(before, result.describeChain());
        assertEquals(List.of("rage", "poison"), effectIds(result));
    }

    @Test
    void shieldRefreshAddsAbsorptionUpToForty() {
        Combatant outer = manager.apply(base, "shield", ctx);
        assertEquals(20, ((ShieldDecorator) outer).absorption());

        outer = manager.apply(outer, "shield", ctx);
        assertEquals(40, ((ShieldDecorator) outer).absorption());

        outer = manager.apply(outer, "shield", ctx);
        assertEquals(40, ((ShieldDecorator) outer).absorption());

        assertEquals(List.of("shield"), effectIds(outer));
        assertEquals(List.of(
                new EffectApplied(HERO, "shield", 3),
                new EffectRefreshed(HERO, "shield", 3),
                new EffectRefreshed(HERO, "shield", 3)), ctx.events());
    }

    @Test
    void shieldRefreshAfterPartialAbsorptionAddsTwenty() {
        Combatant outer = manager.apply(base, "shield", ctx);
        outer.takeDamage(new Damage(15, 0, "enemy", false, true), ctx);
        assertEquals(5, ((ShieldDecorator) outer).absorption());

        outer = manager.apply(outer, "shield", ctx);

        assertEquals(25, ((ShieldDecorator) outer).absorption());
    }

    @Test
    void unknownEffectIdThrowsAndLeavesChainUntouched() {
        Combatant outer = manager.apply(base, "rage", ctx);
        ctx.clear();

        assertThrows(IllegalArgumentException.class, () -> manager.apply(outer, "does_not_exist", ctx));
        assertEquals("Furia(Guerrero)", outer.describeChain());
        assertTrue(ctx.events().isEmpty());
    }

    @Test
    void unknownEffectIdIsRejectedBeforeEvaluatingInteractionRules() {
        InteractionRules rules = new InteractionRules(Map.of("bogus", Set.of("rage")));
        EffectManager custom = new EffectManager(rules);
        Combatant outer = custom.apply(base, "rage", ctx);
        ctx.clear();

        assertThrows(IllegalArgumentException.class, () -> custom.apply(outer, "bogus", ctx));
        assertTrue(custom.hasEffect(outer, "rage"));
        assertTrue(ctx.events().isEmpty());
    }

    @Test
    void equipmentIdCannotBeAppliedAsEffect() {
        assertThrows(IllegalArgumentException.class, () -> manager.apply(base, "sword", ctx));
        assertTrue(ctx.events().isEmpty());
    }

    // ---------------------------------------------------------------- interaction rules (design 4.4, RF-17)


    @Test
    void applyingFrozenRemovesRageWithReasonInteraction() {
        Combatant outer = manager.apply(base, "rage", ctx);
        ctx.clear();

        Combatant result = manager.apply(outer, "frozen", false, ctx);

        assertEquals("Congelado(Guerrero)", result.describeChain());
        assertFalse(manager.hasEffect(result, "rage"));
        assertFalse(result.canAct(ctx));
        assertEquals(List.of(
                new EffectRemoved(HERO, "rage", RemovalReason.INTERACTION),
                new EffectApplied(HERO, "frozen", 1)), ctx.events());
    }

    @Test
    void applyingRageRemovesGuardWithReasonInteraction() {
        Combatant outer = manager.apply(base, "guard", ctx);
        ctx.clear();

        Combatant result = manager.apply(outer, "rage", ctx);

        assertEquals("Furia(Guerrero)", result.describeChain());
        assertFalse(manager.hasEffect(result, "guard"));
        assertEquals(List.of(
                new EffectRemoved(HERO, "guard", RemovalReason.INTERACTION),
                new EffectApplied(HERO, "rage", 2)), ctx.events());
    }

    @Test
    void applyingPoisonRemovesRegenerationWithReasonInteraction() {
        Combatant outer = manager.apply(base, "regeneration", ctx);
        ctx.clear();

        Combatant result = manager.apply(outer, "poison", false, ctx);

        assertEquals("Envenenado(Guerrero)", result.describeChain());
        assertFalse(manager.hasEffect(result, "regeneration"));
        assertEquals(List.of(
                new EffectRemoved(HERO, "regeneration", RemovalReason.INTERACTION),
                new EffectApplied(HERO, "poison", 3)), ctx.events());
    }

    @Test
    void applyingRegenerationRemovesPoisonWithReasonInteraction() {
        Combatant outer = manager.apply(base, "poison", false, ctx);
        ctx.clear();

        Combatant result = manager.apply(outer, "regeneration", ctx);

        assertEquals("Regeneración(Guerrero)", result.describeChain());
        assertFalse(manager.hasEffect(result, "poison"));
        assertEquals(List.of(
                new EffectRemoved(HERO, "poison", RemovalReason.INTERACTION),
                new EffectApplied(HERO, "regeneration", 3)), ctx.events());
    }

    @Test
    void interactionRemovesLayerFromTheMiddleAndKeepsTheRest() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "rage", ctx);
        outer = manager.apply(outer, "shield", ctx);
        ctx.clear();

        Combatant result = manager.apply(outer, "frozen", false, ctx);

        assertEquals("Congelado(Escudo(Espada(Guerrero)))", result.describeChain());
        assertSame(base, innermost(result));
        assertEquals(List.of(
                new EffectRemoved(HERO, "rage", RemovalReason.INTERACTION),
                new EffectApplied(HERO, "frozen", 1)), ctx.events());
    }

    @Test
    void applyingWithoutConflictingEffectOnlyEmitsApplied() {
        Combatant result = manager.apply(base, "frozen", false, ctx);

        assertEquals("Congelado(Guerrero)", result.describeChain());
        assertEquals(List.of(new EffectApplied(HERO, "frozen", 1)), ctx.events());
    }

    @Test
    void customRulesTableChangesBehaviourWithoutTouchingTheEngine() {
        EffectManager custom = new EffectManager(new InteractionRules(Map.of("thorns", Set.of("shield"))));
        Combatant outer = custom.apply(base, "shield", ctx);
        ctx.clear();

        Combatant result = custom.apply(outer, "thorns", ctx);

        assertEquals("Espinas(Guerrero)", result.describeChain());
        assertEquals(List.of(
                new EffectRemoved(HERO, "shield", RemovalReason.INTERACTION),
                new EffectApplied(HERO, "thorns", 3)), ctx.events());
    }

    // ---------------------------------------------------------------- purge (RF-18)


    @Test
    void purgeLeavesOnlyEquipmentInOrderWithReasonPurged() {
        Combatant outer = manager.equip(base, List.of("sword", "leather_armor"));
        outer = manager.apply(outer, "poison", false, ctx);
        outer = manager.apply(outer, "rage", ctx);
        outer = manager.apply(outer, "frozen", false, ctx); // removes rage by interaction
        outer = manager.apply(outer, "shield", ctx);
        ctx.clear();

        Combatant result = manager.purge(outer, ctx);

        assertEquals("Armadura de cuero(Espada(Guerrero))", result.describeChain());
        assertEquals(List.of("leather_armor", "sword"), effectIds(result));
        assertSame(base, innermost(result));
        assertEquals(List.of(
                new EffectRemoved(HERO, "shield", RemovalReason.PURGED),
                new EffectRemoved(HERO, "frozen", RemovalReason.PURGED),
                new EffectRemoved(HERO, "poison", RemovalReason.PURGED)), ctx.events());
    }

    @Test
    void purgeWithoutTemporaryEffectsReturnsSameReferenceAndEmitsNothing() {
        Combatant outer = manager.equip(base, List.of("sword"));

        Combatant result = manager.purge(outer, ctx);

        assertSame(outer, result);
        assertTrue(ctx.events().isEmpty());
    }

    @Test
    void purgeOnUnequippedCharacterReturnsTheBase() {
        Combatant outer = manager.apply(base, "rage", ctx);

        assertSame(base, manager.purge(outer, ctx));
    }

    // ---------------------------------------------------------------- advanceTurn (design 4.5)


    @Test
    void poisonAppliedByOpponentLastsThreeTurnsAndThenExpires() {
        Combatant outer = manager.apply(base, "poison", false, ctx);
        ctx.clear();

        outer = manager.advanceTurn(outer, ctx); // end of hero's turn 1
        assertTrue(manager.hasEffect(outer, "poison"));
        assertEquals(2, layerOf(outer, "poison").turnsRemaining());

        outer = manager.advanceTurn(outer, ctx); // end of turn 2
        assertTrue(manager.hasEffect(outer, "poison"));
        assertEquals(1, layerOf(outer, "poison").turnsRemaining());
        assertTrue(ctx.events().isEmpty());

        outer = manager.advanceTurn(outer, ctx); // end of turn 3
        assertFalse(manager.hasEffect(outer, "poison"));
        assertSame(base, outer);
        assertEquals(List.of(new EffectRemoved(HERO, "poison", RemovalReason.EXPIRED)), ctx.events());
    }

    @Test
    void selfAppliedEffectIsNotDecrementedOnTheTurnItWasApplied() {
        Combatant outer = manager.apply(base, "rage", ctx); // during its own turn
        ctx.clear();

        outer = manager.advanceTurn(outer, ctx); // end of the turn it was applied
        assertEquals(2, layerOf(outer, "rage").turnsRemaining());

        outer = manager.advanceTurn(outer, ctx); // first full turn
        assertEquals(1, layerOf(outer, "rage").turnsRemaining());
        assertTrue(ctx.events().isEmpty());

        outer = manager.advanceTurn(outer, ctx); // second full turn
        assertFalse(manager.hasEffect(outer, "rage"));
        assertEquals(List.of(new EffectRemoved(HERO, "rage", RemovalReason.EXPIRED)), ctx.events());
    }

    @Test
    void applyWithoutFlagIsTheSameAsDuringOwnTurn() {
        Combatant viaDefault = manager.apply(base, "guard", ctx);
        viaDefault = manager.advanceTurn(viaDefault, ctx);
        assertTrue(manager.hasEffect(viaDefault, "guard"));

        BaseCharacter other = new BaseCharacter("other", "Otro", Side.HERO, new Stats(100, 10, 5, 3, 5));
        Combatant viaFlag = manager.apply(other, "guard", true, ctx);
        viaFlag = manager.advanceTurn(viaFlag, ctx);
        assertTrue(manager.hasEffect(viaFlag, "guard"));
    }

    @Test
    void shieldWithZeroAbsorptionIsRemovedAsDepleted() {
        Combatant outer = manager.apply(base, "shield", ctx);
        outer.takeDamage(new Damage(25, 0, "enemy", false, true), ctx);
        assertEquals(0, ((ShieldDecorator) outer).absorption());
        ctx.clear();

        outer = manager.advanceTurn(outer, ctx);

        assertFalse(manager.hasEffect(outer, "shield"));
        assertEquals(List.of(new EffectRemoved(HERO, "shield", RemovalReason.DEPLETED)), ctx.events());
        assertEquals(115, base.currentHealth());
    }

    @Test
    void advanceTurnNeverRemovesEquipment() {
        Combatant outer = manager.equip(base, List.of("sword", "leather_armor"));
        for (int turn = 0; turn < 10; turn++) {
            outer = manager.advanceTurn(outer, ctx);
        }

        assertEquals("Armadura de cuero(Espada(Guerrero))", outer.describeChain());
        assertNull(layerOf(outer, "sword").turnsRemaining());
        assertTrue(ctx.events().isEmpty());
    }

    @Test
    void expiringMiddleLayerKeepsOuterLayersAndBase() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "guard", ctx);  // 1 turn, self
        outer = manager.apply(outer, "shield", ctx); // 3 turns, self
        ctx.clear();

        outer = manager.advanceTurn(outer, ctx); // clears justApplied
        outer = manager.advanceTurn(outer, ctx); // guard 1 -> 0

        assertEquals("Escudo(Espada(Guerrero))", outer.describeChain());
        assertEquals(2, layerOf(outer, "shield").turnsRemaining());
        assertSame(base, innermost(outer));
        assertEquals(List.of(new EffectRemoved(HERO, "guard", RemovalReason.EXPIRED)), ctx.events());
    }

    // ---------------------------------------------------------------- equip (invariant design 1)


    @Test
    void equipWrapsInIterationOrderFirstIdInnermost() {
        Combatant outer = manager.equip(base, List.of("sword", "leather_armor"));

        assertEquals("Armadura de cuero(Espada(Guerrero))", outer.describeChain());
        assertEquals(20, outer.stats().attack());
        assertEquals(12, outer.stats().defense());
        assertSame(base, innermost(outer));
    }

    @Test
    void effectsAppliedAfterEquipAreOutsideTheEquipment() {
        Combatant outer = manager.equip(base, List.of("sword", "leather_armor"));
        outer = manager.apply(outer, "rage", ctx);

        List<Layer> layers = manager.layers(outer);
        assertEquals("rage", layers.get(0).id());
        assertEquals(0, layers.get(0).position());
        assertEquals("Furia(Armadura de cuero(Espada(Guerrero)))", outer.describeChain());
        assertEquals(30, outer.stats().attack());
    }

    @Test
    void equipWithNoItemsReturnsTheBaseItself() {
        assertSame(base, manager.equip(base, List.of()));
    }

    @Test
    void equipEmitsNoEvents() {
        manager.equip(base, List.of("sword", "life_amulet"));
        assertTrue(ctx.events().isEmpty());
    }

    @Test
    void equipUnknownItemThrows() {
        assertThrows(IllegalArgumentException.class, () -> manager.equip(base, List.of("does_not_exist")));
    }

    // ---------------------------------------------------------------- hasEffect (design 3.3)


    @Test
    void hasEffectFindsLayersAtAnyDepth() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "poison", false, ctx);
        outer = manager.apply(outer, "rage", ctx);

        assertTrue(manager.hasEffect(outer, "rage"));
        assertTrue(manager.hasEffect(outer, "poison"));
        assertTrue(manager.hasEffect(outer, "sword"));
        assertFalse(manager.hasEffect(outer, "frozen"));
    }

    @Test
    void bareBaseHasNoEffects() {
        assertFalse(manager.hasEffect(base, "rage"));
    }

    // ---------------------------------------------------------------- layers (inspector)


    @Test
    void layersReturnEffectiveStatsPerLayerOuterToInner() {
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "rage", ctx);

        List<Layer> layers = manager.layers(outer);

        assertEquals(3, layers.size());

        Layer rage = layers.get(0);
        assertEquals(0, rage.position());
        assertEquals("rage", rage.id());
        assertEquals("Furia", rage.label());
        assertEquals(Category.BUFF, rage.category());
        assertEquals(2, rage.turnsRemaining());
        assertFalse(rage.isBase());
        assertEquals(30, rage.statsAtLayer().attack());
        assertEquals(5, rage.statsAtLayer().defense());

        Layer sword = layers.get(1);
        assertEquals(1, sword.position());
        assertEquals("sword", sword.id());
        assertEquals("Espada", sword.label());
        assertEquals(Category.EQUIPMENT, sword.category());
        assertNull(sword.turnsRemaining());
        assertFalse(sword.isBase());
        assertEquals(20, sword.statsAtLayer().attack());

        Layer baseLayer = layers.get(2);
        assertEquals(2, baseLayer.position());
        assertEquals(HERO, baseLayer.id());
        assertEquals("Guerrero", baseLayer.label());
        assertNull(baseLayer.category());
        assertNull(baseLayer.turnsRemaining());
        assertTrue(baseLayer.isBase());
        assertEquals(new Stats(120, 14, 8, 4, 10), baseLayer.statsAtLayer());
    }

    @Test
    void layersShowThatOrderMatters() {
        // Sword(Rage(base)) cannot be built through the manager (invariant), so this checks Rage(Sword(base)).
        Combatant outer = manager.equip(base, List.of("sword"));
        outer = manager.apply(outer, "rage", ctx);

        List<Integer> attacks = manager.layers(outer).stream()
                .map(l -> l.statsAtLayer().attack())
                .toList();

        assertEquals(List.of(30, 20, 14), attacks);
    }

    @Test
    void layersOfBareBaseIsOnlyTheBase() {
        List<Layer> layers = manager.layers(base);

        assertEquals(1, layers.size());
        assertTrue(layers.get(0).isBase());
        assertEquals(0, layers.get(0).position());
    }

    @Test
    void layersReportTurnsRemainingOfTemporaryEffects() {
        Combatant outer = manager.apply(base, "poison", false, ctx);
        outer = manager.advanceTurn(outer, ctx);

        assertEquals(2, manager.layers(outer).get(0).turnsRemaining());
    }

    // ---------------------------------------------------------------- identity (design 3.6)

    @Test
    void everyEventTargetsTheBaseIdRegardlessOfDepth() {
        Combatant outer = manager.equip(base, List.of("sword", "leather_armor"));
        outer = manager.apply(outer, "poison", false, ctx);
        outer = manager.apply(outer, "regeneration", ctx);
        outer = manager.apply(outer, "rage", ctx);
        outer = manager.purge(outer, ctx);

        for (CombatEvent event : ctx.events()) {
            String target = switch (event) {
                case EffectApplied e -> e.targetId();
                case EffectRefreshed e -> e.targetId();
                case EffectRemoved e -> e.targetId();
            };
            assertEquals(HERO, target);
        }
        assertFalse(ctx.events().isEmpty());
    }
}

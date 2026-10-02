package com.rpgdecorator.engine.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.FakeTurnContext;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectManagerTest {

    private final EffectManager manager = new EffectManager();
    private final FakeTurnContext ctx = new FakeTurnContext();
    private final BaseCharacter base = new BaseCharacter("hero", "Guerrero", Side.HERO, new Stats(120, 14, 8, 4, 10));

    private Combatant chain(List<String> equipment, String... effectIds) {
        Combatant outer = manager.equip(base, equipment);
        for (String effectId : effectIds) {
            outer = manager.apply(outer, effectId, ctx, false);
        }
        ctx.events.clear();
        return outer;
    }

    private static Combatant innermost(Combatant outer) {
        Combatant current = outer;
        while (current instanceof EffectDecorator decorator) {
            current = decorator.wrapped();
        }
        return current;
    }

    private Layer layer(Combatant outer, String id) {
        return manager.layers(outer).stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void removingFromTheMiddleRebuildsTheChainAroundTheSameBase() {
        Combatant outer = chain(List.of("sword"), "poison", "rage");
        assertEquals("Furia(Envenenado(Espada(Guerrero)))", outer.describeChain());
        base.changeHealth(-40, 120);

        Combatant rebuilt = manager.remove(outer, l -> l.effectId().equals("poison"), RemovalReason.EXPIRED, ctx);

        assertEquals("Furia(Espada(Guerrero))", rebuilt.describeChain());
        assertEquals(2, layer(rebuilt, "rage").turnsRemaining());
        assertSame(base, innermost(rebuilt));
        assertEquals(80, rebuilt.currentHealth());
        assertEquals(List.of(new CombatEvent.EffectRemoved("hero", "poison", "EXPIRED")), ctx.events);
    }

    @Test
    void removingNothingReturnsTheSameOuterReference() {
        Combatant outer = chain(List.of("sword"), "rage");

        assertSame(outer, manager.remove(outer, l -> l.effectId().equals("poison"), RemovalReason.EXPIRED, ctx));
        assertTrue(ctx.events.isEmpty());
    }

    @Test
    void applyingTwiceKeepsOneLayerAndEmitsRefreshed() {
        Combatant once = manager.apply(base, "poison", ctx, false);
        once = manager.advanceTurn(once, ctx);
        assertEquals(2, layer(once, "poison").turnsRemaining());

        Combatant twice = manager.apply(once, "poison", ctx, false);

        assertSame(once, twice);
        assertEquals("Envenenado(Guerrero)", twice.describeChain());
        assertEquals(3, layer(twice, "poison").turnsRemaining());
        assertEquals(List.of(
                new CombatEvent.EffectApplied("hero", "poison", 3),
                new CombatEvent.EffectRefreshed("hero", "poison", 3)), ctx.events);
    }

    @Test
    void reapplyingShieldAddsAbsorption() {
        Combatant outer = manager.apply(manager.apply(base, "shield", ctx), "shield", ctx);

        assertEquals("Escudo(Guerrero)", outer.describeChain());
        assertEquals(Map.of("absorption", 40), layer(outer, "shield").extra());
    }

    @Test
    void newEffectsGoOnTheOutside() {
        Combatant outer = chain(List.of("sword"), "poison", "rage", "thorns");

        assertEquals("Espinas(Furia(Envenenado(Espada(Guerrero))))", outer.describeChain());
    }

    @Test
    void applyingFrozenRemovesRage() {
        Combatant outer = manager.apply(chain(List.of("sword"), "rage"), "frozen", ctx, false);

        assertEquals("Congelado(Espada(Guerrero))", outer.describeChain());
        assertEquals(List.of(
                new CombatEvent.EffectRemoved("hero", "rage", "INTERACTION"),
                new CombatEvent.EffectApplied("hero", "frozen", 1)), ctx.events);
    }

    @Test
    void applyingRageRemovesGuard() {
        Combatant outer = manager.apply(chain(List.of(), "guard"), "rage", ctx);

        assertEquals("Furia(Guerrero)", outer.describeChain());
    }

    @Test
    void applyingPoisonRemovesRegeneration() {
        Combatant outer = manager.apply(chain(List.of(), "regeneration"), "poison", ctx, false);

        assertEquals("Envenenado(Guerrero)", outer.describeChain());
    }

    @Test
    void applyingRegenerationRemovesPoison() {
        Combatant outer = manager.apply(chain(List.of(), "poison"), "regeneration", ctx);

        assertEquals("Regeneración(Guerrero)", outer.describeChain());
    }

    @Test
    void rulesAreDataSoATableWithoutThemRemovesNothing() {
        EffectManager lenient = new EffectManager(new InteractionRules(Map.of()));

        Combatant outer = lenient.apply(lenient.apply(base, "rage", ctx), "frozen", ctx, false);

        assertEquals("Congelado(Furia(Guerrero))", outer.describeChain());
    }

    @Test
    void purgeLeavesOnlyTheEquipment() {
        Combatant outer = chain(List.of("dragon_armor"), "poison", "rage");

        Combatant purged = manager.purge(outer, ctx);

        assertEquals("Armadura de dragón(Guerrero)", purged.describeChain());
        assertSame(base, innermost(purged));
        assertEquals(List.of(
                new CombatEvent.EffectRemoved("hero", "rage", "PURGED"),
                new CombatEvent.EffectRemoved("hero", "poison", "PURGED")), ctx.events);
    }

    @Test
    void poisonFromTheOpponentLastsThreeTurnsOfTheAffected() {
        Combatant outer = manager.apply(base, "poison", ctx, false);

        outer = manager.advanceTurn(outer, ctx);
        assertEquals(2, layer(outer, "poison").turnsRemaining());
        outer = manager.advanceTurn(outer, ctx);
        assertEquals(1, layer(outer, "poison").turnsRemaining());
        outer = manager.advanceTurn(outer, ctx);

        assertFalse(manager.hasEffect(outer, "poison"));
        assertSame(base, outer);
        assertEquals(new CombatEvent.EffectRemoved("hero", "poison", "EXPIRED"), ctx.events.getLast());
    }

    @Test
    void effectAppliedDuringOwnTurnDoesNotCountThatTurn() {
        Combatant outer = manager.apply(base, "rage", ctx);

        outer = manager.advanceTurn(outer, ctx);
        assertEquals(2, layer(outer, "rage").turnsRemaining());
        outer = manager.advanceTurn(outer, ctx);
        assertEquals(1, layer(outer, "rage").turnsRemaining());
        outer = manager.advanceTurn(outer, ctx);

        assertFalse(manager.hasEffect(outer, "rage"));
    }

    @Test
    void depletedShieldIsRemovedWithItsOwnReason() {
        Combatant outer = manager.apply(base, "shield", ctx);
        outer.takeDamage(new Damage(30, 0, "enemy", false, true), ctx);
        ctx.events.clear();

        Combatant after = manager.advanceTurn(outer, ctx);

        assertSame(base, after);
        assertEquals(List.of(new CombatEvent.EffectRemoved("hero", "shield", "DEPLETED")), ctx.events);
    }

    @Test
    void equipmentNeverExpires() {
        Combatant outer = chain(List.of("sword", "leather_armor"));
        for (int turn = 0; turn < 30; turn++) {
            outer = manager.advanceTurn(outer, ctx);
        }

        assertEquals("Armadura de cuero(Espada(Guerrero))", outer.describeChain());
    }

    @Test
    void layersReportTheEffectiveStatsAtEachLayer() {
        Combatant outer = chain(List.of("sword"), "poison", "rage");

        List<Layer> layers = manager.layers(outer);

        assertEquals(List.of("rage", "poison", "sword", "hero"), layers.stream().map(Layer::id).toList());
        assertEquals(List.of(0, 1, 2, 3), layers.stream().map(Layer::position).toList());
        assertEquals(List.of(30, 20, 20, 14), layers.stream().map(l -> l.statsAtLayer().attack()).toList());
        assertEquals(List.of(5, 8, 8, 8), layers.stream().map(l -> l.statsAtLayer().defense()).toList());
        assertEquals(List.of(false, false, false, true), layers.stream().map(Layer::base).toList());
        assertEquals(List.of("Furia", "Envenenado", "Espada", "Guerrero"), layers.stream().map(Layer::label).toList());

        assertEquals(Category.BUFF, layers.get(0).category());
        assertEquals(2, layers.get(0).turnsRemaining());
        assertEquals(Category.EQUIPMENT, layers.get(2).category());
        assertNull(layers.get(2).turnsRemaining());
        assertNull(layers.get(3).category());
        assertNull(layers.get(3).turnsRemaining());
    }

    @Test
    void hasEffectLooksThroughTheWholeChain() {
        Combatant outer = chain(List.of("sword"), "frozen", "thorns");

        assertTrue(manager.hasEffect(outer, "frozen"));
        assertTrue(manager.hasEffect(outer, "sword"));
        assertFalse(manager.hasEffect(outer, "rage"));
    }

    @Test
    void equipKeepsEquipmentInsideEveryEffect() {
        Combatant outer = manager.equip(base, List.of("sword", "dragon_armor", "wind_boots"));
        assertEquals("Botas de viento(Armadura de dragón(Espada(Guerrero)))", outer.describeChain());

        outer = manager.apply(manager.apply(outer, "rage", ctx), "poison", ctx, false);

        List<Layer> layers = manager.layers(outer);
        int firstEquipment = layers.indexOf(layers.stream().filter(l -> l.category() == Category.EQUIPMENT)
                .findFirst().orElseThrow());
        assertTrue(layers.subList(0, firstEquipment).stream().allMatch(Layer::temporary));
        assertTrue(layers.subList(firstEquipment, layers.size() - 1).stream()
                .allMatch(l -> l.category() == Category.EQUIPMENT));
        assertSame(base, innermost(outer));
    }
}

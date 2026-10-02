package com.rpgdecorator.domain.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.FakeTurnContext;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.effects.RageDecorator;
import org.junit.jupiter.api.Test;

class EquipmentDecoratorsTest {

    private static final Stats WARRIOR = new Stats(120, 14, 8, 4, 10);

    private final FakeTurnContext ctx = new FakeTurnContext();

    private static BaseCharacter warrior() {
        return new BaseCharacter("hero", "Guerrero", Side.HERO, WARRIOR);
    }

    @Test
    void swordAddsSixAttack() {
        assertEquals(WARRIOR.withAttack(20), new SwordDecorator(warrior()).stats());
    }

    @Test
    void warAxeAddsTenAttackAndRemovesThreeSpeed() {
        assertEquals(WARRIOR.withAttack(24).withSpeed(1), new WarAxeDecorator(warrior()).stats());
    }

    @Test
    void runeStaffAddsThreeAttackAndFifteenCritChance() {
        assertEquals(WARRIOR.withAttack(17).withCritChance(25), new RuneStaffDecorator(warrior()).stats());
    }

    @Test
    void leatherArmorAddsFourDefense() {
        assertEquals(WARRIOR.withDefense(12), new LeatherArmorDecorator(warrior()).stats());
    }

    @Test
    void dragonArmorAddsTenDefenseAndSpeedNeverDropsBelowZero() {
        assertEquals(WARRIOR.withDefense(18).withSpeed(0), new DragonArmorDecorator(warrior()).stats());
    }

    @Test
    void fireRingAddsFourElementalToOutgoingDamageAndLeavesStatsAlone() {
        Combatant hero = new FireRingDecorator(warrior());

        Damage outgoing = hero.modifyOutgoingDamage(new Damage(14, 0, "hero", false, true), ctx);

        assertEquals(new Damage(14, 4, "hero", false, true), outgoing);
        assertEquals(WARRIOR, hero.stats());
    }

    @Test
    void lifeAmuletAddsTwentyFiveMaxHealth() {
        assertEquals(WARRIOR.withMaxHealth(145), new LifeAmuletDecorator(warrior()).stats());
    }

    @Test
    void windBootsAddFiveSpeed() {
        assertEquals(WARRIOR.withSpeed(9), new WindBootsDecorator(warrior()).stats());
    }

    @Test
    void equipmentIsPermanentAndDescribesItselfInTheChain() {
        EffectDecorator sword = new SwordDecorator(warrior());
        for (int turn = 0; turn < 20; turn++) {
            sword.advanceTurn();
        }

        assertEquals(Category.EQUIPMENT, sword.category());
        assertFalse(sword.shouldBeRemoved());
        assertEquals("Armadura de dragón(Espada(Guerrero))", new DragonArmorDecorator(sword).describeChain());
    }

    @Test
    void wrappingOrderChangesTheResult() {
        Combatant rageOutside = new RageDecorator(new SwordDecorator(warrior()));
        Combatant swordOutside = new SwordDecorator(new RageDecorator(warrior()));

        assertEquals(30, rageOutside.stats().attack());
        assertEquals(27, swordOutside.stats().attack());
    }
}

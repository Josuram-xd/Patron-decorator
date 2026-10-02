package com.rpgdecorator.engine.combat;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.catalog.EnemyCatalog;
import com.rpgdecorator.domain.catalog.EnemyDefinition;
import com.rpgdecorator.domain.catalog.HeroClass;
import com.rpgdecorator.domain.catalog.HeroClassCatalog;
import com.rpgdecorator.engine.effects.EffectManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-205: EnemyAI choices for every enemy of design 4.8. */
class EnemyAITest {

    private static final String ENEMY = BaseCharacter.ENEMY_ID;

    private final EffectManager effects = new EffectManager();
    private final EnemyAI ai = new EnemyAI(effects);

    // ------------------------------------------------------------------ helpers

    private Combat combat(String enemyId) {
        return combat(enemyId, List.of());
    }

    private Combat combat(String enemyId, List<String> heroEquipment) {
        HeroClass warrior = HeroClassCatalog.get("warrior");
        EnemyDefinition definition = EnemyCatalog.get(enemyId);
        Combatant hero = effects.equip(BaseCharacter.hero(warrior), heroEquipment);
        return new Combat(hero, BaseCharacter.enemy(definition), warrior.abilities(), definition);
    }

    /** Sets the enemy's current health to an absolute value. */
    private static void setEnemyHealth(Combat combat, int health) {
        Combatant enemy = combat.enemy();
        int max = enemy.stats().maxHealth();
        enemy.changeHealth(health - enemy.currentHealth(), max);
        assertEquals(health, enemy.currentHealth());
    }

    private void applyToHero(Combat combat, String... effectIds) {
        TurnContextImpl ctx = new TurnContextImpl(combat, new FixedRandom());
        for (String effectId : effectIds) {
            combat.replace(effects.apply(combat.hero(), effectId, ctx));
        }
    }

    /** Puts abilities on a cooldown that is NOT ready this turn (stored 2). */
    private static void onCooldown(Combat combat, String... abilityIds) {
        for (String abilityId : abilityIds) {
            combat.setCooldown(ENEMY, abilityId, 2);
        }
    }

    private Action decide(Combat combat) {
        return ai.decide(combat, new FixedRandom());
    }

    private static Action ability(String id) {
        return Action.useAbility(id);
    }

    // ------------------------------------------------------------------ goblin

    @Test
    void goblinUsesDirtyDaggerWhenTheChanceSucceeds() {
        FixedRandom random = new FixedRandom(true);
        assertEquals(ability("dirty_dagger"), ai.decide(combat("goblin"), random));
        assertEquals(List.of(50), random.requestedPercents);
    }

    @Test
    void goblinAttacksWhenTheChanceFails() {
        FixedRandom random = new FixedRandom(false);
        assertEquals(Action.attack(), ai.decide(combat("goblin"), random));
        assertEquals(List.of(50), random.requestedPercents);
    }

    @Test
    void goblinOnCooldownAttacksWithoutRollingTheChance() {
        Combat combat = combat("goblin");
        onCooldown(combat, "dirty_dagger");
        FixedRandom random = new FixedRandom(true);

        assertEquals(Action.attack(), ai.decide(combat, random));
        assertTrue(random.requestedPercents.isEmpty(), "no randomness consumed while on cooldown");
    }

    // ------------------------------------------------------------------ wolf

    @Test
    void wolfHowlsBelowSixtyPercentHealth() {
        Combat combat = combat("wolf");          // 60 HP: 35 < 36 (60 %)
        setEnemyHealth(combat, 35);
        assertEquals(ability("howl"), decide(combat));
    }

    @Test
    void wolfAttacksAtExactlySixtyPercentHealth() {
        Combat combat = combat("wolf");
        setEnemyHealth(combat, 36);
        assertEquals(Action.attack(), decide(combat));
    }

    @Test
    void wolfAttacksAtFullHealth() {
        assertEquals(Action.attack(), decide(combat("wolf")));
    }

    @Test
    void wolfDoesNotHowlWhileOnCooldownEvenIfHurt() {
        Combat combat = combat("wolf");
        setEnemyHealth(combat, 10);
        onCooldown(combat, "howl");
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ slime

    @Test
    void slimePrefersJellyShieldWhenBothAreReady() {
        assertEquals(ability("jelly_shield"), decide(combat("slime")));
    }

    @Test
    void slimeSpitsAcidWhenJellyShieldIsOnCooldown() {
        Combat combat = combat("slime");
        onCooldown(combat, "jelly_shield");
        assertEquals(ability("acid_spit"), decide(combat));
    }

    @Test
    void slimeAttacksWhenEverythingIsOnCooldown() {
        Combat combat = combat("slime");
        onCooldown(combat, "jelly_shield", "acid_spit");
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ skeleton

    @Test
    void skeletonReassemblesBelowHalfHealth() {
        Combat combat = combat("skeleton");      // 100 HP
        setEnemyHealth(combat, 49);
        assertEquals(ability("reassemble"), decide(combat));
    }

    @Test
    void skeletonUsesSharpBonesAtHalfHealthOrMore() {
        Combat combat = combat("skeleton");
        setEnemyHealth(combat, 50);
        assertEquals(ability("sharp_bones"), decide(combat));
    }

    @Test
    void skeletonFallsBackToSharpBonesWhenReassembleIsOnCooldown() {
        Combat combat = combat("skeleton");
        setEnemyHealth(combat, 20);
        onCooldown(combat, "reassemble");
        assertEquals(ability("sharp_bones"), decide(combat));
    }

    @Test
    void skeletonAttacksWhenNothingIsAvailable() {
        Combat combat = combat("skeleton");
        onCooldown(combat, "sharp_bones");       // full HP: reassemble's condition fails
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ orc_shaman

    @Test
    void orcCursesWhenTheHeroHasTwoBuffs() {
        Combat combat = combat("orc_shaman");
        applyToHero(combat, "rage", "shield");
        assertEquals(ability("curse"), decide(combat));
    }

    @Test
    void orcUsesBloodTotemWhenTheHeroHasOnlyOneBuff() {
        Combat combat = combat("orc_shaman");
        applyToHero(combat, "rage");
        assertEquals(ability("blood_totem"), decide(combat));
    }

    @Test
    void orcCountsOnlyBuffLayersNotDebuffsControlOrEquipment() {
        Combat combat = combat("orc_shaman", List.of("sword", "leather_armor"));
        applyToHero(combat, "rage", "poison", "frozen");
        assertEquals(ability("blood_totem"), decide(combat));
    }

    @Test
    void orcUsesBloodTotemWhenCurseIsOnCooldown() {
        Combat combat = combat("orc_shaman");
        applyToHero(combat, "rage", "shield", "thorns");
        onCooldown(combat, "curse");
        assertEquals(ability("blood_totem"), decide(combat));
    }

    @Test
    void orcAttacksWhenNothingIsAvailable() {
        Combat combat = combat("orc_shaman");
        onCooldown(combat, "blood_totem");       // hero has no buffs: curse's condition fails
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ stone_golem

    @Test
    void golemPrefersStoneSkin() {
        assertEquals(ability("stone_skin"), decide(combat("stone_golem")));
    }

    @Test
    void golemStompsWhenStoneSkinIsOnCooldown() {
        Combat combat = combat("stone_golem");
        onCooldown(combat, "stone_skin");
        assertEquals(ability("stomp"), decide(combat));
    }

    @Test
    void golemAttacksWhenEverythingIsOnCooldown() {
        Combat combat = combat("stone_golem");
        onCooldown(combat, "stone_skin", "stomp");
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ witch

    @Test
    void witchDrinksPotionBelowHalfHealth() {
        Combat combat = combat("witch");         // 90 HP: 44 < 45
        setEnemyHealth(combat, 44);
        assertEquals(ability("potion"), decide(combat));
    }

    @Test
    void witchCastsFrostHexAtHalfHealthOrMore() {
        Combat combat = combat("witch");
        setEnemyHealth(combat, 45);
        assertEquals(ability("frost_hex"), decide(combat));
    }

    @Test
    void witchSkipsPotionOnCooldownAndCastsFrostHex() {
        Combat combat = combat("witch");
        setEnemyHealth(combat, 10);
        onCooldown(combat, "potion");
        assertEquals(ability("frost_hex"), decide(combat));
    }

    @Test
    void witchCastsHexWhenFrostHexIsOnCooldown() {
        Combat combat = combat("witch");
        onCooldown(combat, "frost_hex");
        assertEquals(ability("hex"), decide(combat));
    }

    @Test
    void witchAttacksWhenEverythingIsOnCooldown() {
        Combat combat = combat("witch");
        setEnemyHealth(combat, 10);
        onCooldown(combat, "potion", "frost_hex", "hex");
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ dragon

    @Test
    void dragonRaisesScalesBelowFortyPercentHealth() {
        Combat combat = combat("dragon");        // 200 HP: 79 < 80
        setEnemyHealth(combat, 79);
        assertEquals(ability("scales"), decide(combat));
    }

    @Test
    void dragonBreathesFrostAtFortyPercentHealthOrMore() {
        Combat combat = combat("dragon");
        setEnemyHealth(combat, 80);
        assertEquals(ability("frost_breath"), decide(combat));
    }

    @Test
    void dragonRoarsWhenFrostBreathIsOnCooldown() {
        Combat combat = combat("dragon");
        onCooldown(combat, "frost_breath");
        assertEquals(ability("roar"), decide(combat));
    }

    @Test
    void dragonPriorityScalesBeforeFrostBreathBeforeRoarWhenAllAreAvailable() {
        Combat combat = combat("dragon");
        setEnemyHealth(combat, 1);
        assertEquals(ability("scales"), decide(combat));
        onCooldown(combat, "scales");
        assertEquals(ability("frost_breath"), decide(combat));
        onCooldown(combat, "frost_breath");
        assertEquals(ability("roar"), decide(combat));
    }

    @Test
    void dragonAttacksWhenEverythingIsOnCooldown() {
        Combat combat = combat("dragon");
        setEnemyHealth(combat, 1);
        onCooldown(combat, "scales", "frost_breath", "roar");
        assertEquals(Action.attack(), decide(combat));
    }

    // ------------------------------------------------------------------ cooldown rule and purity

    @Test
    void storedCooldownOfOneIsReadyBecauseTheTurnDecrementsItBeforeResolving() {
        Combat combat = combat("slime");
        combat.setCooldown(ENEMY, "jelly_shield", 1);
        assertEquals(ability("jelly_shield"), decide(combat));
        assertTrue(EnemyAI.isReady(combat, ENEMY, "jelly_shield"));
        combat.setCooldown(ENEMY, "jelly_shield", 2);
        assertEquals(ability("acid_spit"), decide(combat));
    }

    @Test
    void abilityWithCooldownThreeUsedOnTurnOneIsChosenAgainOnTurnFour() {
        // Simulates the engine: on each enemy turn, decide -> decrement -> resolve (sets cd).
        Combat combat = combat("stone_golem");
        onCooldown(combat, "stone_skin");        // keep stone_skin out of the way
        String[] chosen = new String[6];
        for (int turn = 1; turn <= 5; turn++) {
            combat.setCooldown(ENEMY, "stone_skin", 2);
            Action action = decide(combat);
            combat.decrementCooldowns(ENEMY);
            if (action instanceof Action.UseAbility use) {
                assertEquals(0, combat.cooldown(ENEMY, use.abilityId()), "0 when resolved");
                combat.setCooldown(ENEMY, use.abilityId(), combat.ability(ENEMY, use.abilityId()).cooldown());
            }
            chosen[turn] = action.abilityId();
        }
        // stomp has cd 5: used on turn 1, next on turn 6.
        assertEquals("stomp", chosen[1]);
        for (int turn = 2; turn <= 5; turn++) {
            assertEquals(null, chosen[turn], "turn " + turn);
        }

        Combat slime = combat("slime");
        onCooldown(slime, "jelly_shield");
        String[] spit = new String[5];
        for (int turn = 1; turn <= 4; turn++) {
            slime.setCooldown(ENEMY, "jelly_shield", 2);
            Action action = decide(slime);
            slime.decrementCooldowns(ENEMY);
            if (action instanceof Action.UseAbility use) {
                slime.setCooldown(ENEMY, use.abilityId(), slime.ability(ENEMY, use.abilityId()).cooldown());
            }
            spit[turn] = action.abilityId();
        }
        // acid_spit has cd 3: turn 1, then turn 4.
        assertEquals("acid_spit", spit[1]);
        assertEquals(null, spit[2]);
        assertEquals(null, spit[3]);
        assertEquals("acid_spit", spit[4]);
    }

    @Test
    void decideDoesNotMutateTheCombatNorConsumeRandomnessForDeterministicConditions() {
        Combat combat = combat("witch");
        applyToHero(combat, "rage");
        Combatant heroBefore = combat.hero();
        int eventsBefore = combat.lastSeq();
        Map<String, Integer> cooldownsBefore = combat.cooldowns(ENEMY);
        FixedRandom random = new FixedRandom();

        ai.decide(combat, random);

        assertTrue(random.requestedPercents.isEmpty());
        assertEquals(eventsBefore, combat.lastSeq());
        assertEquals(cooldownsBefore, combat.cooldowns(ENEMY));
        assertTrue(heroBefore == combat.hero());
    }

    @Test
    void everyEnemyOfTheCatalogHasAnAbilityChoiceTestedHere() {
        assertEquals(List.of("goblin", "wolf", "slime", "skeleton", "orc_shaman", "stone_golem", "witch", "dragon"),
                EnemyCatalog.all().stream().map(EnemyDefinition::id).toList());
    }
}

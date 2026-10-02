package com.rpgdecorator.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.RandomSource;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.catalog.EnemyDefinition.Situation;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CatalogsTest {

    private static final RandomSource ALWAYS = fixedChance(true);
    private static final RandomSource NEVER = fixedChance(false);

    private static RandomSource fixedChance(boolean outcome) {
        return new RandomSource() {
            @Override
            public int nextInt(int minInclusive, int maxInclusive) {
                return minInclusive;
            }

            @Override
            public boolean chance(int percent) {
                return outcome;
            }
        };
    }

    private static BaseCharacter dummy() {
        return new BaseCharacter("hero", "Guerrero", Side.HERO, new Stats(120, 14, 8, 4, 10));
    }

    private static String row(Object... cells) {
        return String.join("|", Stream.of(cells).map(String::valueOf).toList());
    }

    private static String abilityRow(Ability a) {
        return row(a.id(), a.name(), "cd" + a.cooldown(), "x" + a.damageMultiplier(),
                a.effectsOn(Target.SELF), a.effectsOn(Target.OPPONENT), a.purgesOpponent());
    }

    private static String statsRow(Stats s) {
        return row(s.maxHealth(), s.attack(), s.defense(), s.speed(), s.critChance());
    }

    @Test
    void effectCatalogMatchesTheDesignTable() {
        List<String> rows = EffectCatalog.all().stream()
                .map(e -> row(e.id(), e.label(), e.category(), e.baseDuration()))
                .toList();

        assertEquals(List.of(
                "poison|Envenenado|DEBUFF|3",
                "regeneration|Regeneración|BUFF|3",
                "shield|Escudo|BUFF|3",
                "thorns|Espinas|BUFF|3",
                "rage|Furia|BUFF|2",
                "guard|En guardia|BUFF|1",
                "frozen|Congelado|CONTROL|1",
                "lifesteal|Vampirismo|BUFF|3"), rows);
    }

    @Test
    void effectFactoriesBuildDecoratorsThatAgreeWithTheirCatalogEntry() {
        for (EffectCatalog.Entry entry : EffectCatalog.all()) {
            EffectDecorator effect = EffectCatalog.create(entry.id(), dummy());

            assertEquals(entry.id(), effect.effectId());
            assertEquals(entry.label(), effect.label());
            assertEquals(entry.category(), effect.category());
            assertEquals(entry.baseDuration(), effect.duration().turns());
        }
    }

    @Test
    void equipmentCatalogMatchesTheDesignTable() {
        List<String> rows = EquipmentCatalog.all().stream()
                .map(i -> row(i.id(), i.name(), i.slot(), statsRow(i.factory().apply(dummy()).stats())))
                .toList();

        assertEquals(List.of(
                "sword|Espada|WEAPON|120|20|8|4|10",
                "war_axe|Hacha de guerra|WEAPON|120|24|8|1|10",
                "rune_staff|Bastón rúnico|WEAPON|120|17|8|4|25",
                "leather_armor|Armadura de cuero|ARMOR|120|14|12|4|10",
                "dragon_armor|Armadura de dragón|ARMOR|120|14|18|0|10",
                "fire_ring|Anillo de fuego|ACCESSORY|120|14|8|4|10",
                "life_amulet|Amuleto de vida|ACCESSORY|145|14|8|4|10",
                "wind_boots|Botas de viento|ACCESSORY|120|14|8|9|10"), rows);
    }

    @Test
    void equipmentFactoriesBuildPermanentEquipmentLayers() {
        for (EquipmentCatalog.Item item : EquipmentCatalog.all()) {
            EffectDecorator layer = EquipmentCatalog.create(item.id(), dummy());

            assertEquals(item.id(), layer.effectId());
            assertEquals(item.name(), layer.label());
            assertEquals(Category.EQUIPMENT, layer.category());
            assertTrue(layer.duration().isPermanent());
        }
    }

    @Test
    void heroClassCatalogMatchesTheDesignTable() {
        List<String> rows = HeroClassCatalog.all().stream()
                .map(c -> row(c.id(), c.name(), statsRow(c.stats()), c.abilities().stream()
                        .map(CatalogsTest::abilityRow).toList()))
                .toList();

        assertEquals(List.of(
                "warrior|Guerrero|120|14|8|4|10|["
                        + "war_cry|Grito de guerra|cd3|x0.0|[rage]|[]|false, "
                        + "shield_wall|Muro de escudos|cd3|x0.0|[shield]|[]|false]",
                "mage|Mago|80|18|4|6|10|["
                        + "ice_bolt|Rayo de hielo|cd4|x0.8|[]|[frozen]|false, "
                        + "arcane_silence|Silencio arcano|cd4|x0.0|[]|[]|true]",
                "archer|Arquero|95|15|5|10|20|["
                        + "poison_arrow|Flecha envenenada|cd3|x0.7|[]|[poison]|false, "
                        + "vampiric_arrow|Flecha vampírica|cd4|x1.0|[lifesteal]|[]|false]"), rows);
    }

    @Test
    void enemyCatalogMatchesTheDesignTable() {
        List<String> rows = EnemyCatalog.all().stream()
                .map(e -> row(e.level(), e.id(), e.name(), statsRow(e.stats()), e.boss(), e.abilities().stream()
                        .map(CatalogsTest::abilityRow).toList()))
                .toList();

        assertEquals(List.of(
                "1|goblin|Goblin|70|11|3|8|10|false|["
                        + "dirty_dagger|Daga sucia|cd3|x0.8|[]|[poison]|false]",
                "1|wolf|Lobo|60|12|2|12|15|false|["
                        + "howl|Aullido|cd4|x0.0|[rage]|[]|false]",
                "1|slime|Slime|90|8|4|2|0|false|["
                        + "jelly_shield|Gelatina|cd4|x0.0|[shield]|[]|false, "
                        + "acid_spit|Ácido|cd3|x0.5|[]|[poison]|false]",
                "2|skeleton|Esqueleto|100|13|7|3|5|false|["
                        + "reassemble|Reensamblar|cd5|x0.0|[regeneration]|[]|false, "
                        + "sharp_bones|Huesos afilados|cd4|x0.0|[thorns]|[]|false]",
                "2|orc_shaman|Orco chamán|110|14|6|5|10|false|["
                        + "curse|Maldición|cd5|x0.0|[]|[]|true, "
                        + "blood_totem|Tótem de sangre|cd4|x0.0|[lifesteal]|[]|false]",
                "3|stone_golem|Golem de piedra|160|15|14|1|0|false|["
                        + "stone_skin|Piel de piedra|cd4|x0.0|[thorns]|[]|false, "
                        + "stomp|Pisotón|cd5|x1.0|[]|[frozen]|false]",
                "3|witch|Bruja|90|16|4|7|15|false|["
                        + "potion|Pócima|cd4|x0.0|[regeneration]|[]|false, "
                        + "frost_hex|Hechizo gélido|cd4|x0.0|[]|[frozen]|false, "
                        + "hex|Maleficio|cd3|x0.0|[]|[poison]|false]",
                "4|dragon|Dragón|200|17|9|5|10|true|["
                        + "scales|Escamas|cd5|x0.0|[shield]|[]|false, "
                        + "frost_breath|Aliento helado|cd5|x0.6|[]|[frozen]|false, "
                        + "roar|Rugido|cd4|x0.0|[rage]|[]|false]"), rows);
    }

    @Test
    void enemyAiConditionsMatchTheDesignTable() {
        Situation healthy = new Situation(100, 100, 0, NEVER);

        assertTrue(EnemyCatalog.get("goblin").conditionMet("dirty_dagger", new Situation(70, 70, 0, ALWAYS)));
        assertFalse(EnemyCatalog.get("goblin").conditionMet("dirty_dagger", new Situation(70, 70, 0, NEVER)));

        assertTrue(EnemyCatalog.get("wolf").conditionMet("howl", new Situation(35, 60, 0, NEVER)));
        assertFalse(EnemyCatalog.get("wolf").conditionMet("howl", new Situation(36, 60, 0, NEVER)));

        assertTrue(EnemyCatalog.get("skeleton").conditionMet("reassemble", new Situation(49, 100, 0, NEVER)));
        assertFalse(EnemyCatalog.get("skeleton").conditionMet("reassemble", new Situation(50, 100, 0, NEVER)));

        assertTrue(EnemyCatalog.get("orc_shaman").conditionMet("curse", new Situation(110, 110, 2, NEVER)));
        assertFalse(EnemyCatalog.get("orc_shaman").conditionMet("curse", new Situation(110, 110, 1, NEVER)));

        assertTrue(EnemyCatalog.get("witch").conditionMet("potion", new Situation(44, 90, 0, NEVER)));
        assertFalse(EnemyCatalog.get("witch").conditionMet("potion", new Situation(45, 90, 0, NEVER)));

        assertTrue(EnemyCatalog.get("dragon").conditionMet("scales", new Situation(79, 200, 0, NEVER)));
        assertFalse(EnemyCatalog.get("dragon").conditionMet("scales", new Situation(80, 200, 0, NEVER)));

        for (String unconditional : List.of("slime:jelly_shield", "slime:acid_spit", "skeleton:sharp_bones",
                "orc_shaman:blood_totem", "stone_golem:stone_skin", "stone_golem:stomp", "witch:frost_hex",
                "witch:hex", "dragon:frost_breath", "dragon:roar")) {
            String[] parts = unconditional.split(":");
            assertTrue(EnemyCatalog.get(parts[0]).conditionMet(parts[1], healthy), unconditional);
        }
    }

    @Test
    void idsAreUniqueWithinEachCatalog() {
        assertUnique(EffectCatalog.all().stream().map(EffectCatalog.Entry::id).toList());
        assertUnique(EquipmentCatalog.all().stream().map(EquipmentCatalog.Item::id).toList());
        assertUnique(HeroClassCatalog.all().stream().map(HeroClassCatalog.HeroClass::id).toList());
        assertUnique(EnemyCatalog.all().stream().map(EnemyDefinition::id).toList());
        assertUnique(Stream.concat(
                HeroClassCatalog.all().stream().flatMap(c -> c.abilities().stream()),
                EnemyCatalog.all().stream().flatMap(e -> e.abilities().stream())).map(Ability::id).toList());
    }

    private static void assertUnique(List<String> ids) {
        assertEquals(ids.size(), new HashSet<>(ids).size(), "duplicated id in " + ids);
    }

    @Test
    void everyAbilityReferencesEffectsThatExist() {
        Stream.concat(
                        HeroClassCatalog.all().stream().flatMap(c -> c.abilities().stream()),
                        EnemyCatalog.all().stream().flatMap(e -> e.abilities().stream()))
                .flatMap(a -> a.effects().stream())
                .forEach(application -> assertTrue(EffectCatalog.find(application.effectId()).isPresent(),
                        application.effectId()));
    }

    @Test
    void everyAiConditionBelongsToAnAbilityOfThatEnemy() {
        for (EnemyDefinition enemy : EnemyCatalog.all()) {
            for (String abilityId : enemy.conditions().keySet()) {
                assertTrue(enemy.findAbility(abilityId).isPresent(), enemy.id() + ":" + abilityId);
            }
        }
    }

    @Test
    void levelsOneToThreeHaveAtLeastTwoEnemiesAndLevelFourIsTheDragon() {
        for (int level = 1; level <= 3; level++) {
            assertTrue(EnemyCatalog.byLevel(level).size() >= 2, "level " + level);
        }
        assertEquals(List.of("dragon"), EnemyCatalog.byLevel(4).stream().map(EnemyDefinition::id).toList());
        assertEquals(8, EnemyCatalog.all().size());
        assertEquals(4, EnemyCatalog.TOTAL_LEVELS);
    }

    @Test
    void thereIsExactlyOnePieceOfEachKindPerSlotGroup() {
        assertEquals(3, EquipmentCatalog.all().stream().filter(i -> i.slot() == Slot.WEAPON).count());
        assertEquals(2, EquipmentCatalog.all().stream().filter(i -> i.slot() == Slot.ARMOR).count());
        assertEquals(3, EquipmentCatalog.all().stream().filter(i -> i.slot() == Slot.ACCESSORY).count());
    }

    @Test
    void unknownIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EffectCatalog.get("silence"));
        assertThrows(IllegalArgumentException.class, () -> EquipmentCatalog.get("excalibur"));
        assertThrows(IllegalArgumentException.class, () -> HeroClassCatalog.get("paladin"));
        assertThrows(IllegalArgumentException.class, () -> EnemyCatalog.get("kraken"));
    }
}

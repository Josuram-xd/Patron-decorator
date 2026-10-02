package com.rpgdecorator.domain.catalog;

import static com.rpgdecorator.domain.catalog.EffectApplication.opponent;
import static com.rpgdecorator.domain.catalog.EffectApplication.self;
import static com.rpgdecorator.domain.catalog.EnemyAbility.always;

import com.rpgdecorator.domain.Stats;
import java.util.List;
import java.util.Optional;

public final class EnemyCatalog {

    public static final int TOTAL_LEVELS = 4;

    private static final List<EnemyDefinition> ENEMIES = List.of(
            new EnemyDefinition("goblin", "Goblin", 1, new Stats(70, 11, 3, 8, 10), List.of(
                    new EnemyAbility(new Ability("dirty_dagger", "Daga sucia",
                            "Golpea con el 80% del ataque y envenena.", 3, 0.8, List.of(opponent("poison")), false),
                            (view, random) -> random.chance(50)))),
            new EnemyDefinition("wolf", "Lobo", 1, new Stats(60, 12, 2, 12, 15), List.of(
                    new EnemyAbility(new Ability("howl", "Aullido",
                            "Entra en Furia cuando está herido.", 4, 0, List.of(self("rage")), false),
                            (view, random) -> view.selfHealthBelow(60)))),
            new EnemyDefinition("slime", "Slime", 1, new Stats(90, 8, 4, 2, 0), List.of(
                    always(new Ability("jelly_shield", "Gelatina",
                            "Se cubre con un Escudo.", 4, 0, List.of(self("shield")), false)),
                    always(new Ability("acid_spit", "Ácido",
                            "Golpea con el 50% del ataque y envenena.", 3, 0.5, List.of(opponent("poison")),
                            false)))),
            new EnemyDefinition("skeleton", "Esqueleto", 2, new Stats(100, 13, 7, 3, 5), List.of(
                    new EnemyAbility(new Ability("reassemble", "Reensamblar",
                            "Gana Regeneración cuando está herido.", 5, 0, List.of(self("regeneration")), false),
                            (view, random) -> view.selfHealthBelow(50)),
                    always(new Ability("sharp_bones", "Huesos afilados",
                            "Se cubre de Espinas.", 4, 0, List.of(self("thorns")), false)))),
            new EnemyDefinition("orc_shaman", "Orco chamán", 2, new Stats(110, 14, 6, 5, 10), List.of(
                    new EnemyAbility(new Ability("curse", "Maldición",
                            "Elimina todos los efectos temporales del rival.", 5, 0, List.of(), true),
                            (view, random) -> view.opponentBuffCount() >= 2),
                    always(new Ability("blood_totem", "Tótem de sangre",
                            "Gana Vampirismo.", 4, 0, List.of(self("lifesteal")), false)))),
            new EnemyDefinition("stone_golem", "Golem de piedra", 3, new Stats(160, 15, 14, 1, 0), List.of(
                    always(new Ability("stone_skin", "Piel de piedra",
                            "Se cubre de Espinas.", 4, 0, List.of(self("thorns")), false)),
                    always(new Ability("stomp", "Pisotón",
                            "Golpea con el 100% del ataque y congela.", 5, 1.0, List.of(opponent("frozen")),
                            false)))),
            new EnemyDefinition("witch", "Bruja", 3, new Stats(90, 16, 4, 7, 15), List.of(
                    new EnemyAbility(new Ability("potion", "Pócima",
                            "Gana Regeneración cuando está herida.", 4, 0, List.of(self("regeneration")), false),
                            (view, random) -> view.selfHealthBelow(50)),
                    always(new Ability("frost_hex", "Hechizo gélido",
                            "Congela al rival.", 4, 0, List.of(opponent("frozen")), false)),
                    always(new Ability("hex", "Maleficio",
                            "Envenena al rival.", 3, 0, List.of(opponent("poison")), false)))),
            new EnemyDefinition("dragon", "Dragón", 4, new Stats(200, 17, 9, 5, 10), List.of(
                    new EnemyAbility(new Ability("scales", "Escamas",
                            "Se cubre con un Escudo cuando está herido.", 5, 0, List.of(self("shield")), false),
                            (view, random) -> view.selfHealthBelow(40)),
                    always(new Ability("frost_breath", "Aliento helado",
                            "Golpea con el 60% del ataque y congela.", 5, 0.6, List.of(opponent("frozen")),
                            false)),
                    always(new Ability("roar", "Rugido",
                            "Entra en Furia.", 4, 0, List.of(self("rage")), false)))));

    private EnemyCatalog() {
    }

    public static List<EnemyDefinition> all() {
        return ENEMIES;
    }

    public static List<EnemyDefinition> byLevel(int level) {
        return ENEMIES.stream().filter(e -> e.level() == level).toList();
    }

    public static Optional<EnemyDefinition> find(String enemyId) {
        return ENEMIES.stream().filter(e -> e.id().equals(enemyId)).findFirst();
    }

    public static EnemyDefinition get(String enemyId) {
        return find(enemyId).orElseThrow(() -> new IllegalArgumentException("Unknown enemy: " + enemyId));
    }
}

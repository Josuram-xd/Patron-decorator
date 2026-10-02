package com.rpgdecorator.domain.catalog;

import static com.rpgdecorator.domain.catalog.EffectApplication.onOpponent;
import static com.rpgdecorator.domain.catalog.EffectApplication.onSelf;

import com.rpgdecorator.domain.Stats;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EnemyCatalog {

    public static final int TOTAL_LEVELS = 4;

    private static final List<EnemyDefinition> ENEMIES = List.of(
            new EnemyDefinition("goblin", "Goblin", 1, false, new Stats(70, 11, 3, 8, 10),
                    List.of(new Ability("dirty_dagger", "Daga sucia",
                            "Golpea con el 80% del ataque y envenena.", 3, 0.8, List.of(onOpponent("poison")), false)),
                    Map.of("dirty_dagger", s -> s.random().chance(50))),
            new EnemyDefinition("wolf", "Lobo", 1, false, new Stats(60, 12, 2, 12, 15),
                    List.of(new Ability("howl", "Aullido",
                            "Entra en Furia cuando está herido.", 4, 0, List.of(onSelf("rage")), false)),
                    Map.of("howl", s -> s.healthBelow(60))),
            new EnemyDefinition("slime", "Slime", 1, false, new Stats(90, 8, 4, 2, 0),
                    List.of(
                            new Ability("jelly_shield", "Gelatina",
                                    "Se cubre con un Escudo.", 4, 0, List.of(onSelf("shield")), false),
                            new Ability("acid_spit", "Ácido",
                                    "Golpea con el 50% del ataque y envenena.", 3, 0.5, List.of(onOpponent("poison")),
                                    false)),
                    Map.of()),
            new EnemyDefinition("skeleton", "Esqueleto", 2, false, new Stats(100, 13, 7, 3, 5),
                    List.of(
                            new Ability("reassemble", "Reensamblar",
                                    "Gana Regeneración cuando está herido.", 5, 0, List.of(onSelf("regeneration")),
                                    false),
                            new Ability("sharp_bones", "Huesos afilados",
                                    "Se cubre de Espinas.", 4, 0, List.of(onSelf("thorns")), false)),
                    Map.of("reassemble", s -> s.healthBelow(50))),
            new EnemyDefinition("orc_shaman", "Orco chamán", 2, false, new Stats(110, 14, 6, 5, 10),
                    List.of(
                            new Ability("curse", "Maldición",
                                    "Elimina todos los efectos temporales del rival.", 5, 0, List.of(), true),
                            new Ability("blood_totem", "Tótem de sangre",
                                    "Gana Vampirismo.", 4, 0, List.of(onSelf("lifesteal")), false)),
                    Map.of("curse", s -> s.opponentBuffCount() >= 2)),
            new EnemyDefinition("stone_golem", "Golem de piedra", 3, false, new Stats(160, 15, 14, 1, 0),
                    List.of(
                            new Ability("stone_skin", "Piel de piedra",
                                    "Se cubre de Espinas.", 4, 0, List.of(onSelf("thorns")), false),
                            new Ability("stomp", "Pisotón",
                                    "Golpea con el 100% del ataque y congela.", 5, 1.0, List.of(onOpponent("frozen")),
                                    false)),
                    Map.of()),
            new EnemyDefinition("witch", "Bruja", 3, false, new Stats(90, 16, 4, 7, 15),
                    List.of(
                            new Ability("potion", "Pócima",
                                    "Gana Regeneración cuando está herida.", 4, 0, List.of(onSelf("regeneration")),
                                    false),
                            new Ability("frost_hex", "Hechizo gélido",
                                    "Congela al rival.", 4, 0, List.of(onOpponent("frozen")), false),
                            new Ability("hex", "Maleficio",
                                    "Envenena al rival.", 3, 0, List.of(onOpponent("poison")), false)),
                    Map.of("potion", s -> s.healthBelow(50))),
            new EnemyDefinition("dragon", "Dragón", 4, true, new Stats(200, 17, 9, 5, 10),
                    List.of(
                            new Ability("scales", "Escamas",
                                    "Se cubre con un Escudo cuando está herido.", 5, 0, List.of(onSelf("shield")),
                                    false),
                            new Ability("frost_breath", "Aliento helado",
                                    "Golpea con el 60% del ataque y congela.", 5, 0.6, List.of(onOpponent("frozen")),
                                    false),
                            new Ability("roar", "Rugido",
                                    "Entra en Furia.", 4, 0, List.of(onSelf("rage")), false)),
                    Map.of("scales", s -> s.healthBelow(40))));

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

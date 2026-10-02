package com.rpgdecorator.domain.catalog;

import static com.rpgdecorator.domain.catalog.EffectApplication.opponent;
import static com.rpgdecorator.domain.catalog.EffectApplication.self;

import com.rpgdecorator.domain.Stats;
import java.util.List;
import java.util.Optional;

public final class HeroClassCatalog {

    private static final List<HeroClass> CLASSES = List.of(
            new HeroClass("warrior", "Guerrero", "Resistente y brutal en el cuerpo a cuerpo.",
                    new Stats(120, 14, 8, 4, 10),
                    List.of(
                            new Ability("war_cry", "Grito de guerra",
                                    "Entra en Furia: +50% ataque, -30% defensa durante 2 turnos.",
                                    3, 0, List.of(self("rage")), false),
                            new Ability("shield_wall", "Muro de escudos",
                                    "Levanta un Escudo que absorbe 20 de daño durante 3 turnos.",
                                    3, 0, List.of(self("shield")), false))),
            new HeroClass("mage", "Mago", "Frágil, pero controla el combate con hielo y silencio.",
                    new Stats(80, 18, 4, 6, 10),
                    List.of(
                            new Ability("ice_bolt", "Rayo de hielo",
                                    "Golpea con el 80% del ataque y congela al rival 1 turno.",
                                    4, 0.8, List.of(opponent("frozen")), false),
                            new Ability("arcane_silence", "Silencio arcano",
                                    "Elimina todos los efectos temporales del rival.",
                                    4, 0, List.of(), true))),
            new HeroClass("archer", "Arquero", "Rápido y certero: venenos y golpes críticos.",
                    new Stats(95, 15, 5, 10, 20),
                    List.of(
                            new Ability("poison_arrow", "Flecha envenenada",
                                    "Golpea con el 70% del ataque y envenena al rival 3 turnos.",
                                    3, 0.7, List.of(opponent("poison")), false),
                            new Ability("vampiric_arrow", "Flecha vampírica",
                                    "Gana Vampirismo 3 turnos y golpea con el 100% del ataque.",
                                    4, 1.0, List.of(self("lifesteal")), false))));

    private HeroClassCatalog() {
    }

    public static List<HeroClass> all() {
        return CLASSES;
    }

    public static Optional<HeroClass> find(String heroClassId) {
        return CLASSES.stream().filter(c -> c.id().equals(heroClassId)).findFirst();
    }

    public static HeroClass get(String heroClassId) {
        return find(heroClassId).orElseThrow(() -> new IllegalArgumentException("Unknown hero class: " + heroClassId));
    }
}

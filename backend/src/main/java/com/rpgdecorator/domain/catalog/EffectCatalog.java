package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.effects.FrozenDecorator;
import com.rpgdecorator.domain.effects.GuardDecorator;
import com.rpgdecorator.domain.effects.LifestealDecorator;
import com.rpgdecorator.domain.effects.PoisonDecorator;
import com.rpgdecorator.domain.effects.RageDecorator;
import com.rpgdecorator.domain.effects.RegenerationDecorator;
import com.rpgdecorator.domain.effects.ShieldDecorator;
import com.rpgdecorator.domain.effects.ThornsDecorator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Temporary effects by id. Adding an effect means one decorator class and one entry here. */
public final class EffectCatalog {

    public record Entry(String id, String label, Category category, int baseDuration, String description,
                        Function<Combatant, EffectDecorator> factory) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(PoisonDecorator.ID, PoisonDecorator.LABEL, Category.DEBUFF, PoisonDecorator.BASE_TURNS,
                    "Pierde 6 de vida al inicio de cada turno.", PoisonDecorator::new),
            new Entry(RegenerationDecorator.ID, RegenerationDecorator.LABEL, Category.BUFF,
                    RegenerationDecorator.BASE_TURNS,
                    "Recupera 8 de vida al inicio de cada turno.", RegenerationDecorator::new),
            new Entry(ShieldDecorator.ID, ShieldDecorator.LABEL, Category.BUFF, ShieldDecorator.BASE_TURNS,
                    "Absorbe hasta 20 de daño antes de que llegue a la vida.", ShieldDecorator::new),
            new Entry(ThornsDecorator.ID, ThornsDecorator.LABEL, Category.BUFF, ThornsDecorator.BASE_TURNS,
                    "Devuelve al atacante el 30 % del daño recibido.", ThornsDecorator::new),
            new Entry(RageDecorator.ID, RageDecorator.LABEL, Category.BUFF, RageDecorator.BASE_TURNS,
                    "+50 % de ataque y -30 % de defensa.", RageDecorator::new),
            new Entry(GuardDecorator.ID, GuardDecorator.LABEL, Category.BUFF, GuardDecorator.BASE_TURNS,
                    "+50 % de defensa.", GuardDecorator::new),
            new Entry(FrozenDecorator.ID, FrozenDecorator.LABEL, Category.CONTROL, FrozenDecorator.BASE_TURNS,
                    "Pierde el turno.", FrozenDecorator::new),
            new Entry(LifestealDecorator.ID, LifestealDecorator.LABEL, Category.BUFF, LifestealDecorator.BASE_TURNS,
                    "Se cura el 30 % del daño que inflige.", LifestealDecorator::new));

    private EffectCatalog() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String effectId) {
        return ENTRIES.stream().filter(e -> e.id().equals(effectId)).findFirst();
    }

    public static Entry get(String effectId) {
        return find(effectId).orElseThrow(() -> new IllegalArgumentException("Unknown effect: " + effectId));
    }

    public static EffectDecorator create(String effectId, Combatant target) {
        return get(effectId).factory().apply(target);
    }
}

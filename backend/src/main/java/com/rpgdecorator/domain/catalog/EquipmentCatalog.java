package com.rpgdecorator.domain.catalog;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.equipment.DragonArmorDecorator;
import com.rpgdecorator.domain.equipment.FireRingDecorator;
import com.rpgdecorator.domain.equipment.LeatherArmorDecorator;
import com.rpgdecorator.domain.equipment.LifeAmuletDecorator;
import com.rpgdecorator.domain.equipment.RuneStaffDecorator;
import com.rpgdecorator.domain.equipment.SwordDecorator;
import com.rpgdecorator.domain.equipment.WarAxeDecorator;
import com.rpgdecorator.domain.equipment.WindBootsDecorator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class EquipmentCatalog {

    public record Item(String id, String name, Slot slot, String description,
                       Function<Combatant, EffectDecorator> factory) {
    }

    private static final List<Item> ITEMS = List.of(
            new Item(SwordDecorator.ID, SwordDecorator.LABEL, Slot.WEAPON, "+6 ataque", SwordDecorator::new),
            new Item(WarAxeDecorator.ID, WarAxeDecorator.LABEL, Slot.WEAPON, "+10 ataque, -3 velocidad",
                    WarAxeDecorator::new),
            new Item(RuneStaffDecorator.ID, RuneStaffDecorator.LABEL, Slot.WEAPON, "+3 ataque, +15 % crítico",
                    RuneStaffDecorator::new),
            new Item(LeatherArmorDecorator.ID, LeatherArmorDecorator.LABEL, Slot.ARMOR, "+4 defensa",
                    LeatherArmorDecorator::new),
            new Item(DragonArmorDecorator.ID, DragonArmorDecorator.LABEL, Slot.ARMOR, "+10 defensa, -4 velocidad",
                    DragonArmorDecorator::new),
            new Item(FireRingDecorator.ID, FireRingDecorator.LABEL, Slot.ACCESSORY,
                    "+4 de daño elemental en cada golpe", FireRingDecorator::new),
            new Item(LifeAmuletDecorator.ID, LifeAmuletDecorator.LABEL, Slot.ACCESSORY, "+25 vida máxima",
                    LifeAmuletDecorator::new),
            new Item(WindBootsDecorator.ID, WindBootsDecorator.LABEL, Slot.ACCESSORY, "+5 velocidad",
                    WindBootsDecorator::new));

    private EquipmentCatalog() {
    }

    public static List<Item> all() {
        return ITEMS;
    }

    public static Optional<Item> find(String itemId) {
        return ITEMS.stream().filter(i -> i.id().equals(itemId)).findFirst();
    }

    public static Item get(String itemId) {
        return find(itemId).orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemId));
    }

    public static EffectDecorator create(String itemId, Combatant target) {
        return get(itemId).factory().apply(target);
    }
}

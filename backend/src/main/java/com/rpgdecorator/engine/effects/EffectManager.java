package com.rpgdecorator.engine.effects;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.catalog.EffectCatalog;
import com.rpgdecorator.domain.catalog.EquipmentCatalog;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The only place that walks, inspects and rebuilds decorator chains. Every method that can
 * change the chain returns the new OUTER reference; callers must keep that one.
 */
public final class EffectManager {

    private final InteractionRules rules;

    public EffectManager() {
        this(InteractionRules.defaults());
    }

    public EffectManager(InteractionRules rules) {
        this.rules = rules;
    }

    /** Applies an effect during the target's own turn (see {@link #apply(Combatant, String, TurnContext, boolean)}). */
    public Combatant apply(Combatant outer, String effectId, TurnContext ctx) {
        return apply(outer, effectId, ctx, true);
    }

    /**
     * Interaction rules first, then refresh the existing layer or wrap a new one on the outside.
     * An effect received outside the target's own turn starts counting on the target's next turn.
     */
    public Combatant apply(Combatant outer, String effectId, TurnContext ctx, boolean duringOwnTurn) {
        Set<String> conflicting = rules.removedBy(effectId);
        Combatant current = remove(outer, layer -> conflicting.contains(layer.effectId()),
                RemovalReason.INTERACTION, ctx);

        EffectDecorator existing = find(current, effectId);
        if (existing != null) {
            existing.refresh(EffectCatalog.create(effectId, current));
            ctx.emit(new CombatEvent.EffectRefreshed(current.id(), effectId, existing.duration().turns()));
            return current;
        }

        EffectDecorator applied = EffectCatalog.create(effectId, current);
        if (!duringOwnTurn) {
            applied.advanceTurn();
        }
        ctx.emit(new CombatEvent.EffectApplied(applied.id(), effectId, applied.duration().turns()));
        return applied;
    }

    public Combatant remove(Combatant outer, Predicate<EffectDecorator> filter, RemovalReason reason,
                            TurnContext ctx) {
        return rebuildWithout(outer, filter, layer -> reason, ctx);
    }

    /** Called at the end of the affected combatant's turn. */
    public Combatant advanceTurn(Combatant outer, TurnContext ctx) {
        decorators(outer).forEach(EffectDecorator::advanceTurn);
        return rebuildWithout(outer, EffectDecorator::shouldBeRemoved,
                layer -> layer.duration().isExpired() ? RemovalReason.EXPIRED : RemovalReason.DEPLETED, ctx);
    }

    /** Silence: removes every temporary layer and keeps the equipment. */
    public Combatant purge(Combatant outer, TurnContext ctx) {
        return remove(outer, layer -> layer.category() != Category.EQUIPMENT, RemovalReason.PURGED, ctx);
    }

    /** Builds a fresh chain with equipment only; taking the base guarantees equipment stays innermost. */
    public Combatant equip(BaseCharacter base, Collection<String> equipmentIds) {
        Combatant chain = base;
        for (String itemId : equipmentIds) {
            chain = EquipmentCatalog.create(itemId, chain);
        }
        return chain;
    }

    public boolean hasEffect(Combatant outer, String effectId) {
        return find(outer, effectId) != null;
    }

    /** Outer to inner, ending with the base, with the effective stats seen at each layer. */
    public List<Layer> layers(Combatant outer) {
        List<Layer> layers = new ArrayList<>();
        Combatant current = outer;
        while (current instanceof EffectDecorator decorator) {
            Integer turns = decorator.duration().isPermanent() ? null : decorator.duration().turns();
            layers.add(new Layer(layers.size(), decorator.effectId(), decorator.label(), decorator.category(),
                    turns, false, decorator.stats(), decorator.extra()));
            current = decorator.wrapped();
        }
        layers.add(new Layer(layers.size(), current.id(), current.name(), null, null, true, current.stats(),
                Map.of()));
        return layers;
    }

    private static List<EffectDecorator> decorators(Combatant outer) {
        List<EffectDecorator> decorators = new ArrayList<>();
        for (Combatant c = outer; c instanceof EffectDecorator decorator; c = decorator.wrapped()) {
            decorators.add(decorator);
        }
        return decorators;
    }

    private static EffectDecorator find(Combatant outer, String effectId) {
        return decorators(outer).stream().filter(d -> d.effectId().equals(effectId)).findFirst().orElse(null);
    }

    // ADR-002: unroll, filter, and re-wrap copies from the inside out. Whatever sits inside the
    // innermost removed layer is untouched and reused, so the base is always the same instance.
    private Combatant rebuildWithout(Combatant outer, Predicate<EffectDecorator> filter,
                                     Function<EffectDecorator, RemovalReason> reasonOf, TurnContext ctx) {
        List<EffectDecorator> chain = decorators(outer);
        int innermostRemoved = -1;
        for (int i = 0; i < chain.size(); i++) {
            if (filter.test(chain.get(i))) {
                innermostRemoved = i;
                ctx.emit(new CombatEvent.EffectRemoved(outer.id(), chain.get(i).effectId(),
                        reasonOf.apply(chain.get(i)).name()));
            }
        }
        if (innermostRemoved < 0) {
            return outer;
        }
        Combatant rebuilt = chain.get(innermostRemoved).wrapped();
        for (int i = innermostRemoved - 1; i >= 0; i--) {
            if (!filter.test(chain.get(i))) {
                rebuilt = chain.get(i).copyOnto(rebuilt);
            }
        }
        return rebuilt;
    }
}

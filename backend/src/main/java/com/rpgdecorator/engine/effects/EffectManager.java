package com.rpgdecorator.engine.effects;

import com.rpgdecorator.domain.BaseCharacter;
import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.TurnContext;
import com.rpgdecorator.domain.catalog.EffectCatalog;
import com.rpgdecorator.domain.catalog.EquipmentCatalog;
import com.rpgdecorator.domain.decorator.Category;
import com.rpgdecorator.domain.decorator.EffectDecorator;
import com.rpgdecorator.domain.event.CombatEvent;
import com.rpgdecorator.domain.event.RemovalReason;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The only class that knows a {@link Combatant} may be a chain of decorators (design 3.2, 3.3, 5.4).
 *
 * <p>The classic Decorator gives you only one operation: wrap. Everything else a combat needs
 * (find an effect, refresh it, remove one from the middle, list the layers) is solved here, by
 * walking the chain. That is why this is the ONLY place that uses {@code instanceof EffectDecorator}:
 * the rest of the engine treats every combatant as an opaque {@link Combatant}.
 *
 * <p>Every operation that can change the structure returns the new OUTER reference. The caller
 * ({@code Combat}) must always replace the reference it keeps with the returned one (ADR-002).
 * The {@link BaseCharacter} at the bottom is never replaced, so health and identity survive.
 *
 * <p>The manager itself is stateless: it can be shared by every combat.
 */
public final class EffectManager {

    private final InteractionRules rules;

    /** Uses the interaction rules of design 4.4. */
    public EffectManager() {
        this(InteractionRules.standard());
    }

    public EffectManager(InteractionRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    // ------------------------------------------------------------------ apply

    /**
     * Applies an effect during the affected combatant's OWN turn (e.g. War Cry on self).
     * Same as {@code apply(outer, effectId, true, ctx)}.
     */
    public Combatant apply(Combatant outer, String effectId, TurnContext ctx) {
        return apply(outer, effectId, true, ctx);
    }

    /**
     * Applies a temporary effect (RF-10, RF-16, RF-17).
     *
     * <ol>
     *   <li>The interaction rules run first: the effects the new one cancels are removed
     *       (reason {@code INTERACTION}).</li>
     *   <li>If the effect is already in the chain it is refreshed instead of wrapped again; otherwise
     *       {@code Poison(Poison(...))} would deal double damage (design 3.5).</li>
     *   <li>Otherwise the new decorator wraps the OUTERMOST layer, so equipment stays inside and
     *       effects stay in application order (design 1).</li>
     * </ol>
     *
     * @param duringOwnTurn {@code false} when someone else applies it (e.g. the enemy poisons the hero).
     *                      Then the new layer must count the affected's very next turn, so its
     *                      {@code justApplied} flag is cleared right away (design 4.5).
     * @throws IllegalArgumentException if {@code effectId} is not a temporary effect of the catalog
     *                                  (equipment cannot be applied this way)
     */
    public Combatant apply(Combatant outer, String effectId, boolean duringOwnTurn, TurnContext ctx) {
        Objects.requireNonNull(outer, "outer");
        Objects.requireNonNull(ctx, "ctx");
        // Created first so an unknown id fails BEFORE anything is mutated or emitted.
        EffectDecorator incoming = EffectCatalog.create(effectId, outer);

        Set<String> cancelled = rules.removedBy(effectId);
        Combatant current = remove(outer, layer -> cancelled.contains(layer.effectId()),
                RemovalReason.INTERACTION, ctx);

        Optional<EffectDecorator> existing = find(current, effectId);
        if (existing.isPresent()) {
            EffectDecorator layer = existing.get();
            layer.refresh(incoming);
            ctx.emit(new CombatEvent.EffectRefreshed(current.id(), effectId, layer.duration().turns()));
            return current;
        }

        if (incoming.wrapped() != current) {
            // The interaction rules rebuilt the chain: put the new layer on top of the new outer.
            incoming = incoming.copyOnto(current);
        }
        if (!duringOwnTurn) {
            incoming.advanceTurn(); // a fresh layer only clears its justApplied flag here
        }
        ctx.emit(new CombatEvent.EffectApplied(current.id(), effectId, incoming.duration().turns()));
        return incoming;
    }

    // ----------------------------------------------------------------- remove

    /**
     * Removes every layer matching {@code filter}, wherever it is in the chain (ADR-002, design 3.2).
     *
     * <p>{@code wrapped} is {@code final}, so a layer in the middle cannot be "unlinked". Instead the
     * chain is unwound, filtered and wrapped again from the inside out with {@code copyOnto}, which
     * copies each layer together with its state (turns, absorption). Keeping {@code wrapped} final
     * means a chain never changes shape under our feet, which makes it easy to reason about and test.
     *
     * <p>Emits one {@code EffectRemoved} per removed layer, outer to inner. If nothing matches,
     * returns {@code outer} itself and emits nothing.
     */
    public Combatant remove(Combatant outer, Predicate<EffectDecorator> filter, RemovalReason reason,
                            TurnContext ctx) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(reason, "reason");
        return removeWhere(outer, layer -> filter.test(layer) ? reason : null, ctx);
    }

    /**
     * End of the affected combatant's turn (design 4.5): every layer advances one turn, then the
     * layers that ask to go are removed: {@code EXPIRED} if out of turns, otherwise {@code DEPLETED}
     * (a shield whose absorption reached 0).
     */
    public Combatant advanceTurn(Combatant outer, TurnContext ctx) {
        for (EffectDecorator layer : unwind(outer).decorators()) {
            layer.advanceTurn();
        }
        return removeWhere(outer, layer -> {
            if (!layer.shouldBeRemoved()) {
                return null;
            }
            return layer.duration().isExpired() ? RemovalReason.EXPIRED : RemovalReason.DEPLETED;
        }, ctx);
    }

    /**
     * Silence (RF-18): removes every temporary effect and keeps the equipment, in the same order.
     * Silence is not a decorator itself; it is an operation over the chain (design 4.2).
     */
    public Combatant purge(Combatant outer, TurnContext ctx) {
        return remove(outer, layer -> layer.category() != Category.EQUIPMENT, RemovalReason.PURGED, ctx);
    }

    // ------------------------------------------------------------------ equip

    /**
     * Wraps a bare base character with its equipment, in iteration order (first id = innermost).
     * It only accepts a {@link BaseCharacter}, so equipment always ends up inside every temporary
     * effect (order invariant of design 1). Emits no events.
     *
     * @throws IllegalArgumentException if an item id is unknown
     */
    public Combatant equip(BaseCharacter base, Collection<String> equipmentIds) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(equipmentIds, "equipmentIds");
        Combatant current = base;
        for (String itemId : equipmentIds) {
            current = EquipmentCatalog.create(itemId, current);
        }
        return current;
    }

    // -------------------------------------------------------------- inspection

    /**
     * {@code outer instanceof FrozenDecorator} only sees the outermost layer (design 3.3);
     * this walks the whole chain. Works for equipment ids too.
     */
    public boolean hasEffect(Combatant outer, String effectId) {
        return find(outer, effectId).isPresent();
    }

    /** The chain for the inspector, outer to inner (positions 0..n), with the base last. */
    public List<Layer> layers(Combatant outer) {
        Chain chain = unwind(outer);
        List<Layer> result = new ArrayList<>();
        for (EffectDecorator layer : chain.decorators()) {
            Integer turns = layer.duration().isPermanent() ? null : layer.duration().turns();
            result.add(new Layer(result.size(), layer.effectId(), layer.label(), layer.category(),
                    turns, false, layer.stats()));
        }
        Combatant base = chain.base();
        result.add(new Layer(result.size(), base.id(), base.name(), null, null, true, base.stats()));
        return List.copyOf(result);
    }

    // ---------------------------------------------------------------- internals

    /** A chain taken apart: decorators from outer to inner, plus the component at the bottom. */
    private record Chain(List<EffectDecorator> decorators, Combatant base) {
    }

    /** Walks the {@code wrapped} references down to the first non-decorator. */
    private static Chain unwind(Combatant outer) {
        Objects.requireNonNull(outer, "outer");
        List<EffectDecorator> decorators = new ArrayList<>();
        Combatant current = outer;
        while (current instanceof EffectDecorator layer) {
            decorators.add(layer);
            current = layer.wrapped();
        }
        return new Chain(decorators, current);
    }

    private static Optional<EffectDecorator> find(Combatant outer, String effectId) {
        return unwind(outer).decorators().stream()
                .filter(layer -> layer.effectId().equals(effectId))
                .findFirst();
    }

    /**
     * Shared core of every removal: unwind, drop, rebuild.
     *
     * @param reasonFor the removal reason for a layer, or {@code null} to keep it
     */
    private Combatant removeWhere(Combatant outer, Function<EffectDecorator, RemovalReason> reasonFor,
                                  TurnContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Chain chain = unwind(outer);
        List<RemovalReason> reasons = chain.decorators().stream().map(reasonFor).toList();
        if (reasons.stream().allMatch(Objects::isNull)) {
            return outer; // nothing to remove: same reference, no events
        }

        // Rebuild from the inside out. Layers below the innermost removed one still wrap exactly
        // what they wrapped before, so they are reused; every layer above it is copied onto the
        // new chain, because its `wrapped` (final) points to something that is no longer there.
        Combatant current = chain.base();
        boolean mustCopy = false;
        for (int i = chain.decorators().size() - 1; i >= 0; i--) {
            EffectDecorator layer = chain.decorators().get(i);
            if (reasons.get(i) != null) {
                mustCopy = true;
            } else {
                current = mustCopy ? layer.copyOnto(current) : layer;
            }
        }

        String targetId = outer.id();
        for (int i = 0; i < reasons.size(); i++) {
            if (reasons.get(i) != null) {
                ctx.emit(new CombatEvent.EffectRemoved(targetId, chain.decorators().get(i).effectId(),
                        reasons.get(i)));
            }
        }
        return current;
    }
}

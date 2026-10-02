package com.rpgdecorator.domain.decorator;

import com.rpgdecorator.domain.Combatant;
import com.rpgdecorator.domain.Damage;
import com.rpgdecorator.domain.DamageResult;
import com.rpgdecorator.domain.Side;
import com.rpgdecorator.domain.Stats;
import com.rpgdecorator.domain.TurnContext;
import java.util.Map;
import java.util.Objects;

/**
 * Base decorator: delegates every {@link Combatant} method to {@code wrapped}.
 * Concrete decorators override only the methods they change.
 */
public abstract class EffectDecorator implements Combatant {

    protected final Combatant wrapped;
    private final String effectId;
    private final String label;
    private final Category category;
    private Duration duration;
    private boolean justApplied = true;

    protected EffectDecorator(Combatant wrapped, String effectId, String label, Category category, Duration duration) {
        this.wrapped = Objects.requireNonNull(wrapped);
        this.effectId = Objects.requireNonNull(effectId);
        this.label = Objects.requireNonNull(label);
        this.category = Objects.requireNonNull(category);
        this.duration = Objects.requireNonNull(duration);
    }

    /** Copy constructor for {@link #copyOnto}: same effect and state on top of another combatant. */
    protected EffectDecorator(Combatant newWrapped, EffectDecorator source) {
        this(newWrapped, source.effectId, source.label, source.category, source.duration);
        this.justApplied = source.justApplied;
    }

    public Combatant wrapped() {
        return wrapped;
    }

    public String effectId() {
        return effectId;
    }

    public String label() {
        return label;
    }

    public Category category() {
        return category;
    }

    public Duration duration() {
        return duration;
    }

    /** State worth showing besides the duration (e.g. the absorption left in a shield). */
    public Map<String, Integer> extra() {
        return Map.of();
    }

    public boolean shouldBeRemoved() {
        return duration.isExpired();
    }

    public void refresh(EffectDecorator incoming) {
        this.duration = incoming.duration;
    }

    public void advanceTurn() {
        if (justApplied) {
            justApplied = false;
        } else {
            duration = duration.decrement();
        }
    }

    /** Copies this decorator (with its state: duration, absorption...) on top of another combatant. */
    public abstract EffectDecorator copyOnto(Combatant newWrapped);

    @Override
    public String id() {
        return wrapped.id();
    }

    @Override
    public String name() {
        return wrapped.name();
    }

    @Override
    public Side side() {
        return wrapped.side();
    }

    @Override
    public Stats stats() {
        return wrapped.stats();
    }

    @Override
    public int currentHealth() {
        return wrapped.currentHealth();
    }

    @Override
    public final void changeHealth(int delta, int effectiveMaxHealth) {
        wrapped.changeHealth(delta, effectiveMaxHealth);
    }

    @Override
    public Damage modifyOutgoingDamage(Damage damage, TurnContext ctx) {
        return wrapped.modifyOutgoingDamage(damage, ctx);
    }

    @Override
    public DamageResult takeDamage(Damage damage, TurnContext ctx) {
        return wrapped.takeDamage(damage, ctx);
    }

    @Override
    public void onDamageDealt(DamageResult result, TurnContext ctx) {
        wrapped.onDamageDealt(result, ctx);
    }

    @Override
    public boolean canAct(TurnContext ctx) {
        return wrapped.canAct(ctx);
    }

    @Override
    public void onTurnStart(TurnContext ctx) {
        wrapped.onTurnStart(ctx);
    }

    @Override
    public String describeChain() {
        return label + "(" + wrapped.describeChain() + ")";
    }
}

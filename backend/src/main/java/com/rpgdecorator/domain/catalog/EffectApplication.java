package com.rpgdecorator.domain.catalog;

public record EffectApplication(String effectId, Target target) {

    public static EffectApplication onSelf(String effectId) {
        return new EffectApplication(effectId, Target.SELF);
    }

    public static EffectApplication onOpponent(String effectId) {
        return new EffectApplication(effectId, Target.OPPONENT);
    }
}

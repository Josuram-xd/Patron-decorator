package com.rpgdecorator.domain.catalog;

public record EffectApplication(String effectId, Target target) {

    public static EffectApplication self(String effectId) {
        return new EffectApplication(effectId, Target.SELF);
    }

    public static EffectApplication opponent(String effectId) {
        return new EffectApplication(effectId, Target.OPPONENT);
    }
}

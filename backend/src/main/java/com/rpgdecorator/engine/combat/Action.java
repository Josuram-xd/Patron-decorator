package com.rpgdecorator.engine.combat;

public sealed interface Action permits Action.Attack, Action.Defend, Action.UseAbility, Action.Pass {

    /** Name used in the {@code ACTION} event and in the HTTP contract. */
    String type();

    record Attack() implements Action {
        @Override
        public String type() {
            return "ATTACK";
        }
    }

    record Defend() implements Action {
        @Override
        public String type() {
            return "DEFEND";
        }
    }

    record UseAbility(String abilityId) implements Action {
        @Override
        public String type() {
            return "ABILITY";
        }
    }

    record Pass() implements Action {
        @Override
        public String type() {
            return "PASS";
        }
    }
}

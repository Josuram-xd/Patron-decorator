package com.rpgdecorator.engine.combat;

import com.rpgdecorator.engine.ErrorCode;

import java.util.Objects;

/**
 * What a combatant does in its turn (design §5.2). The four variants are nested records so the whole
 * closed hierarchy reads in one place: {@code Action.Attack}, {@code Action.Defend},
 * {@code Action.UseAbility}, {@code Action.Pass}.
 *
 * <p>{@link #type()} is the contract value of {@code ActionRequest.type} and also the
 * {@code action} field of the {@code ACTION} event.
 */
public sealed interface Action {

    String ATTACK = "ATTACK";
    String DEFEND = "DEFEND";
    String ABILITY = "ABILITY";
    String PASS = "PASS";

    /** {@code "ATTACK" | "DEFEND" | "ABILITY" | "PASS"}. */
    String type();

    /** The ability id for {@link UseAbility}; {@code null} for the others. */
    default String abilityId() {
        return null;
    }

    /** Basic attack: damage x1.0. */
    record Attack() implements Action {
        @Override public String type() { return ATTACK; }
    }

    /** Applies {@code guard} to oneself. */
    record Defend() implements Action {
        @Override public String type() { return DEFEND; }
    }

    /** Uses an ability of the actor (design §4.7). */
    record UseAbility(String abilityId) implements Action {
        public UseAbility {
            Objects.requireNonNull(abilityId, "abilityId");
        }
        @Override public String type() { return ABILITY; }
    }

    /** Only valid when the actor cannot act (frozen). */
    record Pass() implements Action {
        @Override public String type() { return PASS; }
    }

    static Action attack() { return new Attack(); }
    static Action defend() { return new Defend(); }
    static Action pass() { return new Pass(); }
    static Action useAbility(String abilityId) { return new UseAbility(abilityId); }

    /**
     * Builds an action from the fields of {@code ActionRequest} (api-contract §6).
     *
     * @throws InvalidActionException {@code INVALID_VALUE} if {@code type} is unknown or missing;
     *         {@code REQUIRED_FIELD} if {@code type = ABILITY} and {@code abilityId} is null or blank
     */
    static Action fromType(String type, String abilityId) {
        if (type == null) {
            throw new InvalidActionException(ErrorCode.REQUIRED_FIELD, "Falta el campo 'type'");
        }
        return switch (type) {
            case ATTACK -> attack();
            case DEFEND -> defend();
            case PASS -> pass();
            case ABILITY -> {
                if (abilityId == null || abilityId.isBlank()) {
                    throw new InvalidActionException(ErrorCode.REQUIRED_FIELD, "Falta el campo 'abilityId'");
                }
                yield useAbility(abilityId);
            }
            default -> throw new InvalidActionException(ErrorCode.INVALID_VALUE, "Acción desconocida: " + type);
        };
    }
}

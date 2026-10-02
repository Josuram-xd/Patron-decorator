package com.rpgdecorator.engine.combat;

import com.rpgdecorator.engine.ErrorCode;

import java.util.Objects;

/**
 * A request the engine rejects (design §5.2). Carries an {@link ErrorCode} of api-contract §1; the
 * message is player-visible and therefore in Spanish (ADR-004).
 */
public class InvalidActionException extends RuntimeException {

    private final ErrorCode errorCode;

    public InvalidActionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** The contract code as text, e.g. {@code "ABILITY_ON_COOLDOWN"}. */
    public String code() {
        return errorCode.name();
    }
}

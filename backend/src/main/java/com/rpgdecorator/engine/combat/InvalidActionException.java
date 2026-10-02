package com.rpgdecorator.engine.combat;

/** A rejected action. {@code code} is the error code of the HTTP contract; the message is shown to the player. */
public class InvalidActionException extends RuntimeException {

    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String INVALID_VALUE = "INVALID_VALUE";
    public static final String ABILITY_ON_COOLDOWN = "ABILITY_ON_COOLDOWN";
    public static final String ACTION_NOT_ALLOWED = "ACTION_NOT_ALLOWED";

    private final String code;

    public InvalidActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

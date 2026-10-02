package com.rpgdecorator.engine;

/**
 * Error codes of api-contract §1 that the engine can raise. The API maps each one to its HTTP status
 * (400, 404 or 409). Lives in the root {@code engine} package because both the combat engine and the
 * expedition service use it. HTTP-only codes ({@code INVALID_JSON}, {@code METHOD_NOT_ALLOWED},
 * {@code INTERNAL_ERROR}) are not here: the engine never produces them.
 */
public enum ErrorCode {
    /** 400: a required field is missing (e.g. {@code abilityId} for an ABILITY action). */
    REQUIRED_FIELD,
    /** 400: a value or id does not exist (unknown action type, unknown ability). */
    INVALID_VALUE,
    /** 404: the expedition does not exist. */
    NOT_FOUND,
    /** 409: the operation is not allowed in the current state (e.g. acting on a finished combat). */
    INVALID_STATE,
    /** 409: the ability is still on cooldown. */
    ABILITY_ON_COOLDOWN,
    /** 409: {@code PASS} while able to act, or any other action while frozen. */
    ACTION_NOT_ALLOWED
}

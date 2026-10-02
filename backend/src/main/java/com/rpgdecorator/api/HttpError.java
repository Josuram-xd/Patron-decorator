package com.rpgdecorator.api;

import com.rpgdecorator.api.json.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;

/** An error response of api-contract §1. The message is shown to the player, so it is in Spanish. */
public class HttpError extends RuntimeException {

    private final int status;
    private final String code;

    public HttpError(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static HttpError notFound(String message) {
        return new HttpError(404, "NOT_FOUND", message);
    }

    public static HttpError methodNotAllowed(String method) {
        return new HttpError(405, "METHOD_NOT_ALLOWED", "Método no permitido: " + method);
    }

    public static HttpError invalidJson(String detail) {
        return new HttpError(400, "INVALID_JSON", "El cuerpo no es un JSON válido: " + detail);
    }

    public static HttpError internal() {
        return new HttpError(500, "INTERNAL_ERROR", "Error interno del servidor");
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** {@code { "error": { "code": ..., "message": ... } }}. */
    public JsonValue toJson() {
        Map<String, JsonValue> error = new LinkedHashMap<>();
        error.put("code", JsonValue.string(code));
        error.put("message", JsonValue.string(getMessage()));
        return JsonValue.object(Map.of("error", JsonValue.object(error)));
    }
}

package com.rpgdecorator.api;

import com.sun.net.httpserver.Headers;

/** CORS headers of api-contract §8, sent on every response. */
public final class Cors {

    public static final String ALLOWED_ORIGIN = "http://localhost:5173";
    public static final String ALLOWED_METHODS = "GET, POST, DELETE, OPTIONS";
    public static final String ALLOWED_HEADERS = "Content-Type";

    private Cors() {
    }

    public static void apply(Headers responseHeaders) {
        responseHeaders.set("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        responseHeaders.set("Access-Control-Allow-Methods", ALLOWED_METHODS);
        responseHeaders.set("Access-Control-Allow-Headers", ALLOWED_HEADERS);
    }
}

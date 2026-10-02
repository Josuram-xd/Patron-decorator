package com.rpgdecorator.api;

import com.rpgdecorator.api.Router.Response;
import com.rpgdecorator.api.json.InvalidJsonException;
import com.rpgdecorator.api.json.Json;
import com.rpgdecorator.api.json.JsonValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The JDK {@link HttpServer} in front of a {@link Router}: CORS, JSON bodies and error mapping. */
public final class HttpApiServer {

    public static final int DEFAULT_PORT = 8080;
    public static final String PORT_VARIABLE = "PORT";

    private final HttpServer server;
    private final Router router;

    /** @param port the port to listen on; 0 picks a free one (see {@link #port()}) */
    public HttpApiServer(int port, Router router) {
        this.router = router;
        router.get("/api/health", request -> Response.ok(JsonValue.object(Map.of("status", JsonValue.string("OK")))));
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot listen on port " + port, e);
        }
        server.createContext("/", this::handle);
    }

    /** The value of the {@code PORT} environment variable, or 8080 if it is missing or not a number. */
    public static int portFromEnvironment() {
        return parsePort(System.getenv(PORT_VARIABLE));
    }

    static int parsePort(String value) {
        try {
            return value == null ? DEFAULT_PORT : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Cors.apply(exchange.getResponseHeaders());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            Response response = respond(exchange);
            if (response.body() == null) {
                exchange.sendResponseHeaders(response.status(), -1);
                return;
            }
            byte[] bytes = Json.write(response.body()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private Response respond(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return router.dispatch(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body);
        } catch (HttpError error) {
            return new Response(error.status(), error.toJson());
        } catch (InvalidJsonException invalid) {
            HttpError error = HttpError.invalidJson(invalid.getMessage());
            return new Response(error.status(), error.toJson());
        } catch (RuntimeException unexpected) {
            System.err.println("Unhandled error on " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI() + ": " + unexpected);
            HttpError error = HttpError.internal();
            return new Response(error.status(), error.toJson());
        }
    }
}

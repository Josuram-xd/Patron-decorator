package com.rpgdecorator.api;

import com.rpgdecorator.api.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps an HTTP method plus a path pattern such as {@code /api/expeditions/{id}} to a handler. */
public final class Router {

    /** What a handler receives: {@code body} is the raw request body ("" if there is none). */
    public record Request(String method, String path, Map<String, String> pathParams, String body) {

        public String pathParam(String name) {
            return pathParams.get(name);
        }
    }

    /** What a handler returns; a {@code null} body means an empty response (e.g. 204). */
    public record Response(int status, JsonValue body) {

        public static Response ok(JsonValue body) {
            return new Response(200, body);
        }

        public static Response created(JsonValue body) {
            return new Response(201, body);
        }

        public static Response noContent() {
            return new Response(204, null);
        }
    }

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request);
    }

    private record Route(String method, String[] segments, Handler handler) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String pattern, Handler handler) {
        return register("GET", pattern, handler);
    }

    public Router post(String pattern, Handler handler) {
        return register("POST", pattern, handler);
    }

    public Router delete(String pattern, Handler handler) {
        return register("DELETE", pattern, handler);
    }

    public Router register(String method, String pattern, Handler handler) {
        routes.add(new Route(method, split(pattern), handler));
        return this;
    }

    /**
     * Runs the handler registered for that method and path.
     *
     * @throws HttpError 404 if no route has that path, 405 if the path exists for another method
     */
    public Response dispatch(String method, String path, String body) {
        String[] segments = split(path);
        boolean pathExists = false;
        for (Route route : routes) {
            Map<String, String> params = match(route.segments(), segments);
            if (params == null) {
                continue;
            }
            pathExists = true;
            if (route.method().equals(method)) {
                return route.handler().handle(new Request(method, path, params, body));
            }
        }
        if (pathExists) {
            throw HttpError.methodNotAllowed(method);
        }
        throw HttpError.notFound("La ruta no existe: " + path);
    }

    private static String[] split(String path) {
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    /** The path parameters if the path fits the pattern, or {@code null} if it does not. */
    private static Map<String, String> match(String[] pattern, String[] path) {
        if (pattern.length != path.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String expected = pattern[i];
            if (expected.startsWith("{") && expected.endsWith("}")) {
                params.put(expected.substring(1, expected.length() - 1), path[i]);
            } else if (!expected.equals(path[i])) {
                return null;
            }
        }
        return params;
    }
}

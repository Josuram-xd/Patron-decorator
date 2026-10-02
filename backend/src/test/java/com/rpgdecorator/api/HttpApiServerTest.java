package com.rpgdecorator.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.api.Router.Response;
import com.rpgdecorator.api.json.Json;
import com.rpgdecorator.api.json.JsonValue;
import com.rpgdecorator.api.json.JsonValue.Obj;
import com.rpgdecorator.api.json.JsonValue.Str;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpApiServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private HttpApiServer server;

    @BeforeEach
    void startServer() {
        Router router = new Router()
                .get("/api/expeditions/{id}", request ->
                        Response.ok(JsonValue.object(Map.of("id", JsonValue.string(request.pathParam("id"))))))
                .post("/api/echo", request -> Response.created(Json.parse(request.body())))
                .delete("/api/expeditions/{id}", request -> Response.noContent())
                .get("/api/boom", request -> {
                    throw new IllegalStateException("boom");
                })
                .get("/api/conflict", request -> {
                    throw new HttpError(409, "INVALID_STATE", "La expedición espera una recompensa");
                });
        server = new HttpApiServer(0, router);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    private static String errorCode(HttpResponse<String> response) {
        Obj error = (Obj) ((Obj) Json.parse(response.body())).get("error");
        assertTrue(error.get("message") instanceof Str);
        return ((Str) error.get("code")).value();
    }

    private static void assertCors(HttpResponse<String> response) {
        assertEquals("http://localhost:5173",
                response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("GET, POST, DELETE, OPTIONS",
                response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow());
        assertEquals("Content-Type", response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow());
    }

    @Test
    void healthAnswers200WithStatusOk() throws Exception {
        HttpResponse<String> response = send("GET", "/api/health", null);

        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"OK\"}", response.body());
        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertCors(response);
    }

    @Test
    void unknownRouteAnswers404() throws Exception {
        HttpResponse<String> response = send("GET", "/api/nothing/here", null);

        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", errorCode(response));
        assertCors(response);
    }

    @Test
    void wrongMethodOnAnExistingRouteAnswers405() throws Exception {
        HttpResponse<String> response = send("POST", "/api/health", "{}");

        assertEquals(405, response.statusCode());
        assertEquals("METHOD_NOT_ALLOWED", errorCode(response));
    }

    @Test
    void optionsAnswers204WithCorsHeaders() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/api/expeditions", null);

        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
        assertCors(response);
    }

    @Test
    void pathParametersReachTheHandler() throws Exception {
        HttpResponse<String> response = send("GET", "/api/expeditions/6f1c-2a7e", null);

        assertEquals(200, response.statusCode());
        assertEquals("{\"id\":\"6f1c-2a7e\"}", response.body());
    }

    @Test
    void sameRouteDispatchesByMethod() throws Exception {
        assertEquals(204, send("DELETE", "/api/expeditions/abc", null).statusCode());
        assertEquals(405, send("POST", "/api/expeditions/abc", "{}").statusCode());
    }

    @Test
    void bodyIsReadAndWrittenAsUtf8() throws Exception {
        HttpResponse<String> response = send("POST", "/api/echo", "{\"name\":\"Orco chamán\"}");

        assertEquals(201, response.statusCode());
        assertEquals("{\"name\":\"Orco chamán\"}", response.body());
    }

    @Test
    void invalidJsonBodyAnswers400() throws Exception {
        HttpResponse<String> response = send("POST", "/api/echo", "{not json");

        assertEquals(400, response.statusCode());
        assertEquals("INVALID_JSON", errorCode(response));
    }

    @Test
    void httpErrorFromAHandlerKeepsItsStatusAndCode() throws Exception {
        HttpResponse<String> response = send("GET", "/api/conflict", null);

        assertEquals(409, response.statusCode());
        assertEquals("INVALID_STATE", errorCode(response));
        assertTrue(response.body().contains("La expedición espera una recompensa"));
    }

    @Test
    void unexpectedExceptionAnswers500WithoutLeakingDetails() throws Exception {
        HttpResponse<String> response = send("GET", "/api/boom", null);

        assertEquals(500, response.statusCode());
        assertEquals("INTERNAL_ERROR", errorCode(response));
        assertTrue(!response.body().contains("boom"));
    }

    @Test
    void portComesFromTheEnvironmentVariableOrDefaultsTo8080() {
        assertEquals(8080, HttpApiServer.parsePort(null));
        assertEquals(9090, HttpApiServer.parsePort("9090"));
        assertEquals(8080, HttpApiServer.parsePort("not-a-port"));
    }
}

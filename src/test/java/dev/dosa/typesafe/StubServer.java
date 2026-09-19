package dev.dosa.typesafe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Minimal stub HTTP server for tests: serves queued responses in order and
 * records every request it receives.
 */
final class StubServer implements AutoCloseable {

    record StubResponse(int status, Map<String, String> headers, String body) {
    }

    record RecordedRequest(String method, String path, Map<String, List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }
    }

    private final HttpServer server;
    private final BlockingQueue<StubResponse> stubs = new LinkedBlockingQueue<>();
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void enqueue(int status, String body) {
        enqueue(status, Map.of(), body);
    }

    void enqueue(int status, Map<String, String> headers, String body) {
        stubs.add(new StubResponse(status, headers, body));
    }

    List<RecordedRequest> requests() {
        return requests;
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        Map<String, List<String>> headers = new LinkedHashMap<>(exchange.getRequestHeaders());
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                headers,
                new String(requestBody, StandardCharsets.UTF_8)));

        StubResponse stub = stubs.poll();
        if (stub == null) {
            stub = new StubResponse(500, Map.of(), "{\"error\":\"no stubbed response\"}");
        }
        byte[] bytes = stub.body().getBytes(StandardCharsets.UTF_8);
        stub.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(stub.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

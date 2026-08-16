package com.schoolbus.gateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * Controllable downstream stub that counts invocations.
 */
public final class CountingHttpStub implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger invocations = new AtomicInteger();
    private volatile IntFunction<StubResponse> responder =
            attempt -> StubResponse.of(200, "{\"ok\":true}");

    private CountingHttpStub(HttpServer server) {
        this.server = server;
    }

    public static CountingHttpStub start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountingHttpStub stub = new CountingHttpStub(server);
        HttpHandler handler = stub::handle;
        server.createContext("/", handler);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return stub;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUri() {
        return "http://127.0.0.1:" + port();
    }

    public int invocations() {
        return invocations.get();
    }

    public void reset() {
        invocations.set(0);
    }

    public void respondWith(IntFunction<StubResponse> responder) {
        this.responder = responder;
    }

    public void respondSequence(StubResponse... responses) {
        respondWith(attempt -> {
            int index = Math.min(attempt - 1, responses.length - 1);
            return responses[index];
        });
    }

    private void handle(HttpExchange exchange) throws IOException {
        int attempt = invocations.incrementAndGet();
        StubResponse response = responder.apply(attempt);
        if (response.delayMillis() > 0) {
            try {
                Thread.sleep(response.delayMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record StubResponse(int status, String body, long delayMillis) {
        public static StubResponse of(int status, String body) {
            return new StubResponse(status, body, 0);
        }

        public static StubResponse delayed(int status, String body, long delayMillis) {
            return new StubResponse(status, body, delayMillis);
        }
    }
}

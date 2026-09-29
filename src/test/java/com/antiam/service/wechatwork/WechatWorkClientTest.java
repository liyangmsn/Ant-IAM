package com.antiam.service.wechatwork;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WechatWorkClientTest {

    @Test
    void parsesJsonResponsesFromWeComApi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/cgi-bin/gettoken", exchange -> respond(exchange,
            "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"test-token\",\"expires_in\":7200}"));
        server.createContext("/cgi-bin/department/list", exchange -> respond(exchange,
            "{\"errcode\":0,\"errmsg\":\"ok\",\"department\":[{\"id\":1}]}"));
        server.start();
        try {
            JsonNode response = new WechatWorkClient(new ObjectMapper()).get(
                "http://localhost:" + server.getAddress().getPort(),
                "corp",
                "secret",
                "/cgi-bin/department/list",
                Map.of("id", 1));

            assertThat(response.path("errcode").asInt()).isZero();
            assertThat(response.path("department").get(0).path("id").asLong()).isEqualTo(1L);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }
}

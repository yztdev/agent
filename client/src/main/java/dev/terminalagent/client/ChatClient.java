package dev.terminalagent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** A single prompt has no persistent conversation state in V0.2. */
final class ChatClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    static void ask(HttpClient http, URI server, String token, String question) throws Exception {
        var request = HttpRequest.newBuilder(server.resolve("/api/chat"))
            .timeout(Duration.ofSeconds(180))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(java.util.Map.of("prompt", question))))
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (var body = response.body()) { body.transferTo(java.io.OutputStream.nullOutputStream()); }
            System.out.println("Chat failed: HTTP " + response.statusCode());
            return;
        }
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = "message";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    switch (event) {
                        case "delta" -> System.out.print(JSON.readTree(data).path("text").asText());
                        case "usage" -> System.out.println("\nToken usage: " + data);
                        case "error" -> System.err.println("\n" + data);
                        default -> { }
                    }
                }
            }
        }
        System.out.println();
    }

    private ChatClient() {}
}

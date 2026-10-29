package dev.terminalagent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Each question is independent; optimization is optional and never stored. */
final class ChatClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_OPTIMIZED_LENGTH = 20000;

    static void ask(HttpClient http, URI server, String token, String question,
                    boolean optimize, Console console) throws Exception {
        String optimized = null;
        if (optimize) {
            System.out.println("正在整理问题……");
            try {
                StreamResult result = stream(http, server, token, "/api/optimize", Map.of("prompt", question), false);
                if (!result.complete() || result.text().isBlank() || result.text().length() > MAX_OPTIMIZED_LENGTH) {
                    throw new IllegalStateException("优化结果不完整或超出限制");
                }
                optimized = result.text();
                System.out.println("整理后的问题：\n" + optimized);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception error) {
                System.err.println("问题优化失败：" + error.getMessage());
                String answer = console.readLine("是否使用原问题继续？[y/N] ");
                if (answer == null || !answer.trim().equalsIgnoreCase("y")) return;
            }
        }
        Map<String, String> input = new HashMap<>();
        input.put("prompt", question);
        if (optimized != null) input.put("optimized", optimized);
        StreamResult answer = stream(http, server, token, "/api/chat", input, true);
        if (!answer.complete()) throw new IllegalStateException("回答未完整结束，请检查网络和用量记录");
        System.out.println();
    }

    private static StreamResult stream(HttpClient http, URI server, String token, String endpoint,
                                       Map<String, String> input, boolean display) throws Exception {
        var request = HttpRequest.newBuilder(server.resolve(endpoint))
            .timeout(Duration.ofSeconds(180))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(input)))
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (var body = response.body()) { body.transferTo(java.io.OutputStream.nullOutputStream()); }
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        StringBuilder result = new StringBuilder();
        boolean complete = false;
        boolean tooLong = false;
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = "message";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    switch (event) {
                        case "delta" -> {
                            String text = JSON.readTree(data).path("text").asText();
                            if (display) System.out.print(text);
                            else if (result.length() + text.length() <= MAX_OPTIMIZED_LENGTH) result.append(text);
                            else tooLong = true;
                        }
                        case "usage" -> {
                            complete = true;
                            System.out.println("\n" + (display ? "回答" : "优化") + " Token 用量: " + data);
                        }
                        case "notice" -> System.err.println("\n提示：" + data);
                        case "error" -> throw new IllegalStateException(data);
                        default -> { }
                    }
                }
            }
        }
        return new StreamResult(result.toString(), complete && !tooLong);
    }

    private record StreamResult(String text, boolean complete) {}
    private ChatClient() {}
}

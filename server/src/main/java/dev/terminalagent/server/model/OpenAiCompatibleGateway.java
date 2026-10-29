package dev.terminalagent.server.model;

import dev.terminalagent.server.config.ModelSettings;
import dev.terminalagent.server.config.SystemPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
class OpenAiCompatibleGateway implements ModelGateway {
    private final ObjectMapper json;
    private final HttpClient http;
    private final ModelSettings settings;
    private final SystemPrompt systemPrompt;

    @org.springframework.beans.factory.annotation.Autowired
    OpenAiCompatibleGateway(ObjectMapper json, ModelSettings settings, SystemPrompt systemPrompt) {
        this(json, settings, systemPrompt, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OpenAiCompatibleGateway(ObjectMapper json, ModelSettings settings, SystemPrompt systemPrompt, HttpClient http) {
        this.json = json;
        this.settings = settings;
        this.systemPrompt = systemPrompt;
        this.http = http;
    }

    @Override
    public Usage stream(String prompt, Consumer<String> onText) throws Exception {
        var body = Map.of(
            "model", settings.model(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt.content()),
                Map.of("role", "user", "content", prompt)
            ),
            "stream", true,
            "stream_options", Map.of("include_usage", true),
            "max_tokens", settings.maxOutput()
        );
        var request = HttpRequest.newBuilder(settings.endpoint())
            .timeout(Duration.ofSeconds(150))
            .header("Authorization", "Bearer " + settings.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Model provider returned HTTP " + response.statusCode());
            long input = -1;
            long output = -1;
            boolean finished = false;
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) { finished = true; break; }
                    if (data.isEmpty()) continue;
                    JsonNode event = json.readTree(data);
                    if (event.hasNonNull("usage")) {
                        input = event.get("usage").path("prompt_tokens").asLong(-1);
                        output = event.get("usage").path("completion_tokens").asLong(-1);
                    }
                    JsonNode choices = event.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                        String text = choices.get(0).path("delta").path("content").asText("");
                        if (!text.isEmpty()) onText.accept(text);
                    }
                }
            }
            if (!finished || input < 0 || output < 0) throw new IOException("Provider usage is unavailable");
            return new Usage(input, output);
        }
    }
}

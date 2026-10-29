package dev.terminalagent.server.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration for an OpenAI-compatible Chat Completions endpoint. */
@Component
public class ModelSettings {
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int maxOutput;

    public ModelSettings(@Value("${MODEL_API_URL:}") String apiUrl,
                  @Value("${MODEL_API_KEY:${DEEPSEEK_API_KEY:}}") String apiKey,
                  @Value("${MODEL_NAME:${DEEPSEEK_MODEL:}}") String model,
                  @Value("${MODEL_MAX_OUTPUT:${DEEPSEEK_MAX_OUTPUT:1024}}") int maxOutput) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxOutput = maxOutput;
    }

    public boolean isValid() {
        if (apiUrl.isBlank() || apiKey.isBlank() || model.isBlank() || maxOutput <= 0) return false;
        try {
            URI uri = URI.create(apiUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getFragment() == null && uri.getQuery() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public URI endpoint() { return URI.create(apiUrl); }
    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public int maxOutput() { return maxOutput; }
}

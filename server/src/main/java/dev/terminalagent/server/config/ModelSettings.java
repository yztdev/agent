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
    private final String thinking;

    public ModelSettings(String apiUrl, String apiKey, String model) {
        this(apiUrl, apiKey, model, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ModelSettings(@Value("${MODEL_API_URL:}") String apiUrl,
                  @Value("${MODEL_API_KEY:${DEEPSEEK_API_KEY:}}") String apiKey,
                  @Value("${MODEL_NAME:${DEEPSEEK_MODEL:}}") String model,
                  @Value("${MODEL_THINKING:}") String thinking) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.thinking = thinking;
    }

    public boolean isValid() {
        if (apiUrl.isBlank() || apiKey.isBlank() || model.isBlank()
            || !(thinking.isBlank() || thinking.equals("enabled") || thinking.equals("disabled"))) return false;
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
    public String thinking() { return thinking; }
}

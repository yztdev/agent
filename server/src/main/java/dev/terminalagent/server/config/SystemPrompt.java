package dev.terminalagent.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Loads one immutable system prompt when the service starts. */
@Component
public class SystemPrompt {
    private final String content;

    public SystemPrompt(@Value("${SYSTEM_PROMPT_PATH:}") String path) throws IOException {
        if (path == null || path.isBlank()) {
            try (var stream = SystemPrompt.class.getResourceAsStream("/prompts/system.md")) {
                if (stream == null) throw new IOException("Bundled system prompt not found");
                content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        }
        if (content.isBlank()) throw new IOException("System prompt is empty");
    }

    public String content() { return content; }
}

package dev.terminalagent.server.service;

import dev.terminalagent.server.model.ModelGateway;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class PromptOptimizer {
    private final ModelGateway gateway;
    private final String instructions;

    public PromptOptimizer(ModelGateway gateway) throws IOException {
        this.gateway = gateway;
        try (var input = PromptOptimizer.class.getResourceAsStream("/prompts/optimizer.md")) {
            if (input == null) throw new IOException("Optimizer prompt not found");
            instructions = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (instructions.isBlank()) throw new IOException("Optimizer prompt is empty");
    }

    public ModelGateway.Usage optimize(String original, Consumer<String> onText) throws Exception {
        return gateway.streamWithSystem(instructions, original, onText);
    }
}

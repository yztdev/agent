package dev.terminalagent.server;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class ChatController {
    private final AuthController auth;
    private final QuotaService quota;
    private final ModelGateway modelGateway;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
        2, 8, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32));

    @Value("${DEEPSEEK_API_KEY:}") String apiKey;
    @Value("${DEEPSEEK_MODEL:deepseek-flash}") String model;
    @Value("${DEEPSEEK_MAX_OUTPUT:1024}") int maxOutput;

    record Prompt(String prompt) {}

    ChatController(AuthController auth, QuotaService quota, ModelGateway modelGateway) {
        this.auth = auth;
        this.quota = quota;
        this.modelGateway = modelGateway;
    }

    @PostMapping(value = "/api/chat", produces = "text/event-stream")
    SseEmitter chat(HttpServletRequest request, @RequestBody Prompt input) {
        var user = auth.current(request);
        if (input.prompt() == null || input.prompt().isBlank() || input.prompt().length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is invalid");
        }
        if (apiKey.isBlank() || maxOutput <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DeepSeek configuration incomplete");
        }
        // Keep a request record for usage accounting without reserving a quota budget.
        UUID requestId = quota.reserve(user.id(), model, 0);
        SseEmitter emitter = new SseEmitter(180000L);
        try {
            workers.execute(() -> run(input.prompt(), requestId, emitter));
        } catch (RejectedExecutionException e) {
            quota.releaseBeforeSend(requestId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Server busy; try again later");
        }
        return emitter;
    }

    void run(String prompt, UUID requestId, SseEmitter emitter) {
        AtomicBoolean connected = new AtomicBoolean(true);
        try {
            ModelGateway.Usage usage = modelGateway.stream(prompt, text -> {
                if (!connected.get()) return;
                try {
                    emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
                } catch (IOException | IllegalStateException error) {
                    // Continue consuming the provider stream to recover its actual usage.
                    connected.set(false);
                }
            });
            quota.settle(requestId, usage.inputTokens(), usage.outputTokens());
            if (connected.get()) {
                emitter.send(SseEmitter.event().name("usage").data(Map.of(
                    "input", usage.inputTokens(), "output", usage.outputTokens())));
            }
        } catch (Exception error) {
            quota.markPending(requestId);
            if (connected.get()) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                        "Request incomplete; usage pending review. Request ID: " + requestId));
                } catch (IOException | IllegalStateException ignored) { }
            }
        } finally {
            emitter.complete();
        }
    }
}

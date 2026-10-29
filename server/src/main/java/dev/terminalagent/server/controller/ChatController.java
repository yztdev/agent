package dev.terminalagent.server.controller;

import dev.terminalagent.server.service.AuthService;
import dev.terminalagent.server.service.QuotaService;
import dev.terminalagent.server.service.PromptOptimizer;
import dev.terminalagent.server.model.ModelGateway;
import dev.terminalagent.server.config.ModelSettings;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class ChatController {
    private final AuthService auth;
    private final QuotaService quota;
    private final ModelGateway modelGateway;
    private final PromptOptimizer optimizer;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
        2, 8, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32));

    private final ModelSettings modelSettings;

    record Prompt(String prompt, String optimized) {}

    ChatController(AuthService auth, QuotaService quota, ModelGateway modelGateway, ModelSettings modelSettings, PromptOptimizer optimizer) {
        this.auth = auth;
        this.quota = quota;
        this.modelGateway = modelGateway;
        this.modelSettings = modelSettings;
        this.optimizer = optimizer;
    }

    @PostMapping(value = "/api/chat", produces = "text/event-stream")
    SseEmitter chat(HttpServletRequest request, @RequestBody Prompt input) {
        if (input.optimized() != null && input.optimized().length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Optimized prompt is too long");
        }
        String prompt = input.prompt();
        if (input.optimized() != null && !input.optimized().isBlank() && prompt != null) {
            prompt = "原始问题：\n" + prompt + "\n\n整理后的问题（仅供参考，不覆盖原始问题）：\n" + input.optimized();
        }
        return submit(request, input.prompt(), prompt, false);
    }

    @PostMapping(value = "/api/optimize", produces = "text/event-stream")
    SseEmitter optimize(HttpServletRequest request, @RequestBody Prompt input) {
        return submit(request, input.prompt(), input.prompt(), true);
    }

    private SseEmitter submit(HttpServletRequest request, String original, String prompt, boolean optimize) {
        var user = auth.current(request);
        if (original == null || original.isBlank() || original.length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is invalid");
        }
        if (!modelSettings.isValid()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Model configuration incomplete or invalid");
        }
        UUID requestId = quota.reserve(user.id(), modelSettings.model(), 0);
        SseEmitter emitter = new SseEmitter(180000L);
        try {
            workers.execute(() -> run(prompt, requestId, emitter, optimize));
        } catch (RejectedExecutionException e) {
            quota.releaseBeforeSend(requestId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Server busy; try again later");
        }
        return emitter;
    }

    void run(String prompt, UUID requestId, SseEmitter emitter) {
        run(prompt, requestId, emitter, false);
    }

    void run(String prompt, UUID requestId, SseEmitter emitter, boolean optimize) {
        AtomicBoolean connected = new AtomicBoolean(true);
        try {
            java.util.function.Consumer<String> deliver = text -> {
                if (!connected.get()) return;
                try {
                    emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
                } catch (IOException | IllegalStateException error) {
                    // Continue consuming the provider stream to recover its actual usage.
                    connected.set(false);
                }
            };
            ModelGateway.Usage usage = optimize ? optimizer.optimize(prompt, deliver) : modelGateway.stream(prompt, deliver);
            quota.settle(requestId, usage.inputTokens(), usage.outputTokens());
            if (connected.get()) {
                if (!usage.hasVisibleText()) {
                    emitter.send(SseEmitter.event().name("notice").data("模型没有返回可见正文；可能需要调整思考模式或输出上限。"));
                } else if ("length".equals(usage.finishReason())) {
                    emitter.send(SseEmitter.event().name("notice").data("回答达到输出上限，内容可能不完整。"));
                }
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

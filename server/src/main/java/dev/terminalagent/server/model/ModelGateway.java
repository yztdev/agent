package dev.terminalagent.server.model;

import java.util.function.Consumer;

/** Provider-independent stream contract. No conversation text is persisted. */
public interface ModelGateway {
    record Usage(long inputTokens, long outputTokens, String finishReason, boolean hasVisibleText) {
        public Usage(long inputTokens, long outputTokens) { this(inputTokens, outputTokens, "", true); }
    }
    Usage stream(String prompt, Consumer<String> onText) throws Exception;
    Usage streamWithSystem(String systemPrompt, String prompt, Consumer<String> onText) throws Exception;
}

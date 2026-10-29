package dev.terminalagent.server;

import java.util.function.Consumer;

/** Provider-independent stream contract. No conversation text is persisted. */
interface ModelGateway {
    record Usage(long inputTokens, long outputTokens) {}
    Usage stream(String prompt, Consumer<String> onText) throws Exception;
}

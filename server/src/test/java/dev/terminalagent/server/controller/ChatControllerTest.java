package dev.terminalagent.server.controller;

import dev.terminalagent.server.service.AuthService;
import dev.terminalagent.server.service.QuotaService;
import dev.terminalagent.server.service.PromptOptimizer;
import dev.terminalagent.server.model.ModelGateway;
import dev.terminalagent.server.config.ModelSettings;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatControllerTest {
    @Test
    void successfulStreamSettlesActualUsage() throws Exception {
        var quota = mock(QuotaService.class);
        var gateway = mock(ModelGateway.class);
        when(gateway.stream(eq("question"), any())).thenReturn(new ModelGateway.Usage(3, 4));
        var controller = new ChatController(mock(AuthService.class), quota, gateway, new ModelSettings("https://provider.example/chat", "test", "model"), mock(PromptOptimizer.class));
        UUID id = UUID.randomUUID();
        controller.run("question", id, mock(SseEmitter.class));
        verify(quota).settle(id, 3, 4);
        verify(quota, never()).markPending(id);
    }

    @Test
    void incompleteStreamKeepsReservationForReview() throws Exception {
        var quota = mock(QuotaService.class);
        var gateway = mock(ModelGateway.class);
        when(gateway.stream(eq("question"), any())).thenThrow(new IOException("incomplete"));
        var controller = new ChatController(mock(AuthService.class), quota, gateway, new ModelSettings("https://provider.example/chat", "test", "model"), mock(PromptOptimizer.class));
        UUID id = UUID.randomUUID();
        controller.run("question", id, mock(SseEmitter.class));
        verify(quota).markPending(id);
        verify(quota, never()).settle(any(), anyLong(), anyLong());
    }

    @Test
    void optimizationUsesSeparateCallAndAccountsForItsUsage() throws Exception {
        var quota = mock(QuotaService.class);
        var gateway = mock(ModelGateway.class);
        var optimizer = mock(PromptOptimizer.class);
        when(optimizer.optimize(eq("original"), any())).thenReturn(new ModelGateway.Usage(7, 2));
        var controller = new ChatController(mock(AuthService.class), quota, gateway,
            new ModelSettings("https://provider.example/chat", "test", "model"), optimizer);
        UUID id = UUID.randomUUID();
        controller.run("original", id, mock(SseEmitter.class), true);
        verify(optimizer).optimize(eq("original"), any());
        verify(quota).settle(id, 7, 2);
        verify(gateway, never()).stream(anyString(), any());
    }
}

package dev.terminalagent.server.controller;

import dev.terminalagent.server.service.AuthService;
import dev.terminalagent.server.service.QuotaService;
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
        var controller = new ChatController(mock(AuthService.class), quota, gateway, new ModelSettings("https://provider.example/chat", "test", "model", 100));
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
        var controller = new ChatController(mock(AuthService.class), quota, gateway, new ModelSettings("https://provider.example/chat", "test", "model", 100));
        UUID id = UUID.randomUUID();
        controller.run("question", id, mock(SseEmitter.class));
        verify(quota).markPending(id);
        verify(quota, never()).settle(any(), anyLong(), anyLong());
    }
}

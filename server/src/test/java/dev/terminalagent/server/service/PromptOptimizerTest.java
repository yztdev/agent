package dev.terminalagent.server.service;

import dev.terminalagent.server.model.ModelGateway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromptOptimizerTest {
    @Test void usesDedicatedInstructionsAndPreservesOriginalInput() throws Exception {
        var gateway = mock(ModelGateway.class);
        when(gateway.streamWithSystem(anyString(), anyString(), any())).thenReturn(new ModelGateway.Usage(10, 5));
        var optimizer = new PromptOptimizer(gateway);
        String original = "检查 C:\\work\\app.java，保持路径原样";
        var result = optimizer.optimize(original, text -> {});
        assertEquals(new ModelGateway.Usage(10, 5), result);
        verify(gateway).streamWithSystem(contains("不得添加"), eq(original), any());
        verify(gateway, never()).stream(anyString(), any());
    }
}

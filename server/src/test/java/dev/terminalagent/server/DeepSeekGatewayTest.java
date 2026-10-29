package dev.terminalagent.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeepSeekGatewayTest {
    @SuppressWarnings("unchecked")
    @Test
    void readsStreamedTextAndActualUsage() throws Exception {
        var http = mock(HttpClient.class);
        var response = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String events = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
            + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}\n\n"
            + "data: [DONE]\n\n";
        when(response.body()).thenReturn(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var gateway = new DeepSeekGateway(new ObjectMapper(), http);
        gateway.apiKey = "test";
        gateway.model = "test-model";
        gateway.maxOutput = 100;
        var chunks = new ArrayList<String>();
        var usage = gateway.stream("question", chunks::add);
        assertEquals(java.util.List.of("hello"), chunks);
        assertEquals(new ModelGateway.Usage(3, 4), usage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingUsageIsNeverTreatedAsZero() throws Exception {
        var http = mock(HttpClient.class);
        var response = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("data: [DONE]\n".getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var gateway = new DeepSeekGateway(new ObjectMapper(), http);
        gateway.apiKey = "test";
        gateway.model = "test";
        assertThrows(java.io.IOException.class, () -> gateway.stream("question", text -> {}));
    }
}

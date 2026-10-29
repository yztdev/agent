package dev.terminalagent.server.model;

import dev.terminalagent.server.config.ModelSettings;
import dev.terminalagent.server.config.SystemPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.mockito.ArgumentCaptor;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiCompatibleGatewayTest {
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
        var settings = new ModelSettings("https://provider.example/v1/chat/completions", "test", "test-model", 100);
        var gateway = new OpenAiCompatibleGateway(new ObjectMapper(), settings, new SystemPrompt(""), http);
        var chunks = new ArrayList<String>();
        var usage = gateway.stream("question", chunks::add);
        assertEquals(java.util.List.of("hello"), chunks);
        assertEquals(new ModelGateway.Usage(3, 4), usage);
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        var bytes = new java.io.ByteArrayOutputStream();
        var complete = new java.util.concurrent.CountDownLatch(1);
        request.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer buffer) { byte[] data = new byte[buffer.remaining()]; buffer.get(data); bytes.writeBytes(data); }
            public void onError(Throwable error) { complete.countDown(); }
            public void onComplete() { complete.countDown(); }
        });
        assertTrue(complete.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var messages = new ObjectMapper().readTree(bytes.toByteArray()).path("messages");
        assertEquals("system", messages.get(0).path("role").asText());
        assertTrue(messages.get(0).path("content").asText().contains("自主软件工程 Agent"));
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("question", messages.get(1).path("content").asText());
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingUsageIsNeverTreatedAsZero() throws Exception {
        var http = mock(HttpClient.class);
        var response = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("data: [DONE]\n".getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var settings = new ModelSettings("https://provider.example/v1/chat/completions", "test", "test-model", 100);
        var gateway = new OpenAiCompatibleGateway(new ObjectMapper(), settings, new SystemPrompt(""), http);
        assertThrows(java.io.IOException.class, () -> gateway.stream("question", text -> {}));
    }
}

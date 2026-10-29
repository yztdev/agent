package dev.terminalagent.server.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelSettingsTest {
    @Test void acceptsConfiguredHttpsEndpointAndModel() {
        var settings = new ModelSettings("https://models.example/v1/chat/completions", "key", "another-model");
        assertTrue(settings.isValid());
        assertEquals("another-model", settings.model());
        assertEquals("https://models.example/v1/chat/completions", settings.endpoint().toString());
    }

    @Test void rejectsMissingOrUnsafeConfiguration() {
        assertFalse(new ModelSettings("", "key", "model").isValid());
        assertFalse(new ModelSettings("http://models.example/v1/chat/completions", "key", "model").isValid());
        assertFalse(new ModelSettings("https://models.example/v1/chat/completions", "", "model").isValid());
    }
}

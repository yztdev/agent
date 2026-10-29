package dev.terminalagent.server.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SystemPromptTest {
    @TempDir Path directory;

    @Test void loadsBundledPrompt() throws Exception {
        assertTrue(new SystemPrompt("").content().contains("自主软件工程 Agent"));
    }

    @Test void loadsExternalUtf8PromptAndRejectsEmptyFile() throws Exception {
        Path file = directory.resolve("prompt.md");
        Files.writeString(file, "测试提示词");
        assertEquals("测试提示词", new SystemPrompt(file.toString()).content());
        Files.writeString(file, "   ");
        assertThrows(IOException.class, () -> new SystemPrompt(file.toString()));
    }
}

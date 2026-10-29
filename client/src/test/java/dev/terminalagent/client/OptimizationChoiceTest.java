package dev.terminalagent.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationChoiceTest {
    @Test void acceptsExplicitYesAndDefaultsToNo() {
        assertEquals(Boolean.TRUE, OptimizationChoice.parse(" y "));
        assertEquals(Boolean.TRUE, OptimizationChoice.parse("是"));
        assertEquals(Boolean.FALSE, OptimizationChoice.parse(""));
        assertEquals(Boolean.FALSE, OptimizationChoice.parse("N"));
        assertEquals(Boolean.FALSE, OptimizationChoice.parse("否"));
        assertNull(OptimizationChoice.parse("maybe"));
    }
}

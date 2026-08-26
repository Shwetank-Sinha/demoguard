package io.demoguard.prediction;

import io.demoguard.prediction.MemoryForecaster.MemoryRisk;
import io.demoguard.prediction.MemoryForecaster.MemorySample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryForecasterTest {
    private final MemoryForecaster forecaster = new MemoryForecaster();

    @Test
    void risingMemoryBecomesAtRiskAndCalculatesForecast() {
        var result = forecaster.forecast(List.of(
                new MemorySample(0, 100), new MemorySample(60, 200), new MemorySample(120, 300)),
                600, 5);

        assertEquals(MemoryRisk.AT_RISK, result.risk());
        assertEquals(800L, result.predictedMemoryBytesAtDemoEnd());
        assertEquals(3.0, result.predictedLimitBreachInMinutes(), 0.0001);
        assertTrue(result.message().contains("low-confidence"));
    }

    @Test
    void stableMemoryBecomesSafe() {
        var result = forecaster.forecast(List.of(
                new MemorySample(0, 100), new MemorySample(60, 100), new MemorySample(120, 100)),
                600, 30);

        assertEquals(MemoryRisk.SAFE, result.risk());
        assertEquals(100L, result.predictedMemoryBytesAtDemoEnd());
        assertNull(result.predictedLimitBreachInMinutes());
    }

    @Test
    void insufficientSamplesBecomeUnknown() {
        var result = forecaster.forecast(List.of(new MemorySample(0, 100), new MemorySample(60, 200)), 600, 30);
        assertEquals(MemoryRisk.UNKNOWN, result.risk());
        assertNull(result.predictedMemoryBytesAtDemoEnd());
    }

    @Test
    void calculatesSafeFutureBreachBeyondDemoEnd() {
        var result = forecaster.forecast(List.of(
                new MemorySample(0, 100), new MemorySample(60, 160), new MemorySample(120, 220)),
                1000, 5);

        assertEquals(MemoryRisk.SAFE, result.risk());
        assertEquals(520L, result.predictedMemoryBytesAtDemoEnd());
        assertEquals(13.0, result.predictedLimitBreachInMinutes(), 0.0001);
    }
}

package io.demoguard.prediction;

import io.demoguard.prediction.CpuForecaster.CpuRisk;
import io.demoguard.prediction.CpuForecaster.CpuSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CpuForecasterTest {
    private final CpuForecaster forecaster = new CpuForecaster();

    @Test
    void projectedUsageBelowEightyPercentIsSafe() {
        var result = forecaster.forecast(samples(0.20, 0.25, 0.30), 2.0, 5, 0.01);

        assertEquals(CpuRisk.SAFE, result.risk());
        assertEquals(0.55, result.predictedCpuCoresAtDemoEnd(), 0.0001);
    }

    @Test
    void projectedUsageAtEightyPercentIsAtRisk() {
        var result = forecaster.forecast(samples(0.50, 0.60, 0.70), 1.0, 1, 0.0);

        assertEquals(CpuRisk.AT_RISK, result.risk());
        assertEquals(0.80, result.predictedCpuCoresAtDemoEnd(), 0.0001);
    }

    @Test
    void insufficientHistoryIsUnknown() {
        var result = forecaster.forecast(List.of(new CpuSample(0, 0.2), new CpuSample(60, 0.3)),
                1.0, 10, null);

        assertEquals(CpuRisk.UNKNOWN, result.risk());
        assertNull(result.predictedCpuCoresAtDemoEnd());
    }

    @Test
    void significantSustainedThrottlingIsAtRiskEvenWithoutHistory() {
        var result = forecaster.forecast(List.of(), 2.0, 10, 0.20);

        assertEquals(CpuRisk.AT_RISK, result.risk());
        assertNull(result.predictedCpuCoresAtDemoEnd());
        assertTrue(result.message().contains("throttling"));
    }

    private static List<CpuSample> samples(double first, double second, double third) {
        return List.of(new CpuSample(0, first), new CpuSample(60, second), new CpuSample(120, third));
    }
}

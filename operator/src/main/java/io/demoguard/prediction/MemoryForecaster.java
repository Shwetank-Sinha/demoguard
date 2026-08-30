package io.demoguard.prediction;

import java.util.List;

/** Stateless least-squares memory forecast. Results are directional, not high-confidence predictions. */
public final class MemoryForecaster {

    private static final int MINIMUM_SAMPLES = 3;
    private static final double MINIMUM_POSITIVE_SLOPE_BYTES_PER_SECOND = 1.0;

    public enum MemoryRisk { SAFE, AT_RISK, UNKNOWN }

    public record MemorySample(long timestampEpochSeconds, double memoryBytes) { }

    public record Forecast(MemoryRisk risk, Long predictedMemoryBytesAtDemoEnd,
                           Double predictedLimitBreachInMinutes, String message) { }

    public Forecast forecast(List<MemorySample> samples, long memoryLimitBytes, int demoDurationMinutes) {
        if (samples == null || samples.size() < MINIMUM_SAMPLES) {
            return new Forecast(MemoryRisk.UNKNOWN, null, null,
                    "Not enough historical samples for a memory forecast");
        }
        if (memoryLimitBytes <= 0 || demoDurationMinutes <= 0) {
            return new Forecast(MemoryRisk.UNKNOWN, null, null,
                    "A positive memory limit and demo duration are required for a forecast");
        }

        double origin = samples.getFirst().timestampEpochSeconds();
        double meanX = samples.stream().mapToDouble(s -> s.timestampEpochSeconds() - origin).average().orElse(0);
        double meanY = samples.stream().mapToDouble(MemorySample::memoryBytes).average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (MemorySample sample : samples) {
            double x = sample.timestampEpochSeconds() - origin;
            numerator += (x - meanX) * (sample.memoryBytes() - meanY);
            denominator += (x - meanX) * (x - meanX);
        }
        if (denominator == 0) {
            return new Forecast(MemoryRisk.UNKNOWN, null, null,
                    "Historical samples do not span enough time for a memory forecast");
        }

        double slope = numerator / denominator;
        double intercept = meanY - slope * meanX;
        double latestX = samples.getLast().timestampEpochSeconds() - origin;
        long predicted = nonNegativeRounded(intercept + slope * (latestX + demoDurationMinutes * 60.0));

        if (slope < MINIMUM_POSITIVE_SLOPE_BYTES_PER_SECOND) {
            return new Forecast(MemoryRisk.SAFE, predicted, null,
                    "Memory has no meaningful positive trend; no limit breach is projected, but confidence is limited");
        }

        double breachSecondsFromLatest = (memoryLimitBytes - (intercept + slope * latestX)) / slope;
        double breachMinutes = Math.max(0, breachSecondsFromLatest / 60.0);
        if (breachMinutes <= demoDurationMinutes) {
            return new Forecast(MemoryRisk.AT_RISK, predicted, breachMinutes,
                    "The linear trend projects a memory-limit breach during the demo; this is a low-confidence estimate");
        }
        return new Forecast(MemoryRisk.SAFE, predicted, null,
                "No memory-limit breach is projected during the demo window; confidence is limited");
    }

    private static long nonNegativeRounded(double value) {
        return Math.max(0L, Math.round(value));
    }
}

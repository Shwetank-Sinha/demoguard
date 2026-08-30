package io.demoguard.prediction;

import java.util.List;

/** Stateless least-squares CPU forecast with a conservative throttling signal. */
public final class CpuForecaster {

    private static final int MINIMUM_SAMPLES = 3;
    private static final double RISK_LIMIT_FRACTION = 0.80;
    private static final double SIGNIFICANT_THROTTLING_FRACTION = 0.10;
    private static final double COMPARISON_EPSILON = 1e-9;

    public enum CpuRisk { SAFE, AT_RISK, UNKNOWN }

    public record CpuSample(long timestampEpochSeconds, double cpuCores) { }

    public record Forecast(CpuRisk risk, Double predictedCpuCoresAtDemoEnd, String message) { }

    public Forecast forecast(List<CpuSample> samples, double cpuLimitCores,
                             int demoDurationMinutes, Double cpuThrottlingRate) {
        if (cpuLimitCores <= 0 || demoDurationMinutes <= 0) {
            return unknown("A positive CPU limit and demo duration are required for a forecast");
        }
        if (cpuThrottlingRate != null
                && cpuThrottlingRate >= cpuLimitCores * SIGNIFICANT_THROTTLING_FRACTION) {
            Double predicted = predictIfPossible(samples, demoDurationMinutes);
            return new Forecast(CpuRisk.AT_RISK, predicted,
                    "Sustained CPU throttling is significant relative to the CPU limit");
        }
        if (samples == null || samples.size() < MINIMUM_SAMPLES) {
            return unknown("Not enough historical samples for a CPU forecast");
        }

        Regression regression = regression(samples);
        if (regression == null) {
            return unknown("Historical samples do not span enough time for a CPU forecast");
        }
        double latestX = samples.getLast().timestampEpochSeconds() - regression.origin();
        double predicted = Math.max(0, regression.intercept()
                + regression.slope() * (latestX + demoDurationMinutes * 60.0));
        double latest = Math.max(0, regression.intercept() + regression.slope() * latestX);
        if (Math.max(latest, predicted) + COMPARISON_EPSILON
                >= cpuLimitCores * RISK_LIMIT_FRACTION) {
            return new Forecast(CpuRisk.AT_RISK, predicted,
                    "CPU usage is projected to reach at least 80% of its limit during the demo window");
        }
        return new Forecast(CpuRisk.SAFE, predicted,
                "CPU usage is not projected to reach 80% of its limit during the demo window");
    }

    private static Double predictIfPossible(List<CpuSample> samples, int demoDurationMinutes) {
        if (samples == null || samples.size() < MINIMUM_SAMPLES) return null;
        Regression regression = regression(samples);
        if (regression == null) return null;
        double latestX = samples.getLast().timestampEpochSeconds() - regression.origin();
        return Math.max(0, regression.intercept()
                + regression.slope() * (latestX + demoDurationMinutes * 60.0));
    }

    private static Regression regression(List<CpuSample> samples) {
        double origin = samples.getFirst().timestampEpochSeconds();
        double meanX = samples.stream().mapToDouble(s -> s.timestampEpochSeconds() - origin).average().orElse(0);
        double meanY = samples.stream().mapToDouble(CpuSample::cpuCores).average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (CpuSample sample : samples) {
            double x = sample.timestampEpochSeconds() - origin;
            numerator += (x - meanX) * (sample.cpuCores() - meanY);
            denominator += (x - meanX) * (x - meanX);
        }
        return denominator == 0 ? null : new Regression(origin, meanY - numerator / denominator * meanX,
                numerator / denominator);
    }

    private static Forecast unknown(String message) {
        return new Forecast(CpuRisk.UNKNOWN, null, message);
    }

    private record Regression(double origin, double intercept, double slope) { }
}

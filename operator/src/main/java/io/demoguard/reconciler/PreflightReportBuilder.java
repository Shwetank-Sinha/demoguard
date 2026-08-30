package io.demoguard.reconciler;

import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.PreflightCheck;
import io.demoguard.api.PreflightCheck.Category;
import io.demoguard.api.PreflightCheck.Status;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.api.RemediationPlan;
import io.demoguard.api.RolloutStatus;
import io.demoguard.api.RuntimeStatus;
import io.demoguard.prediction.CpuForecaster.CpuRisk;
import io.demoguard.prediction.MemoryForecaster.MemoryRisk;

import java.util.ArrayList;
import java.util.List;

final class PreflightReportBuilder {

    private PreflightReportBuilder() {
    }

    static void populate(DemoReadinessStatus readiness, ReadinessStatus staticStatus) {
        List<PreflightCheck> checks = new ArrayList<>();
        checks.add(staticCheck(readiness, staticStatus));
        checks.add(runtimeCheck(readiness));
        checks.add(memoryCheck(readiness));
        checks.add(cpuCheck(readiness));
        checks.add(rolloutCheck(readiness));
        checks.add(remediationCheck(readiness));

        readiness.setPreflightStatus(readiness.getReadinessStatus());
        readiness.setPreflightChecks(checks);
        readiness.setPreflightSummary(summary(readiness.getReadinessStatus(), checks));
    }

    private static PreflightCheck staticCheck(DemoReadinessStatus readiness, ReadinessStatus status) {
        return switch (status) {
            case READY -> check(Category.STATIC, Status.PASS, "Static validation passed", null);
            case WARNING -> check(Category.STATIC, Status.WARNING,
                    "Static validation found warnings", first(readiness.getRecommendations()));
            case BLOCKED -> check(Category.STATIC, Status.BLOCKED,
                    "Static validation found blocking issues", first(readiness.getRecommendations()));
        };
    }

    private static PreflightCheck runtimeCheck(DemoReadinessStatus readiness) {
        RuntimeStatus status = readiness.getRuntimeStatus();
        if (status == null) return check(Category.RUNTIME, Status.UNKNOWN,
                message(readiness.getRuntimeMessage(), "Runtime health is unknown"), null);
        return switch (status) {
            case HEALTHY -> check(Category.RUNTIME, Status.PASS, "Runtime health is healthy", null);
            case DEGRADED -> check(Category.RUNTIME, Status.WARNING,
                    message(readiness.getRuntimeMessage(), "Runtime health is degraded"),
                    "Review pod health and restarts before the demo");
            case UNHEALTHY -> check(Category.RUNTIME, Status.BLOCKED,
                    message(readiness.getRuntimeMessage(), "Runtime health is unhealthy"),
                    "Restore healthy, available replicas before the demo");
        };
    }

    private static PreflightCheck memoryCheck(DemoReadinessStatus readiness) {
        MemoryRisk risk = readiness.getMemoryRisk();
        if (risk == null || risk == MemoryRisk.UNKNOWN) return check(Category.MEMORY, Status.UNKNOWN,
                message(readiness.getPredictionMessage(), "Memory forecast is unknown"), null);
        if (risk == MemoryRisk.AT_RISK) return check(Category.MEMORY, Status.WARNING,
                message(readiness.getPredictionMessage(), "Memory may reach its limit during the demo window"),
                "Review memory growth and capacity before the demo");
        return check(Category.MEMORY, Status.PASS, "Memory forecast is within the configured limit", null);
    }

    private static PreflightCheck cpuCheck(DemoReadinessStatus readiness) {
        CpuRisk risk = readiness.getCpuRisk();
        if (risk == null || risk == CpuRisk.UNKNOWN) return check(Category.CPU, Status.UNKNOWN,
                message(readiness.getCpuPredictionMessage(), "CPU forecast is unknown"), null);
        if (risk == CpuRisk.AT_RISK) return check(Category.CPU, Status.WARNING,
                message(readiness.getCpuPredictionMessage(), "CPU demand or throttling may affect the demo"),
                "Review CPU demand, limits, and throttling before the demo");
        return check(Category.CPU, Status.PASS, "CPU forecast is within the configured limit", null);
    }

    private static PreflightCheck rolloutCheck(DemoReadinessStatus readiness) {
        RolloutStatus status = readiness.getRolloutStatus();
        if (status == null || status == RolloutStatus.UNKNOWN) return check(Category.ROLLOUT, Status.UNKNOWN,
                message(readiness.getRolloutMessage(), "Rollout state is unknown"), null);
        return switch (status) {
            case STABLE -> check(Category.ROLLOUT, Status.PASS, "Deployment rollout is stable", null);
            case ROLLING_OUT -> check(Category.ROLLOUT, Status.WARNING,
                    message(readiness.getRolloutMessage(), "Deployment rollout is still in progress"),
                    "Wait for the rollout to complete before presenting");
            case STALLED -> check(Category.ROLLOUT, Status.BLOCKED,
                    message(readiness.getRolloutMessage(), "Deployment rollout is stalled"),
                    "Rollback or fix the stalled Deployment before starting the demo");
            case UNKNOWN -> throw new IllegalStateException("handled above");
        };
    }

    private static PreflightCheck remediationCheck(DemoReadinessStatus readiness) {
        List<RemediationPlan> plans = readiness.getRemediationPlans();
        int count = plans == null ? 0 : plans.size();
        if (count == 0) return check(Category.REMEDIATION, Status.NOT_REQUIRED,
                "No remediation plans are required", null);
        boolean blocking = plans.stream().anyMatch(plan -> plan.getSeverity() == RemediationPlan.Severity.BLOCKING);
        return check(Category.REMEDIATION, blocking ? Status.BLOCKED : Status.WARNING,
                count + " remediation plan(s) available; review them before the demo",
                "Review the remediation plans and apply approved changes");
    }

    private static String summary(ReadinessStatus overall, List<PreflightCheck> checks) {
        if (overall == ReadinessStatus.READY) {
            return "Workload is ready for the configured demo window";
        }
        Status target = overall == ReadinessStatus.BLOCKED ? Status.BLOCKED : Status.WARNING;
        return checks.stream().filter(check -> check.getStatus() == target).findFirst()
                .map(check -> check.getCategory() + ": " + check.getMessage())
                .orElse(overall + ": review readiness findings before the demo");
    }

    private static PreflightCheck check(Category category, Status status, String message, String recommendation) {
        return new PreflightCheck(category, status, message, recommendation);
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String message(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replace('\n', ' ');
    }
}

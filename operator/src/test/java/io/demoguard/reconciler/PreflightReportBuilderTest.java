package io.demoguard.reconciler;

import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.PreflightCheck.Category;
import io.demoguard.api.PreflightCheck.Status;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.api.RemediationPlan;
import io.demoguard.api.RolloutStatus;
import io.demoguard.api.RuntimeStatus;
import io.demoguard.prediction.CpuForecaster.CpuRisk;
import io.demoguard.prediction.MemoryForecaster.MemoryRisk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreflightReportBuilderTest {

    @Test
    void fullyReadyDeploymentHasConciseOrderedReport() {
        DemoReadinessStatus status = healthyStatus();

        PreflightReportBuilder.populate(status, ReadinessStatus.READY);

        assertEquals(ReadinessStatus.READY, status.getPreflightStatus());
        assertEquals("Workload is ready for the configured demo window", status.getPreflightSummary());
        assertEquals(List.of(Category.STATIC, Category.RUNTIME, Category.MEMORY, Category.CPU,
                Category.ROLLOUT, Category.REMEDIATION),
                status.getPreflightChecks().stream().map(check -> check.getCategory()).toList());
        assertEquals(List.of(Status.PASS, Status.PASS, Status.PASS, Status.PASS, Status.PASS,
                Status.NOT_REQUIRED),
                status.getPreflightChecks().stream().map(check -> check.getStatus()).toList());
        assertNull(status.getPreflightChecks().getFirst().getRecommendation());
    }

    @Test
    void runtimeRestartWarningIsTheSummaryReason() {
        DemoReadinessStatus status = healthyStatus();
        status.setReadinessStatus(ReadinessStatus.WARNING);
        status.setRuntimeStatus(RuntimeStatus.DEGRADED);
        status.setRuntimeMessage("2 pod restarts were observed");

        PreflightReportBuilder.populate(status, ReadinessStatus.READY);

        assertEquals(Status.WARNING, check(status, Category.RUNTIME).getStatus());
        assertEquals("RUNTIME: 2 pod restarts were observed", status.getPreflightSummary());
    }

    @Test
    void memoryOrCpuRiskProducesWarningChecks() {
        DemoReadinessStatus status = healthyStatus();
        status.setReadinessStatus(ReadinessStatus.WARNING);
        status.setMemoryRisk(MemoryRisk.AT_RISK);
        status.setPredictionMessage("Memory projection reaches the limit");
        status.setCpuRisk(CpuRisk.AT_RISK);
        status.setCpuPredictionMessage("CPU throttling is sustained");

        PreflightReportBuilder.populate(status, ReadinessStatus.READY);

        assertEquals(Status.WARNING, check(status, Category.MEMORY).getStatus());
        assertEquals(Status.WARNING, check(status, Category.CPU).getStatus());
        assertTrue(status.getPreflightSummary().startsWith("MEMORY:"));
    }

    @Test
    void stalledRolloutIsBlockedAndIsTheSummaryReason() {
        DemoReadinessStatus status = healthyStatus();
        status.setReadinessStatus(ReadinessStatus.BLOCKED);
        status.setRolloutStatus(RolloutStatus.STALLED);
        status.setRolloutMessage("ProgressDeadlineExceeded");

        PreflightReportBuilder.populate(status, ReadinessStatus.READY);

        assertEquals(ReadinessStatus.BLOCKED, status.getPreflightStatus());
        assertEquals(Status.BLOCKED, check(status, Category.ROLLOUT).getStatus());
        assertEquals("ROLLOUT: ProgressDeadlineExceeded", status.getPreflightSummary());
    }

    @Test
    void staticFailureIsBlockedAndTakesSummaryPrecedence() {
        DemoReadinessStatus status = healthyStatus();
        status.setReadinessStatus(ReadinessStatus.BLOCKED);
        status.setRecommendations(List.of("Add readiness probes"));

        PreflightReportBuilder.populate(status, ReadinessStatus.BLOCKED);

        assertEquals(Status.BLOCKED, check(status, Category.STATIC).getStatus());
        assertEquals("Add readiness probes", check(status, Category.STATIC).getRecommendation());
        assertEquals("STATIC: Static validation found blocking issues", status.getPreflightSummary());
    }

    @Test
    void unknownForecastsAreHonestAndDoNotBlockHealthyDeployment() {
        DemoReadinessStatus status = healthyStatus();
        status.setMemoryRisk(MemoryRisk.UNKNOWN);
        status.setPredictionMessage("Prometheus returned no memory history");
        status.setCpuRisk(CpuRisk.UNKNOWN);
        status.setCpuPredictionMessage("Prometheus returned no CPU history");

        PreflightReportBuilder.populate(status, ReadinessStatus.READY);

        assertEquals(ReadinessStatus.READY, status.getPreflightStatus());
        assertEquals(Status.UNKNOWN, check(status, Category.MEMORY).getStatus());
        assertEquals(Status.UNKNOWN, check(status, Category.CPU).getStatus());
        assertEquals("Workload is ready for the configured demo window", status.getPreflightSummary());
    }

    @Test
    void remediationCheckReportsCountWithoutEmbeddingPatch() {
        DemoReadinessStatus status = healthyStatus();
        status.setReadinessStatus(ReadinessStatus.WARNING);
        status.setRemediationPlans(List.of(new RemediationPlan("probe", RemediationPlan.Severity.WARNING,
                "Deployment", "demo", "Add a probe", "Missing probe", false,
                RemediationPlan.PatchFormat.NONE, "secret-yaml-patch")));

        PreflightReportBuilder.populate(status, ReadinessStatus.WARNING);

        var remediation = check(status, Category.REMEDIATION);
        assertEquals(Status.WARNING, remediation.getStatus());
        assertEquals("1 remediation plan(s) available; review them before the demo", remediation.getMessage());
        assertFalse(remediation.getMessage().contains("secret-yaml-patch"));
    }

    private static DemoReadinessStatus healthyStatus() {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(ReadinessStatus.READY);
        status.setRuntimeStatus(RuntimeStatus.HEALTHY);
        status.setRuntimeMessage("all replicas are ready");
        status.setMemoryRisk(MemoryRisk.SAFE);
        status.setCpuRisk(CpuRisk.SAFE);
        status.setRolloutStatus(RolloutStatus.STABLE);
        status.setRemediationPlans(List.of());
        return status;
    }

    private static io.demoguard.api.PreflightCheck check(DemoReadinessStatus status, Category category) {
        return status.getPreflightChecks().stream()
                .filter(check -> check.getCategory() == category).findFirst().orElseThrow();
    }
}

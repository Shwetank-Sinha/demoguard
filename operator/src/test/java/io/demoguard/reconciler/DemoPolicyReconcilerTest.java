package io.demoguard.reconciler;

import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.api.RuntimeStatus;
import io.demoguard.api.RolloutStatus;
import io.demoguard.rollout.RolloutReport;
import io.demoguard.runtime.RuntimeHealthReport;
import io.demoguard.validation.DeploymentValidator;
import io.demoguard.validation.ReadinessReport;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPolicyReconcilerTest {

    @Test
    void processesMetadataOnlyPolicyUpdates() {
        ControllerConfiguration configuration =
                DemoPolicyReconciler.class.getAnnotation(ControllerConfiguration.class);

        assertFalse(configuration.generationAwareEventProcessing());
    }

    @Test
    void buildsReadinessNameAndCopiesValidationReport() {
        ReadinessReport report = new ReadinessReport(3, 5, List.of("finding"), List.of("fix"));

        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(report);

        assertEquals("launch-readiness", DemoPolicyReconciler.readinessName("launch"));
        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(60, status.getScore());
        assertEquals("Static validation score: 60/100", status.getScoreMessage());
        assertEquals(List.of("finding"), status.getFindings());
        assertEquals(List.of("fix"), status.getRecommendations());
    }

    @Test
    void selectsThePdbWhoseSelectorMatchesDeploymentPodLabels() {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName("app").withNamespace("default").endMetadata()
                .withNewSpec().withNewTemplate()
                    .withNewMetadata().withLabels(Map.of("app", "hackathon")).endMetadata()
                .endTemplate().endSpec().build();
        PodDisruptionBudget unrelated = pdb("unrelated", Map.of("app", "other"));
        PodDisruptionBudget matching = pdb("matching", Map.of("app", "hackathon"));

        var result = DemoPolicyReconciler.findMatchingPdb(
                deployment, List.of(unrelated, matching), new DeploymentValidator());

        assertTrue(result.isPresent());
        assertEquals("matching", result.orElseThrow().getMetadata().getName());
    }

    @Test
    void runtimeDegradationDoesNotOverrideStaticBlock() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(2, 5, List.of("static failure"), List.of("static fix")));
        RuntimeHealthReport runtime = new RuntimeHealthReport(RuntimeStatus.DEGRADED,
                2, 1, 1, 1, 0, "not enough ready", List.of("runtime finding"), List.of("wait"));

        DemoPolicyReconciler.mergeRuntime(status, runtime);

        assertEquals(ReadinessStatus.BLOCKED, status.getReadinessStatus());
        assertEquals(40, status.getScore());
        assertEquals(List.of("static failure", "runtime finding"), status.getFindings());
        assertFalse(status.getScoreMessage().contains("runtime DEGRADED"));
    }

    @Test
    void degradedRuntimePenalizesStaticReadyIntoWarningRange() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));
        RuntimeHealthReport runtime = new RuntimeHealthReport(RuntimeStatus.DEGRADED,
                2, 2, 2, 0, 1, "one restart", List.of("restart"), List.of("inspect"));

        DemoPolicyReconciler.mergeRuntime(status, runtime);

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(80, status.getScore());
        assertTrue(status.getScoreMessage().contains("runtime DEGRADED: -20"));
    }

    @Test
    void unhealthyRuntimeOverridesStaticReadyAndMemoryWarningCannotWeakenIt() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));
        RuntimeHealthReport runtime = new RuntimeHealthReport(RuntimeStatus.UNHEALTHY,
                2, 0, 0, 2, 3, "crashing", List.of("CrashLoopBackOff"), List.of("investigate"));

        DemoPolicyReconciler.mergeRuntime(status, runtime);
        status.setMemoryRisk(io.demoguard.prediction.MemoryForecaster.MemoryRisk.AT_RISK);
        DemoPolicyReconciler.applyMemoryRiskWarning(status);

        assertEquals(ReadinessStatus.BLOCKED, status.getReadinessStatus());
        assertEquals(40, status.getScore());
        assertEquals(RuntimeStatus.UNHEALTHY, status.getRuntimeStatus());
        assertTrue(status.getScoreMessage().contains("runtime UNHEALTHY capped score at 40"));
    }

    @Test
    void memoryRiskPenalizesStaticReadyIntoWarningRange() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));

        DemoPolicyReconciler.applyMemoryRiskWarning(status);

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(80, status.getScore());
        assertTrue(status.getScoreMessage().contains("memoryRisk AT_RISK: -20"));
    }

    @Test
    void cpuRiskPromotesReadyToWarning() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));

        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(80, status.getScore());
        assertTrue(status.getFindings().getFirst().contains("CPU"));
        assertTrue(status.getScoreMessage().contains("cpuRisk AT_RISK: -20"));
    }

    @Test
    void warningRisksAccumulateWithoutCrossingBlockedThreshold() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));
        RuntimeHealthReport runtime = new RuntimeHealthReport(RuntimeStatus.DEGRADED,
                2, 2, 2, 0, 1, "one restart", List.of(), List.of());

        DemoPolicyReconciler.mergeRuntime(status, runtime);
        DemoPolicyReconciler.applyMemoryRiskWarning(status);
        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(60, status.getScore());
        assertTrue(status.getScoreMessage().contains("runtime DEGRADED"));
        assertTrue(status.getScoreMessage().contains("memoryRisk AT_RISK"));
        assertTrue(status.getScoreMessage().contains("cpuRisk AT_RISK"));
    }

    @Test
    void cpuRiskDoesNotOverrideBlockedFinalStatus() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(2, 5, List.of("static failure"), List.of("fix")));

        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.BLOCKED, status.getReadinessStatus());
        assertEquals(40, status.getScore());
        assertEquals(List.of("static failure"), status.getFindings());
        assertEquals("Static validation score: 40/100", status.getScoreMessage());
    }

    @Test
    void stableRolloutDoesNotChangeReadinessOrScore() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));

        DemoPolicyReconciler.mergeRollout(status,
                new RolloutReport(RolloutStatus.STABLE, 4L, 4L, 3, "stable", null));

        assertEquals(ReadinessStatus.READY, status.getReadinessStatus());
        assertEquals(100, status.getScore());
        assertEquals("Static validation score: 100/100", status.getScoreMessage());
    }

    @Test
    void rollingOutMakesOtherwiseReadyWorkloadWarning() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));

        DemoPolicyReconciler.mergeRollout(status,
                new RolloutReport(RolloutStatus.ROLLING_OUT, 5L, 4L, 2, "generation lag", "NewReplicaSetCreated"));

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(80, status.getScore());
        assertTrue(status.getScoreMessage().contains("rollout ROLLING_OUT: -20"));
        assertTrue(status.getRecommendations().contains("Wait for the rollout to complete before presenting"));
    }

    @Test
    void rollingOutPreservesStaticBlockButStillRecommendsWaiting() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(2, 5, List.of("static failure"), List.of("static fix")));

        DemoPolicyReconciler.mergeRollout(status,
                new RolloutReport(RolloutStatus.ROLLING_OUT, 5L, 4L, 2, "generation lag", null));

        assertEquals(ReadinessStatus.BLOCKED, status.getReadinessStatus());
        assertEquals(40, status.getScore());
        assertFalse(status.getScoreMessage().contains("rollout ROLLING_OUT"));
        assertTrue(status.getRecommendations().contains("Wait for the rollout to complete before presenting"));
    }

    @Test
    void stalledRolloutBlocksAndCannotBeWeakenedByCpuOrMemoryRisk() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));

        DemoPolicyReconciler.mergeRollout(status,
                new RolloutReport(RolloutStatus.STALLED, 5L, 5L, 1,
                        "stalled (Kubernetes reason: ProgressDeadlineExceeded)", "ProgressDeadlineExceeded"));
        DemoPolicyReconciler.applyMemoryRiskWarning(status);
        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.BLOCKED, status.getReadinessStatus());
        assertEquals(40, status.getScore());
        assertTrue(status.getScoreMessage().contains("rollout STALLED capped score at 40"));
        assertFalse(status.getScoreMessage().contains("memoryRisk"));
        assertFalse(status.getScoreMessage().contains("cpuRisk"));
        assertTrue(status.getRecommendations().contains(
                "Rollback or fix the stalled Deployment before starting the demo"));
    }

    @Test
    void rolloutRuntimeMemoryAndCpuWarningsAccumulateAtWarningFloor() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));
        RuntimeHealthReport runtime = new RuntimeHealthReport(RuntimeStatus.DEGRADED,
                3, 2, 2, 1, 0, "replicas catching up", List.of(), List.of());

        DemoPolicyReconciler.mergeRuntime(status, runtime);
        DemoPolicyReconciler.mergeRollout(status,
                new RolloutReport(RolloutStatus.ROLLING_OUT, 6L, 5L, 2, "rolling", null));
        DemoPolicyReconciler.applyMemoryRiskWarning(status);
        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertEquals(60, status.getScore());
        assertTrue(status.getScoreMessage().contains("runtime DEGRADED"));
        assertTrue(status.getScoreMessage().contains("rollout ROLLING_OUT"));
        assertTrue(status.getScoreMessage().contains("memoryRisk AT_RISK"));
        assertTrue(status.getScoreMessage().contains("cpuRisk AT_RISK"));
    }

    private PodDisruptionBudget pdb(String name, Map<String, String> labels) {
        return new PodDisruptionBudgetBuilder()
                .withNewMetadata().withName(name).withNamespace("default").endMetadata()
                .withNewSpec().withNewSelector().withMatchLabels(labels).endSelector().endSpec()
                .build();
    }
}

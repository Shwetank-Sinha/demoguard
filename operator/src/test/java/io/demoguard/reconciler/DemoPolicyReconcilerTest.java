package io.demoguard.reconciler;

import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.api.RuntimeStatus;
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
        assertEquals(List.of("static failure", "runtime finding"), status.getFindings());
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
        assertEquals(RuntimeStatus.UNHEALTHY, status.getRuntimeStatus());
    }

    @Test
    void cpuRiskPromotesReadyToWarning() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(5, 5, List.of(), List.of()));

        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.WARNING, status.getReadinessStatus());
        assertTrue(status.getFindings().getFirst().contains("CPU"));
    }

    @Test
    void cpuRiskDoesNotOverrideBlockedFinalStatus() {
        DemoReadinessStatus status = DemoPolicyReconciler.statusFrom(
                new ReadinessReport(2, 5, List.of("static failure"), List.of("fix")));

        DemoPolicyReconciler.applyCpuRiskWarning(status);

        assertEquals(ReadinessStatus.BLOCKED, status.getReadinessStatus());
        assertEquals(List.of("static failure"), status.getFindings());
    }

    private PodDisruptionBudget pdb(String name, Map<String, String> labels) {
        return new PodDisruptionBudgetBuilder()
                .withNewMetadata().withName(name).withNamespace("default").endMetadata()
                .withNewSpec().withNewSelector().withMatchLabels(labels).endSelector().endSpec()
                .build();
    }
}

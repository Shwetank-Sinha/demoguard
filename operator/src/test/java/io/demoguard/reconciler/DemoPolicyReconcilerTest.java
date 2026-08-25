package io.demoguard.reconciler;

import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.validation.DeploymentValidator;
import io.demoguard.validation.ReadinessReport;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPolicyReconcilerTest {

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

    private PodDisruptionBudget pdb(String name, Map<String, String> labels) {
        return new PodDisruptionBudgetBuilder()
                .withNewMetadata().withName(name).withNamespace("default").endMetadata()
                .withNewSpec().withNewSelector().withMatchLabels(labels).endSelector().endSpec()
                .build();
    }
}

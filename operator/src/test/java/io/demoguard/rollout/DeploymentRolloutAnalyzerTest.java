package io.demoguard.rollout;

import io.demoguard.api.RolloutStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentRolloutAnalyzerTest {

    private final DeploymentRolloutAnalyzer analyzer = new DeploymentRolloutAnalyzer();

    @Test
    void classifiesFullyCaughtUpDeploymentAsStable() {
        var report = analyzer.analyze(deployment(7L, 7L, 3, 3, 3, 3, 0));

        assertEquals(RolloutStatus.STABLE, report.rolloutStatus());
        assertEquals(7L, report.deploymentGeneration());
        assertEquals(7L, report.observedGeneration());
        assertEquals(3, report.updatedReplicas());
    }

    @Test
    void classifiesFullyCaughtUpDeploymentWithAbsentUnavailableReplicasAsStable() {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName("app").withGeneration(2L).endMetadata()
                .withNewSpec().withReplicas(3).endSpec()
                .withNewStatus()
                    .withObservedGeneration(2L)
                    .withUpdatedReplicas(3)
                    .withReadyReplicas(3)
                    .withAvailableReplicas(3)
                .endStatus()
                .build();

        assertEquals(RolloutStatus.STABLE, analyzer.analyze(deployment).rolloutStatus());
    }

    @Test
    void classifiesInsufficientUpdatedReplicasAsRollingOut() {
        var report = analyzer.analyze(deployment(7L, 7L, 3, 2, 3, 3, 0));

        assertEquals(RolloutStatus.ROLLING_OUT, report.rolloutStatus());
        assertTrue(report.rolloutMessage().contains("updated=2"));
    }

    @Test
    void classifiesObservedGenerationLagAsRollingOut() {
        var report = analyzer.analyze(deployment(8L, 7L, 3, 3, 3, 3, 0));

        assertEquals(RolloutStatus.ROLLING_OUT, report.rolloutStatus());
        assertTrue(report.rolloutMessage().contains("observedGeneration=7"));
    }

    @Test
    void progressDeadlineExceededIsStalledAndRetainsConditionReason() {
        Deployment deployment = new DeploymentBuilder(deployment(9L, 9L, 3, 2, 2, 2, 1))
                .editStatus()
                .addNewCondition()
                    .withType("Progressing").withStatus("False")
                    .withReason("ProgressDeadlineExceeded")
                    .withMessage("ReplicaSet failed to make progress")
                .endCondition()
                .endStatus()
                .build();

        var report = analyzer.analyze(deployment);

        assertEquals(RolloutStatus.STALLED, report.rolloutStatus());
        assertEquals("ProgressDeadlineExceeded", report.conditionReason());
        assertTrue(report.rolloutMessage().contains("ProgressDeadlineExceeded"));
    }

    @Test
    void absentRequiredStatusIsUnknown() {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName("app").withGeneration(2L).endMetadata()
                .withNewSpec().withReplicas(3).endSpec()
                .build();

        assertEquals(RolloutStatus.UNKNOWN, analyzer.analyze(deployment).rolloutStatus());
    }

    private Deployment deployment(long generation, long observed, int desired, int updated,
                                  int ready, int available, int unavailable) {
        return new DeploymentBuilder()
                .withNewMetadata().withName("app").withGeneration(generation).endMetadata()
                .withNewSpec().withReplicas(desired).endSpec()
                .withNewStatus()
                    .withObservedGeneration(observed)
                    .withUpdatedReplicas(updated)
                    .withReadyReplicas(ready)
                    .withAvailableReplicas(available)
                    .withUnavailableReplicas(unavailable)
                .endStatus()
                .build();
    }
}

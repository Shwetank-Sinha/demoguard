package io.demoguard.runtime;

import io.demoguard.api.RuntimeStatus;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeHealthValidatorTest {

    private final RuntimeHealthValidator validator = new RuntimeHealthValidator();

    @Test
    void allReplicasReadyIsHealthy() {
        RuntimeHealthReport report = validator.validate(deployment(2, 2, 2, 0),
                List.of(runningPod("one", 0), runningPod("two", 0)), 2);

        assertEquals(RuntimeStatus.HEALTHY, report.runtimeStatus());
        assertEquals(2, report.desiredReplicas());
        assertEquals(2, report.readyReplicas());
        assertEquals(2, report.availableReplicas());
        assertEquals(0, report.unavailableReplicas());
        assertEquals(0, report.totalRestarts());
        assertTrue(report.findings().isEmpty());
    }

    @Test
    void insufficientReadyReplicasIsDegradedWithExactMinimum() {
        RuntimeHealthReport report = validator.validate(deployment(3, 1, 1, 2),
                List.of(runningPod("one", 0)), 2);

        assertEquals(RuntimeStatus.DEGRADED, report.runtimeStatus());
        assertEquals("Deployment has 1 Ready and 1 available replicas; policy requires at least 2 of each",
                report.runtimeMessage());
        assertEquals(List.of("Wait until at least 2 replicas are Ready before presenting"),
                report.recommendations());
    }

    @Test
    void crashLoopBackOffIsUnhealthy() {
        Pod crashing = new PodBuilder().withNewMetadata().withName("crashing").endMetadata()
                .withNewStatus().withPhase("Running")
                .withContainerStatuses(new ContainerStatusBuilder().withName("app").withRestartCount(4)
                        .withState(new ContainerStateBuilder().withNewWaiting()
                                .withReason("CrashLoopBackOff").endWaiting().build())
                        .build())
                .endStatus().build();

        RuntimeHealthReport report = validator.validate(deployment(2, 1, 1, 1), List.of(crashing), 2);

        assertEquals(RuntimeStatus.UNHEALTHY, report.runtimeStatus());
        assertEquals(4, report.totalRestarts());
        assertEquals("Investigate CrashLoopBackOff before starting the demo",
                report.recommendations().getFirst());
    }

    @Test
    void restartsWithoutActiveCrashLoopAreAWarning() {
        RuntimeHealthReport report = validator.validate(deployment(2, 2, 2, 0),
                List.of(runningPod("one", 2), runningPod("two", 1)), 2);

        assertEquals(RuntimeStatus.DEGRADED, report.runtimeStatus());
        assertEquals(3, report.totalRestarts());
        assertEquals("Deployment containers have restarted 3 times with no active crash loop",
                report.runtimeMessage());
    }

    private Deployment deployment(int desired, int ready, int available, int unavailable) {
        return new DeploymentBuilder().withNewSpec().withReplicas(desired).endSpec()
                .withNewStatus().withReadyReplicas(ready).withAvailableReplicas(available)
                .withUnavailableReplicas(unavailable).endStatus().build();
    }

    private Pod runningPod(String name, int restarts) {
        return new PodBuilder().withNewMetadata().withName(name).endMetadata()
                .withNewStatus().withPhase("Running")
                .withContainerStatuses(new ContainerStatusBuilder().withName("app")
                        .withRestartCount(restarts).build())
                .endStatus().build();
    }
}

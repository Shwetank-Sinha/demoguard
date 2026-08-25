package io.demoguard.validation;

import io.demoguard.api.ReadinessStatus;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentValidatorTest {

    private final DeploymentValidator validator = new DeploymentValidator();

    @Test
    void safeDeploymentPassesEveryRule() {
        Deployment deployment = safeDeployment();
        ReadinessReport report = validator.validate(deployment, Optional.of(matchingPdb()), 2);

        assertEquals(100, report.getScore());
        assertEquals(ReadinessStatus.READY, report.getReadinessStatus());
        assertTrue(report.getFindings().isEmpty());
        assertTrue(report.getRecommendations().isEmpty());
    }

    @Test
    void unsafeDeploymentFailsEveryRule() {
        Deployment deployment = new DeploymentBuilder(safeDeployment())
                .editSpec()
                    .withReplicas(1)
                    .withStrategy(new DeploymentStrategyBuilder()
                            .withType("RollingUpdate")
                            .withRollingUpdate(new RollingUpdateDeploymentBuilder()
                                    .withMaxUnavailable(new IntOrString(1)).build())
                            .build())
                    .editTemplate().editSpec()
                        .withContainers(new ContainerBuilder().withName("app").withImage("app:1").build())
                    .endSpec().endTemplate()
                .endSpec()
                .build();

        ReadinessReport report = validator.validate(deployment, Optional.empty(), 2);

        assertEquals(0, report.getScore());
        assertEquals(ReadinessStatus.BLOCKED, report.getReadinessStatus());
        assertEquals(DeploymentValidator.CHECK_COUNT, report.getFindings().size());
        assertEquals(DeploymentValidator.CHECK_COUNT, report.getRecommendations().size());
    }

    @Test
    void replicaRuleAcceptsMinimumAndRejectsLowerOrMissingReplicas() {
        assertTrue(validator.hasMinimumReplicas(safeDeployment(), 3));
        assertFalse(validator.hasMinimumReplicas(
                new DeploymentBuilder(safeDeployment()).editSpec().withReplicas(2).endSpec().build(), 3));
        assertFalse(validator.hasMinimumReplicas(
                new DeploymentBuilder(safeDeployment()).editSpec().withReplicas(null).endSpec().build(), 3));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(safeDeployment(), Optional.of(matchingPdb()), -1));
    }

    @Test
    void readinessProbeRuleRequiresProbeOnEveryApplicationContainer() {
        Deployment oneMissing = new DeploymentBuilder(safeDeployment())
                .editSpec().editTemplate().editSpec()
                    .addToContainers(new ContainerBuilder().withName("sidecar").withImage("sidecar:1").build())
                .endSpec().endTemplate().endSpec().build();

        assertTrue(validator.allApplicationContainersHaveReadinessProbes(safeDeployment()));
        assertFalse(validator.allApplicationContainersHaveReadinessProbes(oneMissing));
        assertFalse(validator.allApplicationContainersHaveReadinessProbes(deploymentWithNoContainers()));
    }

    @Test
    void rollingUpdateRuleRequiresExplicitZero() {
        assertTrue(validator.hasZeroRollingUpdateMaxUnavailable(safeDeployment()));
        assertTrue(validator.hasZeroRollingUpdateMaxUnavailable(withMaxUnavailable(new IntOrString("0%"))));
        assertFalse(validator.hasZeroRollingUpdateMaxUnavailable(withMaxUnavailable(new IntOrString(1))));
        assertFalse(validator.hasZeroRollingUpdateMaxUnavailable(withMaxUnavailable(new IntOrString("25%"))));
        assertFalse(validator.hasZeroRollingUpdateMaxUnavailable(
                new DeploymentBuilder(safeDeployment()).editSpec().withStrategy(null).endSpec().build()));
    }

    @Test
    void resourcesRuleRequiresCpuAndMemoryInRequestsAndLimitsForEveryContainer() {
        assertTrue(validator.allApplicationContainersHaveResourceRequirements(safeDeployment()));

        Deployment missingMemoryLimit = deploymentWithResources(
                Map.of("cpu", new Quantity("100m"), "memory", new Quantity("128Mi")),
                Map.of("cpu", new Quantity("500m")));
        Deployment missingRequests = deploymentWithResources(Map.of(),
                Map.of("cpu", new Quantity("500m"), "memory", new Quantity("256Mi")));

        assertFalse(validator.allApplicationContainersHaveResourceRequirements(missingMemoryLimit));
        assertFalse(validator.allApplicationContainersHaveResourceRequirements(missingRequests));
        assertFalse(validator.allApplicationContainersHaveResourceRequirements(deploymentWithNoContainers()));
    }

    @Test
    void pdbRuleRequiresPresenceSameNamespaceAndMatchingSelector() {
        Deployment deployment = safeDeployment();
        assertTrue(validator.hasMatchingPodDisruptionBudget(deployment, Optional.of(matchingPdb())));
        assertFalse(validator.hasMatchingPodDisruptionBudget(deployment, Optional.empty()));
        assertFalse(validator.hasMatchingPodDisruptionBudget(deployment,
                Optional.of(pdb("other", Map.of("app", "demo")))));
        assertFalse(validator.hasMatchingPodDisruptionBudget(deployment,
                Optional.of(pdb("demo", Map.of("app", "other")))));
        assertFalse(validator.hasMatchingPodDisruptionBudget(deployment,
                Optional.of(pdb("demo", Map.of()))));
    }

    private Deployment safeDeployment() {
        return new DeploymentBuilder()
                .withNewMetadata().withName("demo").withNamespace("demo").endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .withStrategy(new DeploymentStrategyBuilder()
                            .withType("RollingUpdate")
                            .withRollingUpdate(new RollingUpdateDeploymentBuilder()
                                    .withMaxUnavailable(new IntOrString(0)).build())
                            .build())
                    .withTemplate(new PodTemplateSpecBuilder()
                            .withNewMetadata().withLabels(Map.of("app", "demo", "tier", "web")).endMetadata()
                            .withNewSpec().withContainers(new ContainerBuilder()
                                    .withName("app").withImage("app:1")
                                    .withReadinessProbe(new ProbeBuilder()
                                            .withNewHttpGet().withPath("/ready").withNewPort(8080).endHttpGet()
                                            .build())
                                    .withResources(new ResourceRequirementsBuilder()
                                            .withRequests(Map.of("cpu", new Quantity("100m"),
                                                    "memory", new Quantity("128Mi")))
                                            .withLimits(Map.of("cpu", new Quantity("500m"),
                                                    "memory", new Quantity("256Mi")))
                                            .build())
                                    .build()).endSpec()
                            .build())
                .endSpec()
                .build();
    }

    private Deployment withMaxUnavailable(IntOrString maxUnavailable) {
        return new DeploymentBuilder(safeDeployment()).editSpec()
                .editStrategy().editRollingUpdate().withMaxUnavailable(maxUnavailable)
                .endRollingUpdate().endStrategy().endSpec().build();
    }

    private Deployment deploymentWithResources(Map<String, Quantity> requests,
                                                Map<String, Quantity> limits) {
        return new DeploymentBuilder(safeDeployment()).editSpec().editTemplate().editSpec()
                .withContainers(new ContainerBuilder()
                        .withName("app").withImage("app:1")
                        .withReadinessProbe(new ProbeBuilder().withNewExec().withCommand("true").endExec().build())
                        .withResources(new ResourceRequirementsBuilder()
                                .withRequests(requests).withLimits(limits).build())
                        .build())
                .endSpec().endTemplate().endSpec().build();
    }

    private Deployment deploymentWithNoContainers() {
        return new DeploymentBuilder(safeDeployment()).editSpec().editTemplate().editSpec()
                .withContainers().endSpec().endTemplate().endSpec().build();
    }

    private PodDisruptionBudget matchingPdb() {
        return pdb("demo", Map.of("app", "demo"));
    }

    private PodDisruptionBudget pdb(String namespace, Map<String, String> labels) {
        return new PodDisruptionBudgetBuilder()
                .withNewMetadata().withName("demo").withNamespace(namespace).endMetadata()
                .withNewSpec().withNewSelector().withMatchLabels(labels).endSelector().endSpec()
                .build();
    }
}

package io.demoguard.validation;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DeploymentValidator {

    public static final int CHECK_COUNT = 5;

    public ReadinessReport validate(Deployment deployment,
                                    Optional<PodDisruptionBudget> podDisruptionBudget,
                                    int minimumReplicas) {
        Objects.requireNonNull(deployment, "deployment must not be null");
        Objects.requireNonNull(podDisruptionBudget, "podDisruptionBudget must not be null");
        if (minimumReplicas < 0) {
            throw new IllegalArgumentException("minimumReplicas must not be negative");
        }

        List<String> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int passed = 0;

        passed += record(hasMinimumReplicas(deployment, minimumReplicas),
                "Deployment replicas are below the policy minimum of " + minimumReplicas,
                "Set spec.replicas to at least " + minimumReplicas, findings, recommendations);
        passed += record(allApplicationContainersHaveReadinessProbes(deployment),
                "One or more application containers have no readiness probe",
                "Configure a readinessProbe for every application container", findings, recommendations);
        passed += record(hasZeroRollingUpdateMaxUnavailable(deployment),
                "RollingUpdate maxUnavailable is not exactly zero",
                "Set spec.strategy.rollingUpdate.maxUnavailable to 0", findings, recommendations);
        passed += record(allApplicationContainersHaveResourceRequirements(deployment),
                "One or more application containers lack CPU or memory requests or limits",
                "Set CPU and memory requests and limits for every application container", findings, recommendations);
        passed += record(hasMatchingPodDisruptionBudget(deployment, podDisruptionBudget),
                "No matching PodDisruptionBudget exists",
                "Create a PodDisruptionBudget whose selector matches the deployment pods", findings, recommendations);

        return new ReadinessReport(passed, CHECK_COUNT, findings, recommendations);
    }

    public boolean hasMinimumReplicas(Deployment deployment, int minimumReplicas) {
        return deployment.getSpec() != null
                && deployment.getSpec().getReplicas() != null
                && deployment.getSpec().getReplicas() >= minimumReplicas;
    }

    public boolean allApplicationContainersHaveReadinessProbes(Deployment deployment) {
        List<Container> containers = containers(deployment);
        return !containers.isEmpty() && containers.stream().allMatch(container -> container.getReadinessProbe() != null);
    }

    public boolean hasZeroRollingUpdateMaxUnavailable(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getSpec().getStrategy() == null
                || !"RollingUpdate".equals(deployment.getSpec().getStrategy().getType())
                || deployment.getSpec().getStrategy().getRollingUpdate() == null) {
            return false;
        }
        return isZero(deployment.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable());
    }

    public boolean allApplicationContainersHaveResourceRequirements(Deployment deployment) {
        List<Container> containers = containers(deployment);
        return !containers.isEmpty() && containers.stream().allMatch(this::hasCpuAndMemoryRequestsAndLimits);
    }

    public boolean hasMatchingPodDisruptionBudget(Deployment deployment,
                                                   Optional<PodDisruptionBudget> podDisruptionBudget) {
        if (podDisruptionBudget.isEmpty() || deployment.getMetadata() == null
                || deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getMetadata() == null) {
            return false;
        }
        PodDisruptionBudget pdb = podDisruptionBudget.get();
        if (pdb.getMetadata() == null
                || !Objects.equals(deployment.getMetadata().getNamespace(), pdb.getMetadata().getNamespace())
                || pdb.getSpec() == null) {
            return false;
        }
        LabelSelector selector = pdb.getSpec().getSelector();
        Map<String, String> podLabels = deployment.getSpec().getTemplate().getMetadata().getLabels();
        if (selector == null || podLabels == null || selector.getMatchExpressions() != null
                && !selector.getMatchExpressions().isEmpty()) {
            return false;
        }
        Map<String, String> matchLabels = selector.getMatchLabels();
        return matchLabels != null && !matchLabels.isEmpty()
                && matchLabels.entrySet().stream()
                .allMatch(entry -> Objects.equals(podLabels.get(entry.getKey()), entry.getValue()));
    }

    private List<Container> containers(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getSpec().getTemplate() == null) {
            return List.of();
        }
        PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
        return podSpec == null || podSpec.getContainers() == null ? List.of() : podSpec.getContainers();
    }

    private boolean hasCpuAndMemoryRequestsAndLimits(Container container) {
        ResourceRequirements resources = container.getResources();
        return resources != null
                && containsNonEmpty(resources.getRequests(), "cpu")
                && containsNonEmpty(resources.getRequests(), "memory")
                && containsNonEmpty(resources.getLimits(), "cpu")
                && containsNonEmpty(resources.getLimits(), "memory");
    }

    private boolean containsNonEmpty(Map<String, Quantity> resources, String resourceName) {
        Quantity quantity = resources == null ? null : resources.get(resourceName);
        return quantity != null && quantity.getAmount() != null && !quantity.getAmount().isBlank();
    }

    private boolean isZero(IntOrString value) {
        if (value == null) {
            return false;
        }
        if (value.getIntVal() != null) {
            return value.getIntVal() == 0;
        }
        return "0".equals(value.getStrVal()) || "0%".equals(value.getStrVal());
    }

    private int record(boolean passed, String finding, String recommendation,
                       List<String> findings, List<String> recommendations) {
        if (passed) {
            return 1;
        }
        findings.add(finding);
        recommendations.add(recommendation);
        return 0;
    }
}

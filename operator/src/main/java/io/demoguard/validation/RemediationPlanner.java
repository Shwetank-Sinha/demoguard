package io.demoguard.validation;

import io.demoguard.api.RemediationPlan;
import io.demoguard.api.RemediationPlan.PatchFormat;
import io.demoguard.api.RemediationPlan.Severity;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RemediationPlanner {

    private final DeploymentValidator validator;

    public RemediationPlanner(DeploymentValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    public List<RemediationPlan> plansFor(Deployment deployment, Optional<PodDisruptionBudget> pdb,
                                          int minimumReplicas) {
        List<RemediationPlan> candidates = new ArrayList<>();
        String deploymentName = deployment.getMetadata().getName();
        String namespace = deployment.getMetadata().getNamespace();

        if (!validator.hasMinimumReplicas(deployment, minimumReplicas)) {
            candidates.add(plan("deployment-replicas", Severity.BLOCKING, "Deployment", deploymentName,
                    "Raise replicas to the policy minimum",
                    "The policy requires at least " + minimumReplicas + " replicas for demo availability.",
                    true, PatchFormat.YAML, deploymentPatch(deploymentName, namespace,
                            "spec:\n  replicas: " + minimumReplicas)));
        }
        if (!validator.allApplicationContainersHaveReadinessProbes(deployment)) {
            candidates.add(unsafe("deployment-readiness-probes", "Deployment", deploymentName,
                    "Configure missing readiness probes",
                    "The team must choose a probe type, endpoint or command, port, and timing values that reflect actual application readiness; DemoGuard will not invent them."));
        }
        if (!validator.hasZeroRollingUpdateMaxUnavailable(deployment)) {
            candidates.add(unsafe("deployment-rolling-update", "Deployment", deploymentName,
                    "Review rolling-update availability",
                    "The team must confirm that forcing maxUnavailable to zero is compatible with cluster capacity and the workload's rollout behavior."));
        }
        if (!validator.allApplicationContainersHaveResourceRequirements(deployment)) {
            candidates.add(unsafe("deployment-resources", "Deployment", deploymentName,
                    "Set CPU and memory requests and limits",
                    "The team must size CPU and memory from workload measurements and expected demo demand; DemoGuard will not invent resource quantities."));
        }

        if (pdb.isEmpty()) {
            LabelSelector selector = deployment.getSpec() == null ? null : deployment.getSpec().getSelector();
            if (selector != null && selector.getMatchLabels() != null && !selector.getMatchLabels().isEmpty()
                    && (selector.getMatchExpressions() == null || selector.getMatchExpressions().isEmpty())) {
                candidates.add(plan("pdb-missing", Severity.BLOCKING, "PodDisruptionBudget",
                        deploymentName + "-pdb", "Create a PodDisruptionBudget",
                        "A matching disruption budget protects the policy minimum during voluntary disruptions.",
                        true, PatchFormat.YAML,
                        pdbManifest(deploymentName + "-pdb", namespace, minimumReplicas,
                                selector.getMatchLabels())));
            } else {
                candidates.add(unsafe("pdb-missing", "PodDisruptionBudget", deploymentName + "-pdb",
                        "Create a PodDisruptionBudget",
                        "The Deployment selector cannot be represented safely as a simple matchLabels selector; the team must review and choose the PDB selector."));
            }
        } else if (!validator.hasAdequatePdbMinAvailable(pdb.get(), minimumReplicas)) {
            String pdbName = pdb.get().getMetadata().getName();
            candidates.add(plan("pdb-min-available", Severity.BLOCKING, "PodDisruptionBudget", pdbName,
                    "Raise PDB minAvailable to the policy minimum",
                    "The existing disruption budget permits availability below the policy minimum of "
                            + minimumReplicas + ".", true, PatchFormat.YAML,
                    pdbPatch(pdbName, namespace, minimumReplicas)));
        }

        Map<String, RemediationPlan> unique = new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparing(RemediationPlan::getId)
                        .thenComparing(RemediationPlan::getTargetKind)
                        .thenComparing(RemediationPlan::getTargetName))
                .forEach(candidate -> unique.putIfAbsent(candidate.getId(), candidate));
        return List.copyOf(unique.values());
    }

    public static String summary(List<RemediationPlan> plans) {
        if (plans.isEmpty()) return "No static remediation is required";
        long safe = plans.stream().filter(RemediationPlan::isSafeToApply).count();
        return plans.size() + " static remediation plan(s): " + safe
                + " reviewable patch(es), " + (plans.size() - safe) + " requiring team decisions";
    }

    private RemediationPlan unsafe(String id, String kind, String name, String summary, String rationale) {
        return plan(id, Severity.WARNING, kind, name, summary, rationale, false, PatchFormat.NONE, null);
    }

    private RemediationPlan plan(String id, Severity severity, String kind, String name, String summary,
                                 String rationale, boolean safe, PatchFormat format, String patch) {
        return new RemediationPlan(id, severity, kind, name, summary, rationale, safe, format, patch);
    }

    private String deploymentPatch(String name, String namespace, String body) {
        return "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: " + yaml(name)
                + "\n  namespace: " + yaml(namespace) + "\n" + body + "\n";
    }

    private String pdbPatch(String name, String namespace, int minimumReplicas) {
        return "apiVersion: policy/v1\nkind: PodDisruptionBudget\nmetadata:\n  name: " + yaml(name)
                + "\n  namespace: " + yaml(namespace) + "\nspec:\n  minAvailable: " + minimumReplicas + "\n";
    }

    private String pdbManifest(String name, String namespace, int minimumReplicas, Map<String, String> labels) {
        StringBuilder manifest = new StringBuilder(pdbPatch(name, namespace, minimumReplicas));
        manifest.append("  selector:\n    matchLabels:\n");
        labels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> manifest
                .append("      ").append(yaml(entry.getKey())).append(": ")
                .append(yaml(entry.getValue())).append('\n'));
        return manifest.toString();
    }

    private String yaml(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}

package io.demoguard.reconciler;

import io.demoguard.api.DemoPolicy;
import io.demoguard.api.DemoPolicySpec;
import io.demoguard.api.DemoReadiness;
import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.validation.DeploymentValidator;
import io.demoguard.validation.ReadinessReport;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ControllerConfiguration
public final class DemoPolicyReconciler implements Reconciler<DemoPolicy> {

    private static final String READINESS_SUFFIX = "-readiness";

    private final KubernetesClient client;
    private final DeploymentValidator validator;

    public DemoPolicyReconciler(KubernetesClient client) {
        this(client, new DeploymentValidator());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public UpdateControl<DemoPolicy> reconcile(DemoPolicy policy, Context<DemoPolicy> context) {
        String policyNamespace = policy.getMetadata().getNamespace();
        DemoPolicySpec spec = Objects.requireNonNull(policy.getSpec(), "DemoPolicy spec must not be null");

        Deployment deployment = client.apps().deployments()
                .inNamespace(spec.getTargetNamespace())
                .withName(spec.getTargetDeployment())
                .get();

        DemoReadinessStatus status;
        if (deployment == null) {
            status = missingDeploymentStatus(spec);
        } else {
            Optional<PodDisruptionBudget> pdb = findMatchingPdb(deployment,
                    client.policy().v1().podDisruptionBudget()
                            .inNamespace(spec.getTargetNamespace()).list().getItems(), validator);
            int minimumReplicas = spec.getMinimumReplicas() == null
                    ? DemoPolicySpec.DEFAULT_MINIMUM_REPLICAS : spec.getMinimumReplicas();
            status = statusFrom(validator.validate(deployment, pdb, minimumReplicas));
        }

        upsertReadiness(policy.getMetadata().getName(), policyNamespace, status);
        return UpdateControl.noUpdate();
    }

    private void upsertReadiness(String policyName, String namespace, DemoReadinessStatus status) {
        String name = readinessName(policyName);
        DemoReadiness readiness = client.resources(DemoReadiness.class)
                .inNamespace(namespace).withName(name).get();
        if (readiness == null) {
            readiness = new DemoReadiness();
            readiness.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(namespace).build());
            readiness = client.resources(DemoReadiness.class).inNamespace(namespace).resource(readiness).create();
        }
        readiness.setStatus(status);
        client.resources(DemoReadiness.class).inNamespace(namespace).resource(readiness).updateStatus();
    }

    static Optional<PodDisruptionBudget> findMatchingPdb(
            Deployment deployment, List<PodDisruptionBudget> budgets, DeploymentValidator validator) {
        if (budgets == null) {
            return Optional.empty();
        }
        return budgets.stream()
                .filter(pdb -> validator.hasMatchingPodDisruptionBudget(deployment, Optional.of(pdb)))
                .findFirst();
    }

    static String readinessName(String policyName) {
        return policyName + READINESS_SUFFIX;
    }

    static DemoReadinessStatus statusFrom(ReadinessReport report) {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(report.getReadinessStatus());
        status.setScore(report.getScore());
        status.setFindings(report.getFindings());
        status.setRecommendations(report.getRecommendations());
        return status;
    }

    private static DemoReadinessStatus missingDeploymentStatus(DemoPolicySpec spec) {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(ReadinessStatus.BLOCKED);
        status.setScore(0);
        status.setFindings(List.of("Target Deployment " + spec.getTargetNamespace() + "/"
                + spec.getTargetDeployment() + " was not found"));
        status.setRecommendations(List.of("Create the target Deployment or correct the DemoPolicy target"));
        return status;
    }
}

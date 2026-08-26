package io.demoguard.reconciler;

import io.demoguard.api.DemoPolicy;
import io.demoguard.api.DemoPolicySpec;
import io.demoguard.api.DemoReadiness;
import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.prediction.MemoryForecaster;
import io.demoguard.prediction.MemoryForecaster.Forecast;
import io.demoguard.prediction.MemoryForecaster.MemoryRisk;
import io.demoguard.prometheus.PrometheusClient;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ControllerConfiguration(generationAwareEventProcessing = false)
public final class DemoPolicyReconciler implements Reconciler<DemoPolicy> {

    private static final String READINESS_SUFFIX = "-readiness";

    private final KubernetesClient client;
    private final DeploymentValidator validator;
    private final PrometheusClient prometheus;
    private final MemoryForecaster forecaster;

    public DemoPolicyReconciler(KubernetesClient client) {
        this(client, new DeploymentValidator(), new PrometheusClient(), new MemoryForecaster());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator) {
        this(client, validator, new PrometheusClient(), new MemoryForecaster());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator,
                         PrometheusClient prometheus, MemoryForecaster forecaster) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.prometheus = Objects.requireNonNull(prometheus, "prometheus must not be null");
        this.forecaster = Objects.requireNonNull(forecaster, "forecaster must not be null");
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
            addMemoryForecast(deployment, spec, status);
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

    private void addMemoryForecast(Deployment deployment, DemoPolicySpec spec, DemoReadinessStatus status) {
        try {
            List<String> podNames = client.pods().inNamespace(spec.getTargetNamespace())
                    .withLabels(deployment.getSpec().getSelector().getMatchLabels()).list().getItems().stream()
                    .map(pod -> pod.getMetadata().getName()).toList();
            if (podNames.isEmpty()) {
                unknownForecast(status, "No pods for the target Deployment were found in Prometheus scope");
                return;
            }

            String usageQuery = PrometheusClient.memoryUsageQuery(spec.getTargetNamespace(), podNames);
            String limitQuery = PrometheusClient.memoryLimitQuery(spec.getTargetNamespace(), podNames);
            var current = prometheus.query(usageQuery);
            var limit = prometheus.query(limitQuery);
            if (current.isEmpty() || limit.isEmpty() || limit.getAsDouble() <= 0) {
                unknownForecast(status, "Prometheus did not return current memory usage and a positive memory limit");
                return;
            }

            long currentBytes = Math.round(current.getAsDouble());
            long limitBytes = Math.round(limit.getAsDouble());
            status.setCurrentMemoryBytes(currentBytes);
            status.setMemoryLimitBytes(limitBytes);

            int demoMinutes = spec.getDemoDurationMinutes() == null
                    ? DemoPolicySpec.DEFAULT_DEMO_DURATION_MINUTES : spec.getDemoDurationMinutes();
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofMinutes(Math.max(demoMinutes, 30)));
            Forecast forecast = forecaster.forecast(
                    prometheus.queryRange(usageQuery, start, end, Duration.ofMinutes(1)),
                    limitBytes, demoMinutes);
            status.setMemoryRisk(forecast.risk());
            status.setPredictedMemoryBytesAtDemoEnd(forecast.predictedMemoryBytesAtDemoEnd());
            status.setPredictedLimitBreachInMinutes(forecast.predictedLimitBreachInMinutes());
            status.setPredictionMessage(forecast.message());

            if (forecast.risk() == MemoryRisk.AT_RISK && status.getReadinessStatus() == ReadinessStatus.READY) {
                status.setReadinessStatus(ReadinessStatus.WARNING);
                List<String> findings = new ArrayList<>(status.getFindings());
                findings.add("Memory usage may reach its limit during the declared demo duration");
                status.setFindings(findings);
                List<String> recommendations = new ArrayList<>(status.getRecommendations());
                recommendations.add("Review memory growth and capacity before the demo");
                status.setRecommendations(recommendations);
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            unknownForecast(status, "Memory forecast unavailable because Prometheus could not be queried");
        }
    }

    private static void unknownForecast(DemoReadinessStatus status, String message) {
        status.setMemoryRisk(MemoryRisk.UNKNOWN);
        status.setPredictionMessage(message);
    }

    private static DemoReadinessStatus missingDeploymentStatus(DemoPolicySpec spec) {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(ReadinessStatus.BLOCKED);
        status.setScore(0);
        status.setMemoryRisk(MemoryRisk.UNKNOWN);
        status.setPredictionMessage("Memory forecast unavailable because the target Deployment was not found");
        status.setFindings(List.of("Target Deployment " + spec.getTargetNamespace() + "/"
                + spec.getTargetDeployment() + " was not found"));
        status.setRecommendations(List.of("Create the target Deployment or correct the DemoPolicy target"));
        return status;
    }
}

package io.demoguard.reconciler;

import io.demoguard.api.DemoPolicy;
import io.demoguard.api.DemoPolicySpec;
import io.demoguard.api.DemoReadiness;
import io.demoguard.api.DemoReadinessStatus;
import io.demoguard.api.ReadinessStatus;
import io.demoguard.api.RemediationPlan;
import io.demoguard.api.RolloutStatus;
import io.demoguard.prediction.MemoryForecaster;
import io.demoguard.prediction.CpuForecaster;
import io.demoguard.prediction.CpuForecaster.CpuRisk;
import io.demoguard.prediction.MemoryForecaster.Forecast;
import io.demoguard.prediction.MemoryForecaster.MemoryRisk;
import io.demoguard.prometheus.PrometheusClient;
import io.demoguard.runtime.RuntimeHealthReport;
import io.demoguard.runtime.RuntimeHealthValidator;
import io.demoguard.rollout.DeploymentRolloutAnalyzer;
import io.demoguard.rollout.RolloutReport;
import io.demoguard.validation.DeploymentValidator;
import io.demoguard.validation.ReadinessReport;
import io.demoguard.validation.RemediationPlanner;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ControllerConfiguration(generationAwareEventProcessing = false)
public final class DemoPolicyReconciler implements Reconciler<DemoPolicy> {

    private static final String READINESS_SUFFIX = "-readiness";
    private static final int WARNING_PENALTY = 20;
    private static final int WARNING_SCORE_FLOOR = 60;
    private static final int BLOCKED_SCORE_CAP = 40;

    private final KubernetesClient client;
    private final DeploymentValidator validator;
    private final PrometheusClient prometheus;
    private final MemoryForecaster forecaster;
    private final CpuForecaster cpuForecaster;
    private final RuntimeHealthValidator runtimeValidator;
    private final DeploymentRolloutAnalyzer rolloutAnalyzer;

    public DemoPolicyReconciler(KubernetesClient client) {
        this(client, new DeploymentValidator(), new PrometheusClient(), new MemoryForecaster(), new CpuForecaster(),
                new RuntimeHealthValidator(), new DeploymentRolloutAnalyzer());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator) {
        this(client, validator, new PrometheusClient(), new MemoryForecaster(), new CpuForecaster(),
                new RuntimeHealthValidator(), new DeploymentRolloutAnalyzer());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator,
                         PrometheusClient prometheus, MemoryForecaster forecaster) {
        this(client, validator, prometheus, forecaster, new CpuForecaster(), new RuntimeHealthValidator(),
                new DeploymentRolloutAnalyzer());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator,
                         PrometheusClient prometheus, MemoryForecaster forecaster,
                         CpuForecaster cpuForecaster,
                         RuntimeHealthValidator runtimeValidator) {
        this(client, validator, prometheus, forecaster, cpuForecaster, runtimeValidator,
                new DeploymentRolloutAnalyzer());
    }

    DemoPolicyReconciler(KubernetesClient client, DeploymentValidator validator,
                         PrometheusClient prometheus, MemoryForecaster forecaster,
                         CpuForecaster cpuForecaster, RuntimeHealthValidator runtimeValidator,
                         DeploymentRolloutAnalyzer rolloutAnalyzer) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.prometheus = Objects.requireNonNull(prometheus, "prometheus must not be null");
        this.forecaster = Objects.requireNonNull(forecaster, "forecaster must not be null");
        this.cpuForecaster = Objects.requireNonNull(cpuForecaster, "cpuForecaster must not be null");
        this.runtimeValidator = Objects.requireNonNull(runtimeValidator, "runtimeValidator must not be null");
        this.rolloutAnalyzer = Objects.requireNonNull(rolloutAnalyzer, "rolloutAnalyzer must not be null");
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
        ReadinessStatus staticStatus;
        if (deployment == null) {
            status = missingDeploymentStatus(spec);
            staticStatus = ReadinessStatus.BLOCKED;
        } else {
            Optional<PodDisruptionBudget> pdb = findMatchingPdb(deployment,
                    client.policy().v1().podDisruptionBudget()
                            .inNamespace(spec.getTargetNamespace()).list().getItems(), validator);
            int minimumReplicas = spec.getMinimumReplicas() == null
                    ? DemoPolicySpec.DEFAULT_MINIMUM_REPLICAS : spec.getMinimumReplicas();
            status = statusFrom(validator.validate(deployment, pdb, minimumReplicas));
            staticStatus = status.getReadinessStatus();
            List<RemediationPlan> remediationPlans =
                    new RemediationPlanner(validator).plansFor(deployment, pdb, minimumReplicas);
            status.setRemediationPlans(remediationPlans);
            status.setRemediationSummary(RemediationPlanner.summary(remediationPlans));
            List<Pod> pods = targetPods(deployment, spec.getTargetNamespace());
            mergeRuntime(status, runtimeValidator.validate(deployment, pods, minimumReplicas));
            mergeRollout(status, rolloutAnalyzer.analyze(deployment));
            List<String> podNames = pods.stream().map(pod -> pod.getMetadata().getName()).toList();
            addMemoryForecast(spec, status, podNames);
            addCpuForecast(spec, status, podNames);
        }

        PreflightReportBuilder.populate(status, staticStatus);

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
                .sorted(Comparator.comparing(pdb -> pdb.getMetadata().getName()))
                .findFirst();
    }

    static String readinessName(String policyName) {
        return policyName + READINESS_SUFFIX;
    }

    static DemoReadinessStatus statusFrom(ReadinessReport report) {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(report.getReadinessStatus());
        status.setScore(report.getScore());
        status.setScoreMessage("Static validation score: " + report.getScore() + "/100");
        status.setFindings(report.getFindings());
        status.setRecommendations(report.getRecommendations());
        return status;
    }

    private List<Pod> targetPods(Deployment deployment, String namespace) {
        if (deployment.getSpec() == null || deployment.getSpec().getSelector() == null
                || deployment.getSpec().getSelector().getMatchLabels() == null) {
            return List.of();
        }
        return client.pods().inNamespace(namespace)
                .withLabels(deployment.getSpec().getSelector().getMatchLabels()).list().getItems();
    }

    static void mergeRuntime(DemoReadinessStatus status, RuntimeHealthReport runtime) {
        status.setRuntimeStatus(runtime.runtimeStatus());
        status.setDesiredReplicas(runtime.desiredReplicas());
        status.setReadyReplicas(runtime.readyReplicas());
        status.setAvailableReplicas(runtime.availableReplicas());
        status.setUnavailableReplicas(runtime.unavailableReplicas());
        status.setTotalRestarts(runtime.totalRestarts());
        status.setRuntimeMessage(runtime.runtimeMessage());
        status.setFindings(append(status.getFindings(), runtime.findings()));
        status.setRecommendations(append(status.getRecommendations(), runtime.recommendations()));

        if (runtime.runtimeStatus() == io.demoguard.api.RuntimeStatus.UNHEALTHY) {
            status.setScore(Math.min(status.getScore(), BLOCKED_SCORE_CAP));
            status.setReadinessStatus(ReadinessStatus.BLOCKED);
            appendScoreMessage(status, "runtime UNHEALTHY capped score at " + BLOCKED_SCORE_CAP);
        } else if (runtime.runtimeStatus() == io.demoguard.api.RuntimeStatus.DEGRADED
                && status.getReadinessStatus() != ReadinessStatus.BLOCKED) {
            applyWarningPenalty(status, "runtime DEGRADED");
        }
    }

    static void mergeRollout(DemoReadinessStatus status, RolloutReport rollout) {
        status.setRolloutStatus(rollout.rolloutStatus());
        status.setDeploymentGeneration(rollout.deploymentGeneration());
        status.setObservedGeneration(rollout.observedGeneration());
        status.setUpdatedReplicas(rollout.updatedReplicas());
        status.setRolloutMessage(rollout.rolloutMessage());

        if (rollout.rolloutStatus() == RolloutStatus.STALLED) {
            status.setFindings(append(status.getFindings(), List.of(rollout.rolloutMessage())));
            status.setRecommendations(append(status.getRecommendations(),
                    List.of("Rollback or fix the stalled Deployment before starting the demo")));
            status.setScore(Math.min(status.getScore(), BLOCKED_SCORE_CAP));
            status.setReadinessStatus(ReadinessStatus.BLOCKED);
            appendScoreMessage(status, "rollout STALLED capped score at " + BLOCKED_SCORE_CAP);
        } else if (rollout.rolloutStatus() == RolloutStatus.ROLLING_OUT) {
            status.setFindings(append(status.getFindings(), List.of(rollout.rolloutMessage())));
            status.setRecommendations(append(status.getRecommendations(),
                    List.of("Wait for the rollout to complete before presenting")));
            if (status.getReadinessStatus() != ReadinessStatus.BLOCKED) {
                applyWarningPenalty(status, "rollout ROLLING_OUT");
            }
        }
    }

    private static List<String> append(List<String> existing, List<String> additions) {
        List<String> combined = new ArrayList<>(existing);
        combined.addAll(additions);
        return combined;
    }

    private void addMemoryForecast(DemoPolicySpec spec, DemoReadinessStatus status, List<String> podNames) {
        try {
            if (podNames.isEmpty()) {
                unknownMemoryForecast(status, "No pods for the target Deployment were found in Prometheus scope");
                return;
            }

            String usageQuery = PrometheusClient.memoryUsageQuery(spec.getTargetNamespace(), podNames);
            String limitQuery = PrometheusClient.memoryLimitQuery(spec.getTargetNamespace(), podNames);
            var current = prometheus.query(usageQuery);
            var limit = prometheus.query(limitQuery);
            if (current.isEmpty() || limit.isEmpty() || limit.getAsDouble() <= 0) {
                unknownMemoryForecast(status, "Prometheus did not return current memory usage and a positive memory limit");
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

            if (forecast.risk() == MemoryRisk.AT_RISK) {
                applyMemoryRiskWarning(status);
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            unknownMemoryForecast(status,
                    "Memory forecast unavailable because Prometheus could not be queried: " + detail);
        }
    }

    private void addCpuForecast(DemoPolicySpec spec, DemoReadinessStatus status, List<String> podNames) {
        try {
            if (podNames.isEmpty()) {
                unknownCpuForecast(status, "No pods for the target Deployment were found in Prometheus scope");
                return;
            }
            String usageQuery = PrometheusClient.cpuUsageQuery(spec.getTargetNamespace(), podNames);
            var current = prometheus.query(usageQuery);
            var limit = prometheus.query(PrometheusClient.cpuLimitQuery(spec.getTargetNamespace(), podNames));
            if (current.isEmpty() || limit.isEmpty() || limit.getAsDouble() <= 0) {
                unknownCpuForecast(status, "Prometheus did not return current CPU usage and a positive CPU limit");
                return;
            }
            status.setCurrentCpuCores(current.getAsDouble());
            status.setCpuLimitCores(limit.getAsDouble());
            var throttling = prometheus.query(
                    PrometheusClient.cpuThrottlingQuery(spec.getTargetNamespace(), podNames));
            Double throttlingRate = throttling.isPresent() ? throttling.getAsDouble() : null;
            status.setCpuThrottlingRate(throttlingRate);

            int demoMinutes = spec.getDemoDurationMinutes() == null
                    ? DemoPolicySpec.DEFAULT_DEMO_DURATION_MINUTES : spec.getDemoDurationMinutes();
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofMinutes(Math.max(demoMinutes, 30)));
            var forecast = cpuForecaster.forecast(
                    prometheus.queryCpuRange(usageQuery, start, end, Duration.ofMinutes(1)),
                    limit.getAsDouble(), demoMinutes, throttlingRate);
            status.setCpuRisk(forecast.risk());
            status.setPredictedCpuCoresAtDemoEnd(forecast.predictedCpuCoresAtDemoEnd());
            status.setCpuPredictionMessage(forecast.message());
            if (forecast.risk() == CpuRisk.AT_RISK) applyCpuRiskWarning(status);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            unknownCpuForecast(status,
                    "CPU forecast unavailable because Prometheus could not be queried: " + detail);
        }
    }

    static void applyMemoryRiskWarning(DemoReadinessStatus status) {
        if (status.getReadinessStatus() != ReadinessStatus.BLOCKED) {
            applyWarningPenalty(status, "memoryRisk AT_RISK");
            List<String> findings = new ArrayList<>(status.getFindings());
            findings.add("Memory usage may reach its limit during the declared demo duration");
            status.setFindings(findings);
            List<String> recommendations = new ArrayList<>(status.getRecommendations());
            recommendations.add("Review memory growth and capacity before the demo");
            status.setRecommendations(recommendations);
        }
    }

    static void applyCpuRiskWarning(DemoReadinessStatus status) {
        if (status.getReadinessStatus() != ReadinessStatus.BLOCKED) {
            applyWarningPenalty(status, "cpuRisk AT_RISK");
            status.setFindings(append(status.getFindings(),
                    List.of("CPU usage or throttling may affect the workload during the demo")));
            status.setRecommendations(append(status.getRecommendations(),
                    List.of("Review CPU demand, limits, and throttling before the demo")));
        }
    }

    private static void applyWarningPenalty(DemoReadinessStatus status, String reason) {
        status.setScore(Math.max(WARNING_SCORE_FLOOR, status.getScore() - WARNING_PENALTY));
        status.setReadinessStatus(ReadinessStatus.WARNING);
        appendScoreMessage(status, reason + ": -" + WARNING_PENALTY
                + " points (WARNING floor " + WARNING_SCORE_FLOOR + ")");
    }

    private static void appendScoreMessage(DemoReadinessStatus status, String reason) {
        String existing = status.getScoreMessage();
        status.setScoreMessage(existing == null || existing.isBlank() ? reason : existing + "; " + reason);
    }

    private static void unknownMemoryForecast(DemoReadinessStatus status, String message) {
        status.setMemoryRisk(MemoryRisk.UNKNOWN);
        status.setPredictionMessage(message);
    }

    private static void unknownCpuForecast(DemoReadinessStatus status, String message) {
        status.setCpuRisk(CpuRisk.UNKNOWN);
        status.setCpuPredictionMessage(message);
    }

    private static DemoReadinessStatus missingDeploymentStatus(DemoPolicySpec spec) {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(ReadinessStatus.BLOCKED);
        status.setScore(0);
        status.setScoreMessage("Score 0: target Deployment was not found");
        status.setMemoryRisk(MemoryRisk.UNKNOWN);
        status.setPredictionMessage("Memory forecast unavailable because the target Deployment was not found");
        status.setCpuRisk(CpuRisk.UNKNOWN);
        status.setCpuPredictionMessage("CPU forecast unavailable because the target Deployment was not found");
        status.setRuntimeStatus(io.demoguard.api.RuntimeStatus.UNHEALTHY);
        status.setRuntimeMessage("Runtime health unavailable because the target Deployment was not found");
        status.setRolloutStatus(RolloutStatus.UNKNOWN);
        status.setRolloutMessage("Rollout state unavailable because the target Deployment was not found");
        status.setFindings(List.of("Target Deployment " + spec.getTargetNamespace() + "/"
                + spec.getTargetDeployment() + " was not found"));
        status.setRecommendations(List.of("Create the target Deployment or correct the DemoPolicy target"));
        return status;
    }
}

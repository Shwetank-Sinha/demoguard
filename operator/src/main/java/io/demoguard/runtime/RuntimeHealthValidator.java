package io.demoguard.runtime;

import io.demoguard.api.RuntimeStatus;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RuntimeHealthValidator {

    private static final Set<String> UNHEALTHY_WAITING_REASONS = Set.of(
            "CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull", "CreateContainerConfigError");

    public RuntimeHealthReport validate(Deployment deployment, List<Pod> pods, int minimumReplicas) {
        Objects.requireNonNull(deployment, "deployment must not be null");
        Objects.requireNonNull(pods, "pods must not be null");
        if (minimumReplicas < 0) {
            throw new IllegalArgumentException("minimumReplicas must not be negative");
        }

        DeploymentStatus status = deployment.getStatus();
        int desired = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
                ? 0 : deployment.getSpec().getReplicas();
        int ready = value(status == null ? null : status.getReadyReplicas());
        int available = value(status == null ? null : status.getAvailableReplicas());
        int unavailable = value(status == null ? null : status.getUnavailableReplicas());
        int restarts = pods.stream().mapToInt(this::restartCount).sum();
        Set<String> waitingReasons = new LinkedHashSet<>();
        pods.forEach(pod -> containerStatuses(pod).stream()
                .map(ContainerStatus::getState)
                .filter(Objects::nonNull)
                .map(state -> state.getWaiting())
                .filter(Objects::nonNull)
                .map(ContainerStateWaiting::getReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .forEach(waitingReasons::add));
        boolean failedPod = pods.stream().anyMatch(pod -> pod.getStatus() != null
                && "Failed".equals(pod.getStatus().getPhase()));

        List<String> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        RuntimeStatus runtimeStatus;
        String message;

        if (waitingReasons.contains("CrashLoopBackOff")) {
            runtimeStatus = RuntimeStatus.UNHEALTHY;
            message = "A target Deployment container is in CrashLoopBackOff";
            findings.add(message);
            recommendations.add("Investigate CrashLoopBackOff before starting the demo");
        } else if (failedPod) {
            runtimeStatus = RuntimeStatus.UNHEALTHY;
            message = "A pod belonging to the target Deployment is in Failed phase";
            findings.add(message);
            recommendations.add("Investigate the failed pod before starting the demo");
        } else if (waitingReasons.stream().anyMatch(UNHEALTHY_WAITING_REASONS::contains)) {
            String reasonList = waitingReasons.stream().filter(UNHEALTHY_WAITING_REASONS::contains)
                    .reduce((left, right) -> left + ", " + right).orElseThrow();
            runtimeStatus = RuntimeStatus.UNHEALTHY;
            message = "A target Deployment container is waiting: " + reasonList;
            findings.add(message);
            recommendations.add("Resolve " + reasonList + " before starting the demo");
        } else if (ready == 0 || available == 0) {
            runtimeStatus = RuntimeStatus.UNHEALTHY;
            message = "Deployment has " + ready + " Ready and " + available
                    + " available replicas; both must be greater than zero";
            findings.add(message);
            recommendations.add("Wait until at least " + Math.max(1, minimumReplicas)
                    + " replicas are Ready before presenting");
        } else if (ready < minimumReplicas || available < minimumReplicas) {
            runtimeStatus = RuntimeStatus.DEGRADED;
            message = "Deployment has " + ready + " Ready and " + available
                    + " available replicas; policy requires at least " + minimumReplicas + " of each";
            findings.add(message);
            recommendations.add("Wait until at least " + minimumReplicas + " replicas are Ready before presenting");
        } else if (ready < desired || available < desired || unavailable > 0) {
            runtimeStatus = RuntimeStatus.DEGRADED;
            message = "Deployment rollout is incomplete: desired=" + desired + ", Ready=" + ready
                    + ", available=" + available + ", unavailable=" + unavailable;
            findings.add(message);
            recommendations.add("Wait until all " + desired + " desired replicas are Ready before presenting");
        } else if (!waitingReasons.isEmpty()) {
            String reasonList = String.join(", ", waitingReasons);
            runtimeStatus = RuntimeStatus.DEGRADED;
            message = "A target Deployment container is waiting: " + reasonList;
            findings.add(message);
            recommendations.add("Investigate " + reasonList + " before presenting");
        } else if (hasNonRunningPod(pods)) {
            runtimeStatus = RuntimeStatus.DEGRADED;
            message = "One or more target Deployment pods are not in Running phase";
            findings.add(message);
            recommendations.add("Wait for all target Deployment pods to be Running before presenting");
        } else if (restarts > 0) {
            runtimeStatus = RuntimeStatus.DEGRADED;
            message = "Deployment containers have restarted " + restarts
                    + " time" + (restarts == 1 ? "" : "s") + " with no active crash loop";
            findings.add(message);
            recommendations.add("Review container restart history before presenting");
        } else {
            runtimeStatus = RuntimeStatus.HEALTHY;
            message = "Deployment is fully ready and available with no detected runtime issues";
        }

        return new RuntimeHealthReport(runtimeStatus, desired, ready, available, unavailable,
                restarts, message, findings, recommendations);
    }

    private int restartCount(Pod pod) {
        return containerStatuses(pod).stream()
                .map(ContainerStatus::getRestartCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private List<ContainerStatus> containerStatuses(Pod pod) {
        if (pod.getStatus() == null) {
            return List.of();
        }
        List<ContainerStatus> statuses = new ArrayList<>();
        if (pod.getStatus().getInitContainerStatuses() != null) {
            statuses.addAll(pod.getStatus().getInitContainerStatuses());
        }
        if (pod.getStatus().getContainerStatuses() != null) {
            statuses.addAll(pod.getStatus().getContainerStatuses());
        }
        return statuses;
    }

    private boolean hasNonRunningPod(List<Pod> pods) {
        return pods.stream().anyMatch(pod -> pod.getStatus() == null
                || !"Running".equals(pod.getStatus().getPhase()));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}

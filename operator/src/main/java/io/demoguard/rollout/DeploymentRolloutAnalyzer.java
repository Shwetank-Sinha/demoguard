package io.demoguard.rollout;

import io.demoguard.api.RolloutStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;

import java.util.List;
import java.util.Objects;

public final class DeploymentRolloutAnalyzer {

    public RolloutReport analyze(Deployment deployment) {
        Objects.requireNonNull(deployment, "deployment must not be null");

        Long generation = deployment.getMetadata() == null ? null : deployment.getMetadata().getGeneration();
        DeploymentStatus status = deployment.getStatus();
        Long observed = status == null ? null : status.getObservedGeneration();
        Integer desired = deployment.getSpec() == null ? null : deployment.getSpec().getReplicas();
        Integer updated = status == null ? null : status.getUpdatedReplicas();
        Integer available = status == null ? null : status.getAvailableReplicas();
        Integer ready = status == null ? null : status.getReadyReplicas();
        Integer unavailable = status == null ? null : status.getUnavailableReplicas();
        List<DeploymentCondition> conditions = status == null || status.getConditions() == null
                ? List.of() : status.getConditions();

        DeploymentCondition stalled = conditions.stream().filter(this::isStalledCondition).findFirst().orElse(null);
        if (stalled != null) {
            String reason = meaningful(stalled.getReason());
            return report(RolloutStatus.STALLED, generation, observed, updated,
                    "Deployment rollout is stalled" + conditionDetail(reason, stalled.getMessage()), reason);
        }

        if (generation == null || observed == null || desired == null || updated == null
                || available == null || ready == null || unavailable == null) {
            return report(RolloutStatus.UNKNOWN, generation, observed, updated,
                    "Deployment rollout state is unknown because required generation or replica status is absent", null);
        }

        if (observed >= generation && updated >= desired && available >= desired && ready >= desired
                && unavailable == 0) {
            return report(RolloutStatus.STABLE, generation, observed, updated,
                    "Deployment generation " + generation + " is fully observed and all " + desired
                            + " desired replicas are updated, Ready, and available", null);
        }

        return report(RolloutStatus.ROLLING_OUT, generation, observed, updated,
                "Deployment rollout is in progress: generation=" + generation + ", observedGeneration="
                        + observed + ", desired=" + desired + ", updated=" + updated + ", Ready=" + ready
                        + ", available=" + available + ", unavailable=" + unavailable, rolloutReason(conditions));
    }

    private boolean isStalledCondition(DeploymentCondition condition) {
        String type = condition.getType();
        String status = condition.getStatus();
        String reason = condition.getReason();
        return "ProgressDeadlineExceeded".equals(reason)
                || ("Progressing".equals(type) && "False".equalsIgnoreCase(status))
                || ("ReplicaFailure".equals(type) && "True".equalsIgnoreCase(status));
    }

    private String rolloutReason(List<DeploymentCondition> conditions) {
        return conditions.stream()
                .filter(condition -> "Progressing".equals(condition.getType())
                        || ("Available".equals(condition.getType())
                        && "False".equalsIgnoreCase(condition.getStatus())))
                .map(DeploymentCondition::getReason)
                .map(this::meaningful)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private RolloutReport report(RolloutStatus rolloutStatus, Long generation, Long observed,
                                 Integer updated, String message, String reason) {
        String reasonSuffix = reason == null || message.contains(reason) ? "" : " (Kubernetes reason: " + reason + ")";
        return new RolloutReport(rolloutStatus, generation, observed, updated == null ? 0 : updated,
                message + reasonSuffix, reason);
    }

    private String conditionDetail(String reason, String message) {
        String detail = meaningful(message);
        if (reason == null && detail == null) return "";
        if (reason == null) return ": " + detail;
        if (detail == null) return " (Kubernetes reason: " + reason + ")";
        return " (Kubernetes reason: " + reason + "): " + detail;
    }

    private String meaningful(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

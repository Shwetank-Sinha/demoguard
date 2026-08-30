package io.demoguard.rollout;

import io.demoguard.api.RolloutStatus;

public record RolloutReport(
        RolloutStatus rolloutStatus,
        Long deploymentGeneration,
        Long observedGeneration,
        int updatedReplicas,
        String rolloutMessage,
        String conditionReason) {
}

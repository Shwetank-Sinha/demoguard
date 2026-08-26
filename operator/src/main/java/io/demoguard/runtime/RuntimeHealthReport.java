package io.demoguard.runtime;

import io.demoguard.api.RuntimeStatus;

import java.util.List;

public record RuntimeHealthReport(
        RuntimeStatus runtimeStatus,
        int desiredReplicas,
        int readyReplicas,
        int availableReplicas,
        int unavailableReplicas,
        int totalRestarts,
        String runtimeMessage,
        List<String> findings,
        List<String> recommendations) {

    public RuntimeHealthReport {
        findings = List.copyOf(findings);
        recommendations = List.copyOf(recommendations);
    }
}

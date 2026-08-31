package io.demoguard.dashboard.api;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record NamespaceDto(String name) {}

    public record PolicySpecDto(String targetNamespace, String targetDeployment,
                                Integer minimumReplicas, Integer demoDurationMinutes) {}

    public record DemoPolicyDto(String namespace, String name, String resourceVersion,
                                Instant creationTimestamp, PolicySpecDto spec) {}

    public record PreflightCheckDto(String category, String status, String message,
                                    String recommendation) {}

    public record RemediationPlanDto(String id, String severity, String targetKind,
                                     String targetName, String summary, String rationale,
                                     boolean safeToApply, String patchFormat, String patch) {}

    public record DemoReadinessDto(
            String namespace, String name, String resourceVersion, Instant creationTimestamp,
            Instant lastAssessedAt,
            String readinessStatus, int score, String scoreMessage,
            List<String> findings, List<String> recommendations,
            String remediationSummary, List<RemediationPlanDto> remediationPlans,
            String memoryRisk, Long currentMemoryBytes, Long memoryLimitBytes,
            Long predictedMemoryBytesAtDemoEnd, Double predictedLimitBreachInMinutes,
            String predictionMessage, String cpuRisk, Double currentCpuCores,
            Double cpuLimitCores, Double predictedCpuCoresAtDemoEnd, Double cpuThrottlingRate,
            String cpuPredictionMessage, String runtimeStatus, int desiredReplicas,
            int readyReplicas, int availableReplicas, int unavailableReplicas,
            int totalRestarts, String runtimeMessage, String rolloutStatus,
            Long deploymentGeneration, Long observedGeneration, int updatedReplicas,
            String rolloutMessage, String preflightStatus, String preflightSummary,
            List<PreflightCheckDto> preflightChecks) {}

    public record PreflightDto(DemoPolicyDto policy, DemoReadinessDto readiness,
                               boolean pending, String message) {}

    public record RefreshResponse(String namespace, String name, boolean requested,
                                  long requestedAtEpochSeconds, String message) {}

    public record ApiError(String code, String message, Instant timestamp) {}
}

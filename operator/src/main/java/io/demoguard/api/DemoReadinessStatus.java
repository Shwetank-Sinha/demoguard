package io.demoguard.api;

import io.demoguard.prediction.MemoryForecaster.MemoryRisk;
import io.demoguard.prediction.CpuForecaster.CpuRisk;

import java.util.ArrayList;
import java.util.List;

public class DemoReadinessStatus {

    private ReadinessStatus readinessStatus;
    private int score;
    private String scoreMessage;
    private List<String> findings = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private String remediationSummary = "No static remediation is required";
    private List<RemediationPlan> remediationPlans = new ArrayList<>();
    private MemoryRisk memoryRisk = MemoryRisk.UNKNOWN;
    private Long currentMemoryBytes;
    private Long memoryLimitBytes;
    private Long predictedMemoryBytesAtDemoEnd;
    private Double predictedLimitBreachInMinutes;
    private String predictionMessage = "Memory forecast has not been evaluated";
    private CpuRisk cpuRisk = CpuRisk.UNKNOWN;
    private Double currentCpuCores;
    private Double cpuLimitCores;
    private Double predictedCpuCoresAtDemoEnd;
    private Double cpuThrottlingRate;
    private String cpuPredictionMessage = "CPU forecast has not been evaluated";
    private RuntimeStatus runtimeStatus;
    private int desiredReplicas;
    private int readyReplicas;
    private int availableReplicas;
    private int unavailableReplicas;
    private int totalRestarts;
    private String runtimeMessage;
    private RolloutStatus rolloutStatus = RolloutStatus.UNKNOWN;
    private Long deploymentGeneration;
    private Long observedGeneration;
    private int updatedReplicas;
    private String rolloutMessage = "Deployment rollout has not been evaluated";
    private ReadinessStatus preflightStatus;
    private String preflightSummary;
    private List<PreflightCheck> preflightChecks = new ArrayList<>();

    public ReadinessStatus getReadinessStatus() {
        return readinessStatus;
    }

    public void setReadinessStatus(ReadinessStatus readinessStatus) {
        this.readinessStatus = readinessStatus;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getScoreMessage() {
        return scoreMessage;
    }

    public void setScoreMessage(String scoreMessage) {
        this.scoreMessage = scoreMessage;
    }

    public List<String> getFindings() {
        return findings;
    }

    public void setFindings(List<String> findings) {
        this.findings = findings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public String getRemediationSummary() { return remediationSummary; }
    public void setRemediationSummary(String remediationSummary) { this.remediationSummary = remediationSummary; }
    public List<RemediationPlan> getRemediationPlans() { return remediationPlans; }
    public void setRemediationPlans(List<RemediationPlan> remediationPlans) {
        this.remediationPlans = remediationPlans;
    }

    public MemoryRisk getMemoryRisk() { return memoryRisk; }
    public void setMemoryRisk(MemoryRisk memoryRisk) { this.memoryRisk = memoryRisk; }
    public Long getCurrentMemoryBytes() { return currentMemoryBytes; }
    public void setCurrentMemoryBytes(Long currentMemoryBytes) { this.currentMemoryBytes = currentMemoryBytes; }
    public Long getMemoryLimitBytes() { return memoryLimitBytes; }
    public void setMemoryLimitBytes(Long memoryLimitBytes) { this.memoryLimitBytes = memoryLimitBytes; }
    public Long getPredictedMemoryBytesAtDemoEnd() { return predictedMemoryBytesAtDemoEnd; }
    public void setPredictedMemoryBytesAtDemoEnd(Long value) { this.predictedMemoryBytesAtDemoEnd = value; }
    public Double getPredictedLimitBreachInMinutes() { return predictedLimitBreachInMinutes; }
    public void setPredictedLimitBreachInMinutes(Double value) { this.predictedLimitBreachInMinutes = value; }
    public String getPredictionMessage() { return predictionMessage; }
    public void setPredictionMessage(String predictionMessage) { this.predictionMessage = predictionMessage; }
    public CpuRisk getCpuRisk() { return cpuRisk; }
    public void setCpuRisk(CpuRisk cpuRisk) { this.cpuRisk = cpuRisk; }
    public Double getCurrentCpuCores() { return currentCpuCores; }
    public void setCurrentCpuCores(Double currentCpuCores) { this.currentCpuCores = currentCpuCores; }
    public Double getCpuLimitCores() { return cpuLimitCores; }
    public void setCpuLimitCores(Double cpuLimitCores) { this.cpuLimitCores = cpuLimitCores; }
    public Double getPredictedCpuCoresAtDemoEnd() { return predictedCpuCoresAtDemoEnd; }
    public void setPredictedCpuCoresAtDemoEnd(Double value) { this.predictedCpuCoresAtDemoEnd = value; }
    public Double getCpuThrottlingRate() { return cpuThrottlingRate; }
    public void setCpuThrottlingRate(Double cpuThrottlingRate) { this.cpuThrottlingRate = cpuThrottlingRate; }
    public String getCpuPredictionMessage() { return cpuPredictionMessage; }
    public void setCpuPredictionMessage(String value) { this.cpuPredictionMessage = value; }
    public RuntimeStatus getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(RuntimeStatus runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    public int getDesiredReplicas() { return desiredReplicas; }
    public void setDesiredReplicas(int desiredReplicas) { this.desiredReplicas = desiredReplicas; }
    public int getReadyReplicas() { return readyReplicas; }
    public void setReadyReplicas(int readyReplicas) { this.readyReplicas = readyReplicas; }
    public int getAvailableReplicas() { return availableReplicas; }
    public void setAvailableReplicas(int availableReplicas) { this.availableReplicas = availableReplicas; }
    public int getUnavailableReplicas() { return unavailableReplicas; }
    public void setUnavailableReplicas(int unavailableReplicas) { this.unavailableReplicas = unavailableReplicas; }
    public int getTotalRestarts() { return totalRestarts; }
    public void setTotalRestarts(int totalRestarts) { this.totalRestarts = totalRestarts; }
    public String getRuntimeMessage() { return runtimeMessage; }
    public void setRuntimeMessage(String runtimeMessage) { this.runtimeMessage = runtimeMessage; }
    public RolloutStatus getRolloutStatus() { return rolloutStatus; }
    public void setRolloutStatus(RolloutStatus rolloutStatus) { this.rolloutStatus = rolloutStatus; }
    public Long getDeploymentGeneration() { return deploymentGeneration; }
    public void setDeploymentGeneration(Long deploymentGeneration) { this.deploymentGeneration = deploymentGeneration; }
    public Long getObservedGeneration() { return observedGeneration; }
    public void setObservedGeneration(Long observedGeneration) { this.observedGeneration = observedGeneration; }
    public int getUpdatedReplicas() { return updatedReplicas; }
    public void setUpdatedReplicas(int updatedReplicas) { this.updatedReplicas = updatedReplicas; }
    public String getRolloutMessage() { return rolloutMessage; }
    public void setRolloutMessage(String rolloutMessage) { this.rolloutMessage = rolloutMessage; }
    public ReadinessStatus getPreflightStatus() { return preflightStatus; }
    public void setPreflightStatus(ReadinessStatus preflightStatus) { this.preflightStatus = preflightStatus; }
    public String getPreflightSummary() { return preflightSummary; }
    public void setPreflightSummary(String preflightSummary) { this.preflightSummary = preflightSummary; }
    public List<PreflightCheck> getPreflightChecks() { return preflightChecks; }
    public void setPreflightChecks(List<PreflightCheck> preflightChecks) { this.preflightChecks = preflightChecks; }
}

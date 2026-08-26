package io.demoguard.api;

import io.demoguard.prediction.MemoryForecaster.MemoryRisk;

import java.util.ArrayList;
import java.util.List;

public class DemoReadinessStatus {

    private ReadinessStatus readinessStatus;
    private int score;
    private List<String> findings = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private MemoryRisk memoryRisk = MemoryRisk.UNKNOWN;
    private Long currentMemoryBytes;
    private Long memoryLimitBytes;
    private Long predictedMemoryBytesAtDemoEnd;
    private Double predictedLimitBreachInMinutes;
    private String predictionMessage = "Memory forecast has not been evaluated";

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
}

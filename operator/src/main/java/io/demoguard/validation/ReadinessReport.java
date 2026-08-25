package io.demoguard.validation;

import io.demoguard.api.ReadinessStatus;

import java.util.List;

public final class ReadinessReport {

    private final int passedChecks;
    private final int totalChecks;
    private final int score;
    private final ReadinessStatus readinessStatus;
    private final List<String> findings;
    private final List<String> recommendations;

    public ReadinessReport(int passedChecks, int totalChecks,
                           List<String> findings, List<String> recommendations) {
        if (totalChecks <= 0) {
            throw new IllegalArgumentException("totalChecks must be greater than zero");
        }
        if (passedChecks < 0 || passedChecks > totalChecks) {
            throw new IllegalArgumentException("passedChecks must be between zero and totalChecks");
        }
        this.passedChecks = passedChecks;
        this.totalChecks = totalChecks;
        this.score = (int) Math.round(passedChecks * 100.0 / totalChecks);
        this.readinessStatus = statusFor(score);
        this.findings = List.copyOf(findings);
        this.recommendations = List.copyOf(recommendations);
    }

    private static ReadinessStatus statusFor(int score) {
        if (score == 100) {
            return ReadinessStatus.READY;
        }
        if (score >= 60) {
            return ReadinessStatus.WARNING;
        }
        return ReadinessStatus.BLOCKED;
    }

    public int getPassedChecks() {
        return passedChecks;
    }

    public int getTotalChecks() {
        return totalChecks;
    }

    public int getScore() {
        return score;
    }

    public ReadinessStatus getReadinessStatus() {
        return readinessStatus;
    }

    public List<String> getFindings() {
        return findings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }
}

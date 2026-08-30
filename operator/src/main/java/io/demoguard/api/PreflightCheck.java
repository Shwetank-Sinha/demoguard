package io.demoguard.api;

public class PreflightCheck {

    public enum Category {
        STATIC, RUNTIME, MEMORY, CPU, ROLLOUT, REMEDIATION
    }

    public enum Status {
        PASS, WARNING, BLOCKED, UNKNOWN, NOT_REQUIRED
    }

    private Category category;
    private Status status;
    private String message;
    private String recommendation;

    public PreflightCheck() {
    }

    public PreflightCheck(Category category, Status status, String message, String recommendation) {
        this.category = category;
        this.status = status;
        this.message = message;
        this.recommendation = recommendation;
    }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}

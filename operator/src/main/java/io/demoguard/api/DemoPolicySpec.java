package io.demoguard.api;

public class DemoPolicySpec {

    public static final int DEFAULT_MINIMUM_REPLICAS = 2;
    public static final int DEFAULT_DEMO_DURATION_MINUTES = 30;

    private String targetNamespace;
    private String targetDeployment;
    private Integer minimumReplicas = DEFAULT_MINIMUM_REPLICAS;
    private Integer demoDurationMinutes = DEFAULT_DEMO_DURATION_MINUTES;

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }

    public String getTargetDeployment() {
        return targetDeployment;
    }

    public void setTargetDeployment(String targetDeployment) {
        this.targetDeployment = targetDeployment;
    }

    public Integer getMinimumReplicas() {
        return minimumReplicas;
    }

    public void setMinimumReplicas(Integer minimumReplicas) {
        this.minimumReplicas = minimumReplicas;
    }

    public Integer getDemoDurationMinutes() {
        return demoDurationMinutes;
    }

    public void setDemoDurationMinutes(Integer demoDurationMinutes) {
        this.demoDurationMinutes = demoDurationMinutes;
    }
}

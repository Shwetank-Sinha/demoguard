package io.demoguard.api;

public class DemoPolicySpec {

    public static final int DEFAULT_MINIMUM_REPLICAS = 2;

    private String targetNamespace;
    private String targetDeployment;
    private Integer minimumReplicas = DEFAULT_MINIMUM_REPLICAS;

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
}

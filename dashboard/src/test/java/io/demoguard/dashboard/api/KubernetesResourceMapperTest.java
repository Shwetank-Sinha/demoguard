package io.demoguard.dashboard.api;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesResourceMapperTest {
    private final KubernetesResourceMapper mapper = new KubernetesResourceMapper();

    @Test
    void mapsRealCustomResourceFieldsAndOrdersChecks() {
        GenericKubernetesResource resource = Serialization.unmarshal("""
                apiVersion: demoguard.dev/v1alpha1
                kind: DemoReadiness
                metadata:
                  name: checkout-readiness
                  namespace: demos
                  resourceVersion: "19"
                  creationTimestamp: "2026-08-31T10:15:30Z"
                  managedFields:
                    - manager: unrelated-controller
                      time: "2026-08-31T11:00:00Z"
                status:
                  lastAssessedAt: "2026-08-31T10:45:12.345Z"
                  readinessStatus: WARNING
                  score: 80
                  scoreMessage: "Static validation score: 100/100; CPU AT_RISK deducted 20 points"
                  findings: [CPU pressure]
                  recommendations: [Reduce load]
                  remediationSummary: One plan requires review
                  remediationPlans:
                    - id: replicas
                      severity: WARNING
                      targetKind: Deployment
                      targetName: checkout
                      summary: Raise replicas
                      rationale: Demo availability
                      safeToApply: true
                      patchFormat: YAML
                      patch: "spec:\n  replicas: 3"
                  memoryRisk: SAFE
                  currentMemoryBytes: 1048576
                  memoryLimitBytes: 2097152
                  predictedMemoryBytesAtDemoEnd: 1572864
                  predictionMessage: Memory remains below limit
                  cpuRisk: AT_RISK
                  currentCpuCores: 0.4
                  cpuLimitCores: 1.0
                  predictedCpuCoresAtDemoEnd: 0.9
                  cpuThrottlingRate: 0.12
                  cpuPredictionMessage: CPU may exceed threshold
                  runtimeStatus: HEALTHY
                  desiredReplicas: 3
                  updatedReplicas: 3
                  readyReplicas: 3
                  availableReplicas: 3
                  unavailableReplicas: 0
                  totalRestarts: 1
                  runtimeMessage: Pods are healthy
                  rolloutStatus: STABLE
                  deploymentGeneration: 7
                  observedGeneration: 7
                  rolloutMessage: Rollout is stable
                  preflightStatus: WARNING
                  preflightSummary: CPU is at risk
                  preflightChecks:
                    - category: STATIC
                      status: PASS
                      message: Static checks passed
                    - category: CPU
                      status: WARNING
                      message: CPU is at risk
                      recommendation: Reduce load
                """, GenericKubernetesResource.class);

        var dto = mapper.readiness(resource);

        assertThat(dto.namespace()).isEqualTo("demos");
        assertThat(dto.score()).isEqualTo(80);
        assertThat(dto.lastAssessedAt()).isEqualTo("2026-08-31T10:45:12.345Z");
        assertThat(dto.currentMemoryBytes()).isEqualTo(1048576);
        assertThat(dto.cpuThrottlingRate()).isEqualTo(0.12);
        assertThat(dto.preflightChecks()).extracting(DashboardDtos.PreflightCheckDto::category)
                .containsExactly("STATIC", "CPU");
        assertThat(dto.remediationPlans().getFirst().patch()).contains("replicas: 3");
    }

    @Test
    void mapsPolicySpecWithoutExposingTheKubernetesObject() {
        GenericKubernetesResource resource = Serialization.unmarshal("""
                apiVersion: demoguard.dev/v1alpha1
                kind: DemoPolicy
                metadata:
                  name: checkout
                  namespace: demos
                spec:
                  targetNamespace: workloads
                  targetDeployment: checkout-api
                  minimumReplicas: 3
                  demoDurationMinutes: 45
                """, GenericKubernetesResource.class);

        var dto = mapper.policy(resource);
        assertThat(dto.spec().targetDeployment()).isEqualTo("checkout-api");
        assertThat(dto.spec().demoDurationMinutes()).isEqualTo(45);
    }
}

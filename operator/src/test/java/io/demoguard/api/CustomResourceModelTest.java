package io.demoguard.api;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.demoguard.prediction.MemoryForecaster.MemoryRisk;
import io.demoguard.prediction.CpuForecaster.CpuRisk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CustomResourceModelTest {

    @Test
    void policyUsesExpectedApiAndDefaultMinimumReplicas() {
        assertEquals("demoguard.dev", DemoPolicy.class.getAnnotation(Group.class).value());
        assertEquals("v1alpha1", DemoPolicy.class.getAnnotation(Version.class).value());
        assertInstanceOf(Namespaced.class, new DemoPolicy());
        assertEquals(2, new DemoPolicySpec().getMinimumReplicas());
        assertEquals(30, new DemoPolicySpec().getDemoDurationMinutes());
    }

    @Test
    void readinessCarriesRequiredStatusFields() {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(ReadinessStatus.WARNING);
        status.setScore(80);
        status.setFindings(List.of("finding"));
        status.setRecommendations(List.of("recommendation"));
        status.setMemoryRisk(MemoryRisk.SAFE);
        status.setCurrentMemoryBytes(100L);
        status.setMemoryLimitBytes(200L);
        status.setPredictedMemoryBytesAtDemoEnd(150L);
        status.setPredictedLimitBreachInMinutes(45.0);
        status.setPredictionMessage("limited-confidence forecast");
        status.setCpuRisk(CpuRisk.AT_RISK);
        status.setCurrentCpuCores(0.7);
        status.setCpuLimitCores(1.0);
        status.setPredictedCpuCoresAtDemoEnd(0.9);
        status.setCpuThrottlingRate(0.12);
        status.setCpuPredictionMessage("CPU forecast");
        status.setRuntimeStatus(RuntimeStatus.DEGRADED);
        status.setDesiredReplicas(3);
        status.setReadyReplicas(2);
        status.setAvailableReplicas(2);
        status.setUnavailableReplicas(1);
        status.setTotalRestarts(4);
        status.setRuntimeMessage("one replica is unavailable");

        DemoReadiness readiness = new DemoReadiness();
        readiness.setStatus(status);

        assertInstanceOf(Namespaced.class, readiness);
        assertEquals(ReadinessStatus.WARNING, readiness.getStatus().getReadinessStatus());
        assertEquals(80, readiness.getStatus().getScore());
        assertEquals(List.of("finding"), readiness.getStatus().getFindings());
        assertEquals(List.of("recommendation"), readiness.getStatus().getRecommendations());
        assertEquals(MemoryRisk.SAFE, readiness.getStatus().getMemoryRisk());
        assertEquals(100L, readiness.getStatus().getCurrentMemoryBytes());
        assertEquals(200L, readiness.getStatus().getMemoryLimitBytes());
        assertEquals(150L, readiness.getStatus().getPredictedMemoryBytesAtDemoEnd());
        assertEquals(45.0, readiness.getStatus().getPredictedLimitBreachInMinutes());
        assertEquals("limited-confidence forecast", readiness.getStatus().getPredictionMessage());
        assertEquals(CpuRisk.AT_RISK, readiness.getStatus().getCpuRisk());
        assertEquals(0.7, readiness.getStatus().getCurrentCpuCores());
        assertEquals(1.0, readiness.getStatus().getCpuLimitCores());
        assertEquals(0.9, readiness.getStatus().getPredictedCpuCoresAtDemoEnd());
        assertEquals(0.12, readiness.getStatus().getCpuThrottlingRate());
        assertEquals("CPU forecast", readiness.getStatus().getCpuPredictionMessage());
        assertEquals(RuntimeStatus.DEGRADED, readiness.getStatus().getRuntimeStatus());
        assertEquals(3, readiness.getStatus().getDesiredReplicas());
        assertEquals(2, readiness.getStatus().getReadyReplicas());
        assertEquals(2, readiness.getStatus().getAvailableReplicas());
        assertEquals(1, readiness.getStatus().getUnavailableReplicas());
        assertEquals(4, readiness.getStatus().getTotalRestarts());
        assertEquals("one replica is unavailable", readiness.getStatus().getRuntimeMessage());
    }
}

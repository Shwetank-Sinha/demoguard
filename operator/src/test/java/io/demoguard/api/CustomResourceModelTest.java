package io.demoguard.api;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
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
    }

    @Test
    void readinessCarriesRequiredStatusFields() {
        DemoReadinessStatus status = new DemoReadinessStatus();
        status.setReadinessStatus(ReadinessStatus.WARNING);
        status.setScore(80);
        status.setFindings(List.of("finding"));
        status.setRecommendations(List.of("recommendation"));

        DemoReadiness readiness = new DemoReadiness();
        readiness.setStatus(status);

        assertInstanceOf(Namespaced.class, readiness);
        assertEquals(ReadinessStatus.WARNING, readiness.getStatus().getReadinessStatus());
        assertEquals(80, readiness.getStatus().getScore());
        assertEquals(List.of("finding"), readiness.getStatus().getFindings());
        assertEquals(List.of("recommendation"), readiness.getStatus().getRecommendations());
    }
}

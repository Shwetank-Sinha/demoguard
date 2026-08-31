package io.demoguard.dashboard;

import io.demoguard.dashboard.api.KubernetesDashboardService;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardApplicationContextTest {
    @Autowired KubernetesDashboardService service;
    @Autowired KubernetesClient client;

    @Test
    void applicationContextStartsWithDashboardServiceAndKubernetesClient() {
        assertThat(service).isNotNull();
        assertThat(client).isNotNull();
    }
}

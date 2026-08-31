package io.demoguard.dashboard.api;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingReadinessIntegrationTest {
    private KubernetesMockServer server;
    private KubernetesClient client;

    @BeforeEach
    void start() {
        server = new KubernetesMockServer(true);
        server.init();
        client = server.createClient();
    }

    @AfterEach
    void stop() { server.destroy(); }

    @Test
    void missingReadinessIsAValidPendingResponse() {
        server.expect().get()
                .withPath("/apis/demoguard.dev/v1alpha1/namespaces/demos/demopolicies/checkout")
                .andReturn(200, """
                        {"apiVersion":"demoguard.dev/v1alpha1","kind":"DemoPolicy",
                         "metadata":{"name":"checkout","namespace":"demos"},
                         "spec":{"targetNamespace":"demos","targetDeployment":"checkout"}}
                        """).once();
        server.expect().get()
                .withPath("/apis/demoguard.dev/v1alpha1/namespaces/demos/demoreadinesses/checkout-readiness")
                .andReturn(404, null).once();
        var service = new KubernetesDashboardService(client, new KubernetesResourceMapper());

        var result = service.preflight("demos", "checkout");

        assertThat(result.pending()).isTrue();
        assertThat(result.readiness()).isNull();
        assertThat(result.message()).isEqualTo(
                "Assessment requested; waiting for the operator to publish DemoReadiness.");
    }
}

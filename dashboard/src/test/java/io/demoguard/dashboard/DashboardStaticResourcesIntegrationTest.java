package io.demoguard.dashboard;

import io.demoguard.dashboard.api.DashboardDtos.NamespaceDto;
import io.demoguard.dashboard.api.KubernetesDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardStaticResourcesIntegrationTest {
    @Autowired TestRestTemplate http;
    @MockitoBean KubernetesDashboardService service;

    @Test
    void servesDashboardAtRootAndKeepsApiRoutesAsJson() {
        when(service.namespaces()).thenReturn(List.of(new NamespaceDto("demos")));

        ResponseEntity<String> dashboard = http.getForEntity("/", String.class);

        assertThat(dashboard.getStatusCode().value()).isEqualTo(200);
        assertThat(dashboard.getHeaders().getContentType()).isNotNull();
        assertThat(dashboard.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(dashboard.getBody())
                .contains("<title>DemoGuard Dashboard</title>")
                .contains("<div id=\"root\"></div>");

        ResponseEntity<String> namespaces = http.getForEntity("/api/namespaces", String.class);

        assertThat(namespaces.getStatusCode().value()).isEqualTo(200);
        assertThat(namespaces.getHeaders().getContentType()).isNotNull();
        assertThat(namespaces.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(namespaces.getBody()).isEqualTo("[{\"name\":\"demos\"}]");
    }
}

package io.demoguard.dashboard.api;

import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean KubernetesDashboardService service;

    @Test
    void returnsNotFoundWithoutLeakingDetails() throws Exception {
        when(service.preflight("demos", "missing"))
                .thenThrow(new ResourceNotFoundException("DemoPolicy demos/missing was not found"));
        mvc.perform(get("/api/demopolicies/demos/missing/preflight"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("DemoPolicy demos/missing was not found"));
    }

    @Test
    void returnsForbiddenAsARecognizablePermissionState() throws Exception {
        when(service.namespaces()).thenThrow(new KubernetesClientException("token=secret", 403, null));
        mvc.perform(get("/api/namespaces"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void returnsServiceUnavailableForKubernetesApiFailure() throws Exception {
        when(service.namespaces()).thenThrow(new KubernetesClientException("https://cluster:6443 failed", 0, null));
        mvc.perform(get("/api/namespaces"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KUBERNETES_API_UNAVAILABLE"));
    }
}

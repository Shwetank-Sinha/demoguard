package io.demoguard.dashboard.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesDashboardServiceTest {
    @Test
    void refreshPatchContainsOnlyTheRefreshAnnotation() throws Exception {
        JsonNode patch = JacksonHolder.MAPPER.readTree(KubernetesDashboardService.buildRefreshPatch(1788163200L));

        assertThat(patch.size()).isOne();
        assertThat(patch.path("metadata").size()).isOne();
        assertThat(patch.path("metadata").path("annotations").size()).isOne();
        assertThat(patch.at("/metadata/annotations/demoguard.dev~1refresh").asText())
                .isEqualTo("1788163200");
    }
}

package io.demoguard.prometheus;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrometheusClientTest {
    @Test
    void buildsPromqlCompatiblePodRegex() {
        String regex = PrometheusClient.podRegex(List.of("web-abc", "worker.v1-def"));

        assertFalse(regex.contains("\\Q"));
        assertFalse(regex.contains("\\E"));
        assertEquals("web-abc|worker\\\\.v1-def", regex);
    }

    @Test
    void constructsCadvisorAndLimitQueriesForTargetPods() {
        String usage = PrometheusClient.memoryUsageQuery("demo", List.of("web-abc", "web-def"));
        String limit = PrometheusClient.memoryLimitQuery("demo", List.of("web-abc", "web-def"));

        assertTrue(usage.startsWith("sum(container_memory_working_set_bytes"));
        assertTrue(usage.contains("namespace=\"demo\""));
        assertTrue(usage.contains("pod=~\"web-abc|web-def\""));
        assertFalse(usage.contains("\\Q"));
        assertFalse(usage.contains("\\E"));
        assertTrue(limit.startsWith("sum(kube_pod_container_resource_limits"));
        assertTrue(limit.contains("pod=~\"web-abc|web-def\""));
        assertTrue(limit.contains("resource=\"memory\""));
        assertTrue(limit.contains("unit=\"byte\""));
    }

    @Test
    void constructsCpuUsageLimitAndThrottlingQueries() {
        var pods = List.of("web-abc", "web-def");
        String usage = PrometheusClient.cpuUsageQuery("demo", pods);
        String limit = PrometheusClient.cpuLimitQuery("demo", pods);
        String throttling = PrometheusClient.cpuThrottlingQuery("demo", pods);

        assertTrue(usage.startsWith("sum(rate(container_cpu_usage_seconds_total"));
        assertTrue(usage.contains("[5m]"));
        assertTrue(limit.startsWith("sum(kube_pod_container_resource_limits"));
        assertTrue(limit.contains("resource=\"cpu\""));
        assertTrue(limit.contains("unit=\"core\""));
        assertTrue(throttling.startsWith("sum(rate(container_cpu_cfs_throttled_seconds_total"));
        assertTrue(throttling.contains("pod=~\"web-abc|web-def\""));
    }

    @Test
    void constructsEncodedInstantAndRangeApiUris() {
        String query = "sum(metric{namespace=\"demo\"})";
        var instant = PrometheusClient.buildInstantQueryUri("http://prometheus:9090", query);
        var range = PrometheusClient.buildRangeQueryUri("http://prometheus:9090", query,
                Instant.ofEpochSecond(100), Instant.ofEpochSecond(400), Duration.ofSeconds(60));

        assertEquals("/api/v1/query", instant.getPath());
        assertEquals("query=" + query, URLDecoder.decode(instant.getRawQuery(), StandardCharsets.UTF_8));
        String decodedRange = URLDecoder.decode(range.getRawQuery(), StandardCharsets.UTF_8);
        assertEquals("/api/v1/query_range", range.getPath());
        assertTrue(decodedRange.contains("query=" + query));
        assertTrue(decodedRange.contains("start=100"));
        assertTrue(decodedRange.contains("end=400"));
        assertTrue(decodedRange.contains("step=60"));
    }
}

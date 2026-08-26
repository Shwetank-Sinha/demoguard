package io.demoguard.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.demoguard.prediction.MemoryForecaster.MemorySample;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class PrometheusClient {

    public static final String DEFAULT_BASE_URL = "http://localhost:9090";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PrometheusClient() {
        this(System.getenv().getOrDefault("PROMETHEUS_URL", DEFAULT_BASE_URL));
    }

    public PrometheusClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper());
    }

    PrometheusClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public OptionalDouble query(String promql) throws IOException, InterruptedException {
        JsonNode result = execute(buildInstantQueryUri(baseUrl, promql)).path("data").path("result");
        if (!result.isArray() || result.isEmpty()) return OptionalDouble.empty();
        JsonNode value = result.get(0).path("value");
        return value.isArray() && value.size() > 1
                ? OptionalDouble.of(Double.parseDouble(value.get(1).asText())) : OptionalDouble.empty();
    }

    public List<MemorySample> queryRange(String promql, Instant start, Instant end, Duration step)
            throws IOException, InterruptedException {
        JsonNode result = execute(buildRangeQueryUri(baseUrl, promql, start, end, step))
                .path("data").path("result");
        List<MemorySample> samples = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode series : result) {
                for (JsonNode value : series.path("values")) {
                    samples.add(new MemorySample(value.get(0).asLong(), Double.parseDouble(value.get(1).asText())));
                }
            }
        }
        return samples;
    }

    private JsonNode execute(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Prometheus returned HTTP " + response.statusCode());
        JsonNode root = objectMapper.readTree(response.body());
        if (!"success".equals(root.path("status").asText())) throw new IOException("Prometheus query failed");
        return root;
    }

    public static String memoryUsageQuery(String namespace, List<String> podNames) {
        return "sum(container_memory_working_set_bytes{namespace=\"" + escape(namespace)
                + "\",pod=~\"" + podRegex(podNames) + "\",container!=\"\",image!=\"\"})";
    }

    public static String memoryLimitQuery(String namespace, List<String> podNames) {
        return "sum(kube_pod_container_resource_limits{namespace=\"" + escape(namespace)
                + "\",pod=~\"" + podRegex(podNames) + "\",resource=\"memory\",unit=\"byte\"})";
    }

    static URI buildInstantQueryUri(String baseUrl, String query) {
        return URI.create(baseUrl + "/api/v1/query?query=" + encode(query));
    }

    static URI buildRangeQueryUri(String baseUrl, String query, Instant start, Instant end, Duration step) {
        return URI.create(baseUrl + "/api/v1/query_range?" + encodeParameters(Map.of(
                "query", query, "start", Long.toString(start.getEpochSecond()),
                "end", Long.toString(end.getEpochSecond()), "step", Long.toString(step.toSeconds()))));
    }

    private static String encodeParameters(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    static String podRegex(List<String> podNames) {
        return podNames.stream()
                .map(name -> name.replace(".", "\\\\."))
                .collect(Collectors.joining("|"));
    }
}

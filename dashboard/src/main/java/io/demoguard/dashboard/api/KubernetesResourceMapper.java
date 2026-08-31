package io.demoguard.dashboard.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.demoguard.dashboard.api.DashboardDtos.*;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class KubernetesResourceMapper {
    public DemoPolicyDto policy(GenericKubernetesResource resource) {
        JsonNode spec = node(resource, "spec");
        return new DemoPolicyDto(namespace(resource), name(resource), resourceVersion(resource),
                timestamp(resource), new PolicySpecDto(text(spec, "targetNamespace"),
                text(spec, "targetDeployment"), integer(spec, "minimumReplicas"),
                integer(spec, "demoDurationMinutes")));
    }

    public DemoReadinessDto readiness(GenericKubernetesResource resource) {
        JsonNode s = node(resource, "status");
        List<PreflightCheckDto> checks = new ArrayList<>();
        JsonNode checkNodes = s.path("preflightChecks");
        if (checkNodes.isArray()) {
            checkNodes.forEach(c -> checks.add(new PreflightCheckDto(text(c, "category"),
                    text(c, "status"), text(c, "message"), text(c, "recommendation"))));
        }
        List<RemediationPlanDto> plans = new ArrayList<>();
        JsonNode planNodes = s.path("remediationPlans");
        if (planNodes.isArray()) {
            planNodes.forEach(p -> plans.add(new RemediationPlanDto(text(p, "id"),
                    text(p, "severity"), text(p, "targetKind"), text(p, "targetName"),
                    text(p, "summary"), text(p, "rationale"), bool(p, "safeToApply"),
                    text(p, "patchFormat"), text(p, "patch"))));
        }

        return new DemoReadinessDto(namespace(resource), name(resource), resourceVersion(resource),
                timestamp(resource), instant(s, "lastAssessedAt"), text(s, "readinessStatus"), intValue(s, "score"),
                text(s, "scoreMessage"), strings(s, "findings"), strings(s, "recommendations"),
                text(s, "remediationSummary"), List.copyOf(plans), text(s, "memoryRisk"),
                longValue(s, "currentMemoryBytes"), longValue(s, "memoryLimitBytes"),
                longValue(s, "predictedMemoryBytesAtDemoEnd"), decimal(s, "predictedLimitBreachInMinutes"),
                text(s, "predictionMessage"), text(s, "cpuRisk"), decimal(s, "currentCpuCores"),
                decimal(s, "cpuLimitCores"), decimal(s, "predictedCpuCoresAtDemoEnd"),
                decimal(s, "cpuThrottlingRate"), text(s, "cpuPredictionMessage"),
                text(s, "runtimeStatus"), intValue(s, "desiredReplicas"), intValue(s, "readyReplicas"),
                intValue(s, "availableReplicas"), intValue(s, "unavailableReplicas"),
                intValue(s, "totalRestarts"), text(s, "runtimeMessage"), text(s, "rolloutStatus"),
                longValue(s, "deploymentGeneration"), longValue(s, "observedGeneration"),
                intValue(s, "updatedReplicas"), text(s, "rolloutMessage"),
                text(s, "preflightStatus"), text(s, "preflightSummary"), List.copyOf(checks));
    }

    private static JsonNode node(GenericKubernetesResource r, String name) {
        Object value = r.getAdditionalProperties().get(name);
        return JacksonHolder.MAPPER.valueToTree(value);
    }
    private static String namespace(GenericKubernetesResource r) { return r.getMetadata().getNamespace(); }
    private static String name(GenericKubernetesResource r) { return r.getMetadata().getName(); }
    private static String resourceVersion(GenericKubernetesResource r) { return r.getMetadata().getResourceVersion(); }
    private static Instant timestamp(GenericKubernetesResource r) {
        try { return r.getMetadata().getCreationTimestamp() == null ? null : Instant.parse(r.getMetadata().getCreationTimestamp()); }
        catch (DateTimeParseException ignored) { return null; }
    }
    private static Instant instant(JsonNode n, String key) {
        String value = text(n, key);
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }
    private static String text(JsonNode n, String key) { JsonNode v = n.path(key); return v.isTextual() ? v.asText() : null; }
    private static Integer integer(JsonNode n, String key) { JsonNode v = n.path(key); return v.isIntegralNumber() ? v.intValue() : null; }
    private static int intValue(JsonNode n, String key) { Integer v = integer(n, key); return v == null ? 0 : v; }
    private static Long longValue(JsonNode n, String key) { JsonNode v = n.path(key); return v.isNumber() ? v.longValue() : null; }
    private static Double decimal(JsonNode n, String key) { JsonNode v = n.path(key); return v.isNumber() ? v.doubleValue() : null; }
    private static boolean bool(JsonNode n, String key) { return n.path(key).asBoolean(false); }
    private static List<String> strings(JsonNode n, String key) {
        List<String> result = new ArrayList<>();
        JsonNode values = n.path(key);
        if (values.isArray()) values.forEach(value -> { if (value.isTextual()) result.add(value.asText()); });
        return List.copyOf(result);
    }
}

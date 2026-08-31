package io.demoguard.dashboard.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.demoguard.dashboard.api.DashboardDtos.*;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@Service
public class KubernetesDashboardService {
    static final String REFRESH_ANNOTATION = "demoguard.dev/refresh";
    private static final ResourceDefinitionContext POLICIES = new ResourceDefinitionContext.Builder()
            .withGroup("demoguard.dev").withVersion("v1alpha1").withPlural("demopolicies")
            .withKind("DemoPolicy").withNamespaced(true).build();
    private static final ResourceDefinitionContext READINESSES = new ResourceDefinitionContext.Builder()
            .withGroup("demoguard.dev").withVersion("v1alpha1").withPlural("demoreadinesses")
            .withKind("DemoReadiness").withNamespaced(true).build();

    private final KubernetesClient client;
    private final KubernetesResourceMapper mapper;
    private final Clock clock;

    @Autowired
    public KubernetesDashboardService(KubernetesClient client, KubernetesResourceMapper mapper) {
        this(client, mapper, Clock.systemUTC());
    }

    KubernetesDashboardService(KubernetesClient client, KubernetesResourceMapper mapper, Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<NamespaceDto> namespaces() {
        return client.namespaces().list().getItems().stream()
                .map(namespace -> new NamespaceDto(namespace.getMetadata().getName()))
                .sorted(Comparator.comparing(NamespaceDto::name)).toList();
    }

    public List<DemoPolicyDto> policies(String namespace) {
        validateNamespace(namespace);
        return client.genericKubernetesResources(POLICIES).inNamespace(namespace).list().getItems().stream()
                .map(mapper::policy).sorted(Comparator.comparing(DemoPolicyDto::name)).toList();
    }

    public List<DemoReadinessDto> readinesses(String namespace) {
        validateNamespace(namespace);
        return client.genericKubernetesResources(READINESSES).inNamespace(namespace).list().getItems().stream()
                .map(mapper::readiness).sorted(Comparator.comparing(DemoReadinessDto::name)).toList();
    }

    public PreflightDto preflight(String namespace, String name) {
        validate(namespace, name);
        GenericKubernetesResource policy = client.genericKubernetesResources(POLICIES)
                .inNamespace(namespace).withName(name).get();
        if (policy == null) throw new ResourceNotFoundException("DemoPolicy " + namespace + "/" + name + " was not found");
        GenericKubernetesResource readiness = client.genericKubernetesResources(READINESSES)
                .inNamespace(namespace).withName(name + "-readiness").get();
        if (readiness == null) {
            return new PreflightDto(mapper.policy(policy), null, true,
                    "Assessment requested; waiting for the operator to publish DemoReadiness.");
        }
        return new PreflightDto(mapper.policy(policy), mapper.readiness(readiness), false, null);
    }

    public RefreshResponse refresh(String namespace, String name) {
        validate(namespace, name);
        GenericKubernetesResource policy = client.genericKubernetesResources(POLICIES)
                .inNamespace(namespace).withName(name).get();
        if (policy == null) throw new ResourceNotFoundException("DemoPolicy " + namespace + "/" + name + " was not found");
        long timestamp = clock.instant().getEpochSecond();
        client.genericKubernetesResources(POLICIES).inNamespace(namespace).withName(name)
                .patch(new PatchContext.Builder().withPatchType(PatchType.JSON_MERGE).build(),
                        buildRefreshPatch(timestamp));
        return new RefreshResponse(namespace, name, true, timestamp, "Reconciliation requested");
    }

    static String buildRefreshPatch(long timestamp) {
        try {
            return JacksonHolder.MAPPER.writeValueAsString(java.util.Map.of("metadata", java.util.Map.of(
                    "annotations", java.util.Map.of(REFRESH_ANNOTATION, Long.toString(timestamp)))));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Could not construct refresh patch");
        }
    }

    private static void validateNamespace(String namespace) { ResourceNameValidator.requireValid(namespace, "namespace"); }
    private static void validate(String namespace, String name) {
        validateNamespace(namespace);
        ResourceNameValidator.requireValid(name, "name");
    }
}

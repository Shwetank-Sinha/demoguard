package io.demoguard.validation;

import io.demoguard.api.RemediationPlan;
import io.demoguard.api.RemediationPlan.PatchFormat;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemediationPlannerTest {

    private final DeploymentValidator validator = new DeploymentValidator();
    private final RemediationPlanner planner = new RemediationPlanner(validator);

    @Test
    void lowReplicasProducesSpecificStrategicMergePatch() {
        Deployment deployment = new DeploymentBuilder(compliantDeployment()).editSpec()
                .withReplicas(1).endSpec().build();

        RemediationPlan plan = find(planner.plansFor(deployment, Optional.of(compliantPdb()), 3),
                "deployment-replicas");

        assertTrue(plan.isSafeToApply());
        assertEquals(PatchFormat.YAML, plan.getPatchFormat());
        assertTrue(plan.getPatch().contains("kind: Deployment"));
        assertTrue(plan.getPatch().contains("name: 'demo'"));
        assertTrue(plan.getPatch().contains("replicas: 3"));
    }

    @Test
    void missingPdbProducesCompleteManifestWithSortedDeploymentSelector() {
        RemediationPlan plan = find(planner.plansFor(compliantDeployment(), Optional.empty(), 3),
                "pdb-missing");

        assertTrue(plan.isSafeToApply());
        assertEquals(PatchFormat.YAML, plan.getPatchFormat());
        assertTrue(plan.getPatch().contains("apiVersion: policy/v1\nkind: PodDisruptionBudget"));
        assertTrue(plan.getPatch().contains("minAvailable: 3"));
        assertTrue(plan.getPatch().contains("      'app': 'demo'\n      'tier': 'web'"));
    }

    @Test
    void lowExistingPdbProducesSpecificPatch() {
        PodDisruptionBudget low = new PodDisruptionBudgetBuilder(compliantPdb())
                .editSpec().withMinAvailable(new IntOrString(1)).endSpec().build();

        RemediationPlan plan = find(planner.plansFor(compliantDeployment(), Optional.of(low), 3),
                "pdb-min-available");

        assertTrue(plan.isSafeToApply());
        assertTrue(plan.getPatch().contains("name: 'demo-pdb'"));
        assertTrue(plan.getPatch().contains("minAvailable: 3"));
    }

    @Test
    void probesAndResourcesRequireTeamDecisionsWithoutInventedValues() {
        Deployment unsafe = new DeploymentBuilder(compliantDeployment()).editSpec().editTemplate().editSpec()
                .withContainers(new ContainerBuilder().withName("app").withImage("app:1").build())
                .endSpec().endTemplate().endSpec().build();

        List<RemediationPlan> plans = planner.plansFor(unsafe, Optional.of(compliantPdb()), 3);

        for (String id : List.of("deployment-readiness-probes", "deployment-resources")) {
            RemediationPlan plan = find(plans, id);
            assertFalse(plan.isSafeToApply());
            assertEquals(PatchFormat.NONE, plan.getPatchFormat());
            assertNull(plan.getPatch());
            assertTrue(plan.getRationale().contains("team must"));
        }
    }

    @Test
    void plansAreDeduplicatedAndDeterministicallyOrdered() {
        Deployment unsafe = new DeploymentBuilder(compliantDeployment()).editSpec()
                .withReplicas(1)
                .withStrategy(null)
                .editTemplate().editSpec()
                .withContainers(new ContainerBuilder().withName("app").withImage("app:1").build())
                .endSpec().endTemplate().endSpec().build();

        List<String> first = planner.plansFor(unsafe, Optional.empty(), 3).stream()
                .map(RemediationPlan::getId).toList();
        List<String> second = planner.plansFor(unsafe, Optional.empty(), 3).stream()
                .map(RemediationPlan::getId).toList();

        assertEquals(List.of("deployment-readiness-probes", "deployment-replicas",
                "deployment-resources", "deployment-rolling-update", "pdb-missing"), first);
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().distinct().count());
    }

    @Test
    void compliantDeploymentHasNoRemediationPlans() {
        List<RemediationPlan> plans = planner.plansFor(
                compliantDeployment(), Optional.of(compliantPdb()), 3);

        assertTrue(plans.isEmpty());
        assertEquals("No static remediation is required", RemediationPlanner.summary(plans));
    }

    private RemediationPlan find(List<RemediationPlan> plans, String id) {
        return plans.stream().filter(plan -> id.equals(plan.getId())).findFirst().orElseThrow();
    }

    private Deployment compliantDeployment() {
        return new DeploymentBuilder()
                .withNewMetadata().withName("demo").withNamespace("demo-ns").endMetadata()
                .withNewSpec().withReplicas(3)
                .withNewSelector().addToMatchLabels("tier", "web").addToMatchLabels("app", "demo").endSelector()
                .withStrategy(new DeploymentStrategyBuilder().withType("RollingUpdate")
                        .withRollingUpdate(new RollingUpdateDeploymentBuilder()
                                .withMaxUnavailable(new IntOrString(0)).build()).build())
                .withNewTemplate().withNewMetadata().addToLabels("app", "demo").addToLabels("tier", "web")
                .endMetadata().withNewSpec().withContainers(new ContainerBuilder()
                        .withName("app").withImage("app:1")
                        .withReadinessProbe(new ProbeBuilder().withNewExec().withCommand("true").endExec().build())
                        .withResources(new ResourceRequirementsBuilder()
                                .withRequests(Map.of("cpu", new Quantity("100m"), "memory", new Quantity("128Mi")))
                                .withLimits(Map.of("cpu", new Quantity("500m"), "memory", new Quantity("256Mi")))
                                .build()).build()).endSpec().endTemplate().endSpec().build();
    }

    private PodDisruptionBudget compliantPdb() {
        return new PodDisruptionBudgetBuilder().withNewMetadata().withName("demo-pdb")
                .withNamespace("demo-ns").endMetadata().withNewSpec()
                .withMinAvailable(new IntOrString(3)).withNewSelector().addToMatchLabels("app", "demo")
                .endSelector().endSpec().build();
    }
}

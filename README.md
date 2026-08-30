# DemoGuard

DemoGuard is a Java Kubernetes operator that evaluates Kubernetes Deployments for zero-downtime demo readiness. It combines static Deployment and PodDisruptionBudget checks, live Deployment and pod health, Deployment rollout safety, and low-confidence Prometheus memory and CPU-risk forecasts for the planned demo window.

The operator uses the current kubeconfig and active Kubernetes context when run locally. It watches `DemoPolicy` resources and writes the validation result to a same-namespace `DemoReadiness` resource.

## Run the unsafe example

From the repository root, apply both CRDs:

```bash
kubectl apply -f config/crd/demoguard.dev_demopolicies.yaml
kubectl apply -f config/crd/demoguard.dev_demoreadinesses.yaml
```

The `DemoPolicy` field `spec.demoDurationMinutes` controls the forecast window and defaults to 30 minutes. DemoGuard reads the Prometheus base URL from `PROMETHEUS_URL`; it defaults to `http://localhost:9090` for local development.

Start the operator locally in a separate terminal, pointing it at Prometheus:

```bash
cd operator
PROMETHEUS_URL=http://localhost:9090 mvn exec:java
```

Prometheus must expose cAdvisor's `container_memory_working_set_bytes` and `container_cpu_usage_seconds_total`, plus kube-state-metrics' `kube_pod_container_resource_limits`. DemoGuard uses `query_range` history to project usage through the configured demo window. When available, `container_cpu_cfs_throttled_seconds_total` supplies an additional sustained-throttling signal.

`memoryRisk` and `cpuRisk` are each `SAFE`, `AT_RISK`, or `UNKNOWN`. CPU is at risk when its projection reaches 80% of the CPU limit during the demo window or sustained throttling reaches 10% of the limit. Missing Prometheus metrics or insufficient history produce an honest `UNKNOWN`; they do not change static or runtime-health results. Each `AT_RISK` forecast applies a 20-point penalty to the static-validation base score, with a floor of 60, so forecast risk produces `WARNING` but never `BLOCKED` by itself.

Memory breach timing is only published in `predictedLimitBreachInMinutes` when the breach falls inside `spec.demoDurationMinutes`. Forecasts beyond that horizon remain `SAFE`, omit the distant breach time, and state that no breach is projected during the demo window.

For runtime validation, DemoGuard reads the Deployment's desired, Ready, available, and unavailable replica counts and inspects pods selected by the Deployment. It reports aggregate container restart counts, pod phases, and active waiting reasons including `CrashLoopBackOff`, `ImagePullBackOff`, `ErrImagePull`, and `CreateContainerConfigError`.

`runtimeStatus` is `HEALTHY`, `DEGRADED`, or `UNHEALTHY`. Zero Ready or available replicas, failed pods, and active fatal container waiting states block the demo. Replica counts below `spec.minimumReplicas` and restarts without an active crash loop produce a warning. `DEGRADED` applies a 20-point penalty with a floor of 60; `UNHEALTHY` caps the score at 40. Static or runtime `BLOCKED` results always remain blocked, while memory or CPU risk can promote a non-blocked result to `WARNING` but cannot weaken a block.

For deployment-change protection, DemoGuard compares `metadata.generation` with `status.observedGeneration`, checks desired, updated, Ready, available, and unavailable replicas, and inspects Deployment conditions. `rolloutStatus` is `STABLE` when the latest generation is fully observed and all desired replicas are updated, Ready, and available; `ROLLING_OUT` while those values are catching up; `STALLED` when Kubernetes reports failed progress, including `ProgressDeadlineExceeded` or `ReplicaFailure`; and `UNKNOWN` when required status data is absent. Condition reasons are retained in `rolloutMessage`.

A rolling rollout applies a 20-point warning penalty (with the shared warning floor of 60) and recommends waiting before presenting. A stalled rollout always makes final readiness `BLOCKED`, caps the score at 40, and recommends rollback or repair. A stable rollout does not alter readiness or score. Existing static, runtime, memory, and CPU risks keep their precedence: warning deductions accumulate, and no warning can weaken a blocked result.

The final score always matches `readinessStatus`: `READY` is exactly 100, `WARNING` is 60–99, and `BLOCKED` is below 60. `scoreMessage` starts with the static-validation base score and lists each runtime or forecast adjustment that affected the final score.

Every result also includes a concise demo preflight report. `preflightStatus` exactly matches the final `readinessStatus`, while `preflightSummary` identifies the first blocking or warning reason (or confirms that the workload is ready for the configured demo window). `preflightChecks` always appears in `STATIC`, `RUNTIME`, `MEMORY`, `CPU`, `ROLLOUT`, `REMEDIATION` order. Each check is `PASS`, `WARNING`, `BLOCKED`, `UNKNOWN`, or `NOT_REQUIRED`; unavailable Prometheus forecasts remain `UNKNOWN` and do not block an otherwise healthy workload. Remediation checks report only the plan count and ask the team to review the plans—patch YAML remains in `remediationPlans`.

Static configuration failures also appear in `status.remediationPlans` as deterministic, deduplicated review items. Each item identifies its target and severity, explains the change, and says whether DemoGuard could infer a safe patch. DemoGuard emits strategic-merge YAML for replicas below the policy minimum, a complete PDB manifest when the Deployment selector is safely reusable, and a PDB patch when `minAvailable` is below the policy minimum. `remediationSummary` reports patch and team-decision counts without copying multiline patches into findings or `scoreMessage`.

Probe endpoints and CPU or memory quantities are application decisions, so their plans use `safeToApply: false` and `patchFormat: NONE`. DemoGuard never applies remediation plans or changes the target Deployment or PDB; operators must review any emitted YAML before applying it.

Apply the deliberately unsafe Deployment and its policy:

```bash
kubectl apply -f demo-workloads/unsafe-deployment.yaml
kubectl apply -f demo-workloads/unsafe-policy.yaml
```

View the generated readiness result (the CRD also prints the preflight summary, runtime health, rollout state, CPU risk, and Ready replicas):

```bash
kubectl get demoreadiness
kubectl get demoreadiness hackathon-app-readiness -o yaml
```

## Blocked to Ready demo

After applying the unsafe example and observing the blocked readiness result, apply the safe Deployment and its PodDisruptionBudget:

```bash
kubectl apply -f demo-workloads/safe-deployment.yaml
kubectl apply -f demo-workloads/safe-pdb.yaml
```

Watch the workload become available, then view the updated readiness result:

```bash
kubectl rollout status deployment/hackathon-app --namespace default
kubectl get demoreadiness hackathon-app-readiness -o yaml
```

Delete the test resources:

```bash
kubectl delete -f demo-workloads/unsafe-policy.yaml
kubectl delete demoreadiness hackathon-app-readiness --namespace default --ignore-not-found
kubectl delete -f demo-workloads/safe-pdb.yaml --ignore-not-found
kubectl delete -f demo-workloads/unsafe-deployment.yaml
```

## Test

```bash
cd operator
mvn test
```

# DemoGuard

DemoGuard is a Java Kubernetes operator that evaluates Kubernetes Deployments for zero-downtime demo readiness. It combines static Deployment and PodDisruptionBudget checks, live Deployment and pod health, and a low-confidence Prometheus memory-risk forecast for the planned demo window.

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

Prometheus must expose cAdvisor's `container_memory_working_set_bytes` and kube-state-metrics' `kube_pod_container_resource_limits`. If Prometheus is unavailable or does not have enough data, `memoryRisk` is `UNKNOWN`; static readiness validation continues normally. An otherwise `READY` workload with an `AT_RISK` forecast is reported as `WARNING`, never `BLOCKED` solely because of the prediction.

For runtime validation, DemoGuard reads the Deployment's desired, Ready, available, and unavailable replica counts and inspects pods selected by the Deployment. It reports aggregate container restart counts, pod phases, and active waiting reasons including `CrashLoopBackOff`, `ImagePullBackOff`, `ErrImagePull`, and `CreateContainerConfigError`.

`runtimeStatus` is `HEALTHY`, `DEGRADED`, or `UNHEALTHY`. Zero Ready or available replicas, failed pods, and active fatal container waiting states block the demo. Replica counts below `spec.minimumReplicas` and restarts without an active crash loop produce a warning. Static `BLOCKED` results always remain blocked, while memory risk can promote `READY` to `WARNING` but cannot weaken a block.

Apply the deliberately unsafe Deployment and its policy:

```bash
kubectl apply -f demo-workloads/unsafe-deployment.yaml
kubectl apply -f demo-workloads/unsafe-policy.yaml
```

View the generated readiness result (the CRD also prints runtime health and Ready replicas):

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

# DemoGuard

DemoGuard is a Java Kubernetes operator that evaluates Kubernetes Deployments for zero-downtime demo readiness. In addition to static Deployment and PodDisruptionBudget checks, it uses Prometheus memory history to provide a low-confidence memory-risk forecast for the planned demo window.

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

Apply the deliberately unsafe Deployment and its policy:

```bash
kubectl apply -f demo-workloads/unsafe-deployment.yaml
kubectl apply -f demo-workloads/unsafe-policy.yaml
```

View the generated readiness result (the CRD prints `NAME`, `STATUS`, `SCORE`, and `AGE`):

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

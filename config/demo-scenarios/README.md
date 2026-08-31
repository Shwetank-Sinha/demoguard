# Controlled live demo scenarios

These manifests isolate deliberately risky workloads in `demoguard-demo`. They use real Kubernetes status and DemoGuard checks; they do not use dashboard mock data. Install the DemoGuard CRDs and run the operator before starting.

Verify the cluster context, then create the dedicated namespace once:

```bash
kubectl config current-context
kubectl apply -f config/demo-scenarios/namespace.yaml
```

Apply only one scenario when you want the clearest presentation. In another terminal, watch the generated `DemoReadiness` resources:

```bash
kubectl get demoreadiness --namespace demoguard-demo --watch
```

Open the dashboard as described in the repository README and select the `demoguard-demo` namespace. If the in-cluster dashboard is installed, its default port-forward command is:

```bash
kubectl port-forward service/demoguard-dashboard 8080:8080 --namespace default
```

Then open `http://localhost:8080`.

## Static risk

```bash
kubectl apply -f config/demo-scenarios/static-risk.yaml
kubectl get demoreadiness demoguard-static-risk-readiness --namespace demoguard-demo -o yaml
```

Expected: `BLOCKED` static readiness. The Deployment has one replica, permits one unavailable replica during rollout, and has no PDB. DemoGuard should report those real findings. Its remediation plans should include safe YAML for raising replicas and creating a matching PDB, plus a review-only rolling-update plan.

Cleanup:

```bash
kubectl delete -f config/demo-scenarios/static-risk.yaml --ignore-not-found
kubectl delete demoreadiness demoguard-static-risk-readiness --namespace demoguard-demo --ignore-not-found
```

## Stalled rollout

```bash
kubectl apply -f config/demo-scenarios/stalled-rollout.yaml
kubectl get deployment demoguard-stalled-rollout --namespace demoguard-demo --watch
```

The readiness probe deliberately requests a path that NGINX does not serve. Kubernetes needs at least the configured 30-second `progressDeadlineSeconds` before it records `ProgressDeadlineExceeded`; allow additional reconciliation time on a busy Kind cluster. After the deadline, verify the real condition and DemoGuard result:

```bash
kubectl get deployment demoguard-stalled-rollout --namespace demoguard-demo -o jsonpath='{range .status.conditions[*]}{.type}{"\t"}{.status}{"\t"}{.reason}{"\n"}{end}'
kubectl annotate demopolicy demoguard-stalled-rollout --namespace demoguard-demo demoguard.dev/refresh="$(date +%s)" --overwrite
kubectl get demoreadiness demoguard-stalled-rollout-readiness --namespace demoguard-demo -o yaml
```

Expected: the Deployment condition includes `Progressing False ProgressDeadlineExceeded`; DemoGuard reports rollout status `STALLED` and final readiness `BLOCKED`. Before the deadline it is normal to see `ROLLING_OUT`.

Cleanup:

```bash
kubectl delete -f config/demo-scenarios/stalled-rollout.yaml --ignore-not-found
kubectl delete demoreadiness demoguard-stalled-rollout-readiness --namespace demoguard-demo --ignore-not-found
```

## Healthy baseline

```bash
kubectl apply -f config/demo-scenarios/healthy.yaml
kubectl rollout status deployment/demoguard-healthy --namespace demoguard-demo --timeout=90s
kubectl annotate demopolicy demoguard-healthy --namespace demoguard-demo demoguard.dev/refresh="$(date +%s)" --overwrite
kubectl get demoreadiness demoguard-healthy-readiness --namespace demoguard-demo -o yaml
```

Expected: all five static checks pass, runtime is `HEALTHY`, rollout is `STABLE`, and final readiness is `READY` with score 100. In a local setup without usable Prometheus history, memory and CPU forecasts can honestly remain `UNKNOWN` without lowering readiness.

Cleanup:

```bash
kubectl delete -f config/demo-scenarios/healthy.yaml --ignore-not-found
kubectl delete demoreadiness demoguard-healthy-readiness --namespace demoguard-demo --ignore-not-found
```

After all scenarios are removed, delete the dedicated namespace with its explicit manifest path:

```bash
kubectl delete -f config/demo-scenarios/namespace.yaml --ignore-not-found
```

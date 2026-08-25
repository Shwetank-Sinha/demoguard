# DemoGuard

DemoGuard is a Java Kubernetes operator that evaluates Kubernetes Deployments for zero-downtime demo readiness.

The operator uses the current kubeconfig and active Kubernetes context when run locally. It watches `DemoPolicy` resources and writes the validation result to a same-namespace `DemoReadiness` resource.

## Run the unsafe example

From the repository root, apply both CRDs:

```bash
kubectl apply -f config/crd/demoguard.dev_demopolicies.yaml
kubectl apply -f config/crd/demoguard.dev_demoreadinesses.yaml
```

Start the operator locally in a separate terminal:

```bash
cd operator
mvn exec:java
```

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

Delete the test resources:

```bash
kubectl delete -f demo-workloads/unsafe-policy.yaml
kubectl delete demoreadiness hackathon-app-readiness --namespace default --ignore-not-found
kubectl delete -f demo-workloads/unsafe-deployment.yaml
```

## Test

```bash
cd operator
mvn test
```

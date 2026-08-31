export interface Namespace { name: string }
export interface PolicySpec { targetNamespace: string; targetDeployment: string; minimumReplicas: number | null; demoDurationMinutes: number | null }
export interface DemoPolicy { namespace: string; name: string; resourceVersion: string; creationTimestamp: string | null; spec: PolicySpec }
export interface PreflightCheck { category: string; status: State; message: string | null; recommendation: string | null }
export interface RemediationPlan { id: string; severity: string; targetKind: string; targetName: string; summary: string; rationale: string; safeToApply: boolean; patchFormat: string; patch: string | null }
export type State = 'READY' | 'PASS' | 'SAFE' | 'HEALTHY' | 'STABLE' | 'WARNING' | 'DEGRADED' | 'AT_RISK' | 'ROLLING_OUT' | 'BLOCKED' | 'UNHEALTHY' | 'STALLED' | 'UNKNOWN' | 'NOT_REQUIRED' | string
export interface DemoReadiness {
  namespace: string; name: string; resourceVersion: string; creationTimestamp: string | null; lastAssessedAt: string | null
  readinessStatus: State; score: number; scoreMessage: string | null; findings: string[]; recommendations: string[]
  remediationSummary: string | null; remediationPlans: RemediationPlan[]
  memoryRisk: State; currentMemoryBytes: number | null; memoryLimitBytes: number | null; predictedMemoryBytesAtDemoEnd: number | null; predictedLimitBreachInMinutes: number | null; predictionMessage: string | null
  cpuRisk: State; currentCpuCores: number | null; cpuLimitCores: number | null; predictedCpuCoresAtDemoEnd: number | null; cpuThrottlingRate: number | null; cpuPredictionMessage: string | null
  runtimeStatus: State; desiredReplicas: number; readyReplicas: number; availableReplicas: number; unavailableReplicas: number; totalRestarts: number; runtimeMessage: string | null
  rolloutStatus: State; deploymentGeneration: number | null; observedGeneration: number | null; updatedReplicas: number; rolloutMessage: string | null
  preflightStatus: State; preflightSummary: string | null; preflightChecks: PreflightCheck[]
}
export interface Preflight { policy: DemoPolicy; readiness: DemoReadiness | null; pending: boolean; message: string | null }
export interface ApiError { code: string; message: string }

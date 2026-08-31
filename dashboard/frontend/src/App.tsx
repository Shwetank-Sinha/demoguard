import { useEffect, useState } from 'react'
import { api, DashboardApiError } from './api'
import type { DemoPolicy, DemoReadiness, Namespace, Preflight, RemediationPlan, State } from './types'

const PENDING_MESSAGE = 'Assessment requested; waiting for the operator to publish DemoReadiness.'

export function StatusChip({ state }: { state: State | null | undefined }) {
  const value = state || 'UNKNOWN'
  const tone = ['READY', 'PASS', 'SAFE', 'HEALTHY', 'STABLE'].includes(value) ? 'good'
    : ['WARNING', 'DEGRADED', 'AT_RISK', 'ROLLING_OUT'].includes(value) ? 'warn'
    : ['BLOCKED', 'UNHEALTHY', 'STALLED'].includes(value) ? 'bad' : 'neutral'
  return <span className={`status status--${tone}`}>{value.replaceAll('_', ' ')}</span>
}

export function AssessmentTime({ value }: { value: string | null | undefined }) {
  if (!value) return <time className="mono">Not published</time>
  return <time className="mono" dateTime={value}>{new Date(value).toLocaleString()}</time>
}

function Value({ label, value, mono = true }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return <div className="metric"><dt>{label}</dt><dd className={mono ? 'mono' : ''}>{value ?? '—'}</dd></div>
}

function Panel({ title, state, children }: { title: string; state?: State | null; children: React.ReactNode }) {
  return <section className="panel"><header className="panel__header"><h2>{title}</h2>{state && <StatusChip state={state} />}</header>{children}</section>
}

const bytes = (value: number | null) => {
  if (value === null) return '—'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']; let amount = value; let unit = 0
  while (amount >= 1024 && unit < units.length - 1) { amount /= 1024; unit++ }
  return `${amount.toFixed(unit === 0 ? 0 : 2)} ${units[unit]}`
}
const cores = (value: number | null) => value === null ? '—' : `${value.toFixed(3)} cores`
const percent = (value: number | null) => value === null ? '—' : `${(value * 100).toFixed(1)}%`

function Checks({ readiness }: { readiness: DemoReadiness }) {
  return <section className="checks" aria-labelledby="checks-heading">
    <div className="section-heading"><h2 id="checks-heading">Preflight checks</h2><span>Operator-supplied order</span></div>
    <div className="table-wrap"><table><thead><tr><th scope="col">Check</th><th scope="col">Status</th><th scope="col">Message</th><th scope="col">Recommendation</th></tr></thead>
      <tbody>{readiness.preflightChecks.map(check => <tr key={check.category}>
        <th scope="row" className="mono">{check.category}</th><td><StatusChip state={check.status} /></td>
        <td>{check.message || '—'}</td><td>{check.recommendation || '—'}</td>
      </tr>)}</tbody></table></div>
  </section>
}

function Plan({ plan }: { plan: RemediationPlan }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => { if (!plan.patch) return; await navigator.clipboard.writeText(plan.patch); setCopied(true); setTimeout(() => setCopied(false), 1600) }
  return <article className="plan">
    <div className="plan__head"><div><span className="mono plan__id">{plan.id}</span><h3>{plan.summary}</h3></div><StatusChip state={plan.severity === 'BLOCKING' ? 'BLOCKED' : 'WARNING'} /></div>
    <dl className="metrics metrics--compact"><Value label="Target" value={`${plan.targetKind}/${plan.targetName}`} /><Value label="Patch format" value={plan.patchFormat} /><Value label="Safe to apply" value={plan.safeToApply ? 'YES' : 'NO'} /></dl>
    <p>{plan.rationale}</p>
    {plan.patch && <details><summary>Review patch YAML</summary><div className="code-head"><span>{plan.patchFormat} patch</span><button className="button button--small" onClick={copy}>{copied ? 'Copied' : 'Copy patch'}</button></div><pre><code>{plan.patch}</code></pre></details>}
  </article>
}

function Details({ readiness }: { readiness: DemoReadiness }) {
  return <div className="detail-grid">
    <Panel title="Replica & rollout state" state={readiness.rolloutStatus}><dl className="metrics"><Value label="Desired" value={readiness.desiredReplicas} /><Value label="Updated" value={readiness.updatedReplicas} /><Value label="Ready" value={readiness.readyReplicas} /><Value label="Available" value={readiness.availableReplicas} /><Value label="Unavailable" value={readiness.unavailableReplicas} /><Value label="Generation" value={readiness.deploymentGeneration} /><Value label="Observed generation" value={readiness.observedGeneration} /></dl><p className="panel__message">{readiness.rolloutMessage || '—'}</p></Panel>
    <Panel title="Runtime restarts" state={readiness.runtimeStatus}><dl className="metrics"><Value label="Restart count" value={readiness.totalRestarts} /></dl><p className="panel__message">{readiness.runtimeMessage || '—'}</p></Panel>
    <Panel title="CPU assessment" state={readiness.cpuRisk}><dl className="metrics"><Value label="Current" value={cores(readiness.currentCpuCores)} /><Value label="Predicted at demo end" value={cores(readiness.predictedCpuCoresAtDemoEnd)} /><Value label="Limit" value={cores(readiness.cpuLimitCores)} /><Value label="Throttling" value={percent(readiness.cpuThrottlingRate)} /></dl><p className="panel__message">{readiness.cpuPredictionMessage || '—'}</p></Panel>
    <Panel title="Memory assessment" state={readiness.memoryRisk}><dl className="metrics"><Value label="Current" value={bytes(readiness.currentMemoryBytes)} /><Value label="Predicted at demo end" value={bytes(readiness.predictedMemoryBytesAtDemoEnd)} /><Value label="Limit" value={bytes(readiness.memoryLimitBytes)} /><Value label="Limit breach" value={readiness.predictedLimitBreachInMinutes === null ? '—' : `${readiness.predictedLimitBreachInMinutes.toFixed(1)} min`} /></dl><p className="panel__message">{readiness.predictionMessage || '—'}</p></Panel>
    <Panel title="Remediation plans" state={readiness.remediationPlans.length ? 'WARNING' : 'NOT_REQUIRED'}><p className="panel__message">{readiness.remediationSummary || '—'}</p>{readiness.remediationPlans.length ? readiness.remediationPlans.map(plan => <Plan key={plan.id} plan={plan} />) : <div className="empty-inline">No remediation plans were supplied.</div>}</Panel>
  </div>
}

function Notice({ kind, title, message }: { kind: string; title: string; message: string }) {
  return <div className={`notice notice--${kind}`} role={kind === 'error' ? 'alert' : 'status'}><strong>{title}</strong><span>{message}</span></div>
}

export function App() {
  const [namespaces, setNamespaces] = useState<Namespace[]>([]); const [namespace, setNamespace] = useState('')
  const [policies, setPolicies] = useState<DemoPolicy[]>([]); const [policy, setPolicy] = useState('')
  const [preflight, setPreflight] = useState<Preflight | null>(null); const [loading, setLoading] = useState(true)
  const [error, setError] = useState<DashboardApiError | null>(null); const [refreshing, setRefreshing] = useState(false)
  const [staleVersion, setStaleVersion] = useState<string | null>(null)

  useEffect(() => { api.namespaces().then(items => { setNamespaces(items); if (items.length) setNamespace(items[0].name) }).catch(setError).finally(() => setLoading(false)) }, [])
  useEffect(() => { if (!namespace) { setPolicies([]); return } setLoading(true); setError(null); setPolicy(''); setPreflight(null)
    api.policies(namespace).then(items => { setPolicies(items); if (items.length) setPolicy(items[0].name) }).catch(setError).finally(() => setLoading(false)) }, [namespace])
  useEffect(() => { if (!namespace || !policy) return
    let cancelled = false; let retry: ReturnType<typeof setTimeout> | undefined
    const load = () => { if (!staleVersion) setLoading(true); setError(null)
      api.preflight(namespace, policy).then(result => {
        if (cancelled) return
        setPreflight(result)
        if (staleVersion && result.readiness?.resourceVersion === staleVersion) retry = setTimeout(load, 1000)
        else setStaleVersion(null)
      }).catch(reason => { if (!cancelled) { setError(reason); setStaleVersion(null) } }).finally(() => { if (!cancelled) setLoading(false) })
    }
    load(); return () => { cancelled = true; if (retry) clearTimeout(retry) }
  }, [namespace, policy, staleVersion])

  const refresh = async () => { if (!policy) return; setRefreshing(true); setError(null)
    try { await api.refresh(namespace, policy); setStaleVersion(preflight?.readiness?.resourceVersion || 'pending') }
    catch (reason) { setError(reason as DashboardApiError); setStaleVersion(null) } finally { setRefreshing(false) } }
  const readiness = preflight?.readiness
  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="/" aria-label="DemoGuard dashboard"><span className="brand__mark" aria-hidden="true">DG</span><span>DemoGuard</span></a>
      <div className="connection"><span className="connection__dot" aria-hidden="true" />Kubernetes API <span className="mono">/ {namespace || '—'}</span></div>
      <div className="controls"><label>Namespace<select value={namespace} onChange={e => setNamespace(e.target.value)} disabled={loading || !namespaces.length}><option value="">Select namespace</option>{namespaces.map(item => <option key={item.name}>{item.name}</option>)}</select></label>
        <label>DemoPolicy<select value={policy} onChange={e => setPolicy(e.target.value)} disabled={loading || !policies.length}><option value="">Select policy</option>{policies.map(item => <option key={item.name}>{item.name}</option>)}</select></label>
        <div className="assessed"><span>Last assessed</span><AssessmentTime value={readiness?.lastAssessedAt} /></div>
        <button className="button button--primary" onClick={refresh} disabled={!policy || loading || refreshing}>{refreshing ? 'Requesting…' : 'Refresh assessment'}</button></div>
    </header>
    <main>
      {loading && <Notice kind="loading" title="Loading cluster resources" message="Reading DemoPolicy and DemoReadiness from Kubernetes…" />}
      {error && <Notice kind="error" title={error.status === 403 ? 'Permission denied' : 'Dashboard API error'} message={error.message} />}
      {!loading && !error && namespaces.length === 0 && <Notice kind="empty" title="No visible namespaces" message="The dashboard identity cannot see any Kubernetes namespaces." />}
      {!loading && !error && namespace && policies.length === 0 && <Notice kind="empty" title="No DemoPolicy resources" message={`No DemoPolicy resources are visible in ${namespace}.`} />}
      {!loading && !error && preflight?.pending && <Notice kind="pending" title="Assessment pending" message={preflight.message || PENDING_MESSAGE} />}
      {!loading && !error && staleVersion && readiness && <Notice kind="pending" title="Refreshing assessment" message="Reconciliation was requested; showing the previous result until the operator publishes an updated DemoReadiness." />}
      {readiness && <><section className="preflight" aria-labelledby="preflight-heading"><div><span className="eyebrow">Preflight assessment</span><h1 id="preflight-heading"><span className="mono">{preflight.policy.name}</span><StatusChip state={readiness.preflightStatus} /></h1></div><div className="score"><span>Score</span><strong className="mono">{readiness.score}<small>/100</small></strong></div><div className="summary"><p>{readiness.preflightSummary || '—'}</p><span>{readiness.scoreMessage || '—'}</span></div></section><Checks readiness={readiness} /><Details readiness={readiness} /></>}
    </main>
    <footer>DemoGuard reads operator results. Remediation patches are review and copy only; this dashboard never applies them.</footer>
  </div>
}

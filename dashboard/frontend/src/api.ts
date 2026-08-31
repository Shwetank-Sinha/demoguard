import type { ApiError, DemoPolicy, Namespace, Preflight } from './types'

export class DashboardApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try { response = await fetch(path, init) }
  catch { throw new DashboardApiError(0, 'NETWORK_ERROR', 'The dashboard backend could not be reached.') }
  if (!response.ok) {
    let error: ApiError = { code: 'API_ERROR', message: `Request failed with status ${response.status}` }
    try { error = await response.json() as ApiError } catch { /* keep sanitized fallback */ }
    throw new DashboardApiError(response.status, error.code, error.message)
  }
  return response.json() as Promise<T>
}

export const api = {
  namespaces: () => request<Namespace[]>('/api/namespaces'),
  policies: (namespace: string) => request<DemoPolicy[]>(`/api/demopolicies?namespace=${encodeURIComponent(namespace)}`),
  preflight: (namespace: string, name: string) => request<Preflight>(`/api/demopolicies/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/preflight`),
  refresh: (namespace: string, name: string) => request(`/api/demopolicies/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/refresh`, { method: 'POST' })
}

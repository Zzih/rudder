import request from '@/utils/request'

export function listRunningJobs(params?: {
  workspaceId?: number
  name?: string
  taskType?: string
  runtimeType?: string
  pageNum?: number
  pageSize?: number
}) {
  return request.get('/jobs/running', { params })
}

// scriptCode is a Long (may exceed Number.MAX_SAFE_INTEGER) — always pass as string from the caller
export function listRunningByScript(scriptCode: string) {
  return request.get(`/jobs/running/script/${scriptCode}`)
}

export function getJobDetail(id: number) {
  return request.get(`/jobs/${id}`)
}

export function killJob(id: number) {
  return request.post(`/jobs/${id}/kill`)
}

export function triggerSavepoint(id: number) {
  return request.post(`/jobs/${id}/savepoint`)
}

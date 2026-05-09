import request from '@/utils/request'

// --- Approval Records ---

export function pageApprovals(params: { pageNum?: number; pageSize?: number; status?: string }) {
  return request.get('/approvals', { params })
}

export function getApproval(id: number) {
  return request.get(`/approvals/${id}`)
}

export function listApprovalsByResource(resourceType: string, resourceId: number) {
  return request.get('/approvals/resource', { params: { resourceType, resourceId } })
}

export function approveApproval(id: number, comment?: string) {
  return request.post(`/approvals/${id}/approve`, { comment })
}

export function rejectApproval(id: number, comment?: string) {
  return request.post(`/approvals/${id}/reject`, { comment })
}

// --- Approval Config ---

export interface ParamDefinition {
  name: string
  label: string
  type: string
  required: boolean
  placeholder: string
}

export interface ProviderDefinition {
  params: ParamDefinition[]
  guide: string
}

export function getApprovalProviderDefinitions() {
  return request.get('/config/approval/providers')
}

export function getApprovalConfig() {
  return request.get('/config/approval')
}

export function saveApprovalConfig(data: { provider: string; providerParams?: string; enabled?: boolean }) {
  return request.post('/config/approval', data)
}

export function listApprovalConfigs() {
  return request.get('/config/approval/configs')
}

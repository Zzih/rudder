import request from '@/utils/request'

export function listWorkflowDefinitions(workspaceId: number, projectCode: number | string, params?: { searchVal?: string; pageNum?: number; pageSize?: number }) {
  return request.get(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions`, { params })
}

export function getWorkflowDefinition(workspaceId: number, projectCode: number | string, code: number | string) {
  return request.get(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}`)
}

export function createWorkflowDefinition(workspaceId: number, projectCode: number | string, data: Record<string, unknown>) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions`, data)
}

export function updateWorkflowDefinition(workspaceId: number, projectCode: number | string, code: number | string, data: Record<string, unknown>) {
  return request.put(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}`, data)
}

export function deleteWorkflowDefinition(workspaceId: number, projectCode: number | string, code: number | string) {
  return request.delete(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}`)
}

export function publishWorkflowDefinition(workspaceId: number, projectCode: number | string, code: number | string, data: { versionId?: number; remark?: string }) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/publish/workflow/${code}`, data)
}

export function runWorkflowDefinition(workspaceId: number, projectCode: number | string, code: number | string, data?: { overrideParams?: Record<string, string> }) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}/run`, data)
}

export function getWorkflowInstance(workspaceId: number, workflowDefinitionCode: number | string, id: number) {
  return request.get(`/workspaces/${workspaceId}/workflow-instances/${id}`, { params: { workflowDefinitionCode } })
}

export function listWorkflowInstances(workspaceId: number, params?: { workflowDefinitionCode?: number | string; projectCode?: number | string; searchVal?: string; status?: string; pageNum?: number; pageSize?: number }) {
  return request.get(`/workspaces/${workspaceId}/workflow-instances`, { params })
}

export function cancelWorkflowInstance(workspaceId: number, workflowDefinitionCode: number | string, id: number) {
  return request.post(`/workspaces/${workspaceId}/workflow-instances/${id}/cancel`, null, { params: { workflowDefinitionCode } })
}

export function listWorkflowDefinitionVersions(workspaceId: number, projectCode: number | string, code: number | string, params?: { pageNum?: number; pageSize?: number }) {
  return request.get(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}/versions`, { params })
}

export function listNodeInstances(workspaceId: number, workflowDefinitionCode: number | string, instanceId: number) {
  return request.get(`/workspaces/${workspaceId}/workflow-instances/${instanceId}/nodes`, { params: { workflowDefinitionCode } })
}

export function listTaskDefinitions(workspaceId: number, projectCode: number | string, code: number | string) {
  return request.get(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}/task-definitions`)
}

export function diffWorkflowDefinitionVersions(workspaceId: number, projectCode: number | string, code: number | string, versionIdA: number, versionIdB: number) {
  return request.get(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}/versions/diff`, { params: { versionIdA, versionIdB } })
}

export function commitWorkflowDefinitionVersion(workspaceId: number, projectCode: number | string, code: number | string, data: { message: string }) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}/commit`, data)
}

export function rollbackWorkflowDefinition(workspaceId: number, projectCode: number | string, code: number | string, versionId: number) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${code}/rollback/${versionId}`)
}

export function publishProject(workspaceId: number, projectCode: number | string, data: { items: { workflowDefinitionCode: number | string; versionId?: number }[]; remark?: string }) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/publish/project`, data)
}

export function listPublishRecords(workspaceId: number, projectCode: number | string, params?: { status?: string; pageNum?: number; pageSize?: number }) {
  return request.get(`/workspaces/${workspaceId}/projects/${projectCode}/publish/records`, { params })
}

export function executePublish(workspaceId: number, projectCode: number | string, batchCode: string) {
  return request.post(`/workspaces/${workspaceId}/projects/${projectCode}/publish/records/${batchCode}/execute`)
}

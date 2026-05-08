import request from '@/utils/request'
import type { AxiosResponse } from 'axios'

export function listScriptDirs(workspaceId: number) {
  return request.get(`/workspaces/${workspaceId}/script-dirs`)
}

export function createScriptDir(workspaceId: number, data: { name: string; parentId?: number }) {
  return request.post(`/workspaces/${workspaceId}/script-dirs`, data)
}

export function listScripts(workspaceId: number, params?: { dirId?: number }) {
  return request.get(`/workspaces/${workspaceId}/scripts`, { params })
}

export function getScript(workspaceId: number, code: number | string) {
  return request.get(`/workspaces/${workspaceId}/scripts/${code}`)
}

export function createScript(workspaceId: number, data: Record<string, unknown>) {
  return request.post(`/workspaces/${workspaceId}/scripts`, data)
}

export function updateScript(workspaceId: number, code: number | string, data: Record<string, unknown>) {
  return request.put(`/workspaces/${workspaceId}/scripts/${code}`, data)
}

export function deleteScript(workspaceId: number, code: number | string) {
  return request.delete(`/workspaces/${workspaceId}/scripts/${code}`)
}

export function deleteScriptDir(workspaceId: number, id: number) {
  return request.delete(`/workspaces/${workspaceId}/script-dirs/${id}`)
}

export function renameScriptDir(workspaceId: number, id: number, name: string) {
  return request.put(`/workspaces/${workspaceId}/script-dirs/${id}`, { name })
}

export function renameScript(workspaceId: number, code: number | string, name: string) {
  return request.put(`/workspaces/${workspaceId}/scripts/${code}`, { name })
}

export function moveScript(workspaceId: number, code: number | string, dirId: number | null) {
  return request.post(`/workspaces/${workspaceId}/scripts/${code}/move`, { dirId })
}

export function moveScriptDir(workspaceId: number, id: number, parentId: number | null) {
  return request.post(`/workspaces/${workspaceId}/script-dirs/${id}/move`, { parentId })
}

export function executeScript(workspaceId: number, code: number | string, data: { datasourceId?: number | null; sql?: string; executionMode?: string; params?: Record<string, string> }) {
  return request.post(`/workspaces/${workspaceId}/scripts/${code}/execute`, data)
}

export function executeDirect(data: { taskType: string; datasourceId?: number | null; sql: string; executionMode?: string; workflowDefinitionCode?: number; taskDefinitionCode?: number }) {
  return request.post('/executions/direct', data)
}

export function getExecution(id: number) {
  return request.get(`/executions/${id}`)
}

export function cancelExecution(id: number) {
  return request.post(`/executions/${id}/cancel`)
}

export function getExecutionLog(id: number, offsetLine = 0) {
  return request.get(`/executions/${id}/log`, { params: { offsetLine } })
}

export function getExecutionResult(id: number, offset = 0, limit = 500) {
  return request.get(`/executions/${id}/result`, { params: { offset, limit } })
}

export function listExecutionsByScript(scriptId: number | string) {
  return request.get(`/executions/script/${scriptId}`)
}

export function downloadExecutionResult(id: number, format: 'csv' | 'excel'): Promise<AxiosResponse<Blob>> {
  return request.get(`/executions/${id}/download`, {
    params: { format },
    responseType: 'blob',
  }) as unknown as Promise<AxiosResponse<Blob>>
}

export function dispatchToWorkflow(workspaceId: number, projectCode: number | string, code: number | string, data: { workflowDefinitionCode: number | string; mode: string }) {
  return request.post(`/workspaces/${workspaceId}/scripts/${code}/dispatch`, data, { params: { projectCode } })
}

export function getScriptBinding(workspaceId: number, code: number | string) {
  return request.get(`/workspaces/${workspaceId}/scripts/${code}/binding`)
}

export function pushToWorkflow(workspaceId: number, projectCode: number | string, code: number | string, data: { workflowDefinitionCode: number | string; mode: string; taskDefinitionCode?: number | string }) {
  return request.post(`/workspaces/${workspaceId}/scripts/${code}/push`, data, { params: { projectCode } })
}

export function listScriptVersions(workspaceId: number, code: number | string, params?: { pageNum?: number; pageSize?: number }) {
  return request.get(`/workspaces/${workspaceId}/scripts/${code}/versions`, { params })
}

export function commitScriptVersion(workspaceId: number, code: number | string, data: { message: string }) {
  return request.post(`/workspaces/${workspaceId}/scripts/${code}/commit`, data)
}

export function rollbackScript(workspaceId: number, code: number | string, versionId: number) {
  return request.post(`/workspaces/${workspaceId}/scripts/${code}/rollback/${versionId}`)
}

export function getScriptVersionContent(workspaceId: number, code: number | string, versionId: number) {
  return request.get(`/workspaces/${workspaceId}/scripts/${code}/versions/${versionId}`)
}

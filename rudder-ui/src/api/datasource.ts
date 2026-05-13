import request from '@/utils/request'

export function listDatasources(params?: { workspaceId?: number }) {
  return request.get('/datasources', { params })
}

export function getDatasource(workspaceId: number | undefined, id: number) {
  return request.get(`/datasources/${id}`, { params: { workspaceId } })
}

export function createDatasource(data: Record<string, unknown>) {
  return request.post('/datasources', data)
}

export function updateDatasource(workspaceId: number | undefined, id: number, data: Record<string, unknown>) {
  return request.put(`/datasources/${id}`, data, { params: { workspaceId } })
}

export function deleteDatasource(workspaceId: number | undefined, id: number) {
  return request.delete(`/datasources/${id}`, { params: { workspaceId } })
}

export function testConnection(workspaceId: number | undefined, id: number) {
  return request.post(`/datasources/${id}/test`, null, { params: { workspaceId } })
}

export function listWorkspaceDatasources(workspaceId: number) {
  return request.get('/datasources', { params: { workspaceId } })
}

export interface DatasourceWorkspaceGrant { workspaceId: number; workspaceName: string }

export function listDatasourceWorkspaces(datasourceId: number) {
  return request.get<DatasourceWorkspaceGrant[]>(`/datasources/${datasourceId}/workspaces`)
}

export function setDatasourceWorkspaces(datasourceId: number, workspaceIds: number[]) {
  return request.put(`/datasources/${datasourceId}/workspaces`, workspaceIds)
}

// ==================== Metadata ====================

/** 三层引擎(Trino / StarRocks 带外部 catalog)返回 catalog 名列表;两层引擎返回空数组。 */
export function listMetaCatalogs(workspaceId: number | undefined, datasourceId: number) {
  return request.get<string[]>(`/datasources/${datasourceId}/meta/catalogs`, { params: { workspaceId } })
}

export function listMetaDatabases(workspaceId: number | undefined, datasourceId: number, catalog?: string | null) {
  return request.get<string[]>(`/datasources/${datasourceId}/meta/databases`,
    { params: { workspaceId, catalog: catalog || undefined } })
}

export function listMetaTables(workspaceId: number | undefined, datasourceId: number, database: string, catalog?: string | null) {
  return request.get<{ name: string; comment: string }[]>(
    `/datasources/${datasourceId}/meta/databases/${database}/tables`,
    { params: { workspaceId, catalog: catalog || undefined } },
  )
}

export function listMetaColumns(workspaceId: number, datasourceId: number, database: string, table: string,
  catalog?: string | null) {
  return request.get<{ name: string; type: string; comment: string }[]>(
    `/datasources/${datasourceId}/meta/databases/${database}/tables/${table}/columns`,
    { params: { workspaceId, catalog: catalog || undefined } },
  )
}

export function refreshMetaCache(workspaceId: number, datasourceId: number) {
  return request.delete(`/datasources/${datasourceId}/meta/cache`, { params: { workspaceId } })
}

export interface TableSearchResult { database: string | null; table: string; comment: string | null }

export function searchMetaTables(workspaceId: number, datasourceId: number, keyword: string) {
  return request.get<TableSearchResult[]>(
    `/datasources/${datasourceId}/meta/search`,
    { params: { workspaceId, keyword } },
  )
}

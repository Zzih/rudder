import request from '@/utils/request'

export function listWorkspaces(params?: { searchVal?: string; pageNum?: number; pageSize?: number }) {
  return request.get('/workspaces', { params })
}

export interface WorkspaceOption { id: number; name: string }

/**
 * Admin 选择器场景: 拉前 200 个 workspace,容忍 records / list / 直接数组三种响应形态,
 * 投影到 {id, name}。错误吞掉(交给 request 拦截器统一处理 toast)。
 */
export async function loadWorkspaceOptions(): Promise<WorkspaceOption[]> {
  try {
    const res = (await listWorkspaces({ pageSize: 200 })) as any
    const list = Array.isArray(res?.data)
      ? res.data
      : (res?.data?.records ?? res?.data?.list ?? [])
    return list.map((w: any) => ({ id: w.id, name: w.name }))
  } catch {
    return []
  }
}

export function getWorkspace(id: number) {
  return request.get(`/workspaces/${id}`)
}

export function createWorkspace(data: { name: string; description?: string }) {
  return request.post('/workspaces', data)
}

export function listProjects(workspaceId: number, params?: { searchVal?: string; pageNum?: number; pageSize?: number }) {
  return request.get(`/workspaces/${workspaceId}/projects`, { params })
}

export function getProject(workspaceId: number, code: number | string) {
  return request.get(`/workspaces/${workspaceId}/projects/${code}`)
}

export function createProject(workspaceId: number, data: { name: string; description?: string; params?: any[] }) {
  return request.post(`/workspaces/${workspaceId}/projects`, data)
}

export function updateProject(workspaceId: number, code: number | string, data: { name: string; description?: string; params?: any[] }) {
  return request.put(`/workspaces/${workspaceId}/projects/${code}`, data)
}

export function deleteProject(workspaceId: number, code: number | string) {
  return request.delete(`/workspaces/${workspaceId}/projects/${code}`)
}

// Members
export function listMembers(workspaceId: number) {
  return request.get(`/workspaces/${workspaceId}/members`)
}

export function addMember(workspaceId: number, data: { userId: number; role: string }) {
  return request.post(`/workspaces/${workspaceId}/members`, data)
}

export function updateMemberRole(workspaceId: number, userId: number, role: string) {
  return request.put(`/workspaces/${workspaceId}/members/${userId}`, { role })
}

export function removeMember(workspaceId: number, userId: number) {
  return request.delete(`/workspaces/${workspaceId}/members/${userId}`)
}

export function deleteWorkspace(id: number) {
  return request.delete(`/workspaces/${id}`)
}

export function updateProjectOwner(workspaceId: number, projectCode: number | string, userId: number) {
  return request.put(`/workspaces/${workspaceId}/projects/${projectCode}/owner`, { userId })
}

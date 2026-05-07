import request from '@/utils/request'

// Services
export function listServices() {
  return request.get('/admin/services')
}

// Users
export function listUsers(params?: { searchVal?: string; pageNum?: number; pageSize?: number }) {
  return request.get('/admin/users', { params })
}

export function getUser(id: number) {
  return request.get(`/admin/users/${id}`)
}

export function createUser(data: { username: string; password: string; email?: string }) {
  return request.post('/admin/users', data)
}

export function updateUserEmail(id: number, email: string) {
  return request.put(`/admin/users/${id}/email`, { email })
}

export function resetUserPassword(id: number, password: string) {
  return request.put(`/admin/users/${id}/reset-password`, { password })
}

export function toggleSuperAdmin(id: number, isSuperAdmin: boolean) {
  return request.put(`/admin/users/${id}/super-admin`, { isSuperAdmin })
}

export function deleteUser(id: number) {
  return request.delete(`/admin/users/${id}`)
}

export function listUsersSimple() {
  return request.get('/admin/users/simple')
}

export function listUserWorkspaces(id: number) {
  return request.get(`/admin/users/${id}/workspaces`)
}

// Audit Logs
export function listAuditLogs(params?: {
  module?: string; action?: string; username?: string
  startTime?: string; endTime?: string
  pageNum?: number; pageSize?: number
}) {
  return request.get('/admin/audit-logs', { params })
}

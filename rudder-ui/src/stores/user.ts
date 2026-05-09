import { defineStore } from 'pinia'

export type Role = 'SUPER_ADMIN' | 'WORKSPACE_OWNER' | 'DEVELOPER' | 'VIEWER'

interface UserInfo {
  userId: number
  username: string
  role: Role
  workspaceId?: number
}

function loadUserInfo(): UserInfo | null {
  try {
    const raw = localStorage.getItem('rudder_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function loadNumber(key: string): number | null {
  const raw = localStorage.getItem(key)
  if (!raw) return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('rudder_token') || '',
    userInfo: loadUserInfo(),
    currentWorkspaceId: loadNumber('rudder_workspace_id'),
    currentProjectCode: loadNumber('rudder_project_code'),
  }),
  actions: {
    setToken(token: string) {
      this.token = token
      localStorage.setItem('rudder_token', token)
    },
    setUserInfo(info: UserInfo) {
      this.userInfo = info
      localStorage.setItem('rudder_user', JSON.stringify(info))
    },
    setWorkspace(workspaceId: number) {
      this.currentWorkspaceId = workspaceId
      localStorage.setItem('rudder_workspace_id', String(workspaceId))
    },
    setProject(projectCode: number) {
      this.currentProjectCode = projectCode
      localStorage.setItem('rudder_project_code', String(projectCode))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.currentWorkspaceId = null
      this.currentProjectCode = null
      localStorage.removeItem('rudder_token')
      localStorage.removeItem('rudder_user')
      localStorage.removeItem('rudder_workspace_id')
      localStorage.removeItem('rudder_project_code')
    },
  },
})
